package com.bettingproject.sofascorelocal.integration;

import com.bettingproject.sofascorelocal.adapter.bettingproject.transport.BettingProjectJ7DeliveryHttpTransport;
import com.bettingproject.sofascorelocal.adapter.persistence.delivery.JdbcJ7DeliveryLedgerStore;
import com.bettingproject.sofascorelocal.application.delivery.J7DeliveryAcknowledgementParser;
import com.bettingproject.sofascorelocal.application.delivery.J7DeliveryContract;
import com.bettingproject.sofascorelocal.application.delivery.J7DeliveryExecutionResult;
import com.bettingproject.sofascorelocal.application.delivery.J7DeliveryPolicy;
import com.bettingproject.sofascorelocal.application.delivery.J7OptionalDeliveryService;
import com.bettingproject.sofascorelocal.application.export.J7CanonicalExportService;
import com.bettingproject.sofascorelocal.application.export.J7ExportContract;
import com.bettingproject.sofascorelocal.application.export.J7ValidatedExportArtifact;
import com.bettingproject.sofascorelocal.config.OptionalLocalPushProperties;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryAcknowledgementStatus;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryIdentity;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryState;
import com.bettingproject.sofascorelocal.port.J7DeliveryLedgerStore;
import com.bettingproject.sofascorelocal.port.J7DeliveryTransport;
import com.bettingproject.sofascorelocal.security.Sha256;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpsExchange;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
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
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Testcontainers
class J7OptionalDeliveryEndToEndIT {

    private static final String KEYTOOL_PASSWORD_ENV =
            "WO027_E2E_SYNTHETIC_KEYTOOL_PASSWORD";
    private static final Duration TRANSPORT_TIMEOUT = Duration.ofSeconds(10);
    private static final Instant NOW = Instant.parse("2026-09-01T11:30:00Z");
    private static final AtomicLong SEQUENCE = new AtomicLong(27_100);

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4-alpine")
            .withDatabaseName("sofascore_local_lab")
            .withUsername("sofascore_lab")
            .withPassword("integration-test-only");

    @TempDir
    static Path temporaryDirectory;

    private static AnnotationConfigApplicationContext context;
    private static JdbcTemplate jdbc;
    private static J7DeliveryLedgerStore ledgerStore;
    private static Path tlsMaterialRoot;
    private static SyntheticTlsContexts tlsContexts;

    private HttpsServer server;
    private ExecutorService serverExecutor;
    private BettingProjectJ7DeliveryHttpTransport clientTransport;
    private int boundPort;

    @BeforeAll
    static void prepareDatabaseAndSyntheticMutualTls() throws Exception {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load()
                .migrate();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        context = new AnnotationConfigApplicationContext();
        context.register(TransactionConfiguration.class);
        context.registerBean(
                NamedParameterJdbcTemplate.class,
                () -> new NamedParameterJdbcTemplate(dataSource));
        context.registerBean(
                PlatformTransactionManager.class,
                () -> new JdbcTransactionManager(dataSource));
        context.registerBean(JdbcJ7DeliveryLedgerStore.class);
        context.refresh();
        ledgerStore = context.getBean(J7DeliveryLedgerStore.class);

        tlsMaterialRoot = temporaryDirectory.resolve("wo027-integrated-mtls");
        Files.createDirectory(tlsMaterialRoot);
        tlsContexts = generateSyntheticTlsContexts(tlsMaterialRoot);
    }

