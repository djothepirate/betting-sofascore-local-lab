package com.bettingproject.sofascorelocal.integration;

import com.bettingproject.sofascorelocal.adapter.bettingproject.transport.BettingProjectJ7DeliveryHttpTransport;
import com.bettingproject.sofascorelocal.application.delivery.J7DeliveryAcknowledgementParser;
import com.bettingproject.sofascorelocal.application.delivery.J7DeliveryContract;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryAcknowledgementStatus;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryIdentity;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryState;
import com.bettingproject.sofascorelocal.port.J7DeliveryTransportException;
import com.bettingproject.sofascorelocal.port.J7DeliveryTransportFailure;
import com.bettingproject.sofascorelocal.port.J7DeliveryTransportRequest;
import com.bettingproject.sofascorelocal.port.J7DeliveryTransportResponse;
import com.bettingproject.sofascorelocal.security.Sha256;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpsExchange;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class J7DeliveryMutualTlsLoopbackIT {

    private static final String KEYTOOL_PASSWORD_ENV =
            "WO027_SYNTHETIC_KEYTOOL_PASSWORD";
    private static final Duration TRANSPORT_TIMEOUT = Duration.ofSeconds(10);
    private static final Instant ACKNOWLEDGED_AT =
            Instant.parse("2026-09-01T08:30:00Z");
    private static final UUID EXPORT_ID = UUID.fromString(
            "73000000-0000-4000-8000-000000000001");
    private static final UUID REMOTE_IMPORT_ID = UUID.fromString(
            "74000000-0000-4000-8000-000000000001");
    private static final byte[] SYNTHETIC_EXPORT =
            "{\"synthetic\":\"wo027-mtls-loopback\"}\n"
                    .getBytes(StandardCharsets.UTF_8);
    private static final String FILE_SHA256 = Sha256.hex(SYNTHETIC_EXPORT);
    private static final String DATA_SHA256 = Sha256.hex(
            "{\"syntheticData\":true}"
                    .getBytes(StandardCharsets.UTF_8));
    private static final String COLLISION_DATA_SHA256 = "c".repeat(64);
    private static final J7DeliveryIdentity IDENTITY =
            new J7DeliveryIdentity(EXPORT_ID, FILE_SHA256);

    @TempDir
    Path temporaryDirectory;

    private Path tlsMaterialRoot;
    private HttpsServer server;
    private ExecutorService serverExecutor;

    @AfterEach
    void stopServerAndRemoveEverySyntheticTlsArtifact() throws Exception {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (serverExecutor != null) {
            serverExecutor.shutdownNow();
            assertThat(serverExecutor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            serverExecutor = null;
        }
        if (tlsMaterialRoot != null) {
            deleteExactTemporaryTree(tlsMaterialRoot);
            assertThat(tlsMaterialRoot).doesNotExist();
            tlsMaterialRoot = null;
        }
    }

    @Test
    void qualifiesMutualTlsIdempotenceAndFailsWithoutAClientIdentity()
            throws Exception {
        tlsMaterialRoot = temporaryDirectory.resolve("wo027-mtls-material");
        Files.createDirectory(tlsMaterialRoot);
        SyntheticTlsContexts tls = generateSyntheticTlsContexts(tlsMaterialRoot);
        IdempotentSyntheticReceiver receiver = new IdempotentSyntheticReceiver();
        URI origin = startMutualTlsLoopbackServer(tls.server(), receiver);
        Clock clock = Clock.fixed(ACKNOWLEDGED_AT, ZoneOffset.UTC);

        J7DeliveryTransportRequest request = new J7DeliveryTransportRequest(
                IDENTITY, DATA_SHA256, SYNTHETIC_EXPORT);
        J7DeliveryAcknowledgementParser parser =
                new J7DeliveryAcknowledgementParser();

        var firstResponse = executeAuthenticatedAttempt(
                origin, tls.authenticatedClient(), clock, request);
        var firstAcknowledgement = parser.parse(firstResponse.acknowledgement());

        assertThat(firstResponse.httpStatus()).isEqualTo(201);
        assertThat(firstResponse.contentType())
                .isEqualTo(J7DeliveryContract.ACKNOWLEDGEMENT_MEDIA_TYPE);
        assertThat(firstAcknowledgement.status())
                .isEqualTo(J7DeliveryAcknowledgementStatus.IMPORTED);
        assertThat(firstAcknowledgement.remoteImportId()).isEqualTo(REMOTE_IMPORT_ID);
        assertThat(J7DeliveryContract.acknowledgedState(
                J7DeliveryState.IN_FLIGHT,
                IDENTITY,
                DATA_SHA256,
                firstAcknowledgement)).isEqualTo(J7DeliveryState.DELIVERED);

        var repeatedResponse = executeAuthenticatedAttempt(
                origin, tls.authenticatedClient(), clock, request);
        var repeatedAcknowledgement = parser.parse(
                repeatedResponse.acknowledgement());

        assertThat(repeatedResponse.httpStatus()).isEqualTo(200);
        assertThat(repeatedAcknowledgement.status())
                .isEqualTo(J7DeliveryAcknowledgementStatus.DUPLICATE);
        assertThat(repeatedAcknowledgement.remoteImportId())
                .isEqualTo(firstAcknowledgement.remoteImportId());
        assertThat(J7DeliveryContract.acknowledgedState(
                J7DeliveryState.IN_FLIGHT,
                IDENTITY,
                DATA_SHA256,
                repeatedAcknowledgement))
                .isEqualTo(J7DeliveryState.DUPLICATE_CONFIRMED);

        J7DeliveryTransportRequest collisionRequest =
                new J7DeliveryTransportRequest(
                        IDENTITY,
                        COLLISION_DATA_SHA256,
                        SYNTHETIC_EXPORT);
        var collisionResponse = executeAuthenticatedAttempt(
                origin,
                tls.authenticatedClient(),
                clock,
                collisionRequest);
        assertThat(collisionResponse.httpStatus()).isEqualTo(409);

        assertThat(receiver.requestCount()).isEqualTo(3);
        assertThat(receiver.effectCount()).isEqualTo(1);
        assertThat(receiver.clientIdentityObserved()).isTrue();

        var unauthenticatedTransport = BettingProjectJ7DeliveryHttpTransport
                .forSyntheticLoopback(
                        origin,
                        tls.trustOnlyClient(),
                        TRANSPORT_TIMEOUT,
                        TRANSPORT_TIMEOUT,
                        clock);
        try (unauthenticatedTransport) {
            assertThatThrownBy(() -> unauthenticatedTransport.execute(request))
                    .isInstanceOfSatisfying(
                            J7DeliveryTransportException.class,
                            exception -> {
                                assertThat(exception.failure())
                                        .isIn(J7DeliveryTransportFailure.TLS_FAILURE,
                                                J7DeliveryTransportFailure.IO_FAILURE);
                                assertThat(exception.getMessage())
                                        .isEqualTo(exception.failure().name());
                            });
        }
        assertThat(unauthenticatedTransport.isTerminated()).isTrue();
        assertThat(receiver.requestCount()).isEqualTo(3);
        assertThat(receiver.effectCount()).isEqualTo(1);
    }

    private static J7DeliveryTransportResponse executeAuthenticatedAttempt(
            URI origin,
            SSLContext clientContext,
            Clock clock,
            J7DeliveryTransportRequest request) {
        var transport = BettingProjectJ7DeliveryHttpTransport
                .forSyntheticLoopback(
                        origin,
                        clientContext,
                        TRANSPORT_TIMEOUT,
                        TRANSPORT_TIMEOUT,
                        clock);
        try (transport) {
            return transport.execute(request);
        }
        finally {
            assertThat(transport.isTerminated()).isTrue();
        }
    }

    @Test
    void rejectsAServerOutsideTheClientTrustStoreBeforeAnyApplicationRequest()
            throws Exception {
        tlsMaterialRoot = temporaryDirectory.resolve("wo027-mtls-material");
        Files.createDirectory(tlsMaterialRoot);
        SyntheticTlsContexts tls = generateSyntheticTlsContexts(tlsMaterialRoot);
        IdempotentSyntheticReceiver receiver = new IdempotentSyntheticReceiver();
        URI origin = startMutualTlsLoopbackServer(tls.server(), receiver);

        assertFailureWithoutNewApplicationRequest(
                origin,
                tls.clientTrustingOnlyAnUnrelatedServer(),
                receiver,
                J7DeliveryTransportFailure.TLS_FAILURE);
    }

    @Test
    void rejectsATrustedServerWhoseSanDoesNotMatchIpv4Loopback()
            throws Exception {
        tlsMaterialRoot = temporaryDirectory.resolve("wo027-mtls-material");
        Files.createDirectory(tlsMaterialRoot);
        SyntheticTlsContexts tls = generateSyntheticTlsContexts(tlsMaterialRoot);
        IdempotentSyntheticReceiver receiver = new IdempotentSyntheticReceiver();
        URI origin = startMutualTlsLoopbackServer(
                tls.hostnameMismatchServer(), receiver);

        assertFailureWithoutNewApplicationRequest(
                origin,
                tls.clientTrustingHostnameMismatchServer(),
                receiver,
                J7DeliveryTransportFailure.TLS_FAILURE);
    }

    @Test
    void rejectsASignedClientIdentityNotApprovedByTheServer()
            throws Exception {
        tlsMaterialRoot = temporaryDirectory.resolve("wo027-mtls-material");
        Files.createDirectory(tlsMaterialRoot);
        SyntheticTlsContexts tls = generateSyntheticTlsContexts(tlsMaterialRoot);
        IdempotentSyntheticReceiver receiver = new IdempotentSyntheticReceiver();
        URI origin = startMutualTlsLoopbackServer(tls.server(), receiver);

        // Positive control on the very same listener: an unavailable server must not
        // make this negative authentication test pass merely by returning IO_FAILURE.
        var accepted = executeAuthenticatedAttempt(origin, tls.authenticatedClient(),
                Clock.fixed(ACKNOWLEDGED_AT, ZoneOffset.UTC),
                new J7DeliveryTransportRequest(IDENTITY, DATA_SHA256, SYNTHETIC_EXPORT));
        assertThat(accepted.httpStatus()).isEqualTo(201);
        assertThat(receiver.requestCount()).isEqualTo(1);
        assertThat(receiver.effectCount()).isEqualTo(1);
        assertThat(receiver.clientIdentityObserved()).isTrue();

        // A peer rejecting our identity can close the connection without HttpClient
        // retaining an SSLException. Do not turn opaque I/O into a runtime TLS claim.
        assertFailureWithoutNewApplicationRequest(
                origin,
                tls.unapprovedClient(),
                receiver,
                J7DeliveryTransportFailure.TLS_FAILURE,
                J7DeliveryTransportFailure.IO_FAILURE);
    }

    private static void assertFailureWithoutNewApplicationRequest(
            URI origin,
            SSLContext clientContext,
            IdempotentSyntheticReceiver receiver,
            J7DeliveryTransportFailure... allowedFailures) {
        int requestsBefore = receiver.requestCount();
        int effectsBefore = receiver.effectCount();
        boolean identityBefore = receiver.clientIdentityObserved();
        var transport = BettingProjectJ7DeliveryHttpTransport
                .forSyntheticLoopback(
                        origin,
                        clientContext,
                        TRANSPORT_TIMEOUT,
                        TRANSPORT_TIMEOUT,
                        Clock.fixed(ACKNOWLEDGED_AT, ZoneOffset.UTC));
        try (transport) {
            J7DeliveryTransportRequest request = new J7DeliveryTransportRequest(
                    IDENTITY, DATA_SHA256, SYNTHETIC_EXPORT);

            assertThatThrownBy(() -> transport.execute(request))
                    .isInstanceOfSatisfying(
                            J7DeliveryTransportException.class,
                            exception -> {
                                assertThat(exception.failure())
                                        .isIn(Arrays.asList(allowedFailures));
                                assertThat(exception.getMessage())
                                        .isEqualTo(exception.failure().name());
                            });
        }
        assertThat(transport.isTerminated()).isTrue();
        assertThat(receiver.requestCount()).isEqualTo(requestsBefore);
        assertThat(receiver.effectCount()).isEqualTo(effectsBefore);
        assertThat(receiver.clientIdentityObserved()).isEqualTo(identityBefore);
    }

    private URI startMutualTlsLoopbackServer(
            SSLContext serverContext,
            IdempotentSyntheticReceiver receiver) throws IOException {
        InetAddress exactLoopback = InetAddress.getByAddress(
                "wo027-ipv4-loopback", new byte[] {127, 0, 0, 1});
        server = HttpsServer.create(new InetSocketAddress(exactLoopback, 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(serverContext) {
            @Override
            public void configure(HttpsParameters parameters) {
                SSLParameters sslParameters = serverContext.getDefaultSSLParameters();
                sslParameters.setNeedClientAuth(true);
                parameters.setSSLParameters(sslParameters);
                parameters.setNeedClientAuth(true);
            }
        });
        server.createContext(
                BettingProjectJ7DeliveryHttpTransport.IMPORT_PATH,
                receiver);
        serverExecutor = Executors.newSingleThreadExecutor(
                Thread.ofPlatform()
                        .daemon(true)
                        .name("wo027-mtls-loopback-receiver")
                        .factory());
        server.setExecutor(serverExecutor);
        server.start();

        InetSocketAddress bound = server.getAddress();
        assertThat(bound.getAddress().getHostAddress()).isEqualTo("127.0.0.1");
        assertThat(bound.getPort()).isPositive();
        return URI.create("https://127.0.0.1:" + bound.getPort());
    }

    private static SyntheticTlsContexts generateSyntheticTlsContexts(Path root)
            throws Exception {
        Path keytool = keytoolExecutable();
        Path serverKeys = root.resolve("server-keys.p12");
        Path hostnameMismatchServerKeys = root.resolve(
                "hostname-mismatch-server-keys.p12");
        Path unrelatedServerKeys = root.resolve("unrelated-server-keys.p12");
        Path clientKeys = root.resolve("client-keys.p12");
        Path unapprovedClientKeys = root.resolve("unapproved-client-keys.p12");
        Path serverCertificate = root.resolve("server.cer");
        Path hostnameMismatchServerCertificate = root.resolve(
                "hostname-mismatch-server.cer");
        Path unrelatedServerCertificate = root.resolve("unrelated-server.cer");
        Path clientCertificate = root.resolve("client.cer");
        Path unapprovedClientCertificate = root.resolve("unapproved-client.cer");
        Path serverTrust = root.resolve("server-trust.p12");
        Path clientTrust = root.resolve("client-trust.p12");
        Path hostnameMismatchClientTrust = root.resolve(
                "hostname-mismatch-client-trust.p12");
        Path unrelatedServerClientTrust = root.resolve(
                "unrelated-server-client-trust.p12");

        String serverPassword = randomPassword();
        String hostnameMismatchServerPassword = randomPassword();
        String unrelatedServerPassword = randomPassword();
        String clientPassword = randomPassword();
        String unapprovedClientPassword = randomPassword();
        String serverTrustPassword = randomPassword();
        String clientTrustPassword = randomPassword();
        String hostnameMismatchClientTrustPassword = randomPassword();
        String unrelatedServerClientTrustPassword = randomPassword();

        generateKeyPair(
                keytool,
                serverKeys,
                serverPassword,
                "server",
                "CN=127.0.0.1,OU=WO027,O=Betting Project,C=FR",
                "serverAuth",
                "digitalSignature,keyEncipherment",
                "SAN=IP:127.0.0.1");
        exportCertificate(
                keytool, serverKeys, serverPassword, "server", serverCertificate);

        generateKeyPair(
                keytool,
                hostnameMismatchServerKeys,
                hostnameMismatchServerPassword,
                "hostname-mismatch-server",
                "CN=localhost,OU=WO027,O=Betting Project,C=FR",
                "serverAuth",
                "digitalSignature,keyEncipherment",
                "SAN=DNS:localhost");
        exportCertificate(
                keytool,
                hostnameMismatchServerKeys,
                hostnameMismatchServerPassword,
                "hostname-mismatch-server",
                hostnameMismatchServerCertificate);

        generateKeyPair(
                keytool,
                unrelatedServerKeys,
                unrelatedServerPassword,
                "unrelated-server",
                "CN=127.0.0.1,OU=WO027 Unrelated,O=Betting Project,C=FR",
                "serverAuth",
                "digitalSignature,keyEncipherment",
                "SAN=IP:127.0.0.1");
        exportCertificate(
                keytool,
                unrelatedServerKeys,
                unrelatedServerPassword,
                "unrelated-server",
                unrelatedServerCertificate);

        generateKeyPair(
                keytool,
                clientKeys,
                clientPassword,
                "client",
                "CN=WO027 Synthetic Client,OU=WO027,O=Betting Project,C=FR",
                "clientAuth",
                "digitalSignature");
        exportCertificate(
                keytool, clientKeys, clientPassword, "client", clientCertificate);

        generateKeyPair(
                keytool,
                unapprovedClientKeys,
                unapprovedClientPassword,
                "unapproved-client",
                "CN=WO027 Unapproved Client,OU=WO027,O=Betting Project,C=FR",
                "clientAuth",
                "digitalSignature");
        exportCertificate(
                keytool,
                unapprovedClientKeys,
                unapprovedClientPassword,
                "unapproved-client",
                unapprovedClientCertificate);

        importTrustedCertificate(
                keytool,
                serverTrust,
                serverTrustPassword,
                "trusted-client",
                clientCertificate);
        importTrustedCertificate(
                keytool,
                clientTrust,
                clientTrustPassword,
                "trusted-server",
                serverCertificate);
        importTrustedCertificate(
                keytool,
                hostnameMismatchClientTrust,
                hostnameMismatchClientTrustPassword,
                "trusted-hostname-mismatch-server",
                hostnameMismatchServerCertificate);
        importTrustedCertificate(
                keytool,
                unrelatedServerClientTrust,
                unrelatedServerClientTrustPassword,
                "trusted-unrelated-server",
                unrelatedServerCertificate);

        SSLContext serverContext = sslContext(
                serverKeys,
                serverPassword,
                serverTrust,
                serverTrustPassword,
                true);
        SSLContext authenticatedClientContext = sslContext(
                clientKeys,
                clientPassword,
                clientTrust,
                clientTrustPassword,
                true);
        SSLContext trustOnlyClientContext = sslContext(
                null,
                null,
                clientTrust,
                clientTrustPassword,
                false);
        SSLContext hostnameMismatchServerContext = sslContext(
                hostnameMismatchServerKeys,
                hostnameMismatchServerPassword,
                serverTrust,
                serverTrustPassword,
                true);
        SSLContext clientTrustingHostnameMismatchServerContext = sslContext(
                clientKeys,
                clientPassword,
                hostnameMismatchClientTrust,
                hostnameMismatchClientTrustPassword,
                true);
        SSLContext clientTrustingOnlyAnUnrelatedServerContext = sslContext(
                clientKeys,
                clientPassword,
                unrelatedServerClientTrust,
                unrelatedServerClientTrustPassword,
                true);
        SSLContext unapprovedClientContext = sslContext(
                unapprovedClientKeys,
                unapprovedClientPassword,
                clientTrust,
                clientTrustPassword,
                true);
        return new SyntheticTlsContexts(
                serverContext,
                authenticatedClientContext,
                trustOnlyClientContext,
                hostnameMismatchServerContext,
                clientTrustingHostnameMismatchServerContext,
                clientTrustingOnlyAnUnrelatedServerContext,
                unapprovedClientContext);
    }

    private static void generateKeyPair(
            Path keytool,
            Path keyStore,
            String password,
            String alias,
            String distinguishedName,
            String extendedKeyUsage,
            String keyUsage,
            String... additionalExtensions) throws Exception {
        List<String> arguments = new ArrayList<>(List.of(
                "-genkeypair",
                "-alias", alias,
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-sigalg", "SHA256withRSA",
                "-dname", distinguishedName,
                "-validity", "2",
                "-storetype", "PKCS12",
                "-keystore", keyStore.toString(),
                "-storepass:env", KEYTOOL_PASSWORD_ENV,
                "-keypass:env", KEYTOOL_PASSWORD_ENV,
                "-ext", "EKU=" + extendedKeyUsage,
                "-ext", "KU=" + keyUsage,
                "-noprompt"));
        for (String extension : additionalExtensions) {
            arguments.add("-ext");
            arguments.add(extension);
        }
        runKeytool(keytool, password, arguments);
    }

    private static void exportCertificate(
            Path keytool,
            Path keyStore,
            String password,
            String alias,
            Path certificate) throws Exception {
        runKeytool(keytool, password, List.of(
                "-exportcert",
                "-alias", alias,
                "-keystore", keyStore.toString(),
                "-storetype", "PKCS12",
                "-storepass:env", KEYTOOL_PASSWORD_ENV,
                "-file", certificate.toString()));
    }

    private static void importTrustedCertificate(
            Path keytool,
            Path trustStore,
            String password,
            String alias,
            Path certificate) throws Exception {
        runKeytool(keytool, password, List.of(
                "-importcert",
                "-alias", alias,
                "-file", certificate.toString(),
                "-keystore", trustStore.toString(),
                "-storetype", "PKCS12",
                "-storepass:env", KEYTOOL_PASSWORD_ENV,
                "-noprompt"));
    }

    private static void runKeytool(
            Path keytool,
            String password,
            List<String> arguments) throws Exception {
        List<String> command = new ArrayList<>(arguments.size() + 1);
        command.add(keytool.toString());
        command.addAll(arguments);
        ProcessBuilder processBuilder = new ProcessBuilder(command)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD);
        processBuilder.environment().put(KEYTOOL_PASSWORD_ENV, password);
        Process process = processBuilder.start();
        try {
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(10, TimeUnit.SECONDS);
                throw new IllegalStateException("synthetic keytool operation timed out");
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException(
                        "synthetic keytool operation failed with exit code "
                                + process.exitValue());
            }
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw exception;
        }
        finally {
            processBuilder.environment().remove(KEYTOOL_PASSWORD_ENV);
        }
    }

    private static SSLContext sslContext(
            Path keyStorePath,
            String keyStorePassword,
            Path trustStorePath,
            String trustStorePassword,
            boolean includeIdentity) throws Exception {
        char[] keyPassword = keyStorePassword == null
                ? null
                : keyStorePassword.toCharArray();
        char[] trustPassword = trustStorePassword.toCharArray();
        try {
            KeyManager[] keyManagers = new KeyManager[0];
            if (includeIdentity) {
                KeyStore keyStore = loadKeyStore(keyStorePath, keyPassword);
                KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(
                        KeyManagerFactory.getDefaultAlgorithm());
                keyManagerFactory.init(keyStore, keyPassword);
                keyManagers = keyManagerFactory.getKeyManagers();
            }

            KeyStore trustStore = loadKeyStore(trustStorePath, trustPassword);
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);

            SSLContext context = SSLContext.getInstance("TLS");
            context.init(
                    keyManagers,
                    trustManagerFactory.getTrustManagers(),
                    new SecureRandom());
            return context;
        }
        finally {
            if (keyPassword != null) {
                Arrays.fill(keyPassword, '\0');
            }
            Arrays.fill(trustPassword, '\0');
        }
    }

    private static KeyStore loadKeyStore(Path path, char[] password)
            throws GeneralSecurityException, IOException {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream input = Files.newInputStream(path)) {
            keyStore.load(input, password);
        }
        return keyStore;
    }

    private static Path keytoolExecutable() {
        String executableName = System.getProperty("os.name", "")
                .toLowerCase(java.util.Locale.ROOT)
                .contains("win")
                ? "keytool.exe"
                : "keytool";
        Path executable = Path.of(
                System.getProperty("java.home"), "bin", executableName)
                .toAbsolutePath()
                .normalize();
        if (!Files.isRegularFile(executable) || !Files.isExecutable(executable)) {
            throw new IllegalStateException("JDK keytool is unavailable");
        }
        return executable;
    }

    private static String randomPassword() {
        byte[] entropy = new byte[24];
        new SecureRandom().nextBytes(entropy);
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
        }
        finally {
            Arrays.fill(entropy, (byte) 0);
        }
    }

    private void deleteExactTemporaryTree(Path root) throws IOException {
        Path normalizedTemporaryDirectory = temporaryDirectory
                .toAbsolutePath()
                .normalize();
        Path normalizedRoot = root.toAbsolutePath().normalize();
        if (!normalizedRoot.getParent().equals(normalizedTemporaryDirectory)
                || !normalizedRoot.getFileName().toString()
                        .equals("wo027-mtls-material")) {
            throw new IllegalStateException("unexpected synthetic TLS cleanup root");
        }
        if (!Files.exists(normalizedRoot)) {
            return;
        }
        try (var paths = Files.walk(normalizedRoot)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    private record SyntheticTlsContexts(
            SSLContext server,
            SSLContext authenticatedClient,
            SSLContext trustOnlyClient,
            SSLContext hostnameMismatchServer,
            SSLContext clientTrustingHostnameMismatchServer,
            SSLContext clientTrustingOnlyAnUnrelatedServer,
            SSLContext unapprovedClient) {
    }

    private static final class IdempotentSyntheticReceiver implements HttpHandler {

        private final Map<String, ReceivedIdentity> received = new ConcurrentHashMap<>();
        private final AtomicInteger requestCount = new AtomicInteger();
        private final AtomicInteger effectCount = new AtomicInteger();
        private final AtomicBoolean clientIdentityObserved = new AtomicBoolean();

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            requestCount.incrementAndGet();
            try (exchange) {
                if (!"POST".equals(exchange.getRequestMethod())
                        || !BettingProjectJ7DeliveryHttpTransport.IMPORT_PATH.equals(
                                exchange.getRequestURI().getPath())
                        || !(exchange instanceof HttpsExchange httpsExchange)) {
                    send(exchange, 400, "{\"error\":\"INVALID_REQUEST\"}");
                    return;
                }
                try {
                    httpsExchange.getSSLSession().getPeerPrincipal();
                }
                catch (SSLPeerUnverifiedException exception) {
                    send(exchange, 400, "{\"error\":\"CLIENT_IDENTITY_REQUIRED\"}");
                    return;
                }
                clientIdentityObserved.set(true);
                byte[] body = exchange.getRequestBody().readNBytes(
                        Math.toIntExact(J7DeliveryContract.MAXIMUM_PAYLOAD_BYTES + 1));
                String idempotencyKey = exchange.getRequestHeaders().getFirst(
                        J7DeliveryContract.IDEMPOTENCY_HEADER);
                String protocol = exchange.getRequestHeaders().getFirst(
                        BettingProjectJ7DeliveryHttpTransport.PROTOCOL_HEADER);
                String exportId = exchange.getRequestHeaders().getFirst(
                        BettingProjectJ7DeliveryHttpTransport.EXPORT_ID_HEADER);
                String fileSha256 = exchange.getRequestHeaders().getFirst(
                        BettingProjectJ7DeliveryHttpTransport.FILE_SHA256_HEADER);
                String dataSha256 = exchange.getRequestHeaders().getFirst(
                        BettingProjectJ7DeliveryHttpTransport.DATA_SHA256_HEADER);
                if (!IDENTITY.idempotencyKey().equals(idempotencyKey)
                        || !J7DeliveryContract.PROTOCOL_VERSION.equals(protocol)
                        || !EXPORT_ID.toString().equals(exportId)
                        || !FILE_SHA256.equals(fileSha256)
                        || !(DATA_SHA256.equals(dataSha256)
                                || COLLISION_DATA_SHA256.equals(dataSha256))
                        || !FILE_SHA256.equals(Sha256.hex(body))) {
                    send(exchange, 422, "{\"error\":\"EVIDENCE_MISMATCH\"}");
                    return;
                }

                ReceivedIdentity incoming = new ReceivedIdentity(
                        exportId, fileSha256, dataSha256, Sha256.hex(body));
                ReceivedIdentity existing = received.putIfAbsent(
                        idempotencyKey, incoming);
                if (existing == null) {
                    effectCount.incrementAndGet();
                    sendAcknowledgement(
                            exchange,
                            201,
                            J7DeliveryAcknowledgementStatus.IMPORTED);
                    return;
                }
                if (!existing.equals(incoming)) {
                    send(exchange, 409, "{\"error\":\"IDEMPOTENCY_CONFLICT\"}");
                    return;
                }
                sendAcknowledgement(
                        exchange,
                        200,
                        J7DeliveryAcknowledgementStatus.DUPLICATE);
            }
        }

        int requestCount() {
            return requestCount.get();
        }

        int effectCount() {
            return effectCount.get();
        }

        boolean clientIdentityObserved() {
            return clientIdentityObserved.get();
        }

        private static void sendAcknowledgement(
                HttpExchange exchange,
                int status,
                J7DeliveryAcknowledgementStatus acknowledgementStatus)
                throws IOException {
            String acknowledgement = """
                    {"protocolVersion":"%s","remoteImportId":"%s","status":"%s","exportId":"%s","fileSha256":"%s","dataSha256":"%s","receivedAt":"%s"}
                    """.formatted(
                    J7DeliveryContract.PROTOCOL_VERSION,
                    REMOTE_IMPORT_ID,
                    acknowledgementStatus,
                    EXPORT_ID,
                    FILE_SHA256,
                    DATA_SHA256,
                    ACKNOWLEDGED_AT);
            send(exchange, status, acknowledgement);
        }

        private static void send(
                HttpExchange exchange,
                int status,
                String json) throws IOException {
            byte[] response = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set(
                    "Content-Type", J7DeliveryContract.ACKNOWLEDGEMENT_MEDIA_TYPE);
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.sendResponseHeaders(status, response.length);
            exchange.getResponseBody().write(response);
        }

        private record ReceivedIdentity(
                String exportId,
                String fileSha256,
                String dataSha256,
                String receivedContentSha256) {
        }
    }
}
