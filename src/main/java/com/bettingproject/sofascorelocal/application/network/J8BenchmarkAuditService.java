package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkCampaign;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkCampaignResult;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkCampaignTerminalState;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkCampaignType;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkExecutionMode;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkOutcomeType;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkResolutionSource;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkUnit;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkUnitResult;
import com.bettingproject.sofascorelocal.domain.benchmark.J8ProviderCallAttempt;
import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessStatus;
import com.bettingproject.sofascorelocal.domain.provider.EventDetailsProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceOutcome;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceResult;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.port.J8BenchmarkEvidenceStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

@Service
public final class J8BenchmarkAuditService {

    private static final Pattern PARSER_VERSION = Pattern.compile("[A-Za-z0-9._-]{1,32}");

    private final Optional<J8BenchmarkEvidenceStore> evidenceStore;
    private final Clock clock;

    @Autowired
    public J8BenchmarkAuditService(J8BenchmarkEvidenceStore evidenceStore) {
        this(evidenceStore, Clock.systemUTC());
    }

    J8BenchmarkAuditService(
            J8BenchmarkEvidenceStore evidenceStore,
            Clock clock) {
        this(Optional.of(Objects.requireNonNull(evidenceStore, "evidenceStore")), clock);
    }

    private J8BenchmarkAuditService(
            Optional<J8BenchmarkEvidenceStore> evidenceStore,
            Clock clock) {
        this.evidenceStore = Objects.requireNonNull(evidenceStore, "evidenceStore");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public static J8BenchmarkAuditService disabled(Clock clock) {
        return new J8BenchmarkAuditService(Optional.empty(), clock);
    }

    public Session start(
            UUID campaignId,
            J8BenchmarkCampaignType campaignType,
            J8BenchmarkExecutionMode executionMode,
            int maximumUnits,
            Optional<LocalDate> collectionDate) {
        J8BenchmarkCampaign campaign = new J8BenchmarkCampaign(
                campaignId,
                campaignType,
                executionMode,
                clock.instant(),
                maximumUnits,
                collectionDate);
        evidenceStore.ifPresent(store -> store.startCampaign(campaign));
        return new Session(evidenceStore, clock, campaign);
    }

    /**
     * Mutable state for one bounded campaign. A session is deliberately non-thread-safe and must
     * stay on the single operator flow that owns the corresponding J3/J4/J5 campaign.
     */
    public static final class Session {

        private final Optional<J8BenchmarkEvidenceStore> evidenceStore;
        private final Clock clock;
        private final J8BenchmarkCampaign campaign;
        private final Map<Unit, UnitState> units = new LinkedHashMap<>();
        private long nextDisabledIdentifier = 1;
        private boolean finished;
        private boolean poisoned;

        private Session(
                Optional<J8BenchmarkEvidenceStore> evidenceStore,
                Clock clock,
                J8BenchmarkCampaign campaign) {
            this.evidenceStore = evidenceStore;
            this.clock = clock;
            this.campaign = campaign;
        }

        public Unit declare(
                int ordinal,
                SofascoreEndpointType endpoint,
                String requestKey,
                Optional<UUID> canonicalEventId,
                OptionalLong providerEventId) {
            return auditOperation(() -> {
                ensureUsable();
                if (ordinal > campaign.maximumUnits()) {
                    throw new IllegalArgumentException(
                            "benchmark unit ordinal exceeds the campaign bound");
                }
                if (!campaign.campaignType().accepts(endpoint)) {
                    throw new IllegalArgumentException(
                            "benchmark endpoint is outside the campaign type");
                }
                J8BenchmarkUnit declaration = new J8BenchmarkUnit(
                        campaign.campaignId(),
                        ordinal,
                        endpoint,
                        requestKey,
                        canonicalEventId,
                        providerEventId,
                        clock.instant());
                requireCampaignUnitContract(declaration);
                if (units.values().stream().anyMatch(state ->
                        state.declaration.unitOrdinal() == declaration.unitOrdinal())) {
                    throw new IllegalStateException("benchmark unit ordinal is already declared");
                }
                if (units.values().stream().anyMatch(state ->
                        state.declaration.requestKey().equals(declaration.requestKey()))) {
                    throw new IllegalStateException("benchmark request key is already declared");
                }

                long identifier = write(() -> evidenceStore
                        .map(store -> store.declareUnit(declaration))
                        .orElseGet(this::nextDisabledIdentifier));
                if (identifier < 1) {
                    poisoned = true;
                    throw new IllegalStateException(
                            "benchmark unit persistence returned an invalid identifier");
                }
                Unit unit = new Unit(this, identifier);
                units.put(unit, new UnitState(declaration));
                return unit;
            });
        }

        private void requireCampaignUnitContract(J8BenchmarkUnit declaration) {
            switch (campaign.campaignType()) {
                case J3_SCHEDULED_EVENTS -> {
                    String expected = SofascoreEndpointType.SCHEDULED_EVENTS.name()
                            + "|date=" + campaign.collectionDate().orElseThrow()
                            + "|page=" + declaration.unitOrdinal();
                    if (!declaration.requestKey().equals(expected)) {
                        throw new IllegalArgumentException(
                                "scheduled-events request key must match campaign date and ordinal");
                    }
                }
                case J3_TOURNAMENT_DISCOVERY -> {
                    String expectedPrefix = SofascoreEndpointType
                            .TOURNAMENT_SCHEDULED_EVENTS.name()
                            + "|date=" + campaign.collectionDate().orElseThrow()
                            + "|uniqueTournamentId=";
                    if (!declaration.requestKey().startsWith(expectedPrefix)) {
                        throw new IllegalArgumentException(
                                "tournament request key must match the campaign date");
                    }
                }
                case J4_EVENT_DETAILS_PHASE1 -> {
                    long target = declaration.providerEventId().orElseThrow();
                    if (!EventDetailsProviderRequest.PHASE_1_EVENT_IDS.contains(target)) {
                        throw new IllegalArgumentException(
                                "phase-1 benchmark target is outside the fixed allowlist");
                    }
                    if (units.values().stream().anyMatch(existing ->
                            existing.declaration.providerEventId().orElseThrow() == target)) {
                        throw new IllegalStateException(
                                "phase-1 benchmark targets must be distinct");
                    }
                }
                case J4_EVENT_DETAILS_PHASE2 -> {
                    // The exact key and bounded parameterized id are domain invariants.
                }
                case J5_EVENT_DATA -> requireJ5UnitContract(declaration);
            }
        }

        private void requireJ5UnitContract(J8BenchmarkUnit declaration) {
            SofascoreEndpointType expectedEndpoint = switch (declaration.unitOrdinal()) {
                case 1 -> SofascoreEndpointType.EVENT_STATISTICS;
                case 2 -> SofascoreEndpointType.EVENT_INCIDENTS;
                case 3 -> SofascoreEndpointType.EVENT_LINEUPS;
                default -> throw new IllegalArgumentException(
                        "J5 benchmark ordinal is outside its fixed endpoint family");
            };
            if (declaration.endpointType() != expectedEndpoint) {
                throw new IllegalArgumentException(
                        "J5 benchmark ordinals have fixed endpoint families");
            }
            if (units.values().stream().anyMatch(existing ->
                    !existing.declaration.providerEventId().equals(
                            declaration.providerEventId())
                            || !existing.declaration.canonicalEventId().equals(
                                    declaration.canonicalEventId()))) {
                throw new IllegalArgumentException(
                        "all J5 benchmark units must target the same canonical event");
            }
        }

        public void startAttempt(Unit unit) {
            auditOperation(() -> {
                UnitState state = pending(unit);
                if (campaign.executionMode() != J8BenchmarkExecutionMode.GUARDED_PROVIDER) {
                    throw new IllegalStateException(
                            "provider attempts are forbidden for a local-import campaign");
                }
                if (state.attemptId.isPresent()) {
                    throw new IllegalStateException("a benchmark unit cannot be retried");
                }
                if (state.responseReceived || state.snapshot != null) {
                    throw new IllegalStateException(
                            "a provider attempt must precede response and snapshot capture");
                }
                J8ProviderCallAttempt attempt = new J8ProviderCallAttempt(
                        unit.identifier,
                        clock.instant());
                long attemptId = write(() -> evidenceStore
                        .map(store -> store.startProviderAttempt(attempt))
                        .orElseGet(this::nextDisabledIdentifier));
                if (attemptId < 1) {
                    poisoned = true;
                    throw new IllegalStateException(
                            "provider attempt persistence returned an invalid identifier");
                }
                state.attemptId = OptionalLong.of(attemptId);
                state.reached = true;
            });
        }

        public void reach(Unit unit) {
            auditOperation(() -> {
                UnitState state = pending(unit);
                state.reached = true;
            });
        }

        public void captureResponse(Unit unit, int httpStatus, long latencyMillis) {
            auditOperation(() -> {
                UnitState state = pending(unit);
                if (state.attemptId.isEmpty()) {
                    throw new IllegalStateException(
                            "a response cannot precede its provider attempt");
                }
                if (state.responseReceived) {
                    throw new IllegalStateException("a provider response is already captured");
                }
                if (state.snapshot != null) {
                    throw new IllegalStateException(
                            "a provider response must precede its persisted snapshot");
                }
                if (httpStatus < 100 || httpStatus > 599) {
                    throw new IllegalArgumentException("httpStatus must be between 100 and 599");
                }
                if (latencyMillis < 0) {
                    throw new IllegalArgumentException("latencyMillis cannot be negative");
                }
                state.responseReceived = true;
                state.reached = true;
                state.httpStatus = OptionalInt.of(httpStatus);
                state.latencyMillis = OptionalLong.of(latencyMillis);
            });
        }

        public void captureSnapshot(
                Unit unit,
                RawSnapshotPersistenceResult persistence,
                String parserVersion) {
            auditOperation(() -> {
                UnitState state = pending(unit);
                Objects.requireNonNull(persistence, "persistence");
                String normalizedParserVersion = Objects.requireNonNull(
                        parserVersion,
                        "parserVersion").trim();
                if (!PARSER_VERSION.matcher(normalizedParserVersion).matches()) {
                    throw new IllegalArgumentException(
                            "parserVersion must be a bounded safe identifier");
                }
                if (state.snapshot != null) {
                    throw new IllegalStateException(
                            "a snapshot is already captured for this unit");
                }

                if (persistence.outcome() == RawSnapshotPersistenceOutcome.CACHE_HIT) {
                    if (campaign.executionMode() != J8BenchmarkExecutionMode.GUARDED_PROVIDER
                            || state.attemptId.isPresent()
                            || state.responseReceived) {
                        throw new IllegalStateException(
                                "a cache hit cannot follow a provider attempt or local import");
                    }
                }
                else {
                    if (persistence.occurrenceId().isEmpty()) {
                        throw new IllegalArgumentException(
                                "an inserted or deduplicated snapshot requires its occurrence");
                    }
                    if (campaign.executionMode() == J8BenchmarkExecutionMode.GUARDED_PROVIDER
                            && (state.attemptId.isEmpty() || !state.responseReceived)) {
                        throw new IllegalStateException(
                                "a provider snapshot requires its captured response");
                    }
                    if (campaign.executionMode()
                            == J8BenchmarkExecutionMode.MANUAL_LOCAL_JSON_IMPORT
                            && (state.attemptId.isPresent() || state.responseReceived)) {
                        throw new IllegalStateException(
                                "a local-import snapshot cannot claim provider evidence");
                    }
                }

                state.snapshot = persistence;
                state.reached = true;
                state.parserVersion = Optional.of(normalizedParserVersion);
            });
        }

        public void resolve(
                Unit unit,
                J8BenchmarkResolutionSource source,
                J8BenchmarkOutcomeType outcome,
                Optional<RawSnapshotSchemaStatus> schema,
                int warnings,
                Optional<J5CompletenessStatus> completeness,
                OptionalInt completenessScore,
                Optional<String> terminalCode) {
            auditOperation(() -> {
                UnitState state = pending(unit);
                Objects.requireNonNull(source, "source");
                Objects.requireNonNull(outcome, "outcome");
                Objects.requireNonNull(schema, "schema");
                Objects.requireNonNull(completeness, "completeness");
                Objects.requireNonNull(completenessScore, "completenessScore");
                Objects.requireNonNull(terminalCode, "terminalCode");
                requireCampaignSource(source);
                state.reached = true;
                record(unit, state, source, outcome, schema, warnings,
                        completeness, completenessScore, terminalCode);
            });
        }

        public void resolveFailure(Unit unit, String terminalCode) {
            auditOperation(() -> {
                UnitState state = pending(unit);
                String normalizedCode = Objects.requireNonNull(
                        terminalCode,
                        "terminalCode").trim();
                Optional<String> code = Optional.of(normalizedCode);
                J8BenchmarkResolutionSource currentSource = capturedSource(state);

                if (currentSource == J8BenchmarkResolutionSource.BLOCKED
                        && !state.reached) {
                    recordFailure(
                            unit,
                            state,
                            currentSource,
                            J8BenchmarkOutcomeType.NOT_REACHED_AFTER_TERMINAL_FAILURE,
                            Optional.empty(),
                            code);
                    return;
                }

            if (normalizedCode.contains("OPERATOR")) {
                record(
                        unit,
                        state,
                        currentSource,
                        J8BenchmarkOutcomeType.OPERATOR_STOP,
                        Optional.empty(),
                        0,
                        Optional.empty(),
                        OptionalInt.empty(),
                        code);
                return;
            }

            if (isPersistenceFailure(normalizedCode)) {
                J8BenchmarkResolutionSource source = currentSource;
                if (campaign.executionMode()
                                == J8BenchmarkExecutionMode.MANUAL_LOCAL_JSON_IMPORT
                        && source == J8BenchmarkResolutionSource.BLOCKED) {
                    source = J8BenchmarkResolutionSource.MANUAL_LOCAL_JSON_IMPORT;
                }
                recordFailure(
                        unit,
                        state,
                        source,
                        J8BenchmarkOutcomeType.PERSISTENCE_FAILURE,
                        Optional.empty(),
                        code);
                return;
            }

            if (state.responseReceived) {
                int status = state.httpStatus.orElseThrow();
                if (status == 401 || status == 403 || status == 429) {
                    recordFailure(
                            unit,
                            state,
                            J8BenchmarkResolutionSource.PROVIDER,
                            J8BenchmarkOutcomeType.HTTP_REFUSED,
                            Optional.empty(),
                            code);
                    return;
                }
                if (status == 404 && state.snapshot != null) {
                    recordFailure(
                            unit,
                            state,
                            J8BenchmarkResolutionSource.PROVIDER,
                            J8BenchmarkOutcomeType.ENDPOINT_UNAVAILABLE,
                            Optional.of(RawSnapshotSchemaStatus.ENDPOINT_UNAVAILABLE),
                            code);
                    return;
                }
                if (status != 404 && (status < 200 || status >= 300)) {
                    recordFailure(
                            unit,
                            state,
                            J8BenchmarkResolutionSource.PROVIDER,
                            J8BenchmarkOutcomeType.HTTP_ERROR,
                            Optional.empty(),
                            code);
                    return;
                }
            }

            J8BenchmarkResolutionSource capturedSource = currentSource;
            if (capturedSource == J8BenchmarkResolutionSource.BLOCKED) {
                recordFailure(
                        unit,
                        state,
                        capturedSource,
                        state.reached
                                ? J8BenchmarkOutcomeType.PROCESSING_FAILURE
                                : J8BenchmarkOutcomeType.NOT_REACHED_AFTER_TERMINAL_FAILURE,
                        Optional.empty(),
                        code);
                return;
            }
            if (!state.responseReceived && state.attemptId.isPresent()) {
                recordFailure(
                        unit,
                        state,
                        J8BenchmarkResolutionSource.PROVIDER,
                        J8BenchmarkOutcomeType.TRANSPORT_FAILURE,
                        Optional.empty(),
                        code);
                return;
            }
            if (isSchemaIncompatible(normalizedCode)) {
                recordFailure(
                        unit,
                        state,
                        capturedSource,
                        J8BenchmarkOutcomeType.SCHEMA_INCOMPATIBLE,
                        Optional.of(RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE),
                        code);
                return;
            }
            if (normalizedCode.contains("UNEXPECTED_CONTENT")) {
                recordFailure(
                        unit,
                        state,
                        capturedSource,
                        J8BenchmarkOutcomeType.UNEXPECTED_CONTENT,
                        Optional.of(RawSnapshotSchemaStatus.UNEXPECTED_CONTENT),
                        code);
                return;
            }
                recordFailure(
                        unit,
                        state,
                        capturedSource,
                        J8BenchmarkOutcomeType.PROCESSING_FAILURE,
                        Optional.empty(),
                        code);
            });
        }

        public void resolveNotReached(Unit unit, String terminalCode) {
            auditOperation(() -> {
                UnitState state = pending(unit);
                String normalizedCode = Objects.requireNonNull(
                        terminalCode,
                        "terminalCode").trim();
                record(
                        unit,
                        state,
                        J8BenchmarkResolutionSource.BLOCKED,
                        J8BenchmarkOutcomeType.NOT_REACHED_AFTER_TERMINAL_FAILURE,
                        Optional.empty(),
                        0,
                        Optional.empty(),
                        OptionalInt.empty(),
                        Optional.of(normalizedCode));
            });
        }

        public boolean resolveFailureIfPending(Unit unit, String terminalCode) {
            return auditOperation(() -> {
                ensureUsable();
                if (unit == null || unit.owner != this) {
                    throw new IllegalArgumentException(
                            "benchmark unit does not belong to this session");
                }
                UnitState state = units.get(unit);
                if (state == null) {
                    throw new IllegalArgumentException("benchmark unit is unknown");
                }
                if (state.resolved) {
                    return false;
                }
                resolveFailure(unit, terminalCode);
                return true;
            });
        }

        public void finish(
                J8BenchmarkCampaignTerminalState state,
                Optional<String> terminalCode) {
            auditOperation(() -> {
                ensureUsable();
                Objects.requireNonNull(state, "state");
                Objects.requireNonNull(terminalCode, "terminalCode");

            if (state == J8BenchmarkCampaignTerminalState.COMPLETED
                    && units.values().stream().anyMatch(unit -> !unit.resolved)) {
                throw new IllegalStateException(
                        "a completed benchmark campaign cannot contain pending units");
            }

            new J8BenchmarkCampaignResult(
                    campaign.campaignId(),
                    clock.instant(),
                    state,
                    terminalCode,
                    0);

            if (state != J8BenchmarkCampaignTerminalState.COMPLETED) {
                for (Map.Entry<Unit, UnitState> entry : units.entrySet()) {
                    if (!entry.getValue().resolved) {
                        autoResolve(entry.getKey(), entry.getValue(), state, terminalCode);
                    }
                }
            }

            J8BenchmarkCampaignResult result = new J8BenchmarkCampaignResult(
                    campaign.campaignId(),
                    clock.instant(),
                    state,
                    terminalCode,
                    units.size());
            write(() -> {
                evidenceStore.ifPresent(store -> store.finishCampaign(result));
                return null;
            });
                finished = true;
            });
        }

        private void autoResolve(
                Unit unit,
                UnitState pending,
                J8BenchmarkCampaignTerminalState campaignState,
                Optional<String> terminalCode) {
            if (pending.responseReceived) {
                record(
                        unit,
                        pending,
                        J8BenchmarkResolutionSource.PROVIDER,
                        outcomeForCapturedResponse(pending.httpStatus.orElseThrow()),
                        Optional.empty(),
                        0,
                        Optional.empty(),
                        OptionalInt.empty(),
                        terminalCode);
                return;
            }
            if (pending.attemptId.isPresent()) {
                J8BenchmarkOutcomeType outcome =
                        campaignState == J8BenchmarkCampaignTerminalState.CANCELLED
                                ? J8BenchmarkOutcomeType.OPERATOR_STOP
                                : J8BenchmarkOutcomeType.TRANSPORT_FAILURE;
                record(
                        unit,
                        pending,
                        J8BenchmarkResolutionSource.PROVIDER,
                        outcome,
                        Optional.empty(),
                        0,
                        Optional.empty(),
                        OptionalInt.empty(),
                        terminalCode);
                return;
            }
            if (pending.snapshot != null) {
                J8BenchmarkResolutionSource source =
                        pending.snapshot.outcome() == RawSnapshotPersistenceOutcome.CACHE_HIT
                                ? J8BenchmarkResolutionSource.CACHE
                                : J8BenchmarkResolutionSource.MANUAL_LOCAL_JSON_IMPORT;
                record(
                        unit,
                        pending,
                        source,
                        J8BenchmarkOutcomeType.PROCESSING_FAILURE,
                        Optional.empty(),
                        0,
                        Optional.empty(),
                        OptionalInt.empty(),
                        terminalCode);
                return;
            }
            record(
                    unit,
                    pending,
                    J8BenchmarkResolutionSource.BLOCKED,
                    pending.reached
                            ? J8BenchmarkOutcomeType.PROCESSING_FAILURE
                            : J8BenchmarkOutcomeType.NOT_REACHED_AFTER_TERMINAL_FAILURE,
                    Optional.empty(),
                    0,
                    Optional.empty(),
                    OptionalInt.empty(),
                    terminalCode);
        }

        private static J8BenchmarkOutcomeType outcomeForCapturedResponse(int httpStatus) {
            if (httpStatus == 401 || httpStatus == 403 || httpStatus == 429) {
                return J8BenchmarkOutcomeType.HTTP_REFUSED;
            }
            if (httpStatus != 404 && (httpStatus < 200 || httpStatus >= 300)) {
                return J8BenchmarkOutcomeType.HTTP_ERROR;
            }
            return J8BenchmarkOutcomeType.PROCESSING_FAILURE;
        }

        private void recordFailure(
                Unit unit,
                UnitState state,
                J8BenchmarkResolutionSource source,
                J8BenchmarkOutcomeType outcome,
                Optional<RawSnapshotSchemaStatus> schema,
                Optional<String> terminalCode) {
            Optional<J5CompletenessStatus> completeness = Optional.empty();
            OptionalInt completenessScore = OptionalInt.empty();
            if (outcome == J8BenchmarkOutcomeType.ENDPOINT_UNAVAILABLE
                    && campaign.campaignType() == J8BenchmarkCampaignType.J5_EVENT_DATA) {
                completeness = Optional.of(J5CompletenessStatus.UNAVAILABLE);
                completenessScore = OptionalInt.of(0);
            }
            record(
                    unit,
                    state,
                    source,
                    outcome,
                    schema,
                    0,
                    completeness,
                    completenessScore,
                    terminalCode);
        }

        private J8BenchmarkResolutionSource capturedSource(UnitState state) {
            if (state.attemptId.isPresent()) {
                return J8BenchmarkResolutionSource.PROVIDER;
            }
            if (state.snapshot == null) {
                return J8BenchmarkResolutionSource.BLOCKED;
            }
            return state.snapshot.outcome() == RawSnapshotPersistenceOutcome.CACHE_HIT
                    ? J8BenchmarkResolutionSource.CACHE
                    : J8BenchmarkResolutionSource.MANUAL_LOCAL_JSON_IMPORT;
        }

        private static boolean isSchemaIncompatible(String terminalCode) {
            return terminalCode.contains("SCHEMA_INCOMPATIBLE")
                    || terminalCode.contains("CACHE_REPARSE_INCOMPATIBLE")
                    || terminalCode.contains("EVENT_ID_MISMATCH")
                    || terminalCode.contains("CANONICAL_EVENT_MISMATCH")
                    || terminalCode.contains("PAYLOAD_INCOMPATIBLE")
                    || terminalCode.equals("UNIQUE_TOURNAMENT_ID_MISMATCH")
                    || terminalCode.equals("CONFLICTING_EVENT_DUPLICATE")
                    || terminalCode.equals("EVENT_COUNT_MISMATCH");
        }

        private static boolean isPersistenceFailure(String terminalCode) {
            return (terminalCode.startsWith("RAW_")
                            && (terminalCode.contains("PERSISTENCE")
                                    || terminalCode.contains("CLASSIFICATION")))
                    || terminalCode.startsWith("NORMALIZATION_PERSISTENCE")
                    || terminalCode.startsWith("LOCAL_IMPORT_PERSISTENCE")
                    || terminalCode.equals("CACHE_WRITE_ERROR");
        }

        private void record(
                Unit unit,
                UnitState state,
                J8BenchmarkResolutionSource source,
                J8BenchmarkOutcomeType outcome,
                Optional<RawSnapshotSchemaStatus> schema,
                int warnings,
                Optional<J5CompletenessStatus> completeness,
                OptionalInt completenessScore,
                Optional<String> terminalCode) {
            OptionalLong snapshotId = state.snapshot == null
                    ? OptionalLong.empty()
                    : OptionalLong.of(state.snapshot.snapshotId());
            OptionalLong occurrenceId = state.snapshot == null
                    ? OptionalLong.empty()
                    : state.snapshot.occurrenceId();
            write(() -> {
                J8BenchmarkUnitResult result = new J8BenchmarkUnitResult(
                        unit.identifier,
                        state.attemptId,
                        clock.instant(),
                        source,
                        outcome,
                        state.responseReceived,
                        state.httpStatus,
                        state.latencyMillis,
                        snapshotId,
                        occurrenceId,
                        state.parserVersion,
                        schema,
                        warnings,
                        completeness,
                        completenessScore,
                        terminalCode);
                evidenceStore.ifPresent(store -> store.recordUnitResult(result));
                return null;
            });
            state.resolved = true;
        }

        private void requireCampaignSource(J8BenchmarkResolutionSource source) {
            if (campaign.executionMode() == J8BenchmarkExecutionMode.GUARDED_PROVIDER
                    && source == J8BenchmarkResolutionSource.MANUAL_LOCAL_JSON_IMPORT) {
                throw new IllegalArgumentException(
                        "a guarded-provider campaign cannot resolve from a local import");
            }
            if (campaign.executionMode()
                    == J8BenchmarkExecutionMode.MANUAL_LOCAL_JSON_IMPORT
                    && source != J8BenchmarkResolutionSource.MANUAL_LOCAL_JSON_IMPORT
                    && source != J8BenchmarkResolutionSource.BLOCKED) {
                throw new IllegalArgumentException(
                        "a local-import campaign cannot claim provider or cache evidence");
            }
        }

        private UnitState pending(Unit unit) {
            ensureUsable();
            if (unit == null || unit.owner != this) {
                throw new IllegalArgumentException(
                        "benchmark unit does not belong to this session");
            }
            UnitState state = units.get(unit);
            if (state == null) {
                throw new IllegalArgumentException("benchmark unit is unknown");
            }
            if (state.resolved) {
                throw new IllegalStateException("benchmark unit is already resolved");
            }
            return state;
        }

        private void ensureUsable() {
            if (poisoned) {
                throw new IllegalStateException(
                        "benchmark audit session is unusable after an audit failure");
            }
            if (finished) {
                throw new IllegalStateException("benchmark audit session is already finished");
            }
        }

        private long nextDisabledIdentifier() {
            return nextDisabledIdentifier++;
        }

        private void auditOperation(Runnable operation) {
            auditOperation(() -> {
                operation.run();
                return null;
            });
        }

        private <T> T auditOperation(Supplier<T> operation) {
            try {
                return operation.get();
            }
            catch (RuntimeException exception) {
                if (units.values().stream().anyMatch(
                        state -> state.attemptId.isPresent())) {
                    poisoned = true;
                }
                throw exception;
            }
        }

        private <T> T write(Supplier<T> operation) {
            try {
                return operation.get();
            }
            catch (RuntimeException exception) {
                poisoned = true;
                throw exception;
            }
        }
    }

    public static final class Unit {

        private final Session owner;
        private final long identifier;

        private Unit(Session owner, long identifier) {
            this.owner = owner;
            this.identifier = identifier;
        }
    }

    private static final class UnitState {

        private final J8BenchmarkUnit declaration;
        private OptionalLong attemptId = OptionalLong.empty();
        private boolean responseReceived;
        private OptionalInt httpStatus = OptionalInt.empty();
        private OptionalLong latencyMillis = OptionalLong.empty();
        private RawSnapshotPersistenceResult snapshot;
        private Optional<String> parserVersion = Optional.empty();
        private boolean reached;
        private boolean resolved;

        private UnitState(J8BenchmarkUnit declaration) {
            this.declaration = declaration;
        }
    }
}
