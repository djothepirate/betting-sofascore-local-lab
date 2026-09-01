package com.bettingproject.sofascorelocal.application.delivery;

import com.bettingproject.sofascorelocal.application.export.J7CanonicalExportService;
import com.bettingproject.sofascorelocal.application.export.J7ExportContract;
import com.bettingproject.sofascorelocal.application.export.J7ValidatedExportArtifact;
import com.bettingproject.sofascorelocal.config.OptionalLocalPushProperties;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryError;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryException;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryIdentity;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryState;
import com.bettingproject.sofascorelocal.port.J7DeliveryLedgerStore;
import com.bettingproject.sofascorelocal.port.J7DeliveryTransport;
import com.bettingproject.sofascorelocal.port.J7DeliveryTransportException;
import com.bettingproject.sofascorelocal.port.J7DeliveryTransportFailure;
import com.bettingproject.sofascorelocal.port.J7DeliveryTransportRequest;
import com.bettingproject.sofascorelocal.port.J7DeliveryTransportResponse;
import com.bettingproject.sofascorelocal.security.Sha256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class J7OptionalDeliveryServiceTest {

    private static final UUID CANONICAL_EVENT_ID =
            UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID EXPORT_ID =
            UUID.fromString("20000000-0000-4000-8000-000000000002");
    private static final UUID DELIVERY_ID =
            UUID.fromString("30000000-0000-4000-8000-000000000003");
    private static final UUID REMOTE_IMPORT_ID =
            UUID.fromString("40000000-0000-4000-8000-000000000004");
    private static final byte[] CONTENT = "{}".getBytes(StandardCharsets.UTF_8);
    private static final String FILE_SHA256 = Sha256.hex(CONTENT);
    private static final String DATA_SHA256 = "b".repeat(64);
    private static final Instant NOW = Instant.parse("2026-09-01T08:00:00Z");

    private J7CanonicalExportService exportService;
    private J7DeliveryLedgerStore ledgerStore;
    private J7DeliveryTransport transport;
    private J7OptionalDeliveryService service;
    private J7DeliveryIdentity identity;

    @BeforeEach
    void setUp() {
        exportService = mock(J7CanonicalExportService.class);
        ledgerStore = mock(J7DeliveryLedgerStore.class);
        transport = mock(J7DeliveryTransport.class);
        OptionalLocalPushProperties properties = new OptionalLocalPushProperties();
        properties.setLoopbackQualification(true);
        properties.setLoopbackOrigin("https://127.0.0.1:49152");
        service = new J7OptionalDeliveryService(
                exportService,
                ledgerStore,
                transport,
                new J7DeliveryAcknowledgementParser(),
                new J7DeliveryPolicy(properties),
                Clock.fixed(NOW, ZoneOffset.UTC));
        identity = new J7DeliveryIdentity(EXPORT_ID, FILE_SHA256);
        when(exportService.loadHumanValidatedForDelivery(CANONICAL_EVENT_ID, EXPORT_ID))
                .thenReturn(artifact());
        when(ledgerStore.claim(
                EXPORT_ID,
                FILE_SHA256,
                DATA_SHA256,
                identity.idempotencyKey(),
                NOW))
                .thenReturn(new J7DeliveryLedgerStore.ClaimReceipt(
                        DELIVERY_ID, 1, J7DeliveryLedgerStore.DeliveryState.IN_FLIGHT,
                        identity.idempotencyKey(), NOW));
        when(ledgerStore.complete(
                eq(DELIVERY_ID),
                anyInt(),
                any(),
                any(),
                anyString(),
                any(),
                any(),
                any(),
                eq(NOW)))
                .thenAnswer(invocation -> snapshot(
                        invocation.getArgument(2, J7DeliveryLedgerStore.DeliveryState.class)));
    }

    @Test
    void exactManualConfirmationClaimsBeforeOnePostAndPersistsDelivered() {
        when(transport.execute(any())).thenReturn(response(201, ack("IMPORTED")));

        J7DeliveryExecutionResult result = service.deliverSyntheticLoopback(
                CANONICAL_EVENT_ID,
                EXPORT_ID,
                J7OptionalDeliveryService.confirmationFor(identity));

        assertThat(result.state()).isEqualTo(J7DeliveryState.DELIVERED);
        assertThat(result.httpStatus()).hasValue(201);
        assertThat(result.safeResultCode()).isEqualTo("HTTP_201_IMPORTED");
        ArgumentCaptor<J7DeliveryTransportRequest> request =
                ArgumentCaptor.forClass(J7DeliveryTransportRequest.class);
        InOrder deliveryOrder = inOrder(exportService, ledgerStore, transport);
        deliveryOrder.verify(exportService)
                .loadHumanValidatedForDelivery(CANONICAL_EVENT_ID, EXPORT_ID);
        deliveryOrder.verify(ledgerStore).claim(
                EXPORT_ID, FILE_SHA256, DATA_SHA256, identity.idempotencyKey(), NOW);
        deliveryOrder.verify(transport).execute(request.capture());
        deliveryOrder.verify(ledgerStore).complete(
                eq(DELIVERY_ID),
                eq(1),
                eq(J7DeliveryLedgerStore.DeliveryState.DELIVERED),
                eq(OptionalInt.of(201)),
                eq("HTTP_201_IMPORTED"),
                any(),
                eq(Optional.of(REMOTE_IMPORT_ID)),
                eq(Optional.of(NOW)),
                eq(NOW));
        assertThat(request.getValue().identity()).isEqualTo(identity);
        assertThat(request.getValue().dataSha256()).isEqualTo(DATA_SHA256);
        assertThat(request.getValue().content()).isEqualTo(CONTENT);
        verify(transport, times(1)).execute(any());
    }

    @Test
    void exactDuplicateAcknowledgementIsSeparateAndDoesNotBecomeDelivered() {
        when(transport.execute(any())).thenReturn(response(200, ack("DUPLICATE")));

        J7DeliveryExecutionResult result = deliver();

        assertThat(result.state()).isEqualTo(J7DeliveryState.DUPLICATE_CONFIRMED);
        verify(ledgerStore).complete(
                eq(DELIVERY_ID),
                eq(1),
                eq(J7DeliveryLedgerStore.DeliveryState.DUPLICATE_CONFIRMED),
                eq(OptionalInt.of(200)),
                eq("HTTP_DUPLICATE_CONFIRMED"),
                any(),
                eq(Optional.of(REMOTE_IMPORT_ID)),
                eq(Optional.of(NOW)),
                eq(NOW));
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 413, 415, 422, 429})
    void everyContractualFourHundredResponseIsTerminalWithoutRetry(int status) {
        when(transport.execute(any())).thenReturn(response(status, new byte[0]));

        J7DeliveryExecutionResult result = deliver();

        assertThat(result.state()).isEqualTo(J7DeliveryState.REJECTED_TERMINAL);
        assertThat(result.httpStatus()).hasValue(status);
        assertThat(result.safeResultCode()).isEqualTo("HTTP_4XX_REJECTED");
        verify(transport, times(1)).execute(any());
    }

    @ParameterizedTest
    @ValueSource(ints = {301, 307, 500, 503, 204})
    void redirectServerFailureAndNoContentRemainUnknownWithoutRetry(int status) {
        when(transport.execute(any())).thenReturn(response(status, new byte[0]));

        J7DeliveryExecutionResult result = deliver();

        assertThat(result.state())
                .isEqualTo(J7DeliveryState.UNKNOWN_RECONCILIATION_REQUIRED);
        assertThat(result.httpStatus()).hasValue(status);
        String expectedSafeResultCode = "HTTP_AMBIGUOUS";
        if (status == 204) {
            expectedSafeResultCode = "ACK_INVALID_OR_MISMATCHED";
        }
        else if (status >= 500) {
            expectedSafeResultCode = "HTTP_5XX_AMBIGUOUS";
        }
        assertThat(result.safeResultCode()).isEqualTo(expectedSafeResultCode);
        verify(transport, times(1)).execute(any());
    }

    @Test
    void malformedAcknowledgementRemainsUnknownWithoutRetry() {
        when(transport.execute(any())).thenReturn(
                response(201, "{}".getBytes(StandardCharsets.UTF_8)));

        J7DeliveryExecutionResult result = deliver();

        assertThat(result.state())
                .isEqualTo(J7DeliveryState.UNKNOWN_RECONCILIATION_REQUIRED);
        assertThat(result.safeResultCode()).isEqualTo("ACK_INVALID_OR_MISMATCHED");
        verify(transport, times(1)).execute(any());
    }

    @ParameterizedTest
    @CsvSource({"200, IMPORTED", "201, DUPLICATE"})
    void incompatibleHttpAndAcknowledgementStatusesRemainUnknownWithoutRetry(
            int httpStatus,
            String acknowledgementStatus) {
        when(transport.execute(any())).thenReturn(
                response(httpStatus, ack(acknowledgementStatus)));

        J7DeliveryExecutionResult result = deliver();

        assertThat(result.state())
                .isEqualTo(J7DeliveryState.UNKNOWN_RECONCILIATION_REQUIRED);
        assertThat(result.httpStatus()).hasValue(httpStatus);
        assertThat(result.safeResultCode()).isEqualTo("ACK_HTTP_STATUS_MISMATCH");
        verify(transport, times(1)).execute(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "application/json", "text/html; charset=utf-8"})
    void absentOrHostileAcknowledgementMediaTypeRemainsUnknownWithoutRetry(
            String contentType) {
        when(transport.execute(any())).thenReturn(
                response(201, contentType, ack("IMPORTED"), NOW));

        J7DeliveryExecutionResult result = deliver();

        assertThat(result.state())
                .isEqualTo(J7DeliveryState.UNKNOWN_RECONCILIATION_REQUIRED);
        assertThat(result.safeResultCode()).isEqualTo("ACK_CONTENT_TYPE_INVALID");
        verify(transport, times(1)).execute(any());
    }

    @ParameterizedTest
    @ValueSource(longs = {-1L, 1L})
    void acknowledgementOutsideTheClaimAndReceiveWindowRemainsUnknownWithoutRetry(
            long acknowledgementOffsetSeconds) {
        when(transport.execute(any())).thenReturn(response(
                201,
                J7DeliveryContract.ACKNOWLEDGEMENT_MEDIA_TYPE,
                ack("IMPORTED", NOW.plusSeconds(acknowledgementOffsetSeconds)),
                NOW));

        J7DeliveryExecutionResult result = deliver();

        assertThat(result.state())
                .isEqualTo(J7DeliveryState.UNKNOWN_RECONCILIATION_REQUIRED);
        assertThat(result.safeResultCode()).isEqualTo("ACK_HTTP_STATUS_MISMATCH");
        verify(transport, times(1)).execute(any());
    }

    @Test
    void aConflictIsTerminalAndNeverAcceptedAsADuplicateAcknowledgement() {
        when(transport.execute(any())).thenReturn(response(409, ack("DUPLICATE")));

        J7DeliveryExecutionResult result = deliver();

        assertThat(result.state()).isEqualTo(J7DeliveryState.REJECTED_TERMINAL);
        assertThat(result.safeResultCode()).isEqualTo("HTTP_4XX_REJECTED");
    }

    @Test
    void timeoutBecomesUnknownWithoutAnyAutomaticRetry() {
        when(transport.execute(any())).thenThrow(new J7DeliveryTransportException(
                J7DeliveryTransportFailure.TIMEOUT));

        J7DeliveryExecutionResult result = deliver();

        assertThat(result.state())
                .isEqualTo(J7DeliveryState.UNKNOWN_RECONCILIATION_REQUIRED);
        assertThat(result.httpStatus()).isEmpty();
        assertThat(result.safeResultCode()).isEqualTo("TRANSPORT_TIMEOUT");
        verify(transport, times(1)).execute(any());
    }

    @Test
    void wrongConfirmationStopsBeforeClaimOrTransportAndDoesNotLeakIt() {
        assertThatThrownBy(() -> service.deliverSyntheticLoopback(
                CANONICAL_EVENT_ID, EXPORT_ID, "wrong-sensitive-confirmation"))
                .isInstanceOf(J7DeliveryException.class)
                .hasMessage(J7DeliveryError.INVALID_CONFIRMATION.name());

        verify(ledgerStore, never()).claim(any(), anyString(), anyString(), anyString(), any());
        verify(transport, never()).execute(any());
    }

    @Test
    void aLedgerClaimFailureStopsBeforeTransport() {
        when(ledgerStore.claim(any(), anyString(), anyString(), anyString(), any()))
                .thenThrow(new J7DeliveryLedgerStore.LedgerException(
                        J7DeliveryLedgerStore.LedgerFailure.ANOTHER_DELIVERY_IN_FLIGHT));

        assertThatThrownBy(this::deliver)
                .isInstanceOf(J7DeliveryLedgerStore.LedgerException.class);
        verify(transport, never()).execute(any());
    }

    @Test
    void defaultConfigurationRemainsNotEvidencedAndStopsBeforeEveryDeliveryDependency() {
        J7CanonicalExportService blockedExportService = mock(J7CanonicalExportService.class);
        J7DeliveryLedgerStore blockedLedgerStore = mock(J7DeliveryLedgerStore.class);
        J7DeliveryTransport blockedTransport = mock(J7DeliveryTransport.class);
        OptionalLocalPushProperties defaults = new OptionalLocalPushProperties();
        J7OptionalDeliveryService blockedService = new J7OptionalDeliveryService(
                blockedExportService,
                blockedLedgerStore,
                blockedTransport,
                new J7DeliveryAcknowledgementParser(),
                new J7DeliveryPolicy(defaults),
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(defaults.getOfficialPermissionStatus())
                .isEqualTo(OptionalLocalPushProperties.PermissionStatus.NOT_EVIDENCED);
        assertThatThrownBy(() -> blockedService.deliverSyntheticLoopback(
                CANONICAL_EVENT_ID,
                EXPORT_ID,
                J7OptionalDeliveryService.confirmationFor(identity)))
                .isInstanceOf(J7DeliveryException.class)
                .hasMessage(J7DeliveryError.LOOPBACK_QUALIFICATION_DISABLED.name());
        verifyNoInteractions(blockedExportService, blockedLedgerStore, blockedTransport);
    }

    private J7DeliveryExecutionResult deliver() {
        return service.deliverSyntheticLoopback(
                CANONICAL_EVENT_ID,
                EXPORT_ID,
                J7OptionalDeliveryService.confirmationFor(identity));
    }

    private static J7ValidatedExportArtifact artifact() {
        return new J7ValidatedExportArtifact(
                EXPORT_ID,
                CANONICAL_EVENT_ID,
                J7ExportContract.SCHEMA_ID,
                J7ExportContract.SCHEMA_VERSION,
                DATA_SHA256,
                FILE_SHA256,
                CONTENT);
    }

    private static J7DeliveryLedgerStore.DeliverySnapshot snapshot(
            J7DeliveryLedgerStore.DeliveryState state) {
        return new J7DeliveryLedgerStore.DeliverySnapshot(
                DELIVERY_ID,
                EXPORT_ID,
                FILE_SHA256,
                DATA_SHA256,
                CONTENT.length,
                new J7DeliveryIdentity(EXPORT_ID, FILE_SHA256).idempotencyKey(),
                "1.0",
                state,
                1,
                NOW,
                NOW);
    }

    private static J7DeliveryTransportResponse response(int status, byte[] body) {
        return response(
                status,
                J7DeliveryContract.ACKNOWLEDGEMENT_MEDIA_TYPE,
                body,
                NOW);
    }

    private static J7DeliveryTransportResponse response(
            int status,
            String contentType,
            byte[] body,
            Instant receivedAt) {
        return new J7DeliveryTransportResponse(
                status,
                contentType,
                body,
                receivedAt);
    }

    private static byte[] ack(String status) {
        return ack(status, NOW);
    }

    private static byte[] ack(String status, Instant receivedAt) {
        return ("{" +
                "\"protocolVersion\":\"1.0\"," +
                "\"remoteImportId\":\"" + REMOTE_IMPORT_ID + "\"," +
                "\"status\":\"" + status + "\"," +
                "\"exportId\":\"" + EXPORT_ID + "\"," +
                "\"fileSha256\":\"" + FILE_SHA256 + "\"," +
                "\"dataSha256\":\"" + DATA_SHA256 + "\"," +
                "\"receivedAt\":\"" + receivedAt + "\"" +
                "}").getBytes(StandardCharsets.UTF_8);
    }
}