    @AfterEach
    void stopReceiverAndProveTheEphemeralListenerIsReleased() throws Exception {
        Throwable cleanupFailure = null;
        if (clientTransport != null) {
            try {
                clientTransport.close();
                assertThat(clientTransport.isTerminated()).isTrue();
            }
            catch (Throwable failure) {
                cleanupFailure = appendCleanupFailure(cleanupFailure, failure);
            }
            finally {
                clientTransport = null;
            }
        }
        if (server != null) {
            try {
                server.stop(0);
            }
            catch (Throwable failure) {
                cleanupFailure = appendCleanupFailure(cleanupFailure, failure);
            }
            finally {
                server = null;
            }
        }
        if (serverExecutor != null) {
            try {
                serverExecutor.shutdownNow();
                assertThat(serverExecutor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            }
            catch (Throwable failure) {
                cleanupFailure = appendCleanupFailure(cleanupFailure, failure);
            }
            finally {
                serverExecutor = null;
            }
        }
        if (boundPort > 0) {
            try {
                InetAddress loopback = exactLoopback();
                try (ServerSocket releaseProbe = new ServerSocket()) {
                    releaseProbe.bind(new InetSocketAddress(loopback, boundPort));
                    assertThat(releaseProbe.isBound()).isTrue();
                }
            }
            catch (Throwable failure) {
                cleanupFailure = appendCleanupFailure(cleanupFailure, failure);
            }
            finally {
                boundPort = 0;
            }
        }
        if (cleanupFailure instanceof Exception exception) {
            throw exception;
        }
        if (cleanupFailure instanceof Error error) {
            throw error;
        }
    }

    private static Throwable appendCleanupFailure(Throwable accumulated, Throwable next) {
        if (accumulated == null) {
            return next;
        }
        accumulated.addSuppressed(next);
        return accumulated;
    }

    @AfterAll
    static void closeDatabaseAndRemoveEverySyntheticTlsArtifact() throws Exception {
        if (context != null) {
            context.close();
            context = null;
        }
        if (tlsMaterialRoot != null) {
            deleteExactTemporaryTree(tlsMaterialRoot);
            assertThat(tlsMaterialRoot).doesNotExist();
            tlsMaterialRoot = null;
            tlsContexts = null;
        }
    }

    @Test
    void persistsTheClaimBeforeOneMutualTlsPostAndCompletesWithoutChangingJ7()
            throws Exception {
        ExportEvidence export = insertValidatedExport("nominal");
        SyntheticReceiver receiver = new SyntheticReceiver(
                export, FirstEffectResponse.ACK_IMPORTED);
        AtomicInteger persistentPreSocketChecks = new AtomicInteger();
        DeliveryScenario scenario = deliveryScenario(
                export, receiver, persistentPreSocketChecks);

        J7DeliveryExecutionResult result = scenario.service().deliverSyntheticLoopback(
                export.canonicalEventId(),
                export.exportId(),
                J7OptionalDeliveryService.confirmationFor(export.identity()));

        assertThat(result.state()).isEqualTo(J7DeliveryState.DELIVERED);
        assertThat(result.httpStatus()).hasValue(201);
        assertThat(result.attemptNumber()).isEqualTo(1);
        assertThat(persistentPreSocketChecks).hasValue(1);
        assertThat(receiver.requestCount()).isEqualTo(1);
        assertThat(receiver.effectCount()).isEqualTo(1);
        assertThat(receiver.clientIdentityObserved()).isTrue();
        assertThat(ledgerStore.find(export.exportId(), export.fileSha256()))
                .hasValueSatisfying(snapshot -> assertThat(snapshot)
                        .extracting(
                                J7DeliveryLedgerStore.DeliverySnapshot::state,
                                J7DeliveryLedgerStore.DeliverySnapshot::attemptCount,
                                J7DeliveryLedgerStore.DeliverySnapshot::idempotencyKey)
                        .containsExactly(
                                J7DeliveryLedgerStore.DeliveryState.DELIVERED,
                                1,
                                export.identity().idempotencyKey()));
        assertJ7EvidenceUnchanged(export);
        verify(scenario.exportService(), times(1))
                .loadHumanValidatedForDelivery(
                        export.canonicalEventId(), export.exportId());
    }

    @Test
    void postEffectDisconnectBecomesUnknownThenManualSameIdentityClaimGetsDuplicate()
            throws Exception {
        ExportEvidence export = insertValidatedExport("post-effect-disconnect");
        SyntheticReceiver receiver = new SyntheticReceiver(
                export, FirstEffectResponse.CLOSE_WITHOUT_ACK);
        AtomicInteger persistentPreSocketChecks = new AtomicInteger();
        DeliveryScenario scenario = deliveryScenario(
                export, receiver, persistentPreSocketChecks);
        String confirmation = J7OptionalDeliveryService.confirmationFor(export.identity());

        J7DeliveryExecutionResult unknown = scenario.service().deliverSyntheticLoopback(
                export.canonicalEventId(), export.exportId(), confirmation);

        assertThat(unknown.state())
                .isEqualTo(J7DeliveryState.UNKNOWN_RECONCILIATION_REQUIRED);
        assertThat(unknown.attemptNumber()).isEqualTo(1);
        assertThat(receiver.effectCount()).isEqualTo(1);
        assertThat(receiver.requestCount()).isEqualTo(1);
        assertThat(ledgerStore.find(export.exportId(), export.fileSha256()))
                .hasValueSatisfying(snapshot -> assertThat(snapshot)
                        .extracting(
                                J7DeliveryLedgerStore.DeliverySnapshot::state,
                                J7DeliveryLedgerStore.DeliverySnapshot::attemptCount)
                        .containsExactly(
                                J7DeliveryLedgerStore.DeliveryState
                                        .UNKNOWN_RECONCILIATION_REQUIRED,
                                1));
        assertJ7EvidenceUnchanged(export);

        J7DeliveryExecutionResult duplicate = scenario.service().deliverSyntheticLoopback(
                export.canonicalEventId(), export.exportId(), confirmation);

        assertThat(duplicate.state()).isEqualTo(J7DeliveryState.DUPLICATE_CONFIRMED);
        assertThat(duplicate.httpStatus()).hasValue(200);
        assertThat(duplicate.attemptNumber()).isEqualTo(2);
        assertThat(persistentPreSocketChecks).hasValue(2);
        assertThat(receiver.requestCount()).isEqualTo(2);
        assertThat(receiver.effectCount()).isEqualTo(1);
        assertThat(ledgerStore.find(export.exportId(), export.fileSha256()))
                .hasValueSatisfying(snapshot -> assertThat(snapshot)
                        .extracting(
                                J7DeliveryLedgerStore.DeliverySnapshot::state,
                                J7DeliveryLedgerStore.DeliverySnapshot::attemptCount,
                                J7DeliveryLedgerStore.DeliverySnapshot::idempotencyKey)
                        .containsExactly(
                                J7DeliveryLedgerStore.DeliveryState.DUPLICATE_CONFIRMED,
                                2,
                                export.identity().idempotencyKey()));
        assertThat(attemptTerminalStates(duplicate.deliveryId())).containsExactly(
                "UNKNOWN_RECONCILIATION_REQUIRED",
                "DUPLICATE_CONFIRMED");
        assertJ7EvidenceUnchanged(export);
        verify(scenario.exportService(), times(2))
                .loadHumanValidatedForDelivery(
                        export.canonicalEventId(), export.exportId());
    }

    private DeliveryScenario deliveryScenario(
            ExportEvidence export,
            SyntheticReceiver receiver,
            AtomicInteger persistentPreSocketChecks) throws IOException {
        URI origin = startMutualTlsLoopbackServer(receiver);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        clientTransport = BettingProjectJ7DeliveryHttpTransport
                .forSyntheticLoopback(
                        origin,
                        tlsContexts.authenticatedClient(),
                        TRANSPORT_TIMEOUT,
                        TRANSPORT_TIMEOUT,
                        clock);
        J7DeliveryTransport persistentClaimGuard = request -> {
            Map<String, Object> row = jdbc.queryForMap("""
                    select delivery.current_state, count(attempt.id) as attempt_count
                    from j7_delivery delivery
                    join j7_delivery_attempt attempt on attempt.delivery_id = delivery.id
                    where delivery.export_uuid = ? and delivery.file_sha256 = ?
                    group by delivery.current_state
                    """, export.exportId(), export.fileSha256());
            assertThat(row).containsEntry("current_state", "IN_FLIGHT");
            assertThat(((Number) row.get("attempt_count")).intValue())
                    .isEqualTo(persistentPreSocketChecks.get() + 1);
            assertThat(jdbc.queryForObject("""
                    select count(*)
                    from j7_delivery_attempt attempt
                    join j7_delivery delivery on delivery.id = attempt.delivery_id
                    left join j7_delivery_attempt_result result
                           on result.attempt_id = attempt.id
                    where delivery.export_uuid = ?
                      and delivery.file_sha256 = ?
                  and result.attempt_id is null
                    """, Long.class, export.exportId(), export.fileSha256())).isEqualTo(1L);
            persistentPreSocketChecks.incrementAndGet();
            return clientTransport.execute(request);
        };

        J7CanonicalExportService exportService = mock(J7CanonicalExportService.class);
        when(exportService.loadHumanValidatedForDelivery(
                export.canonicalEventId(), export.exportId()))
                .thenReturn(export.artifact());
        OptionalLocalPushProperties properties = new OptionalLocalPushProperties();
        properties.setLoopbackQualification(true);
        properties.setLoopbackOrigin(origin.toString());
        J7OptionalDeliveryService service = new J7OptionalDeliveryService(
                exportService,
                ledgerStore,
                persistentClaimGuard,
                new J7DeliveryAcknowledgementParser(),
                new J7DeliveryPolicy(properties),
                clock);
        return new DeliveryScenario(service, exportService);
    }

    private URI startMutualTlsLoopbackServer(SyntheticReceiver receiver) throws IOException {
        InetAddress loopback = exactLoopback();
        server = HttpsServer.create(new InetSocketAddress(loopback, 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(tlsContexts.server()) {
            @Override
            public void configure(HttpsParameters parameters) {
                SSLParameters sslParameters = tlsContexts.server().getDefaultSSLParameters();
                sslParameters.setNeedClientAuth(true);
                parameters.setSSLParameters(sslParameters);
                parameters.setNeedClientAuth(true);
            }
        });
        server.createContext(BettingProjectJ7DeliveryHttpTransport.IMPORT_PATH, receiver);
        serverExecutor = Executors.newSingleThreadExecutor(
                Thread.ofPlatform()
                        .daemon(true)
                        .name("wo027-e2e-mtls-loopback-receiver")
                        .factory());
        server.setExecutor(serverExecutor);
        server.start();
        boundPort = server.getAddress().getPort();
        assertThat(server.getAddress().getAddress().getHostAddress()).isEqualTo("127.0.0.1");
        assertThat(boundPort).isPositive();
        return URI.create("https://127.0.0.1:" + boundPort);
    }

    private static InetAddress exactLoopback() throws IOException {
        return InetAddress.getByAddress(
                "wo027-e2e-ipv4-loopback", new byte[] {127, 0, 0, 1});
    }

    private static ExportEvidence insertValidatedExport(String label) {
        long sequence = SEQUENCE.incrementAndGet();
        UUID canonicalEventId = UUID.nameUUIDFromBytes(
                ("wo027-e2e-event-" + sequence).getBytes(StandardCharsets.UTF_8));
        UUID exportId = UUID.nameUUIDFromBytes(
                ("wo027-e2e-export-" + sequence).getBytes(StandardCharsets.UTF_8));
        byte[] content = ("{\"synthetic\":\"wo027-e2e-" + label + "\"}\n")
                .getBytes(StandardCharsets.UTF_8);
        String fileSha256 = Sha256.hex(content);
        String dataSha256 = Sha256.hex(
                ("wo027-e2e-data-" + sequence).getBytes(StandardCharsets.UTF_8));
        String sourceSetSha256 = Sha256.hex(
                ("wo027-e2e-sources-" + sequence).getBytes(StandardCharsets.UTF_8));
        String candidateSha256 = Sha256.hex(
                ("wo027-e2e-candidate-" + sequence).getBytes(StandardCharsets.UTF_8));
        String candidatePath = "j7-" + canonicalEventId + "-" + exportId + ".candidate.json";
        String validatedPath = "j7-" + canonicalEventId + "-" + exportId + ".validated.json";

        assertThat(jdbc.update("""
                insert into canonical_event (id, provider, provider_event_id)
                values (?, 'SOFASCORE', ?)
                """, canonicalEventId, sequence)).isEqualTo(1);
        assertThat(jdbc.update("""
                insert into export_manifest (
                    export_kind, export_uuid, canonical_event_id, schema_id,
                    schema_version, generated_at, data_sha256, source_set_sha256,
                    candidate_content_sha256, content_size_bytes, source_observations,
                    export_path, content_sha256, validation_status,
                    source_snapshot_ids, warnings
                ) values (
                    'J7_CANONICAL_EVENT', ?, ?, ?, ?, ?, ?, ?, ?, ?,
                    cast(? as jsonb), ?, ?, 'COHERENCE_CHECKED',
                    '{}'::bigint[], '[]'::jsonb
                )
                """,
                exportId,
                canonicalEventId,
                J7ExportContract.SCHEMA_ID,
                J7ExportContract.SCHEMA_VERSION,
                java.sql.Timestamp.from(NOW.minusSeconds(120)),
                dataSha256,
                sourceSetSha256,
                candidateSha256,
                content.length,
                """
                        [
                          {"component":"EVENT_STATE","snapshotId":null},
                          {"component":"EVENT_DETAILS","snapshotId":null},
                          {"component":"EVENT_STATISTICS","snapshotId":null},
                          {"component":"EVENT_INCIDENTS","snapshotId":null},
                          {"component":"EVENT_LINEUPS","snapshotId":null}
                        ]
                """,
                candidatePath,
                candidateSha256)).isEqualTo(1);
        assertThat(jdbc.update("""
                update export_manifest
                set decision_intent_status = 'HUMAN_VALIDATED',
                    decision_intent_at = ?,
                    decision_intent_reason = null,
                    decision_intent_path = ?,
                    decision_intent_content_sha256 = ?,
                    decision_intent_content_size_bytes = ?
                where export_uuid = ?
                  and validation_status = 'COHERENCE_CHECKED'
                """,
                java.sql.Timestamp.from(NOW.minusSeconds(60)),
                validatedPath,
                fileSha256,
                content.length,
                exportId)).isEqualTo(1);
        assertThat(jdbc.update("""
                update export_manifest
                set export_path = ?,
                    content_sha256 = ?,
                    content_size_bytes = ?,
                    validation_status = 'HUMAN_VALIDATED',
                    decided_at = ?,
                    decision_reason = null
                where export_uuid = ?
                """,
                validatedPath,
                fileSha256,
                content.length,
                java.sql.Timestamp.from(NOW.minusSeconds(60)),
                exportId)).isEqualTo(1);
        return new ExportEvidence(
                canonicalEventId, exportId, fileSha256, dataSha256, content);
    }

    private static void assertJ7EvidenceUnchanged(ExportEvidence export) {
        assertThat(jdbc.queryForMap("""
                select validation_status, content_sha256, data_sha256,
                       content_size_bytes
                from export_manifest
                where export_uuid = ?
                """, export.exportId()))
                .containsEntry("validation_status", "HUMAN_VALIDATED")
                .containsEntry("content_sha256", export.fileSha256())
                .containsEntry("data_sha256", export.dataSha256())
                .containsEntry("content_size_bytes", (long) export.content().length);
    }

    private static List<String> attemptTerminalStates(UUID deliveryId) {
        return jdbc.queryForList("""
                select result.terminal_state
                from j7_delivery_attempt attempt
                join j7_delivery delivery on delivery.id = attempt.delivery_id
                join j7_delivery_attempt_result result on result.attempt_id = attempt.id
                where delivery.delivery_uuid = ?
                order by attempt.attempt_number
                """, String.class, deliveryId);
    }

    private static SyntheticTlsContexts generateSyntheticTlsContexts(Path root)
            throws Exception {
        Path keytool = keytoolExecutable();
        Path serverKeys = root.resolve("server-keys.p12");
        Path clientKeys = root.resolve("client-keys.p12");
        Path serverCertificate = root.resolve("server.cer");
        Path clientCertificate = root.resolve("client.cer");
        Path serverTrust = root.resolve("server-trust.p12");
        Path clientTrust = root.resolve("client-trust.p12");

        String serverPassword = randomPassword();
        String clientPassword = randomPassword();
        String serverTrustPassword = randomPassword();
        String clientTrustPassword = randomPassword();

        generateKeyPair(
                keytool,
                serverKeys,
                serverPassword,
                "server",
                "CN=127.0.0.1,OU=WO027 E2E,O=Betting Project,C=FR",
                "serverAuth",
                "digitalSignature,keyEncipherment",
                "SAN=IP:127.0.0.1");
        exportCertificate(
                keytool, serverKeys, serverPassword, "server", serverCertificate);
        generateKeyPair(
                keytool,
                clientKeys,
                clientPassword,
                "client",
                "CN=WO027 E2E Synthetic Client,OU=WO027 E2E,O=Betting Project,C=FR",
                "clientAuth",
                "digitalSignature");
        exportCertificate(
                keytool, clientKeys, clientPassword, "client", clientCertificate);
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

        return new SyntheticTlsContexts(
                sslContext(
                        serverKeys,
                        serverPassword,
                        serverTrust,
                        serverTrustPassword),
                sslContext(
                        clientKeys,
                        clientPassword,
                        clientTrust,
                        clientTrustPassword));
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
            String trustStorePassword) throws Exception {
        char[] keyPassword = keyStorePassword.toCharArray();
        char[] trustPassword = trustStorePassword.toCharArray();
        try {
            KeyStore keyStore = loadKeyStore(keyStorePath, keyPassword);
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(
                    KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, keyPassword);
            KeyStore trustStore = loadKeyStore(trustStorePath, trustPassword);
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);

            SSLContext context = SSLContext.getInstance("TLS");
            context.init(
                    keyManagerFactory.getKeyManagers(),
                    trustManagerFactory.getTrustManagers(),
                    new SecureRandom());
            return context;
        }
        finally {
            Arrays.fill(keyPassword, '\0');
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

    private static void deleteExactTemporaryTree(Path root) throws IOException {
        Path normalizedTemporaryDirectory = temporaryDirectory.toAbsolutePath().normalize();
        Path normalizedRoot = root.toAbsolutePath().normalize();
        if (!normalizedRoot.getParent().equals(normalizedTemporaryDirectory)
                || !normalizedRoot.getFileName().toString()
                        .equals("wo027-integrated-mtls")) {
            throw new IllegalStateException("unexpected integrated TLS cleanup root");
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

    private enum FirstEffectResponse {
        ACK_IMPORTED,
        CLOSE_WITHOUT_ACK
    }

    private record SyntheticTlsContexts(
            SSLContext server,
            SSLContext authenticatedClient) {
    }

    private record DeliveryScenario(
            J7OptionalDeliveryService service,
            J7CanonicalExportService exportService) {
    }

    private record ExportEvidence(
            UUID canonicalEventId,
            UUID exportId,
            String fileSha256,
            String dataSha256,
            byte[] content) {

        private ExportEvidence {
            content = content.clone();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }

        private J7DeliveryIdentity identity() {
            return new J7DeliveryIdentity(exportId, fileSha256);
        }

        private J7ValidatedExportArtifact artifact() {
            return new J7ValidatedExportArtifact(
                    exportId,
                    canonicalEventId,
                    J7ExportContract.SCHEMA_ID,
                    J7ExportContract.SCHEMA_VERSION,
                    dataSha256,
                    fileSha256,
                    content);
        }
    }

    private static final class SyntheticReceiver implements HttpHandler {

        private final ExportEvidence expected;
        private final FirstEffectResponse firstEffectResponse;
        private final UUID remoteImportId;
        private final Map<String, ReceivedIdentity> received = new ConcurrentHashMap<>();
        private final AtomicInteger requestCount = new AtomicInteger();
        private final AtomicInteger effectCount = new AtomicInteger();
        private final AtomicBoolean clientIdentityObserved = new AtomicBoolean();
        private final AtomicBoolean firstEffectResponseConsumed = new AtomicBoolean();

        private SyntheticReceiver(
                ExportEvidence expected,
                FirstEffectResponse firstEffectResponse) {
            this.expected = expected;
            this.firstEffectResponse = firstEffectResponse;
            this.remoteImportId = UUID.nameUUIDFromBytes(
                    ("wo027-e2e-remote-" + expected.exportId())
                            .getBytes(StandardCharsets.UTF_8));
        }

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
                        J7DeliveryContract.PROTOCOL_HEADER);
                String exportId = exchange.getRequestHeaders().getFirst(
                        J7DeliveryContract.EXPORT_ID_HEADER);
                String fileSha256 = exchange.getRequestHeaders().getFirst(
                        J7DeliveryContract.FILE_SHA256_HEADER);
                String dataSha256 = exchange.getRequestHeaders().getFirst(
                        J7DeliveryContract.DATA_SHA256_HEADER);
                if (!expected.identity().idempotencyKey().equals(idempotencyKey)
                        || !J7DeliveryContract.PROTOCOL_VERSION.equals(protocol)
                        || !expected.exportId().toString().equals(exportId)
                        || !expected.fileSha256().equals(fileSha256)
                        || !expected.dataSha256().equals(dataSha256)
                        || !expected.fileSha256().equals(Sha256.hex(body))) {
                    send(exchange, 422, "{\"error\":\"EVIDENCE_MISMATCH\"}");
                    return;
                }

                ReceivedIdentity incoming = new ReceivedIdentity(
                        exportId, fileSha256, dataSha256, Sha256.hex(body));
                ReceivedIdentity existing = received.putIfAbsent(idempotencyKey, incoming);
                if (existing == null) {
                    effectCount.incrementAndGet();
                    if (firstEffectResponse == FirstEffectResponse.CLOSE_WITHOUT_ACK
                            && firstEffectResponseConsumed.compareAndSet(false, true)) {
                        return;
                    }
                    sendAcknowledgement(
                            exchange, 201, J7DeliveryAcknowledgementStatus.IMPORTED);
                    return;
                }
                if (!existing.equals(incoming)) {
                    send(exchange, 409, "{\"error\":\"IDEMPOTENCY_CONFLICT\"}");
                    return;
                }
                sendAcknowledgement(
                        exchange, 200, J7DeliveryAcknowledgementStatus.DUPLICATE);
            }
        }

        private int requestCount() {
            return requestCount.get();
        }

        private int effectCount() {
            return effectCount.get();
        }

        private boolean clientIdentityObserved() {
            return clientIdentityObserved.get();
        }

        private void sendAcknowledgement(
                HttpExchange exchange,
                int status,
                J7DeliveryAcknowledgementStatus acknowledgementStatus) throws IOException {
            String acknowledgement = """
                    {"protocolVersion":"%s","remoteImportId":"%s","status":"%s","exportId":"%s","fileSha256":"%s","dataSha256":"%s","receivedAt":"%s"}
                    """.formatted(
                    J7DeliveryContract.PROTOCOL_VERSION,
                    remoteImportId,
                    acknowledgementStatus,
                    expected.exportId(),
                    expected.fileSha256(),
                    expected.dataSha256(),
                    NOW);
            send(exchange, status, acknowledgement);
        }

        private static void send(HttpExchange exchange, int status, String json)
                throws IOException {
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

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class TransactionConfiguration {
    }
}
