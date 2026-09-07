package com.bettingproject.sofascorelocal.application.network.playwright;

import com.bettingproject.sofascorelocal.config.ProviderPlaywrightProperties;
import com.bettingproject.sofascorelocal.config.SofascoreProperties;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

/** Owns exactly one isolated Playwright worker process for the current manual campaign. */
@Component
public final class ChildJvmPlaywrightProviderSupervisor
        implements PlaywrightProviderCampaignFactory, PlaywrightProviderSupervisor {

    static final int MAGIC = 0x53335057;
    static final int VERSION = 5;
    static final byte GET = 1;
    static final byte CLOSE = 2;
    static final byte START = 3;
    static final byte RESPONSE = 10;
    static final byte FAILURE = 11;
    static final byte CLOSED = 12;
    static final byte READY = 13;
    static final int MAXIMUM_CONTENT_TYPE_BYTES = 160;
    static final int MAXIMUM_FAILURE_CODE_LENGTH = 64;
    static final Duration STOP_ACKNOWLEDGEMENT_MAX = Duration.ofMillis(500);
    static final Duration SOFT_PROCESS_TERMINATION_MAX = Duration.ofSeconds(1);
    static final Duration IN_FLIGHT_CANCELLATION_MAX = Duration.ofSeconds(2);
    static final Duration PROCESS_TREE_CLEANUP_MAX = Duration.ofSeconds(5);
    static final Duration DEFAULT_MINIMUM_PROVIDER_NETWORK_START_DELAY = Duration.ofSeconds(3);
    static final int MAXIMUM_STOP_TOMBSTONES_PER_ALLOWLIST = 256;

    private static final Duration NATURAL_PROCESS_EXIT_MAX = Duration.ofMillis(250);
    private static final Duration PROCESS_TREE_POLL_INTERVAL = Duration.ofMillis(20);

    private static final String IPC_PORT = "SOFASCORE_PLAYWRIGHT_IPC_PORT";
    private static final String IPC_TOKEN = "SOFASCORE_PLAYWRIGHT_IPC_TOKEN";
    private static final String LOOPBACK_QUALIFICATION =
            "SOFASCORE_PLAYWRIGHT_LOOPBACK_QUALIFICATION";
    private static final String LOOPBACK_ORIGIN = "SOFASCORE_PLAYWRIGHT_LOOPBACK_ORIGIN";
    private static final String SKIP_BROWSER_DOWNLOAD =
            "PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD";
    private static final Set<SofascoreEndpointType> IMPLEMENTED_ENDPOINTS = Set.of(
            SofascoreEndpointType.SCHEDULED_EVENTS,
            SofascoreEndpointType.TOURNAMENT_SCHEDULED_EVENTS,
            SofascoreEndpointType.EVENT_DETAILS,
            SofascoreEndpointType.EVENT_STATISTICS,
            SofascoreEndpointType.EVENT_INCIDENTS,
            SofascoreEndpointType.EVENT_LINEUPS);
    private static final List<String> SAFE_INHERITED_ENVIRONMENT = List.of(
            "SystemRoot", "WINDIR", "PATH", "PATHEXT", "TEMP", "TMP",
            "USERPROFILE", "LOCALAPPDATA", "ProgramFiles", "ProgramFiles(x86)",
            "HOME", "TMPDIR", "LANG", "JAVA_HOME", "PLAYWRIGHT_BROWSERS_PATH");

    private final ProviderPlaywrightProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom;
    private final ProcessStarter processStarter;
    private final ProcessTreeAccess processTreeAccess;
    private final ProviderNetworkStartDelayGate providerNetworkStartDelayGate;
    private final AtomicReference<CampaignState> active = new AtomicReference<>();
    private final AtomicReference<SupervisorLifecycle> lifecycle =
            new AtomicReference<>(SupervisorLifecycle.OPEN);
    private final ConcurrentMap<Set<SofascoreEndpointType>, StopTombstones> stopTombstones =
            new ConcurrentHashMap<>();

    public ChildJvmPlaywrightProviderSupervisor(ProviderPlaywrightProperties properties) {
        this(
                properties,
                Clock.systemUTC(),
                new SecureRandom(),
                ProcessBuilder::start,
                new SystemProcessTreeAccess(),
                DEFAULT_MINIMUM_PROVIDER_NETWORK_START_DELAY);
    }

    @Autowired
    public ChildJvmPlaywrightProviderSupervisor(
            ProviderPlaywrightProperties properties,
            SofascoreProperties sofascoreProperties) {
        this(
                properties,
                Clock.systemUTC(),
                new SecureRandom(),
                ProcessBuilder::start,
                new SystemProcessTreeAccess(),
                Objects.requireNonNull(sofascoreProperties, "sofascoreProperties")
                        .getMinimumDelay());
    }

    ChildJvmPlaywrightProviderSupervisor(
            ProviderPlaywrightProperties properties,
            Clock clock,
            SecureRandom secureRandom,
            ProcessStarter processStarter) {
        this(
                properties,
                clock,
                secureRandom,
                processStarter,
                new SystemProcessTreeAccess(),
                DEFAULT_MINIMUM_PROVIDER_NETWORK_START_DELAY);
    }

    ChildJvmPlaywrightProviderSupervisor(
            ProviderPlaywrightProperties properties,
            Clock clock,
            SecureRandom secureRandom,
            ProcessStarter processStarter,
            ProcessTreeAccess processTreeAccess) {
        this(
                properties,
                clock,
                secureRandom,
                processStarter,
                processTreeAccess,
                DEFAULT_MINIMUM_PROVIDER_NETWORK_START_DELAY);
    }

    ChildJvmPlaywrightProviderSupervisor(
            ProviderPlaywrightProperties properties,
            Clock clock,
            SecureRandom secureRandom,
            ProcessStarter processStarter,
            ProcessTreeAccess processTreeAccess,
            Duration minimumProviderNetworkStartDelay) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
        this.processStarter = Objects.requireNonNull(processStarter, "processStarter");
        this.processTreeAccess = Objects.requireNonNull(
                processTreeAccess, "processTreeAccess");
        this.providerNetworkStartDelayGate = new ProviderNetworkStartDelayGate(
                Objects.requireNonNull(
                        minimumProviderNetworkStartDelay,
                        "minimumProviderNetworkStartDelay"),
                this.processTreeAccess::nanoTime,
                this.processTreeAccess::pause);
    }

    @Override
    public PlaywrightProviderCampaign open(
            UUID campaignId,
            Set<SofascoreEndpointType> allowedEndpoints) {
        Objects.requireNonNull(campaignId, "campaignId");
        Set<SofascoreEndpointType> allowlist = Set.copyOf(
                Objects.requireNonNull(allowedEndpoints, "allowedEndpoints"));
        if (allowlist.isEmpty() || !IMPLEMENTED_ENDPOINTS.containsAll(allowlist)) {
            throw new PlaywrightProviderException(PlaywrightProviderFailure.INVALID_ENDPOINT);
        }
        if (!properties.isEnabled()) {
            throw new PlaywrightProviderException(PlaywrightProviderFailure.DISABLED);
        }
        requireSupervisorOpen();
        if (active.get() != null) {
            throw new PlaywrightProviderException(
                    PlaywrightProviderFailure.CAMPAIGN_ALREADY_ACTIVE);
        }

        StopTombstones tombstones = stopTombstonesFor(allowlist);
        if (tombstones.blocks(campaignId)) {
            throw new PlaywrightProviderException(PlaywrightProviderFailure.OPERATOR_STOP);
        }
        Path workerJar = requireWorkerJar();
        CampaignState state = new CampaignState(campaignId, allowlist);
        if (!active.compareAndSet(null, state)) {
            throw new PlaywrightProviderException(
                    PlaywrightProviderFailure.CAMPAIGN_ALREADY_ACTIVE);
        }
        try {
            requireStartupAllowed(state, tombstones, campaignId);
            ServerSocket server = openLoopbackServer();
            state.server = server;
            requireStartupAllowed(state, tombstones, campaignId);
            String token = newToken();
            requireStartupAllowed(state, tombstones, campaignId);
            Process process = startWorker(workerJar, server.getLocalPort(), token);
            Instant processStartedAt = process.info().startInstant().orElse(null);
            state.publishProcess(process, processStartedAt);
            if (processStartedAt == null) {
                process.destroyForcibly();
                throw new PlaywrightProviderException(
                        PlaywrightProviderFailure.RUNTIME_FAILURE);
            }
            requireStartupAllowed(state, tombstones, campaignId);

            Socket socket = server.accept();
            requireStartupAllowed(state, tombstones, campaignId);
            requireExactLoopbackPeer(socket);
            socket.setTcpNoDelay(true);
            socket.setKeepAlive(false);
            socket.setSoTimeout(toMillis(properties.getStartupTimeout()));
            DataInputStream input = new DataInputStream(
                    new BufferedInputStream(socket.getInputStream()));
            DataOutputStream output = new DataOutputStream(
                    new BufferedOutputStream(socket.getOutputStream()));
            state.attach(socket, input, output);
            authenticate(input, token);
            requireStartupAllowed(state, tombstones, campaignId);
            startWorkerRuntime(input, output);
            requireStartupAllowed(state, tombstones, campaignId);
            ProcessRegistration registration = state.processRegistration.getNow(null);
            ProcessTreeSnapshot initialInventory = captureInitialProcessInventory(
                    state,
                    Objects.requireNonNull(registration, "process registration"));
            if (!initialInventory.isExactFor(registration)) {
                throw new PlaywrightProviderException(
                        PlaywrightProviderFailure.RUNTIME_FAILURE);
            }
            requireStartupAllowed(state, tombstones, campaignId);
            socket.setSoTimeout(toMillis(properties.getRequestTimeout().plusSeconds(1)));
            closeQuietly(server);
            state.server = null;
            requireStartupAllowed(state, tombstones, campaignId);
            state.phase.set(CampaignPhase.READY);
            return new Campaign(this, state);
        }
        catch (PlaywrightProviderException exception) {
            state.publishNoProcess();
            PlaywrightProviderException reported = stoppedDuringOpen(
                    state, tombstones, campaignId)
                            && exception.failure() != PlaywrightProviderFailure.OPERATOR_STOP
                                    ? new PlaywrightProviderException(
                                            PlaywrightProviderFailure.OPERATOR_STOP,
                                            exception)
                                    : exception;
            throw terminateFailedOpen(state, reported);
        }
        catch (SocketTimeoutException exception) {
            state.publishNoProcess();
            boolean stopped = stoppedDuringOpen(state, tombstones, campaignId);
            throw terminateFailedOpen(state, new PlaywrightProviderException(
                    stopped
                            ? PlaywrightProviderFailure.OPERATOR_STOP
                            : PlaywrightProviderFailure.STARTUP_FAILED,
                    exception));
        }
        catch (IOException | RuntimeException exception) {
            state.publishNoProcess();
            boolean stopped = stoppedDuringOpen(state, tombstones, campaignId);
            throw terminateFailedOpen(state, new PlaywrightProviderException(
                    stopped
                            ? PlaywrightProviderFailure.OPERATOR_STOP
                            : PlaywrightProviderFailure.STARTUP_FAILED,
                    exception));
        }
    }

    private void requireSupervisorOpen() {
        if (lifecycle.get() != SupervisorLifecycle.OPEN) {
            throw new PlaywrightProviderException(PlaywrightProviderFailure.OPERATOR_STOP);
        }
    }

    private void requireStartupAllowed(
            CampaignState state,
            StopTombstones tombstones,
            UUID campaignId) {
        if (lifecycle.get() != SupervisorLifecycle.OPEN
                || active.get() != state
                || tombstones.blocks(campaignId)
                || state.terminationRequested.get()) {
            throw new PlaywrightProviderException(PlaywrightProviderFailure.OPERATOR_STOP);
        }
    }

    private PlaywrightProviderException terminateFailedOpen(
            CampaignState state,
            PlaywrightProviderException reported) {
        if (active.get() != state) {
            return reported;
        }
        try {
            terminateSynchronously(state, false);
            return reported;
        }
        catch (PlaywrightProviderException cleanupFailure) {
            cleanupFailure.addSuppressed(reported);
            return cleanupFailure;
        }
    }

    private static boolean stoppedDuringOpen(
            CampaignState state,
            StopTombstones tombstones,
            UUID campaignId) {
        return tombstones.blocks(campaignId)
                || state != null && state.terminationRequested.get();
    }

    @Override
    public PlaywrightProviderStopReceipt stopCampaign(
            UUID campaignId,
            Set<SofascoreEndpointType> allowedEndpoints) {
        CampaignTarget target = new CampaignTarget(campaignId, allowedEndpoints);
        long requestedAtNanos = processTreeAccess.nanoTime();
        stopTombstonesFor(target.allowedEndpoints()).record(target.campaignId());
        CampaignState state = active.get();
        if (state == null || !target.matches(state)) {
            Instant acknowledgedAt = clock.instant();
            return new PlaywrightProviderStopReceipt(
                    null,
                    false,
                    acknowledgedAt,
                    elapsed(requestedAtNanos, processTreeAccess.nanoTime()));
        }
        state.requestOperatorStop(requestedAtNanos, processTreeAccess);
        Thread.ofVirtual()
                .name("provider-playwright-stop-" + state.campaignId)
                .start(() -> terminateAfterStopSignal(state));
        Instant acknowledgedAt = clock.instant();
        Duration latency = elapsed(requestedAtNanos, processTreeAccess.nanoTime());
        if (latency.compareTo(STOP_ACKNOWLEDGEMENT_MAX) > 0) {
            throw new PlaywrightProviderException(PlaywrightProviderFailure.RUNTIME_FAILURE);
        }
        return new PlaywrightProviderStopReceipt(
                state.campaignId,
                true,
                acknowledgedAt,
                latency);
    }

    @Override
    public Optional<UUID> activeCampaignId() {
        CampaignState state = active.get();
        return state == null ? Optional.empty() : Optional.of(state.campaignId);
    }

    @PreDestroy
    void shutdown() {
        if (!lifecycle.compareAndSet(
                SupervisorLifecycle.OPEN,
                SupervisorLifecycle.CLOSING)) {
            return;
        }
        CampaignState state = active.get();
        try {
            if (state != null) {
                state.requestOperatorStop(
                        processTreeAccess.nanoTime(),
                        processTreeAccess);
                try {
                    terminateSynchronously(state, false);
                }
                catch (PlaywrightProviderException ignored) {
                    // The exact state remains published when ownership or cleanup is inconclusive.
                }
            }
        }
        finally {
            lifecycle.set(SupervisorLifecycle.CLOSED);
        }
    }

    private PlaywrightProviderResponse execute(
            CampaignState state,
            PlaywrightProviderRequest request,
            PlaywrightDispatchAdmission admission) {
        Objects.requireNonNull(request, "request");
        requireActive(state);
        if (!state.allowedEndpoints.contains(request.endpoint())) {
            throw new PlaywrightProviderException(PlaywrightProviderFailure.INVALID_ENDPOINT);
        }
        state.ioLock.lock();
        boolean dispatchStarted = false;
        boolean usableResponseEvidence = false;
        try {
            requireActive(state);
            try {
                DataOutputStream output = Objects.requireNonNull(state.output, "output");
                DataInputStream input = Objects.requireNonNull(state.input, "input");
                providerNetworkStartDelayGate.awaitNextDispatch(() -> { requireActive(state); admission.check(); });
                try (PlaywrightDispatchAdmission.Permit permit = admission.acquireDispatchPermit()) {
                    // Admission may perform durable checks. Never hold the supervisor stop lock during SQL.
                    state.dispatchLock.lock();
                    try {
                    requireActive(state);
                    state.providerDispatchStarted.set(true);
                    dispatchStarted = true;
                    output.writeByte(GET);
                    output.writeUTF(request.endpoint().name());
                    switch (request.endpoint()) {
                        case SCHEDULED_EVENTS -> {
                            output.writeUTF(request.date().toString());
                            output.writeInt(request.page());
                        }
                        case TOURNAMENT_SCHEDULED_EVENTS -> {
                            output.writeUTF(request.date().toString());
                            output.writeLong(request.uniqueTournamentId());
                        }
                        case EVENT_DETAILS, EVENT_STATISTICS, EVENT_INCIDENTS, EVENT_LINEUPS ->
                                output.writeLong(request.eventId());
                        default -> throw new PlaywrightProviderException(
                                PlaywrightProviderFailure.INVALID_ENDPOINT);
                    }
                    output.writeInt(toMillis(properties.getRequestTimeout()));
                    output.flush();
                    } finally {
                        state.dispatchLock.unlock();
                    }
                }
                int frame = input.readUnsignedByte();
                if (frame == FAILURE) {
                    PlaywrightProviderException failure = workerFailure(input.readUTF());
                    state.authenticatedTerminalFrameReceived.set(true);
                    throw failure;
                }
                if (frame != RESPONSE) {
                    throw new PlaywrightProviderException(
                            PlaywrightProviderFailure.PROTOCOL_ERROR);
                }
                long requestedAtEpochMillis = input.readLong();
                long receivedAtEpochMillis = input.readLong();
                if (requestedAtEpochMillis <= 0
                        || receivedAtEpochMillis < requestedAtEpochMillis) {
                    throw new PlaywrightProviderException(
                            PlaywrightProviderFailure.PROTOCOL_ERROR);
                }
                Instant requestedAt = Instant.ofEpochMilli(requestedAtEpochMillis);
                Instant receivedAt = Instant.ofEpochMilli(receivedAtEpochMillis);
                int status = input.readInt();
                String contentType = input.readUTF();
                requireContentType(contentType);
                int length = input.readInt();
                if (length < 0 || length > RawPayloadEvidence.MAXIMUM_BYTES) {
                    throw new PlaywrightProviderException(
                            PlaywrightProviderFailure.PAYLOAD_TOO_LARGE);
                }
                byte[] body = input.readNBytes(length);
                if (body.length != length) {
                    Arrays.fill(body, (byte) 0);
                    throw new PlaywrightProviderException(
                            PlaywrightProviderFailure.PROTOCOL_ERROR);
                }
                try {
                    RawPayloadEvidence payload;
                    try {
                        payload = RawPayloadEvidence.capture(body);
                    }
                    catch (IllegalArgumentException exception) {
                        throw new PlaywrightProviderException(
                                PlaywrightProviderFailure.SENSITIVE_CONTENT_REJECTED);
                    }
                    Duration latency = Duration.between(requestedAt, receivedAt);
                    PlaywrightProviderResponse response = new PlaywrightProviderResponse(
                            requestedAt,
                            receivedAt,
                            status,
                            contentType,
                            latency,
                            payload);
                    usableResponseEvidence = true;
                    return response;
                }
                finally {
                    Arrays.fill(body, (byte) 0);
                }
            }
            catch (PlaywrightDispatchCancelledException exception) {
                throw exception;
            }
            catch (PlaywrightProviderException exception) {
                throw exception;
            }
            catch (ProviderNetworkStartDelayGate.TimingEvidenceException exception) {
                throw new PlaywrightProviderException(
                        PlaywrightProviderFailure.RUNTIME_FAILURE,
                        exception);
            }
            catch (IOException | RuntimeException exception) {
                PlaywrightProviderFailure failure = state.terminationRequested.get()
                        ? PlaywrightProviderFailure.OPERATOR_STOP
                        : PlaywrightProviderFailure.PROTOCOL_ERROR;
                throw new PlaywrightProviderException(failure, exception);
            }
        }
        finally {
            if (dispatchStarted) {
                providerNetworkStartDelayGate.recordDispatchFinished(
                        usableResponseEvidence);
            }
            state.ioLock.unlock();
        }
    }

    private void closeCampaign(CampaignState state) {
        if (active.get() != state) {
            return;
        }
        state.requestTermination(processTreeAccess);
        boolean requestGracefulClose = !state.operatorStopRequested.get();
        terminateSynchronously(state, requestGracefulClose);
    }

    private void terminateAfterStopSignal(CampaignState state) {
        try {
            terminateSynchronously(state, false);
        }
        catch (PlaywrightProviderException ignored) {
            // activeCampaignId deliberately remains observable after a failed cleanup.
        }
    }

    private void terminateSynchronously(CampaignState state, boolean requestGracefulClose) {
        state.requestTermination(processTreeAccess);
        long terminationStartedAt = state.markTerminationStarted(processTreeAccess);
        synchronized (state.cleanupLock) {
            if (state.cleanupCompleted) {
                return;
            }
            if (state.cleanupTerminalFailure != null) {
                throw state.cleanupTerminalFailure;
            }
            boolean gracefulAttempt = requestGracefulClose
                    && !state.operatorStopRequested.get();
            long cleanupStartedAt = gracefulAttempt
                    ? processTreeAccess.nanoTime()
                    : state.operatorStopStartedAtOr(terminationStartedAt);
            AtomicBoolean mutationStarted = new AtomicBoolean();
            Throwable cleanupFailure = null;
            try {
                ProcessRegistration registration = awaitProcessRegistration(
                        state,
                        cleanupStartedAt);
                state.phase.set(CampaignPhase.TERMINATING);
                if (registration.processLaunched()) {
                    if (!registration.identityComplete()) {
                        mutationStarted.set(true);
                        registration.process().destroyForcibly();
                        throw new PlaywrightProviderException(
                                PlaywrightProviderFailure.RUNTIME_FAILURE);
                    }
                    ProcessTreeSnapshot initialInventory = captureInitialProcessInventory(
                            state,
                            registration);
                    boolean authenticatedWorkerExitExpected =
                            state.authenticatedTerminalFrameReceived.get();
                    ProcessTreeSnapshot terminationInventory = captureTerminationProcessInventory(
                            registration,
                            initialInventory,
                            authenticatedWorkerExitExpected);
                    boolean gracefulCloseMode = gracefulAttempt;
                    boolean closeAcknowledged = false;
                    boolean parentTerminationSignalled = false;
                    if (gracefulCloseMode) {
                        closeAcknowledged = requestClose(
                                state,
                                mutationStarted,
                                cleanupStartedAt);
                        authenticatedWorkerExitExpected |= closeAcknowledged;
                        terminationInventory = captureTerminationProcessInventory(
                                registration,
                                terminationInventory,
                                authenticatedWorkerExitExpected);
                    }
                    gracefulCloseMode &= !state.operatorStopRequested.get();
                    if (closeAcknowledged) {
                        parentTerminationSignalled = signalParentTermination(
                                state,
                                mutationStarted);
                    }
                    long processTerminationStartedAt = gracefulCloseMode
                            ? processTreeAccess.nanoTime()
                            : state.operatorStopStartedAtOr(terminationStartedAt);
                    boolean naturalTerminationExpected = gracefulCloseMode
                            && closeAcknowledged
                            && parentTerminationSignalled;
                    ProcessTreeCleanupOutcome outcome = terminateOwnedProcessTree(
                            registration.process(),
                            registration.processStartedAt(),
                            terminationInventory,
                            processTreeAccess,
                            cleanupStartedAt,
                            processTerminationStartedAt,
                            mutationStarted,
                            authenticatedWorkerExitExpected,
                            naturalTerminationExpected);
                    boolean operatorStopMode = !gracefulCloseMode
                            || state.operatorStopRequested.get();
                    long cancellationObservedAt = deadline(
                            processTerminationStartedAt,
                            outcome.cancellationLatency());
                    boolean operatorCancellationWithinBound = !operatorStopMode
                            || cancellationObservedAt <= deadline(
                                    state.operatorStopStartedAtOr(terminationStartedAt),
                                    IN_FLIGHT_CANCELLATION_MAX);
                    if ((!operatorStopMode && !authenticatedWorkerExitExpected)
                            || (closeAcknowledged && !parentTerminationSignalled)
                            || !outcome.identityComplete()
                            || !operatorCancellationWithinBound
                            || outcome.cleanupLatency().compareTo(PROCESS_TREE_CLEANUP_MAX) > 0
                            || outcome.residualOwnedProcessCount() != 0) {
                        throw new PlaywrightProviderException(
                                PlaywrightProviderFailure.RUNTIME_FAILURE);
                    }
                }
                else {
                    closeQuietly(state.socket);
                    closeQuietly(state.server);
                }
                if (processTreeAccess.nanoTime()
                        > deadline(cleanupStartedAt, PROCESS_TREE_CLEANUP_MAX)) {
                    throw new PlaywrightProviderException(
                            PlaywrightProviderFailure.RUNTIME_FAILURE);
                }
            }
            catch (RuntimeException | Error failure) {
                cleanupFailure = failure;
                PlaywrightProviderException reported = asProviderException(failure);
                if (mutationStarted.get()) {
                    state.cleanupTerminalFailure = reported;
                }
                if (failure instanceof Error error) {
                    throw error;
                }
                throw reported;
            }
            finally {
                if (cleanupFailure == null || mutationStarted.get()) {
                    closeQuietly(state.input);
                    closeQuietly(state.output);
                    closeQuietly(state.socket);
                    closeQuietly(state.server);
                }
                if (state.providerDispatchStarted.get()) {
                    providerNetworkStartDelayGate.recordDispatchFinished(true);
                }
                if (cleanupFailure == null) {
                    state.cleanupCompleted = true;
                    active.compareAndSet(state, null);
                }
            }
        }
    }

    private static PlaywrightProviderException asProviderException(Throwable failure) {
        return failure instanceof PlaywrightProviderException providerFailure
                ? providerFailure
                : new PlaywrightProviderException(
                        PlaywrightProviderFailure.RUNTIME_FAILURE,
                        failure);
    }

    private ProcessTreeSnapshot captureInitialProcessInventory(
            CampaignState state,
            ProcessRegistration registration) {
        if (!registration.identityComplete()) {
            throw new PlaywrightProviderException(
                    PlaywrightProviderFailure.RUNTIME_FAILURE);
        }
        synchronized (state.processInventoryLock) {
            if (state.initialProcessInventory != null) {
                return state.initialProcessInventory;
            }
            ProcessTreeSnapshot captured;
            try {
                captured = processTreeAccess.capture(
                        registration.process(),
                        registration.processStartedAt());
            }
            catch (RuntimeException exception) {
                throw new PlaywrightProviderException(
                        PlaywrightProviderFailure.RUNTIME_FAILURE,
                        exception);
            }
            if (!captured.isExactFor(registration)) {
                return ProcessTreeSnapshot.rootNotAuthenticated(
                        Math.max(1, captured.unverifiedAliveProcessCount()));
            }
            state.initialProcessInventory = captured;
            return captured;
        }
    }

    private ProcessTreeSnapshot captureTerminationProcessInventory(
            ProcessRegistration registration,
            ProcessTreeSnapshot initialInventory,
            boolean authenticatedWorkerExitExpected) {
        if (!initialInventory.isExactFor(registration)) {
            throw new PlaywrightProviderException(
                    PlaywrightProviderFailure.RUNTIME_FAILURE);
        }
        ProcessTreeSnapshot current;
        try {
            current = processTreeAccess.capture(
                    registration.process(),
                    registration.processStartedAt());
        }
        catch (RuntimeException exception) {
            throw new PlaywrightProviderException(
                    PlaywrightProviderFailure.RUNTIME_FAILURE,
                    exception);
        }
        if (!current.isExactFor(registration)
                && !(authenticatedWorkerExitExpected && current.rootObservedAbsent())) {
            throw new PlaywrightProviderException(
                    PlaywrightProviderFailure.RUNTIME_FAILURE);
        }
        Map<OwnedProcessIdentity, OwnedProcess> retained = new LinkedHashMap<>();
        mergeOwned(retained, initialInventory.ownedProcesses());
        mergeOwned(retained, current.ownedProcesses());
        return ProcessTreeSnapshot.exact(new ArrayList<>(retained.values()));
    }

    private ProcessRegistration awaitProcessRegistration(
            CampaignState state,
            long cleanupStartedAt) {
        ProcessRegistration immediate = state.processRegistration.getNow(null);
        if (immediate != null) {
            return immediate;
        }
        long remaining = deadline(cleanupStartedAt, PROCESS_TREE_CLEANUP_MAX)
                - processTreeAccess.nanoTime();
        if (remaining <= 0) {
            throw new PlaywrightProviderException(PlaywrightProviderFailure.RUNTIME_FAILURE);
        }
        try {
            return state.processRegistration.get(remaining, TimeUnit.NANOSECONDS);
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PlaywrightProviderException(
                    PlaywrightProviderFailure.RUNTIME_FAILURE,
                    exception);
        }
        catch (ExecutionException | TimeoutException exception) {
            throw new PlaywrightProviderException(
                    PlaywrightProviderFailure.RUNTIME_FAILURE,
                    exception);
        }
    }

    private boolean requestClose(
            CampaignState state,
            AtomicBoolean mutationStarted,
            long cleanupStartedAt) {
        if (!acquireCloseIoLock(state, cleanupStartedAt)) {
            return false;
        }
        try {
            if (state.socket == null || state.socket.isClosed()) {
                return false;
            }
            try {
                long cleanupDeadline = deadline(
                        cleanupStartedAt,
                        PROCESS_TREE_CLEANUP_MAX);
                long remainingNanos = cleanupDeadline - processTreeAccess.nanoTime();
                if (remainingNanos < TimeUnit.MILLISECONDS.toNanos(1)) {
                    return false;
                }
                Duration remainingCleanup = Duration.ofNanos(remainingNanos);
                Duration closeTimeout = properties.getGracefulCloseTimeout()
                        .compareTo(remainingCleanup) < 0
                                ? properties.getGracefulCloseTimeout()
                                : remainingCleanup;
                long closeDeadline = deadline(processTreeAccess.nanoTime(), closeTimeout);
                mutationStarted.set(true);
                state.output.writeByte(CLOSE);
                state.output.flush();
                while (!state.operatorStopRequested.get()) {
                    long remainingCloseNanos = Math.min(
                            closeDeadline,
                            cleanupDeadline) - processTreeAccess.nanoTime();
                    if (remainingCloseNanos < TimeUnit.MILLISECONDS.toNanos(1)) {
                        return false;
                    }
                    Duration readSlice = Duration.ofNanos(Math.min(
                            PROCESS_TREE_POLL_INTERVAL.toNanos(),
                            remainingCloseNanos));
                    state.socket.setSoTimeout(toMillis(readSlice));
                    try {
                        return state.input.readUnsignedByte() == CLOSED;
                    }
                    catch (SocketTimeoutException timeout) {
                        // Recheck an operator-stop upgrade before the next bounded read.
                    }
                }
                return false;
            }
            catch (IOException | RuntimeException exception) {
                return false;
            }
        }
        finally {
            state.ioLock.unlock();
        }
    }

    private boolean acquireCloseIoLock(CampaignState state, long cleanupStartedAt) {
        long cleanupDeadline = deadline(cleanupStartedAt, PROCESS_TREE_CLEANUP_MAX);
        while (!state.operatorStopRequested.get()) {
            long remainingNanos = cleanupDeadline - processTreeAccess.nanoTime();
            if (remainingNanos < TimeUnit.MILLISECONDS.toNanos(1)) {
                return false;
            }
            long waitNanos = Math.min(
                    PROCESS_TREE_POLL_INTERVAL.toNanos(),
                    remainingNanos);
            try {
                if (state.ioLock.tryLock(waitNanos, TimeUnit.NANOSECONDS)) {
                    return true;
                }
            }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private static boolean signalParentTermination(
            CampaignState state,
            AtomicBoolean mutationStarted) {
        state.ioLock.lock();
        try {
            Socket socket = state.socket;
            if (socket == null) {
                return false;
            }
            if (socket.isClosed() || socket.isOutputShutdown()) {
                return true;
            }
            try {
                mutationStarted.set(true);
                socket.shutdownOutput();
                return true;
            }
            catch (IOException | RuntimeException exception) {
                return false;
            }
        }
        finally {
            state.ioLock.unlock();
        }
    }

    private void requireActive(CampaignState state) {
        ProcessRegistration registration = state.processRegistration.getNow(null);
        if (active.get() != state
                || state.terminationRequested.get()
                || lifecycle.get() != SupervisorLifecycle.OPEN
                || registration == null
                || !registration.identityComplete()
                || !sameProcess(
                        registration.process().toHandle(),
                        registration.processStartedAt())) {
            throw new PlaywrightProviderException(PlaywrightProviderFailure.OPERATOR_STOP);
        }
    }

    private Path requireWorkerJar() {
        Path configured = properties.getWorkerJar();
        if (configured == null) {
            throw new PlaywrightProviderException(
                    PlaywrightProviderFailure.WORKER_ARTIFACT_INVALID);
        }
        try {
            Path real = configured.toAbsolutePath().normalize().toRealPath();
            if (!Files.isRegularFile(real)
                    || !real.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                throw new PlaywrightProviderException(
                        PlaywrightProviderFailure.WORKER_ARTIFACT_INVALID);
            }
            return real;
        }
        catch (IOException exception) {
            throw new PlaywrightProviderException(
                    PlaywrightProviderFailure.WORKER_ARTIFACT_INVALID, exception);
        }
    }

    private ServerSocket openLoopbackServer() throws IOException {
        ServerSocket server = new ServerSocket();
        server.setReuseAddress(false);
        server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 1);
        server.setSoTimeout(toMillis(properties.getStartupTimeout()));
        return server;
    }

    private Process startWorker(Path workerJar, int port, String token) throws IOException {
        Path java = Path.of(
                System.getProperty("java.home"),
                "bin",
                isWindows() ? "java.exe" : "java").toAbsolutePath().normalize();
        ProcessBuilder builder = new ProcessBuilder(
                java.toString(),
                "-Xms32m",
                "-Xmx" + properties.getMaximumHeapMib() + "m",
                "-jar",
                workerJar.toString());
        builder.redirectInput(ProcessBuilder.Redirect.PIPE);
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        Map<String, String> environment = builder.environment();
        environment.clear();
        copySafeEnvironment(environment);
        environment.put(SKIP_BROWSER_DOWNLOAD, "1");
        environment.put(IPC_PORT, Integer.toString(port));
        environment.put(IPC_TOKEN, token);
        if (properties.isLoopbackQualification()) {
            environment.put(LOOPBACK_QUALIFICATION, "true");
            environment.put(LOOPBACK_ORIGIN, properties.getLoopbackOrigin());
        }
        return processStarter.start(builder);
    }

    private static void copySafeEnvironment(Map<String, String> target) {
        Map<String, String> source = System.getenv();
        for (String name : SAFE_INHERITED_ENVIRONMENT) {
            String value = source.get(name);
            if (value != null && !value.isBlank()) {
                target.put(name, value);
            }
        }
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        }
        finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    private static void authenticate(DataInputStream input, String expectedToken)
            throws IOException {
        int magic = input.readInt();
        int version = input.readInt();
        String actualToken = input.readUTF();
        byte[] expected = expectedToken.getBytes(StandardCharsets.UTF_8);
        byte[] actual = actualToken.getBytes(StandardCharsets.UTF_8);
        boolean authenticated;
        try {
            authenticated = magic == MAGIC
                    && version == VERSION
                    && actual.length <= 512
                    && MessageDigest.isEqual(expected, actual);
        }
        finally {
            Arrays.fill(expected, (byte) 0);
            Arrays.fill(actual, (byte) 0);
        }
        if (!authenticated) {
            throw new PlaywrightProviderException(
                    PlaywrightProviderFailure.AUTHENTICATION_FAILED);
        }
    }

    private static void startWorkerRuntime(
            DataInputStream input,
            DataOutputStream output) throws IOException {
        output.writeByte(START);
        output.flush();
        int frame = input.readUnsignedByte();
        if (frame == FAILURE) {
            throw workerFailure(input.readUTF());
        }
        if (frame != READY) {
            throw new PlaywrightProviderException(
                    PlaywrightProviderFailure.PROTOCOL_ERROR);
        }
    }

    private static void requireExactLoopbackPeer(Socket socket) {
        if (socket == null
                || socket.getInetAddress() == null
                || !"127.0.0.1".equals(socket.getInetAddress().getHostAddress())) {
            closeQuietly(socket);
            throw new PlaywrightProviderException(
                    PlaywrightProviderFailure.AUTHENTICATION_FAILED);
        }
    }

    private static void requireContentType(String contentType) {
        byte[] bytes = contentType.getBytes(StandardCharsets.UTF_8);
        try {
            if (bytes.length > MAXIMUM_CONTENT_TYPE_BYTES
                    || contentType.indexOf('\r') >= 0
                    || contentType.indexOf('\n') >= 0
                    || contentType.indexOf('\0') >= 0) {
                throw new PlaywrightProviderException(
                        PlaywrightProviderFailure.UNEXPECTED_CONTENT);
            }
        }
        finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    private static PlaywrightProviderException workerFailure(String value) {
        if (value == null
                || value.isEmpty()
                || value.length() > MAXIMUM_FAILURE_CODE_LENGTH
                || value.chars().anyMatch(Character::isISOControl)) {
            return new PlaywrightProviderException(PlaywrightProviderFailure.PROTOCOL_ERROR);
        }
        return new PlaywrightProviderException(switch (value) {
            case "TIMEOUT" -> PlaywrightProviderFailure.TIMEOUT;
            case "PAYLOAD_TOO_LARGE" -> PlaywrightProviderFailure.PAYLOAD_TOO_LARGE;
            case "SENSITIVE_REQUEST_BLOCKED" ->
                    PlaywrightProviderFailure.SENSITIVE_CONTENT_REJECTED;
            case "UNEXPECTED_ROUTE" -> PlaywrightProviderFailure.UNEXPECTED_ROUTE;
            case "REDIRECT_BLOCKED" -> PlaywrightProviderFailure.REDIRECT_BLOCKED;
            case "CONTENT_TYPE_TOO_LONG" -> PlaywrightProviderFailure.UNEXPECTED_CONTENT;
            case "INVALID_ENDPOINT" -> PlaywrightProviderFailure.INVALID_ENDPOINT;
            case "INVALID_DATE", "INVALID_PAGE", "INVALID_TOURNAMENT_ID", "INVALID_EVENT_ID",
                    "INVALID_TIMEOUT" ->
                    PlaywrightProviderFailure.INVALID_REQUEST;
            case "INVALID_CONFIGURATION", "RUNTIME_START_FAILED", "IPC_CONNECT_FAILED",
                    "RESPONSE_READ_FAILED", "PLAYWRIGHT_FAILURE" ->
                    PlaywrightProviderFailure.RUNTIME_FAILURE;
            default -> PlaywrightProviderFailure.PROTOCOL_ERROR;
        });
    }

    static ProcessTreeCleanupOutcome terminateOwnedProcessTree(
            Process process,
            Instant rootStartedAt,
            ProcessTreeAccess access) {
        ProcessTreeSnapshot initialInventory = access.capture(process, rootStartedAt);
        long startedAt = access.nanoTime();
        return terminateOwnedProcessTree(
                process,
                rootStartedAt,
                initialInventory,
                access,
                startedAt,
                startedAt,
                new AtomicBoolean(),
                false,
                false);
    }

    static ProcessTreeCleanupOutcome terminateOwnedProcessTree(
            Process process,
            Instant rootStartedAt,
            ProcessTreeSnapshot initialInventory,
            ProcessTreeAccess access) {
        long startedAt = access.nanoTime();
        return terminateOwnedProcessTree(
                process,
                rootStartedAt,
                initialInventory,
                access,
                startedAt,
                startedAt,
                new AtomicBoolean(),
                false,
                false);
    }

    static ProcessTreeCleanupOutcome terminateOwnedProcessTree(
            Process process,
            Instant rootStartedAt,
            ProcessTreeSnapshot initialInventory,
            ProcessTreeAccess access,
            boolean authenticatedWorkerExitExpected) {
        long startedAt = access.nanoTime();
        return terminateOwnedProcessTree(
                process,
                rootStartedAt,
                initialInventory,
                access,
                startedAt,
                startedAt,
                new AtomicBoolean(),
                authenticatedWorkerExitExpected,
                false);
    }

    private static ProcessTreeCleanupOutcome terminateOwnedProcessTree(
            Process process,
            Instant rootStartedAt,
            ProcessTreeSnapshot initialInventory,
            ProcessTreeAccess access,
            long cleanupStartedAt,
            long processTerminationStartedAt,
            AtomicBoolean mutationStarted,
            boolean authenticatedWorkerExitExpected,
            boolean naturalTerminationExpected) {
        Objects.requireNonNull(process, "process");
        Objects.requireNonNull(rootStartedAt, "rootStartedAt");
        Objects.requireNonNull(initialInventory, "initialInventory");
        Objects.requireNonNull(access, "access");
        Objects.requireNonNull(mutationStarted, "mutationStarted");

        long softDeadline = deadline(
                processTerminationStartedAt,
                SOFT_PROCESS_TERMINATION_MAX);
        long naturalExitDeadline = deadline(
                processTerminationStartedAt,
                NATURAL_PROCESS_EXIT_MAX);
        long cancellationDeadline = deadline(
                processTerminationStartedAt,
                IN_FLIGHT_CANCELLATION_MAX);
        long cleanupDeadline = deadline(cleanupStartedAt, PROCESS_TREE_CLEANUP_MAX);
        Map<OwnedProcessIdentity, OwnedProcess> owned = new LinkedHashMap<>();
        OwnedProcess root = new OwnedProcess(process.toHandle(), rootStartedAt);
        owned.put(root.identity(), root);
        mergeOwned(owned, initialInventory.ownedProcesses());
        int capturePasses = 1;
        int unverifiedAliveProcessCount = initialInventory.unverifiedAliveProcessCount();
        boolean identityComplete = initialInventory.isExact();
        boolean authenticatedRootTermination = authenticatedWorkerExitExpected;
        long cancelledAt = -1L;

        try {
            while (access.nanoTime() < cleanupDeadline) {
                ProcessTreeSnapshot beforeSignal = access.capture(process, rootStartedAt);
                mergeOwned(owned, beforeSignal.ownedProcesses());
                capturePasses++;
                unverifiedAliveProcessCount = Math.max(
                        unverifiedAliveProcessCount,
                        beforeSignal.unverifiedAliveProcessCount());
                identityComplete &= captureIsConclusive(
                        beforeSignal,
                        authenticatedRootTermination);
                long now = access.nanoTime();
                boolean forcibly = now >= softDeadline;

                boolean signalOwnedProcesses = !naturalTerminationExpected
                        || now >= naturalExitDeadline;
                // A fresh, re-authenticated union immediately precedes every signal group.
                if (signalOwnedProcesses) {
                    authenticatedRootTermination |= root.destroy(forcibly, mutationStarted);
                }
                ProcessTreeSnapshot afterRootSignal = access.capture(process, rootStartedAt);
                mergeOwned(owned, afterRootSignal.ownedProcesses());
                capturePasses++;
                unverifiedAliveProcessCount = Math.max(
                        unverifiedAliveProcessCount,
                        afterRootSignal.unverifiedAliveProcessCount());
                identityComplete &= captureIsConclusive(
                        afterRootSignal,
                        authenticatedRootTermination);
                if (signalOwnedProcesses) {
                    destroyOwnedExceptRoot(
                            owned.values(),
                            root.identity(),
                            forcibly,
                            mutationStarted);
                }

                if (owned.values().stream().noneMatch(OwnedProcess::sameProcessAlive)) {
                    ProcessTreeSnapshot finalCheck = access.capture(process, rootStartedAt);
                    mergeOwned(owned, finalCheck.ownedProcesses());
                    capturePasses++;
                    unverifiedAliveProcessCount = Math.max(
                            unverifiedAliveProcessCount,
                            finalCheck.unverifiedAliveProcessCount());
                    identityComplete &= captureIsConclusive(
                            finalCheck,
                            authenticatedRootTermination);
                    if (finalCheck.unverifiedAliveProcessCount() == 0
                            && owned.values().stream().noneMatch(OwnedProcess::sameProcessAlive)) {
                        cancelledAt = access.nanoTime();
                        break;
                    }
                }

                long remaining = cleanupDeadline - access.nanoTime();
                if (remaining <= 0) {
                    break;
                }
                access.pause(Duration.ofNanos(Math.min(
                        PROCESS_TREE_POLL_INTERVAL.toNanos(), remaining)));
            }

            ProcessTreeSnapshot terminalSnapshot = access.capture(process, rootStartedAt);
            mergeOwned(owned, terminalSnapshot.ownedProcesses());
            capturePasses++;
            unverifiedAliveProcessCount = Math.max(
                    unverifiedAliveProcessCount,
                    terminalSnapshot.unverifiedAliveProcessCount());
            identityComplete &= captureIsConclusive(
                    terminalSnapshot,
                    authenticatedRootTermination);
            root.destroy(true, mutationStarted);
            destroyOwnedExceptRoot(
                    owned.values(),
                    root.identity(),
                    true,
                    mutationStarted);
            int residual = residualOwnedProcessCount(owned.values());
            long finishedAt = Math.min(access.nanoTime(), cleanupDeadline);
            Duration cleanupLatency = elapsed(cleanupStartedAt, finishedAt);
            Duration cancellationLatency = cancelledAt < 0
                    ? elapsed(processTerminationStartedAt, finishedAt)
                    : elapsed(processTerminationStartedAt, cancelledAt);
            boolean cancellationWithinBound = cancelledAt >= 0
                    && cancelledAt <= cancellationDeadline;
            return new ProcessTreeCleanupOutcome(
                    cancellationLatency,
                    cleanupLatency,
                    cancellationWithinBound,
                    identityComplete,
                    unverifiedAliveProcessCount,
                    residual,
                    capturePasses);
        }
        catch (RuntimeException captureOrSignalFailure) {
            if (!mutationStarted.get()) {
                throw captureOrSignalFailure;
            }
            destroyOwnedBestEffort(owned.values(), mutationStarted);
            return incompleteCleanupOutcome(
                    cleanupStartedAt,
                    processTerminationStartedAt,
                    cleanupDeadline,
                    owned.values(),
                    access,
                    capturePasses,
                    Math.max(1, unverifiedAliveProcessCount));
        }
    }

    private static boolean captureIsConclusive(
            ProcessTreeSnapshot snapshot,
            boolean authenticatedRootTermination) {
        return snapshot.isExact()
                || authenticatedRootTermination && snapshot.rootObservedAbsent();
    }

    private static void mergeOwned(
            Map<OwnedProcessIdentity, OwnedProcess> target,
            Collection<OwnedProcess> observed) {
        for (OwnedProcess process : observed) {
            target.putIfAbsent(process.identity(), process);
        }
    }

    private static void destroyOwnedExceptRoot(
            Collection<OwnedProcess> owned,
            OwnedProcessIdentity rootIdentity,
            boolean forcibly,
            AtomicBoolean mutationStarted) {
        for (OwnedProcess process : owned) {
            if (!process.identity().equals(rootIdentity)) {
                process.destroy(forcibly, mutationStarted);
            }
        }
    }

    private static void destroyOwnedBestEffort(
            Collection<OwnedProcess> owned,
            AtomicBoolean mutationStarted) {
        for (OwnedProcess process : owned) {
            try {
                process.destroy(true, mutationStarted);
            }
            catch (RuntimeException ignored) {
                // Ownership is now inconclusive, but every retained exact identity is attempted.
            }
        }
    }

    private static ProcessTreeCleanupOutcome incompleteCleanupOutcome(
            long cleanupStartedAt,
            long processTerminationStartedAt,
            long cleanupDeadline,
            Collection<OwnedProcess> owned,
            ProcessTreeAccess access,
            int capturePasses,
            int unverifiedAliveProcessCount) {
        long finishedAt = Math.min(access.nanoTime(), cleanupDeadline);
        Duration cleanupLatency = elapsed(cleanupStartedAt, finishedAt);
        Duration cancellationLatency = elapsed(processTerminationStartedAt, finishedAt);
        return new ProcessTreeCleanupOutcome(
                cancellationLatency,
                cleanupLatency,
                false,
                false,
                unverifiedAliveProcessCount,
                residualOwnedProcessCount(owned),
                capturePasses);
    }

    private static int residualOwnedProcessCount(Collection<OwnedProcess> owned) {
        int residual = 0;
        for (OwnedProcess process : owned) {
            try {
                if (process.sameProcessAlive()) {
                    residual++;
                }
            }
            catch (RuntimeException ignored) {
                residual++;
            }
        }
        return residual;
    }

    private static long deadline(long startedAt, Duration duration) {
        try {
            return Math.addExact(startedAt, duration.toNanos());
        }
        catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private static Duration elapsed(long startedAt, long finishedAt) {
        return Duration.ofNanos(Math.max(0L, finishedAt - startedAt));
    }

    private static boolean sameProcess(ProcessHandle handle, Instant startedAt) {
        return handle.isAlive() && handle.info().startInstant().filter(startedAt::equals).isPresent();
    }

    private static int toMillis(Duration duration) {
        long value = duration.toMillis();
        if (value < 1 || value > 60_000) {
            throw new PlaywrightProviderException(PlaywrightProviderFailure.INVALID_REQUEST);
        }
        return Math.toIntExact(value);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            }
            catch (Exception ignored) {
                // Cleanup is completed against the exact owned process tree below.
            }
        }
    }

    @FunctionalInterface
    interface ProcessStarter {
        Process start(ProcessBuilder builder) throws IOException;
    }

    interface ProcessTreeAccess {

        long nanoTime();

        ProcessTreeSnapshot capture(Process process, Instant rootStartedAt);

        void pause(Duration duration);
    }

    static final class SystemProcessTreeAccess implements ProcessTreeAccess {

        @Override
        public long nanoTime() {
            return System.nanoTime();
        }

        @Override
        public ProcessTreeSnapshot capture(Process process, Instant rootStartedAt) {
            List<OwnedProcess> result = new ArrayList<>();
            AtomicLong unverified = new AtomicLong();
            ProcessHandle root = process.toHandle();
            boolean rootAlive = root.isAlive();
            Optional<Instant> observedRootStart = rootAlive
                    ? root.info().startInstant()
                    : Optional.empty();
            if (!rootAlive || observedRootStart.filter(rootStartedAt::equals).isEmpty()) {
                return ProcessTreeSnapshot.rootNotAuthenticated(rootAlive ? 1 : 0);
            }
            result.add(new OwnedProcess(root, rootStartedAt));
            try (Stream<ProcessHandle> descendants = root.descendants()) {
                descendants.filter(ProcessHandle::isAlive).forEach(handle -> {
                    Optional<OwnedProcess> captured = OwnedProcess.capture(handle);
                    if (captured.isPresent()) {
                        result.add(captured.orElseThrow());
                    }
                    else {
                        unverified.incrementAndGet();
                    }
                });
            }
            boolean rootStillAlive = root.isAlive();
            Optional<Instant> finalRootStart = rootStillAlive
                    ? root.info().startInstant()
                    : Optional.empty();
            if (!rootStillAlive || finalRootStart.filter(rootStartedAt::equals).isEmpty()) {
                return ProcessTreeSnapshot.rootNotAuthenticated(rootStillAlive ? 1 : 0);
            }
            return new ProcessTreeSnapshot(
                    result,
                    Math.toIntExact(unverified.get()),
                    true);
        }

        @Override
        public void pause(Duration duration) {
            try {
                TimeUnit.NANOSECONDS.sleep(duration.toNanos());
            }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static final class Campaign implements PlaywrightProviderCampaign {

        private volatile ChildJvmPlaywrightProviderSupervisor owner;
        private final CampaignState state;
        private volatile boolean closeRequested;

        private Campaign(
                ChildJvmPlaywrightProviderSupervisor owner,
                CampaignState state) {
            this.owner = owner;
            this.state = state;
        }

        @Override
        public PlaywrightProviderResponse execute(PlaywrightProviderRequest request) {
            return execute(request, PlaywrightDispatchAdmission.UNRESTRICTED);
        }

        @Override
        public PlaywrightProviderResponse execute(PlaywrightProviderRequest request, PlaywrightDispatchAdmission admission) {
            ChildJvmPlaywrightProviderSupervisor current = owner;
            if (current == null || closeRequested) {
                throw new PlaywrightProviderException(
                        PlaywrightProviderFailure.OPERATOR_STOP);
            }
            return current.execute(state, request, Objects.requireNonNull(admission));
        }

        @Override
        public void close() {
            closeRequested = true;
            ChildJvmPlaywrightProviderSupervisor current = owner;
            if (current != null) {
                current.closeCampaign(state);
                owner = null;
            }
        }
    }

    private static final class CampaignState {

        private final UUID campaignId;
        private final Set<SofascoreEndpointType> allowedEndpoints;
        private final ReentrantLock ioLock = new ReentrantLock();
        private final ReentrantLock dispatchLock = new ReentrantLock();
        private final Object processInventoryLock = new Object();
        private final Object cleanupLock = new Object();
        private final AtomicReference<CampaignPhase> phase =
                new AtomicReference<>(CampaignPhase.STARTING);
        private final AtomicBoolean terminationRequested = new AtomicBoolean();
        private final AtomicBoolean operatorStopRequested = new AtomicBoolean();
        private final AtomicBoolean authenticatedTerminalFrameReceived = new AtomicBoolean();
        private final AtomicBoolean providerDispatchStarted = new AtomicBoolean();
        private final AtomicLong terminationStartedAtNanos = new AtomicLong(Long.MIN_VALUE);
        private final AtomicLong operatorStopStartedAtNanos =
                new AtomicLong(Long.MIN_VALUE);
        private final CompletableFuture<ProcessRegistration> processRegistration =
                new CompletableFuture<>();
        private volatile ServerSocket server;
        private volatile Socket socket;
        private volatile DataInputStream input;
        private volatile DataOutputStream output;
        private volatile ProcessTreeSnapshot initialProcessInventory;
        private boolean cleanupCompleted;
        private PlaywrightProviderException cleanupTerminalFailure;

        private CampaignState(
                UUID campaignId,
                Set<SofascoreEndpointType> allowedEndpoints) {
            this.campaignId = campaignId;
            this.allowedEndpoints = allowedEndpoints;
        }

        private void publishProcess(Process process, Instant processStartedAt) {
            processRegistration.complete(ProcessRegistration.launched(
                    process,
                    processStartedAt));
        }

        private void publishNoProcess() {
            processRegistration.complete(ProcessRegistration.notLaunched());
        }

        private long markTerminationStarted(ProcessTreeAccess access) {
            long observed = terminationStartedAtNanos.get();
            if (observed != Long.MIN_VALUE) {
                return observed;
            }
            long now = access.nanoTime();
            terminationStartedAtNanos.compareAndSet(Long.MIN_VALUE, now);
            return terminationStartedAtNanos.get();
        }

        private boolean requestTermination(ProcessTreeAccess access) {
            dispatchLock.lock();
            try {
                return requestTerminationLocked(access);
            }
            finally {
                dispatchLock.unlock();
            }
        }

        private boolean requestOperatorStop(
                long requestedAtNanos,
                ProcessTreeAccess access) {
            dispatchLock.lock();
            try {
                markOperatorStopRequested(requestedAtNanos);
                return requestTerminationLocked(access);
            }
            finally {
                dispatchLock.unlock();
            }
        }

        private boolean requestTerminationLocked(ProcessTreeAccess access) {
            boolean firstSignal = terminationRequested.compareAndSet(false, true);
            if (firstSignal) {
                phase.set(CampaignPhase.TERMINATING);
                markTerminationStarted(access);
            }
            return firstSignal;
        }

        private void markOperatorStopRequested(long requestedAtNanos) {
            operatorStopStartedAtNanos.compareAndSet(Long.MIN_VALUE, requestedAtNanos);
            operatorStopRequested.set(true);
        }

        private long operatorStopStartedAtOr(long fallback) {
            long observed = operatorStopStartedAtNanos.get();
            return observed == Long.MIN_VALUE ? fallback : observed;
        }

        private void attach(
                Socket socket,
                DataInputStream input,
                DataOutputStream output) {
            this.socket = socket;
            this.input = input;
            this.output = output;
        }
    }

    private enum SupervisorLifecycle {
        OPEN,
        CLOSING,
        CLOSED
    }

    private enum CampaignPhase {
        STARTING,
        READY,
        TERMINATING
    }

    private record ProcessRegistration(
            Process process,
            Instant processStartedAt,
            boolean processLaunched) {

        private ProcessRegistration {
            if (processLaunched) {
                Objects.requireNonNull(process, "process");
            }
            else if (process != null || processStartedAt != null) {
                throw new IllegalArgumentException(
                        "a process was not launched but process identity was supplied");
            }
        }

        private static ProcessRegistration launched(
                Process process,
                Instant processStartedAt) {
            return new ProcessRegistration(
                    Objects.requireNonNull(process, "process"),
                    processStartedAt,
                    true);
        }

        private static ProcessRegistration notLaunched() {
            return new ProcessRegistration(null, null, false);
        }

        private boolean identityComplete() {
            return processLaunched && processStartedAt != null;
        }
    }

    private record CampaignTarget(
            UUID campaignId,
            Set<SofascoreEndpointType> allowedEndpoints) {

        private CampaignTarget {
            Objects.requireNonNull(campaignId, "campaignId");
            allowedEndpoints = Set.copyOf(Objects.requireNonNull(
                    allowedEndpoints, "allowedEndpoints"));
            if (allowedEndpoints.isEmpty()
                    || !IMPLEMENTED_ENDPOINTS.containsAll(allowedEndpoints)) {
                throw new PlaywrightProviderException(
                        PlaywrightProviderFailure.INVALID_ENDPOINT);
            }
        }

        private boolean matches(CampaignState state) {
            return campaignId.equals(state.campaignId)
                    && allowedEndpoints.equals(state.allowedEndpoints);
        }
    }

    private StopTombstones stopTombstonesFor(
            Set<SofascoreEndpointType> allowedEndpoints) {
        Set<SofascoreEndpointType> allowlist = Set.copyOf(allowedEndpoints);
        return stopTombstones.computeIfAbsent(
                allowlist, ignored -> new StopTombstones());
    }

    private static final class StopTombstones {

        private final Set<UUID> campaignIds = new LinkedHashSet<>();
        private boolean saturated;

        private synchronized void record(UUID campaignId) {
            Objects.requireNonNull(campaignId, "campaignId");
            if (saturated || campaignIds.contains(campaignId)) {
                return;
            }
            if (campaignIds.size() >= MAXIMUM_STOP_TOMBSTONES_PER_ALLOWLIST) {
                campaignIds.clear();
                saturated = true;
                return;
            }
            campaignIds.add(campaignId);
        }

        private synchronized boolean blocks(UUID campaignId) {
            return saturated || campaignIds.contains(campaignId);
        }
    }

    record ProcessTreeCleanupOutcome(
            Duration cancellationLatency,
            Duration cleanupLatency,
            boolean cancellationWithinBound,
            boolean identityComplete,
            int unverifiedAliveProcessCount,
            int residualOwnedProcessCount,
            int capturePasses) {

        ProcessTreeCleanupOutcome {
            Objects.requireNonNull(cancellationLatency, "cancellationLatency");
            Objects.requireNonNull(cleanupLatency, "cleanupLatency");
            if (cancellationLatency.isNegative()
                    || cleanupLatency.isNegative()
                    || unverifiedAliveProcessCount < 0
                    || residualOwnedProcessCount < 0
                    || capturePasses < 1) {
                throw new IllegalArgumentException("invalid process cleanup outcome");
            }
        }
    }

    record ProcessTreeSnapshot(
            List<OwnedProcess> ownedProcesses,
            int unverifiedAliveProcessCount,
            boolean rootIdentityAuthenticated) {

        ProcessTreeSnapshot {
            ownedProcesses = List.copyOf(Objects.requireNonNull(
                    ownedProcesses, "ownedProcesses"));
            if (unverifiedAliveProcessCount < 0) {
                throw new IllegalArgumentException(
                        "unverifiedAliveProcessCount must not be negative");
            }
        }

        static ProcessTreeSnapshot exact(List<OwnedProcess> ownedProcesses) {
            return new ProcessTreeSnapshot(ownedProcesses, 0, true);
        }

        static ProcessTreeSnapshot rootNotAuthenticated(int unverifiedAliveProcessCount) {
            return new ProcessTreeSnapshot(
                    List.of(),
                    unverifiedAliveProcessCount,
                    false);
        }

        private boolean isExact() {
            return rootIdentityAuthenticated && unverifiedAliveProcessCount == 0;
        }

        private boolean rootObservedAbsent() {
            return !rootIdentityAuthenticated
                    && unverifiedAliveProcessCount == 0
                    && ownedProcesses.isEmpty();
        }

        private boolean isExactFor(ProcessRegistration registration) {
            if (!isExact() || !registration.identityComplete()) {
                return false;
            }
            OwnedProcessIdentity expected = new OwnedProcessIdentity(
                    registration.process().toHandle().pid(),
                    registration.processStartedAt());
            return ownedProcesses.stream()
                    .map(OwnedProcess::identity)
                    .anyMatch(expected::equals);
        }
    }

    record OwnedProcessIdentity(long pid, Instant startedAt) {

        OwnedProcessIdentity {
            if (pid < 0) {
                throw new IllegalArgumentException("pid must not be negative");
            }
            Objects.requireNonNull(startedAt, "startedAt");
        }
    }

    record OwnedProcess(ProcessHandle handle, Instant startedAt) {

        OwnedProcess {
            Objects.requireNonNull(handle, "handle");
            Objects.requireNonNull(startedAt, "startedAt");
        }

        private static Optional<OwnedProcess> capture(ProcessHandle handle) {
            return handle.info().startInstant().map(started -> new OwnedProcess(handle, started));
        }

        private OwnedProcessIdentity identity() {
            return new OwnedProcessIdentity(handle.pid(), startedAt);
        }

        private boolean sameProcessAlive() {
            return sameProcess(handle, startedAt);
        }

        private boolean destroy(boolean forcibly, AtomicBoolean mutationStarted) {
            if (!sameProcessAlive()) {
                return false;
            }
            mutationStarted.set(true);
            if (forcibly) {
                return handle.destroyForcibly();
            }
            return handle.destroy();
        }
    }
}
