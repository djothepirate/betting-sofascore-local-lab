package com.bettingproject.sofascorelocal.application.delivery;

import com.bettingproject.sofascorelocal.application.export.J7CanonicalExportService;
import com.bettingproject.sofascorelocal.application.export.J7DeliveryCandidate;
import com.bettingproject.sofascorelocal.application.export.J7ExportContract;
import com.bettingproject.sofascorelocal.application.export.J7ValidatedExportArtifact;
import com.bettingproject.sofascorelocal.config.OptionalLocalPushProperties;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryError;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryException;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryIdentity;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryState;
import com.bettingproject.sofascorelocal.domain.delivery.J7ProviderDerivedOwnerGo;
import com.bettingproject.sofascorelocal.port.J7DeliveryLedgerStore;
import com.bettingproject.sofascorelocal.port.J7DeliveryTransportFactory;
import com.bettingproject.sofascorelocal.port.J7DeliveryTransportRequest;
import com.bettingproject.sofascorelocal.port.J7DeliveryTransportResponse;
import com.bettingproject.sofascorelocal.port.OwnedJ7DeliveryTransport;
import com.bettingproject.sofascorelocal.security.Sha256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class J7DeliveryRuntimeServiceTest {

    private static final UUID CANONICAL_EVENT_ID =
            UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID EXPORT_ID =
            UUID.fromString("20000000-0000-4000-8000-000000000002");
    private static final UUID DELIVERY_ID =
            UUID.fromString("30000000-0000-4000-8000-000000000003");
    private static final UUID REMOTE_IMPORT_ID =
            UUID.fromString("40000000-0000-4000-8000-000000000004");
    private static final UUID GO_ID =
            UUID.fromString("50000000-0000-4000-8000-000000000005");
    private static final byte[] CONTENT =
            "{\"manifest\":{},\"data\":{}}\n".getBytes(StandardCharsets.UTF_8);
    private static final String FILE_SHA256 = Sha256.hex(CONTENT);
    private static final String DATA_SHA256 = "b".repeat(64);
    private static final String CERTIFICATE_SHA256 = "c".repeat(64);
    private static final String OWNER_GO_SHA256 = "d".repeat(64);
    private static final Instant NOW = Instant.parse("2026-09-02T08:00:00Z");

    private J7CanonicalExportService exportService;
    private J7DeliveryLedgerStore ledgerStore;
    private J7DeliveryTransportFactory transportFactory;
    private OwnedJ7DeliveryTransport transport;
    private J7DeliveryExecutionGate executionGate;
    private J7DeliveryConfirmationCapabilityVerifier confirmationCapabilityVerifier;
    private J7DeliveryRuntimeService service;
    private J7DeliveryIdentity identity;

    @BeforeEach
    void setUp() {
        exportService = mock(J7CanonicalExportService.class);
        ledgerStore = mock(J7DeliveryLedgerStore.class);
        transportFactory = mock(J7DeliveryTransportFactory.class);
        transport = mock(OwnedJ7DeliveryTransport.class);
        executionGate = new J7DeliveryExecutionGate();
        confirmationCapabilityVerifier = mock(
                J7DeliveryConfirmationCapabilityVerifier.class);
        identity = new J7DeliveryIdentity(EXPORT_ID, FILE_SHA256);
        when(exportService.deliveryCandidate(CANONICAL_EVENT_ID, EXPORT_ID))
                .thenReturn(candidate(J7DeliveryPayloadClass.SYNTHETIC_ONLY));
        when(exportService.loadHumanValidatedForDelivery(CANONICAL_EVENT_ID, EXPORT_ID))
                .thenReturn(artifact(J7DeliveryPayloadClass.SYNTHETIC_ONLY));
        when(transportFactory.open(any())).thenReturn(transport);
        when(ledgerStore.claim(
                EXPORT_ID,
                FILE_SHA256,
                DATA_SHA256,
                identity.idempotencyKey(),
                1,
                NOW))
                .thenReturn(new J7DeliveryLedgerStore.ClaimReceipt(
                        DELIVERY_ID,
                        1,
                        J7DeliveryLedgerStore.DeliveryState.IN_FLIGHT,
                        identity.idempotencyKey(),
                        NOW));
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
                        invocation.getArgument(
                                2,
                                J7DeliveryLedgerStore.DeliveryState.class)));
        service = service(syntheticProperties());
    }

    @Test
    void qualifiedSyntheticDeliveryOpensLatePostsExactBytesOnceAndCloses() {
        when(transport.execute(any())).thenReturn(response(201, ack("IMPORTED")));

        J7DeliveryExecutionResult result = service.deliver(
                CANONICAL_EVENT_ID,
                EXPORT_ID,
                confirmation(),
                1);

        assertThat(result.state()).isEqualTo(J7DeliveryState.DELIVERED);
        assertThat(result.safeResultCode()).isEqualTo("HTTP_201_IMPORTED");
        ArgumentCaptor<J7DeliveryTransportFactory.Configuration> configuration =
                ArgumentCaptor.forClass(
                        J7DeliveryTransportFactory.Configuration.class);
        ArgumentCaptor<J7DeliveryTransportRequest> request =
                ArgumentCaptor.forClass(J7DeliveryTransportRequest.class);
        InOrder order = inOrder(exportService, transportFactory, ledgerStore, transport);
        order.verify(exportService)
                .deliveryCandidate(CANONICAL_EVENT_ID, EXPORT_ID);
        order.verify(exportService)
                .loadHumanValidatedForDelivery(CANONICAL_EVENT_ID, EXPORT_ID);
        order.verify(transportFactory).open(configuration.capture());
        order.verify(ledgerStore).claim(
                EXPORT_ID,
                FILE_SHA256,
                DATA_SHA256,
                identity.idempotencyKey(),
                1,
                NOW);
        order.verify(transport).execute(request.capture());
        order.verify(ledgerStore).complete(
                eq(DELIVERY_ID),
                eq(1),
                eq(J7DeliveryLedgerStore.DeliveryState.DELIVERED),
                eq(OptionalInt.of(201)),
                eq("HTTP_201_IMPORTED"),
                any(),
                eq(Optional.of(REMOTE_IMPORT_ID)),
                eq(Optional.of(NOW)),
                eq(NOW));
        order.verify(transport).close();
        assertThat(configuration.getValue().receiverOrigin().toString())
                .isEqualTo(OptionalLocalPushProperties.LOCAL_RECEIVER_ORIGIN);
        assertThat(configuration.getValue().clientCertificateSha256())
                .isEqualTo(CERTIFICATE_SHA256);
        assertThat(request.getValue().content()).isEqualTo(CONTENT);
        assertThat(request.getValue().identity()).isEqualTo(identity);
        verify(transportFactory, times(1)).open(any());
        verify(transport, times(1)).execute(any());
    }

    @Test
    void historicalDuplicateAcknowledgementIsAcceptedAndTransportClosesOnce() {
        Instant initialDurableReceivedAt = Instant.parse("1900-01-01T00:00:00Z");
        when(transport.execute(any())).thenReturn(
                response(200, ack("DUPLICATE", initialDurableReceivedAt)));

        J7DeliveryExecutionResult result = service.deliver(
                CANONICAL_EVENT_ID,
                EXPORT_ID,
                confirmation(),
                1);

        assertThat(result.state()).isEqualTo(J7DeliveryState.DUPLICATE_CONFIRMED);
        assertThat(result.httpStatus()).hasValue(200);
        assertThat(result.safeResultCode()).isEqualTo("HTTP_DUPLICATE_CONFIRMED");
        verify(ledgerStore).complete(
                eq(DELIVERY_ID),
                eq(1),
                eq(J7DeliveryLedgerStore.DeliveryState.DUPLICATE_CONFIRMED),
                eq(OptionalInt.of(200)),
                eq("HTTP_DUPLICATE_CONFIRMED"),
                any(),
                eq(Optional.of(REMOTE_IMPORT_ID)),
                eq(Optional.of(initialDurableReceivedAt)),
                eq(NOW));
        verify(transport, times(1)).execute(any());
        verify(transport, times(1)).close();
        assertGateIdle();
    }

    @Test
    void defaultRuntimeGateStopsBeforeCertificateFactoryClaimAndPost() {
        J7DeliveryRuntimeService blocked = service(new OptionalLocalPushProperties());

        assertError(
                () -> blocked.deliver(
                        CANONICAL_EVENT_ID, EXPORT_ID, confirmation(), 1),
                J7DeliveryError.DELIVERY_DISABLED);

        verify(exportService, times(1))
                .deliveryCandidate(CANONICAL_EVENT_ID, EXPORT_ID);
        verify(exportService, never())
                .loadHumanValidatedForDelivery(any(), any());
        verifyNoInteractions(transportFactory, ledgerStore, transport);
        assertGateIdle();
    }

    @Test
    void invalidExpectedAttemptStopsBeforeExportCertificateClaimAndPost() {
        assertError(
                () -> service.deliver(
                        CANONICAL_EVENT_ID, EXPORT_ID, confirmation(), 0),
                J7DeliveryError.INVALID_CONFIRMATION);

        verifyNoInteractions(exportService, transportFactory, ledgerStore, transport);
        assertGateIdle();
    }

    @Test
    void providerArtifactRemainsBlockedBeforeCertificateFactoryClaimAndPost() {
        when(exportService.deliveryCandidate(CANONICAL_EVENT_ID, EXPORT_ID))
                .thenReturn(candidate(J7DeliveryPayloadClass.PROVIDER_DERIVED));
        OptionalLocalPushProperties properties = syntheticProperties();
        properties.setExecutionMode(
                OptionalLocalPushProperties.ExecutionMode.PROVIDER_DERIVED);
        properties.setOfficialPermissionStatus(
                OptionalLocalPushProperties.PermissionStatus.EVIDENCED_COMPATIBLE);
        properties.setRemoteDeliveryAuthorized(true);

        assertError(
                () -> service(properties).deliver(
                        CANONICAL_EVENT_ID,
                        EXPORT_ID,
                        confirmation(),
                        1),
                J7DeliveryError.PROVIDER_OWNER_GO_REQUIRED);

        verify(exportService, times(1))
                .deliveryCandidate(CANONICAL_EVENT_ID, EXPORT_ID);
        verify(exportService, never())
                .loadHumanValidatedForDelivery(any(), any());
        verifyNoInteractions(transportFactory, ledgerStore, transport);
        assertGateIdle();
    }

    @Test
    void legacyOverloadCannotUseEvenACompleteConfiguredProviderGo() {
        J7ProviderDerivedOwnerGo.Grant grant = providerGrant();
        when(exportService.deliveryCandidate(CANONICAL_EVENT_ID, EXPORT_ID))
                .thenReturn(candidate(J7DeliveryPayloadClass.PROVIDER_DERIVED));

        assertError(
                () -> service(providerProperties(grant)).deliver(
                        CANONICAL_EVENT_ID,
                        EXPORT_ID,
                        confirmation(),
                        1),
                J7DeliveryError.PROVIDER_OWNER_GO_REQUIRED);

        verify(exportService, never()).loadHumanValidatedForDelivery(any(), any());
        verifyNoInteractions(transportFactory, ledgerStore, transport);
    }

    @Test
    void providerReceiptLoadsExactGrantClaimsAtomicallyBeforeFactoryAndPostsOnce() {
        J7ProviderDerivedOwnerGo.Grant grant = providerGrant();
        prepareProvider(grant, J7ProviderDerivedOwnerGo.Status.AVAILABLE);
        when(transport.execute(any())).thenReturn(response(201, ack("IMPORTED")));

        J7DeliveryExecutionResult result = service(providerProperties(grant)).deliver(
                providerReceipt(grant.reference()), confirmation());

        assertThat(result.state()).isEqualTo(J7DeliveryState.DELIVERED);
        InOrder order = inOrder(exportService, ledgerStore, transportFactory, transport);
        order.verify(exportService).deliveryCandidate(CANONICAL_EVENT_ID, EXPORT_ID);
        order.verify(exportService).loadHumanValidatedForDelivery(
                CANONICAL_EVENT_ID, EXPORT_ID);
        order.verify(ledgerStore).findProviderDerivedOwnerGo(grant.reference());
        order.verify(ledgerStore).claimProviderDerived(any());
        order.verify(transportFactory).open(any());
        order.verify(transport).execute(any());
        order.verify(ledgerStore).complete(
                eq(DELIVERY_ID),
                eq(1),
                eq(J7DeliveryLedgerStore.DeliveryState.DELIVERED),
                eq(OptionalInt.of(201)),
                eq("HTTP_201_IMPORTED"),
                any(),
                eq(Optional.of(REMOTE_IMPORT_ID)),
                eq(Optional.of(NOW)),
                eq(NOW));
        order.verify(transport).close();
        verify(ledgerStore, never()).claim(
                any(), anyString(), anyString(), anyString(), anyInt(), any());
        verify(transport, times(1)).execute(any());
        verify(confirmationCapabilityVerifier).consumeRuntimeCapability(any(), eq(NOW));
    }

    @Test
    void forgedProviderReceiptStopsBeforeExportGrantClaimFactoryAndPost() {
        J7ProviderDerivedOwnerGo.Grant grant = providerGrant();
        J7DeliveryConfirmationReceipt forged = providerReceipt(grant.reference());
        doThrow(new J7DeliveryException(J7DeliveryError.INVALID_CONFIRMATION))
                .when(confirmationCapabilityVerifier)
                .consumeRuntimeCapability(forged, NOW);
        assertError(
                () -> service(providerProperties(grant)).deliver(forged, confirmation()),
                J7DeliveryError.INVALID_CONFIRMATION);

        verifyNoInteractions(exportService, ledgerStore, transportFactory, transport);
        assertGateIdle();
    }
    @Test
    void providerReceiptWithDifferentReferenceStopsBeforeGrantLookupClaimAndFactory() {
        J7ProviderDerivedOwnerGo.Grant grant = providerGrant();
        J7ProviderDerivedOwnerGo.Reference otherReference =
                new J7ProviderDerivedOwnerGo.Reference(
                        UUID.fromString("60000000-0000-4000-8000-000000000006"),
                        "e".repeat(64));
        when(exportService.deliveryCandidate(CANONICAL_EVENT_ID, EXPORT_ID))
                .thenReturn(candidate(J7DeliveryPayloadClass.PROVIDER_DERIVED));
        when(exportService.loadHumanValidatedForDelivery(CANONICAL_EVENT_ID, EXPORT_ID))
                .thenReturn(artifact(J7DeliveryPayloadClass.PROVIDER_DERIVED));

        assertError(
                () -> service(providerProperties(grant)).deliver(
                        providerReceipt(otherReference), confirmation()),
                J7DeliveryError.PROVIDER_OWNER_GO_MISMATCH);

        verify(ledgerStore, never()).findProviderDerivedOwnerGo(any());
        verify(ledgerStore, never()).claimProviderDerived(any());
        verifyNoInteractions(transportFactory, transport);
    }

    @Test
    void unavailableProviderGrantStopsBeforeAtomicClaimFactoryAndPost() {
        J7ProviderDerivedOwnerGo.Grant grant = providerGrant();
        prepareProvider(grant, J7ProviderDerivedOwnerGo.Status.EXPIRED);

        assertError(
                () -> service(providerProperties(grant)).deliver(
                        providerReceipt(grant.reference()), confirmation()),
                J7DeliveryError.PROVIDER_OWNER_GO_NOT_AVAILABLE);

        verify(ledgerStore, never()).claimProviderDerived(any());
        verifyNoInteractions(transportFactory, transport);
    }

    @Test
    void providerFactoryFailureAfterAtomicClaimIsPersistedUnknownWithoutPost() {
        J7ProviderDerivedOwnerGo.Grant grant = providerGrant();
        prepareProvider(grant, J7ProviderDerivedOwnerGo.Status.AVAILABLE);
        when(transportFactory.open(any())).thenThrow(
                new IllegalStateException("sensitive certificate detail"));

        J7DeliveryExecutionResult result = service(providerProperties(grant)).deliver(
                providerReceipt(grant.reference()), confirmation());

        assertThat(result.state())
                .isEqualTo(J7DeliveryState.UNKNOWN_RECONCILIATION_REQUIRED);
        assertThat(result.safeResultCode())
                .isEqualTo("TRANSPORT_CONFIGURATION_FAILED");
        InOrder order = inOrder(ledgerStore, transportFactory);
        order.verify(ledgerStore).findProviderDerivedOwnerGo(grant.reference());
        order.verify(ledgerStore).claimProviderDerived(any());
        order.verify(transportFactory).open(any());
        order.verify(ledgerStore).complete(
                eq(DELIVERY_ID),
                eq(1),
                eq(J7DeliveryLedgerStore.DeliveryState.UNKNOWN_RECONCILIATION_REQUIRED),
                eq(OptionalInt.empty()),
                eq("TRANSPORT_CONFIGURATION_FAILED"),
                eq(Optional.empty()),
                eq(Optional.empty()),
                eq(Optional.empty()),
                eq(NOW));
        verifyNoInteractions(transport);
    }

    @Test
    void providerFactoryAndUnknownCompletionFailurePoisonRuntimeWithoutRefundOrPost() {
        J7ProviderDerivedOwnerGo.Grant grant = providerGrant();
        prepareProvider(grant, J7ProviderDerivedOwnerGo.Status.AVAILABLE);
        when(transportFactory.open(any())).thenThrow(
                new IllegalStateException("sensitive certificate detail"));
        when(ledgerStore.complete(
                eq(DELIVERY_ID),
                eq(1),
                eq(J7DeliveryLedgerStore.DeliveryState.UNKNOWN_RECONCILIATION_REQUIRED),
                eq(OptionalInt.empty()),
                eq("TRANSPORT_CONFIGURATION_FAILED"),
                eq(Optional.empty()),
                eq(Optional.empty()),
                eq(Optional.empty()),
                eq(NOW)))
                .thenThrow(new J7DeliveryLedgerStore.LedgerException(
                        J7DeliveryLedgerStore.LedgerFailure.STORAGE_UNAVAILABLE));

        assertError(
                () -> service(providerProperties(grant)).deliver(
                        providerReceipt(grant.reference()), confirmation()),
                J7DeliveryError.TRANSPORT_CONFIGURATION_FAILED);

        verify(ledgerStore, times(1)).claimProviderDerived(any());
        verifyNoInteractions(transport);
        assertThat(executionGate.state())
                .isEqualTo(J7DeliveryExecutionGate.State.POISONED);
    }

    @Test
    void wrongConfirmationStopsBeforeCertificateFactoryClaimAndPost() {
        assertError(
                () -> service.deliver(
                        CANONICAL_EVENT_ID,
                        EXPORT_ID,
                        "wrong-sensitive-value",
                        1),
                J7DeliveryError.INVALID_CONFIRMATION);

        verify(exportService, times(1))
                .deliveryCandidate(CANONICAL_EVENT_ID, EXPORT_ID);
        verify(exportService, never())
                .loadHumanValidatedForDelivery(any(), any());
        verifyNoInteractions(transportFactory, ledgerStore, transport);
        assertGateIdle();
    }

    @Test
    void certificateFactoryFailureIsSanitizedAndNeverClaims() {
        when(transportFactory.open(any())).thenThrow(
                new IllegalStateException("sensitive certificate detail"));

        assertError(
                () -> service.deliver(
                        CANONICAL_EVENT_ID, EXPORT_ID, confirmation(), 1),
                J7DeliveryError.TRANSPORT_CONFIGURATION_FAILED);

        verify(transportFactory, times(1)).open(any());
        verifyNoInteractions(ledgerStore, transport);
        assertGateIdle();
    }

    @Test
    void changedVerifiedArtifactIsRefusedBeforeCertificateFactoryAndClaim() {
        when(exportService.loadHumanValidatedForDelivery(
                CANONICAL_EVENT_ID, EXPORT_ID))
                .thenReturn(artifact(J7DeliveryPayloadClass.PROVIDER_DERIVED));

        assertError(
                () -> service.deliver(
                        CANONICAL_EVENT_ID, EXPORT_ID, confirmation(), 1),
                J7DeliveryError.PAYLOAD_IDENTITY_CHANGED);

        verify(exportService).loadHumanValidatedForDelivery(
                CANONICAL_EVENT_ID, EXPORT_ID);
        verifyNoInteractions(transportFactory, ledgerStore, transport);
        assertGateIdle();
    }

    @Test
    void unexpectedTransportFailureBecomesUnknownWithoutRetryAndStillCloses() {
        when(transport.execute(any())).thenThrow(
                new IllegalStateException("sensitive receiver detail"));

        J7DeliveryExecutionResult result = service.deliver(
                CANONICAL_EVENT_ID,
                EXPORT_ID,
                confirmation(),
                1);

        assertThat(result.state())
                .isEqualTo(J7DeliveryState.UNKNOWN_RECONCILIATION_REQUIRED);
        assertThat(result.httpStatus()).isEmpty();
        assertThat(result.safeResultCode()).isEqualTo("TRANSPORT_RUNTIME_FAILURE");
        verify(transport, times(1)).execute(any());
        verify(transport, times(1)).close();
        verify(ledgerStore).complete(
                eq(DELIVERY_ID),
                eq(1),
                eq(J7DeliveryLedgerStore.DeliveryState.UNKNOWN_RECONCILIATION_REQUIRED),
                eq(OptionalInt.empty()),
                eq("TRANSPORT_RUNTIME_FAILURE"),
                eq(Optional.empty()),
                eq(Optional.empty()),
                eq(Optional.empty()),
                eq(NOW));
        assertGateIdle();
    }

    @Test
    void ledgerClaimFailureStillClosesTheOwnedTransportBeforeEscaping() {
        when(ledgerStore.claim(
                any(), anyString(), anyString(), anyString(), anyInt(), any()))
                .thenThrow(new J7DeliveryLedgerStore.LedgerException(
                        J7DeliveryLedgerStore.LedgerFailure.ANOTHER_DELIVERY_IN_FLIGHT));

        assertThatThrownBy(() -> service.deliver(
                CANONICAL_EVENT_ID, EXPORT_ID, confirmation(), 1))
                .isInstanceOf(J7DeliveryLedgerStore.LedgerException.class);

        verify(transport, never()).execute(any());
        verify(transport, times(1)).close();
        assertGateIdle();
    }

    @Test
    void closeFailureAfterACompletedAttemptIsReportedWithoutRepeatingThePost() {
        when(transport.execute(any())).thenReturn(response(201, ack("IMPORTED")));
        org.mockito.Mockito.doThrow(new IllegalStateException("sensitive close detail"))
                .when(transport).close();

        assertError(
                () -> service.deliver(
                        CANONICAL_EVENT_ID, EXPORT_ID, confirmation(), 1),
                J7DeliveryError.TRANSPORT_CLEANUP_FAILED);

        verify(transport, times(1)).execute(any());
        verify(transport, times(1)).close();
        verify(ledgerStore, times(1)).complete(
                any(), anyInt(), any(), any(), anyString(), any(), any(), any(), any());
        assertThat(executionGate.state())
                .isEqualTo(J7DeliveryExecutionGate.State.POISONED);

        assertError(
                () -> service.deliver(
                        CANONICAL_EVENT_ID, EXPORT_ID, confirmation(), 2),
                J7DeliveryError.DELIVERY_RUNTIME_POISONED);
        verify(exportService, times(1))
                .deliveryCandidate(CANONICAL_EVENT_ID, EXPORT_ID);
        verify(transportFactory, times(1)).open(any());
        verify(transport, times(1)).execute(any());
    }

    @Test
    void closeFailureNeverMasksThePrimaryLedgerFailure() {
        J7DeliveryLedgerStore.LedgerException primary =
                new J7DeliveryLedgerStore.LedgerException(
                        J7DeliveryLedgerStore.LedgerFailure.ANOTHER_DELIVERY_IN_FLIGHT);
        when(ledgerStore.claim(
                any(), anyString(), anyString(), anyString(), anyInt(), any()))
                .thenThrow(primary);
        org.mockito.Mockito.doThrow(new IllegalStateException("sensitive close detail"))
                .when(transport).close();

        assertThatThrownBy(() -> service.deliver(
                CANONICAL_EVENT_ID, EXPORT_ID, confirmation(), 1))
                .isSameAs(primary)
                .satisfies(exception -> assertThat(exception.getSuppressed())
                        .hasSize(1));

        verify(transport, never()).execute(any());
        verify(transport, times(1)).close();
        assertThat(executionGate.state())
                .isEqualTo(J7DeliveryExecutionGate.State.POISONED);

        assertError(
                () -> service.deliver(
                        CANONICAL_EVENT_ID, EXPORT_ID, confirmation(), 2),
                J7DeliveryError.DELIVERY_RUNTIME_POISONED);
        verify(exportService, times(1))
                .deliveryCandidate(CANONICAL_EVENT_ID, EXPORT_ID);
        verify(transportFactory, times(1)).open(any());
    }

    @Test
    void globalGuardIsHeldThroughCloseAndReleasedForTheNextManualInvocation()
            throws Exception {
        OwnedJ7DeliveryTransport nextTransport =
                mock(OwnedJ7DeliveryTransport.class);
        CountDownLatch closeEntered = new CountDownLatch(1);
        CountDownLatch releaseClose = new CountDownLatch(1);
        when(transport.execute(any())).thenReturn(response(201, ack("IMPORTED")));
        when(nextTransport.execute(any())).thenReturn(response(201, ack("IMPORTED")));
        when(transportFactory.open(any())).thenReturn(transport, nextTransport);
        when(ledgerStore.claim(
                EXPORT_ID,
                FILE_SHA256,
                DATA_SHA256,
                identity.idempotencyKey(),
                2,
                NOW))
                .thenReturn(new J7DeliveryLedgerStore.ClaimReceipt(
                        DELIVERY_ID,
                        2,
                        J7DeliveryLedgerStore.DeliveryState.IN_FLIGHT,
                        identity.idempotencyKey(),
                        NOW));
        org.mockito.Mockito.doAnswer(invocation -> {
            closeEntered.countDown();
            if (!releaseClose.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("test close release timed out");
            }
            return null;
        }).when(transport).close();

        try (var executor = Executors.newSingleThreadExecutor()) {
            var first = executor.submit(() -> service.deliver(
                    CANONICAL_EVENT_ID,
                    EXPORT_ID,
                    confirmation(),
                    1));
            assertThat(closeEntered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(executionGate.state())
                    .isEqualTo(J7DeliveryExecutionGate.State.ACTIVE);

            assertError(
                    () -> service.deliver(
                            CANONICAL_EVENT_ID,
                            EXPORT_ID,
                            confirmation(),
                            2),
                    J7DeliveryError.DELIVERY_IN_PROGRESS);
            verify(exportService, times(1))
                    .deliveryCandidate(CANONICAL_EVENT_ID, EXPORT_ID);
            verify(exportService, times(1))
                    .loadHumanValidatedForDelivery(CANONICAL_EVENT_ID, EXPORT_ID);
            verify(transportFactory, times(1)).open(any());
            verify(ledgerStore, times(1)).claim(
                    any(), anyString(), anyString(), anyString(), anyInt(), any());
            verify(ledgerStore, times(1)).complete(
                    any(), anyInt(), any(), any(), anyString(), any(), any(), any(), any());
            verify(transport, times(1)).execute(any());
            verifyNoInteractions(nextTransport);

            releaseClose.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS).state())
                    .isEqualTo(J7DeliveryState.DELIVERED);
            assertThat(executionGate.state())
                    .isEqualTo(J7DeliveryExecutionGate.State.IDLE);

            J7DeliveryExecutionResult next = service.deliver(
                    CANONICAL_EVENT_ID,
                    EXPORT_ID,
                    confirmation(),
                    2);
            assertThat(next.state()).isEqualTo(J7DeliveryState.DELIVERED);
            assertThat(next.attemptNumber()).isEqualTo(2);
            verify(exportService, times(2))
                    .deliveryCandidate(CANONICAL_EVENT_ID, EXPORT_ID);
            verify(exportService, times(2))
                    .loadHumanValidatedForDelivery(CANONICAL_EVENT_ID, EXPORT_ID);
            verify(transportFactory, times(2)).open(any());
            verify(nextTransport, times(1)).execute(any());
            verify(nextTransport, times(1)).close();
            assertThat(executionGate.state())
                    .isEqualTo(J7DeliveryExecutionGate.State.IDLE);
        }
        finally {
            releaseClose.countDown();
        }
    }

    private J7DeliveryRuntimeService service(
            OptionalLocalPushProperties properties) {
        return new J7DeliveryRuntimeService(
                exportService,
                ledgerStore,
                transportFactory,
                new J7DeliveryAcknowledgementParser(),
                new J7DeliveryPolicy(properties),
                Clock.fixed(NOW, ZoneOffset.UTC),
                executionGate,
                confirmationCapabilityVerifier);
    }

    private static OptionalLocalPushProperties syntheticProperties() {
        OptionalLocalPushProperties properties = new OptionalLocalPushProperties();
        properties.setEnabled(true);
        properties.setExecutionMode(
                OptionalLocalPushProperties.ExecutionMode.SYNTHETIC_LOOPBACK);
        properties.setReceiverOrigin(
                OptionalLocalPushProperties.LOCAL_RECEIVER_ORIGIN);
        properties.setReceiverQualification(
                OptionalLocalPushProperties.QualificationStatus.PASS);
        properties.setSenderQualification(
                OptionalLocalPushProperties.QualificationStatus.PASS);
        properties.getMtls().setClientCertificateSha256(CERTIFICATE_SHA256);
        return properties;
    }

    private static OptionalLocalPushProperties providerProperties(
            J7ProviderDerivedOwnerGo.Grant grant) {
        OptionalLocalPushProperties properties = syntheticProperties();
        properties.setExecutionMode(
                OptionalLocalPushProperties.ExecutionMode.PROVIDER_DERIVED);
        properties.setRemoteDeliveryAuthorized(true);
        properties.setOfficialPermissionStatus(
                OptionalLocalPushProperties.PermissionStatus.EVIDENCED_COMPATIBLE);
        properties.getProviderOwnerGo().setGoId(grant.goId().toString());
        properties.getProviderOwnerGo().setOwnerGoDocumentSha256(
                grant.ownerDecisionBlockSha256());
        return properties;
    }

    private void prepareProvider(
            J7ProviderDerivedOwnerGo.Grant grant,
            J7ProviderDerivedOwnerGo.Status status) {
        when(exportService.deliveryCandidate(CANONICAL_EVENT_ID, EXPORT_ID))
                .thenReturn(candidate(J7DeliveryPayloadClass.PROVIDER_DERIVED));
        when(exportService.loadHumanValidatedForDelivery(CANONICAL_EVENT_ID, EXPORT_ID))
                .thenReturn(artifact(J7DeliveryPayloadClass.PROVIDER_DERIVED));
        when(ledgerStore.findProviderDerivedOwnerGo(grant.reference()))
                .thenReturn(Optional.of(providerSnapshot(grant, status)));
        when(ledgerStore.claimProviderDerived(any()))
                .thenReturn(new J7DeliveryLedgerStore.ClaimReceipt(
                        DELIVERY_ID,
                        1,
                        J7DeliveryLedgerStore.DeliveryState.IN_FLIGHT,
                        identity.idempotencyKey(),
                        NOW));
    }

    private static J7DeliveryConfirmationReceipt providerReceipt(
            J7ProviderDerivedOwnerGo.Reference reference) {
        return new J7DeliveryConfirmationReceipt(
                J7DeliveryConfirmationAction.DELIVERY,
                CANONICAL_EVENT_ID,
                EXPORT_ID,
                FILE_SHA256,
                1,
                Optional.of(reference));
    }

    private static J7ProviderDerivedOwnerGo.Grant providerGrant() {
        return new J7ProviderDerivedOwnerGo.Grant(
                GO_ID,
                OWNER_GO_SHA256,
                "WO-SS-20260904-046-j9-provider-derived-real-j7-delivery",
                "docs/validation/J9-WO046-CAMPAIGN-MANIFEST.md",
                "e".repeat(64),
                "1".repeat(40),
                "2".repeat(40),
                "docs/validation/J9-OFFICIAL-PERMISSION-EVIDENCE.md",
                "f".repeat(64),
                "EVIDENCED_COMPATIBLE",
                "PASS",
                "PASS",
                "CODEX_LOCAL_UI",
                CANONICAL_EVENT_ID,
                16_310_945L,
                EXPORT_ID,
                FILE_SHA256,
                DATA_SHA256,
                CONTENT.length,
                J7ExportContract.SCHEMA_ID,
                J7ExportContract.SCHEMA_VERSION,
                URI.create(OptionalLocalPushProperties.LOCAL_RECEIVER_ORIGIN),
                CERTIFICATE_SHA256,
                1,
                1,
                NOW.minusSeconds(60),
                NOW.plusSeconds(60),
                "GRANT",
                "ONE_TIME",
                "PROVIDER_DERIVED",
                "HUMAN_VALIDATED",
                true,
                false,
                false,
                false,
                false,
                false);
    }

    private static J7ProviderDerivedOwnerGo.Snapshot providerSnapshot(
            J7ProviderDerivedOwnerGo.Grant grant,
            J7ProviderDerivedOwnerGo.Status status) {
        return new J7ProviderDerivedOwnerGo.Snapshot(
                grant,
                status,
                NOW.minusSeconds(120),
                NOW,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                OptionalInt.empty());
    }

    private static J7ValidatedExportArtifact artifact(
            J7DeliveryPayloadClass payloadClass) {
        return new J7ValidatedExportArtifact(
                EXPORT_ID,
                CANONICAL_EVENT_ID,
                J7ExportContract.SCHEMA_ID,
                J7ExportContract.SCHEMA_VERSION,
                DATA_SHA256,
                FILE_SHA256,
                payloadClass,
                CONTENT);
    }

    private static J7DeliveryCandidate candidate(
            J7DeliveryPayloadClass payloadClass) {
        return new J7DeliveryCandidate(
                EXPORT_ID,
                CANONICAL_EVENT_ID,
                FILE_SHA256,
                payloadClass);
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

    private static String confirmation() {
        return J7OptionalDeliveryService.confirmationFor(
                new J7DeliveryIdentity(EXPORT_ID, FILE_SHA256));
    }

    private static J7DeliveryTransportResponse response(int status, byte[] body) {
        return new J7DeliveryTransportResponse(
                status,
                J7DeliveryContract.ACKNOWLEDGEMENT_MEDIA_TYPE,
                body,
                NOW);
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

    private static void assertError(Runnable action, J7DeliveryError error) {
        assertThatThrownBy(action::run)
                .isInstanceOf(J7DeliveryException.class)
                .hasMessage(error.name())
                .extracting(exception -> ((J7DeliveryException) exception).error())
                .isEqualTo(error);
    }

    private void assertGateIdle() {
        assertThat(executionGate.state())
                .isEqualTo(J7DeliveryExecutionGate.State.IDLE);
    }
}
