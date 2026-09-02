package com.bettingproject.sofascorelocal.application.export;

import com.bettingproject.sofascorelocal.application.delivery.J7DeliveryPayloadClass;
import com.bettingproject.sofascorelocal.domain.export.J7ExportDecision;
import com.bettingproject.sofascorelocal.domain.export.J7ExportError;
import com.bettingproject.sofascorelocal.domain.export.J7ExportException;
import com.bettingproject.sofascorelocal.domain.export.J7ExportManifest;
import com.bettingproject.sofascorelocal.domain.export.J7ExportStatus;
import com.bettingproject.sofascorelocal.domain.export.J7StoredFile;
import com.bettingproject.sofascorelocal.port.J7ExportFileStore;
import com.bettingproject.sofascorelocal.port.J7ExportManifestStore;
import com.bettingproject.sofascorelocal.security.Sha256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class J7CanonicalExportServiceTest {

    private static final UUID EVENT_ID = UUID.fromString(
            "9740bb59-0207-31a3-a6ae-5c8463255887");
    private static final UUID EXPORT_ID = UUID.fromString(
            "70000000-0000-4000-8000-000000000007");
    private static final UUID ORPHAN_EXPORT_ID = UUID.fromString(
            "71111111-1111-4111-8111-111111111111");
    private static final Instant GENERATED_AT = Instant.parse("2026-08-19T10:00:00Z");
    private static final Instant DECIDED_AT = Instant.parse("2026-08-19T10:05:00Z");
    private static final String DATA_SHA = "a".repeat(64);
    private static final String SOURCE_SET_SHA = "b".repeat(64);
    private static final byte[] CANDIDATE_BYTES = "{}\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] TERMINAL_BYTES = "{ }\n".getBytes(StandardCharsets.UTF_8);
    private static final String CANDIDATE_SHA = Sha256.hex(CANDIDATE_BYTES);
    private static final String TERMINAL_SHA = Sha256.hex(TERMINAL_BYTES);

    private final J7CurrentEventSelectionReader selectionReader =
            mock(J7CurrentEventSelectionReader.class);
    private final J7EnvelopeAssembler assembler = mock(J7EnvelopeAssembler.class);
    private final J7ExportIntegrityGuard guard = mock(J7ExportIntegrityGuard.class);
    private final J7ExportManifestStore manifestStore = mock(J7ExportManifestStore.class);
    private final J7ExportFileStore fileStore = mock(J7ExportFileStore.class);
    private final J7CurrentEventSelection selection = mock(J7CurrentEventSelection.class);

    private J7CanonicalExportService service;

    @BeforeEach
    void setUp() {
        service = new J7CanonicalExportService(
                selectionReader,
                assembler,
                guard,
                manifestStore,
                fileStore,
                Clock.fixed(DECIDED_AT, ZoneOffset.UTC),
                () -> EXPORT_ID,
                "0.1.0-SNAPSHOT");
        when(manifestStore.findDecisionIntent(any())).thenReturn(Optional.empty());
        when(manifestStore.recordDecisionIntent(any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(1));
    }

    @Test
    void refusesApprovalWhenTheCurrentSourceSetChanged() {
        J7ExportManifest candidate = manifest(J7ExportStatus.COHERENCE_CHECKED);
        when(manifestStore.findByExportId(EXPORT_ID)).thenReturn(Optional.of(candidate));
        when(fileStore.readVerified(
                candidate.relativePath(), CANDIDATE_SHA, CANDIDATE_BYTES.length))
                .thenReturn(CANDIDATE_BYTES);
        when(guard.verify(CANDIDATE_BYTES, EVENT_ID, EXPORT_ID))
                .thenReturn(verified(
                        J7ExportStatus.COHERENCE_CHECKED,
                        CANDIDATE_SHA,
                        CANDIDATE_BYTES,
                        GENERATED_AT,
                        Optional.empty(),
                        Optional.empty()));
        when(selectionReader.load(EVENT_ID)).thenReturn(Optional.of(selection));
        when(assembler.assembleCandidate(EXPORT_ID, GENERATED_AT, "0.1.0-SNAPSHOT", selection))
                .thenReturn(assembled(
                        J7ExportStatus.COHERENCE_CHECKED,
                        "e".repeat(64),
                        CANDIDATE_BYTES));

        assertThatThrownBy(() -> service.validate(
                EVENT_ID,
                EXPORT_ID,
                validationConfirmation()))
                .isInstanceOfSatisfying(J7ExportException.class, exception ->
                        assertThat(exception.error())
                                .isEqualTo(J7ExportError.SOURCE_SET_CHANGED));
        verify(fileStore, never()).writeNewOrVerify(any(), any());
        verify(manifestStore, never()).decide(any(), any());
    }

    @Test
    void writesAndVerifiesTheTerminalFileBeforeRecordingHumanValidation() {
        J7ExportManifest candidate = manifest(J7ExportStatus.COHERENCE_CHECKED);
        J7ExportManifest validated = manifest(J7ExportStatus.HUMAN_VALIDATED);
        stubCandidateRead(candidate);
        when(selectionReader.load(EVENT_ID)).thenReturn(Optional.of(selection));
        when(assembler.assembleCandidate(EXPORT_ID, GENERATED_AT, "0.1.0-SNAPSHOT", selection))
                .thenReturn(assembled(
                        J7ExportStatus.COHERENCE_CHECKED,
                        SOURCE_SET_SHA,
                        CANDIDATE_BYTES));
        J7AssembledEnvelope terminal = assembled(
                J7ExportStatus.HUMAN_VALIDATED,
                SOURCE_SET_SHA,
                TERMINAL_BYTES);
        when(assembler.decide(
                CANDIDATE_BYTES,
                J7ExportStatus.HUMAN_VALIDATED,
                DECIDED_AT,
                null)).thenReturn(terminal);
        when(guard.verify(TERMINAL_BYTES, EVENT_ID, EXPORT_ID))
                .thenReturn(verified(
                        J7ExportStatus.HUMAN_VALIDATED,
                        TERMINAL_SHA,
                        TERMINAL_BYTES,
                        GENERATED_AT,
                        Optional.of(DECIDED_AT),
                        Optional.empty()));
        String validatedPath = path("validated");
        when(fileStore.writeNewOrVerify(validatedPath, TERMINAL_BYTES))
                .thenReturn(new J7StoredFile(
                        validatedPath, TERMINAL_SHA, TERMINAL_BYTES.length));
        when(manifestStore.decide(eq(EXPORT_ID), any())).thenReturn(validated);

        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThat(service.validate(EVENT_ID, EXPORT_ID, validationConfirmation()))
                    .isEqualTo(validated);

            InOrder order = inOrder(fileStore, manifestStore);
            order.verify(manifestStore).recordDecisionIntent(eq(EXPORT_ID), any());
            order.verify(fileStore).writeNewOrVerify(validatedPath, TERMINAL_BYTES);
            order.verify(manifestStore).decide(eq(EXPORT_ID), any());
            verify(fileStore, never()).deleteVerified(
                    candidate.relativePath(), CANDIDATE_SHA, CANDIDATE_BYTES.length);

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);
            verify(fileStore).deleteVerified(
                    candidate.relativePath(), CANDIDATE_SHA, CANDIDATE_BYTES.length);
        }
        finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void neverRecordsATerminalStatusWhenTerminalStorageFails() {
        J7ExportManifest candidate = manifest(J7ExportStatus.COHERENCE_CHECKED);
        stubCandidateRead(candidate);
        when(selectionReader.load(EVENT_ID)).thenReturn(Optional.of(selection));
        when(assembler.assembleCandidate(EXPORT_ID, GENERATED_AT, "0.1.0-SNAPSHOT", selection))
                .thenReturn(assembled(
                        J7ExportStatus.COHERENCE_CHECKED,
                        SOURCE_SET_SHA,
                        CANDIDATE_BYTES));
        when(assembler.decide(any(), eq(J7ExportStatus.HUMAN_VALIDATED), any(), eq(null)))
                .thenReturn(assembled(
                        J7ExportStatus.HUMAN_VALIDATED,
                        SOURCE_SET_SHA,
                        TERMINAL_BYTES));
        when(guard.verify(TERMINAL_BYTES, EVENT_ID, EXPORT_ID))
                .thenReturn(verified(
                        J7ExportStatus.HUMAN_VALIDATED,
                        TERMINAL_SHA,
                        TERMINAL_BYTES,
                        GENERATED_AT,
                        Optional.of(DECIDED_AT),
                        Optional.empty()));
        when(fileStore.writeNewOrVerify(path("validated"), TERMINAL_BYTES))
                .thenThrow(new J7ExportException(J7ExportError.STORAGE_UNAVAILABLE));

        assertThatThrownBy(() -> service.validate(
                EVENT_ID,
                EXPORT_ID,
                validationConfirmation()))
                .isInstanceOfSatisfying(J7ExportException.class, exception ->
                        assertThat(exception.error())
                                .isEqualTo(J7ExportError.STORAGE_UNAVAILABLE));
        verify(manifestStore, never()).decide(any(), any());
        verify(manifestStore).recordDecisionIntent(eq(EXPORT_ID), any());
    }

    @Test
    void reusesAnExactlyVerifiedTerminalOrphanAfterADatabaseFailure() {
        J7ExportManifest candidate = manifest(J7ExportStatus.COHERENCE_CHECKED);
        J7ExportManifest validated = manifest(J7ExportStatus.HUMAN_VALIDATED);
        stubCandidateRead(candidate);
        when(selectionReader.load(EVENT_ID)).thenReturn(Optional.of(selection));
        when(assembler.assembleCandidate(EXPORT_ID, GENERATED_AT, "0.1.0-SNAPSHOT", selection))
                .thenReturn(assembled(
                        J7ExportStatus.COHERENCE_CHECKED,
                        SOURCE_SET_SHA,
                        CANDIDATE_BYTES));
        J7ExportDecision expectedDecision = new J7ExportDecision(
                J7ExportStatus.HUMAN_VALIDATED,
                DECIDED_AT,
                Optional.empty(),
                path("validated"),
                TERMINAL_SHA,
                TERMINAL_BYTES.length);
        when(manifestStore.findDecisionIntent(EXPORT_ID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(expectedDecision));
        when(assembler.decide(
                CANDIDATE_BYTES,
                J7ExportStatus.HUMAN_VALIDATED,
                DECIDED_AT,
                null)).thenReturn(assembled(
                        J7ExportStatus.HUMAN_VALIDATED,
                        SOURCE_SET_SHA,
                        TERMINAL_BYTES));
        when(guard.verify(TERMINAL_BYTES, EVENT_ID, EXPORT_ID))
                .thenReturn(verified(
                        J7ExportStatus.HUMAN_VALIDATED,
                        TERMINAL_SHA,
                        TERMINAL_BYTES,
                        GENERATED_AT,
                        Optional.of(DECIDED_AT),
                        Optional.empty()));
        when(fileStore.writeNewOrVerify(path("validated"), TERMINAL_BYTES))
                .thenReturn(new J7StoredFile(
                        path("validated"), TERMINAL_SHA, TERMINAL_BYTES.length));
        when(manifestStore.decide(eq(EXPORT_ID), any()))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"))
                .thenReturn(validated);

        assertError(
                () -> service.validate(EVENT_ID, EXPORT_ID, validationConfirmation()),
                J7ExportError.DATABASE_UNAVAILABLE);
        assertThat(service.validate(EVENT_ID, EXPORT_ID, validationConfirmation()))
                .isEqualTo(validated);

        verify(assembler, times(2)).decide(
                CANDIDATE_BYTES,
                J7ExportStatus.HUMAN_VALIDATED,
                DECIDED_AT,
                null);
        verify(fileStore, times(2)).writeNewOrVerify(
                path("validated"),
                TERMINAL_BYTES);
        verify(manifestStore, times(2)).decide(eq(EXPORT_ID), any());
    }

    @Test
    void requiresExactConfirmationsAndABoundedNonBlankRejectionReason() {
        J7ExportManifest candidate = manifest(J7ExportStatus.COHERENCE_CHECKED);
        when(manifestStore.findByExportId(EXPORT_ID)).thenReturn(Optional.of(candidate));

        assertError(
                () -> service.validate(EVENT_ID, EXPORT_ID, validationConfirmation() + " "),
                J7ExportError.INVALID_CONFIRMATION);
        assertError(
                () -> service.reject(EVENT_ID, EXPORT_ID, rejectionConfirmation(), "   "),
                J7ExportError.INVALID_REJECTION_REASON);
        assertError(
                () -> service.reject(
                        EVENT_ID,
                        EXPORT_ID,
                        rejectionConfirmation(),
                        "x".repeat(501)),
                J7ExportError.INVALID_REJECTION_REASON);
        verify(fileStore, never()).readVerified(any(), any(), any(Long.class));
    }

    @Test
    void neverAllowsCandidateOrRejectedContentToBeDownloaded() {
        when(manifestStore.findByExportId(EXPORT_ID))
                .thenReturn(Optional.of(manifest(J7ExportStatus.COHERENCE_CHECKED)))
                .thenReturn(Optional.of(manifest(J7ExportStatus.REJECTED)));

        assertError(
                () -> service.download(EVENT_ID, EXPORT_ID),
                J7ExportError.NOT_DOWNLOADABLE);
        assertError(
                () -> service.download(EVENT_ID, EXPORT_ID),
                J7ExportError.NOT_DOWNLOADABLE);
        verify(fileStore, never()).readVerified(any(), any(), any(Long.class));
    }

    @Test
    void exposesOnlyPersistedDeliveryMetadataBeforeReadingValidatedBytes() {
        J7ExportManifest validated = manifest(J7ExportStatus.HUMAN_VALIDATED);
        when(manifestStore.findByExportId(EXPORT_ID)).thenReturn(Optional.of(validated));

        J7DeliveryCandidate candidate = service.deliveryCandidate(EVENT_ID, EXPORT_ID);

        assertThat(candidate.exportId()).isEqualTo(EXPORT_ID);
        assertThat(candidate.canonicalEventId()).isEqualTo(EVENT_ID);
        assertThat(candidate.fileSha256()).isEqualTo(TERMINAL_SHA);
        assertThat(candidate.payloadClass())
                .isEqualTo(J7DeliveryPayloadClass.MIXED_OR_UNKNOWN);
        verify(fileStore, never()).readVerified(any(), any(), any(Long.class));
        verifyNoInteractions(guard);
    }

    @Test
    void exposesOnlyAReverifiedHumanValidatedArtifactForSeparateDelivery() {
        J7ExportManifest validated = manifest(J7ExportStatus.HUMAN_VALIDATED);
        when(manifestStore.findByExportId(EXPORT_ID)).thenReturn(Optional.of(validated));
        when(fileStore.readVerified(
                validated.relativePath(), TERMINAL_SHA, TERMINAL_BYTES.length))
                .thenReturn(TERMINAL_BYTES);
        when(guard.verify(TERMINAL_BYTES, EVENT_ID, EXPORT_ID))
                .thenReturn(verified(
                        J7ExportStatus.HUMAN_VALIDATED,
                        TERMINAL_SHA,
                        TERMINAL_BYTES,
                        GENERATED_AT,
                        Optional.of(DECIDED_AT),
                        Optional.empty()));

        J7ValidatedExportArtifact artifact = service.loadHumanValidatedForDelivery(
                EVENT_ID, EXPORT_ID);

        assertThat(artifact.exportId()).isEqualTo(EXPORT_ID);
        assertThat(artifact.canonicalEventId()).isEqualTo(EVENT_ID);
        assertThat(artifact.schemaId()).isEqualTo(J7ExportContract.SCHEMA_ID);
        assertThat(artifact.schemaVersion()).isEqualTo(J7ExportContract.SCHEMA_VERSION);
        assertThat(artifact.dataSha256()).isEqualTo(DATA_SHA);
        assertThat(artifact.fileSha256()).isEqualTo(TERMINAL_SHA);
        assertThat(artifact.payloadClass())
                .isEqualTo(J7DeliveryPayloadClass.MIXED_OR_UNKNOWN);
        assertThat(artifact.content()).isEqualTo(TERMINAL_BYTES);
        assertThat(artifact.sizeBytes()).isEqualTo(TERMINAL_BYTES.length);
    }

    @Test
    void neverExposesCandidateOrRejectedContentForDelivery() {
        when(manifestStore.findByExportId(EXPORT_ID))
                .thenReturn(Optional.of(manifest(J7ExportStatus.COHERENCE_CHECKED)))
                .thenReturn(Optional.of(manifest(J7ExportStatus.REJECTED)));

        assertError(
                () -> service.loadHumanValidatedForDelivery(EVENT_ID, EXPORT_ID),
                J7ExportError.NOT_DOWNLOADABLE);
        assertError(
                () -> service.loadHumanValidatedForDelivery(EVENT_ID, EXPORT_ID),
                J7ExportError.NOT_DOWNLOADABLE);
        verify(fileStore, never()).readVerified(any(), any(), any(Long.class));
    }

    @Test
    void neverExposesCandidateOrRejectedMetadataForDelivery() {
        when(manifestStore.findByExportId(EXPORT_ID))
                .thenReturn(Optional.of(manifest(J7ExportStatus.COHERENCE_CHECKED)))
                .thenReturn(Optional.of(manifest(J7ExportStatus.REJECTED)));

        assertError(
                () -> service.deliveryCandidate(EVENT_ID, EXPORT_ID),
                J7ExportError.NOT_DOWNLOADABLE);
        assertError(
                () -> service.deliveryCandidate(EVENT_ID, EXPORT_ID),
                J7ExportError.NOT_DOWNLOADABLE);
        verify(fileStore, never()).readVerified(any(), any(), any(Long.class));
        verifyNoInteractions(guard);
    }

    @Test
    void anIdempotentTerminalRetryCleansAResidualCandidateUsingItsOwnSize() {
        J7ExportManifest validated = manifest(J7ExportStatus.HUMAN_VALIDATED);
        when(manifestStore.findByExportId(EXPORT_ID)).thenReturn(Optional.of(validated));
        when(fileStore.readVerified(
                validated.relativePath(), TERMINAL_SHA, TERMINAL_BYTES.length))
                .thenReturn(TERMINAL_BYTES);
        when(guard.verify(TERMINAL_BYTES, EVENT_ID, EXPORT_ID))
                .thenReturn(verified(
                        J7ExportStatus.HUMAN_VALIDATED,
                        TERMINAL_SHA,
                        TERMINAL_BYTES,
                        GENERATED_AT,
                        Optional.of(DECIDED_AT),
                        Optional.empty()));
        when(fileStore.readExisting(path("candidate")))
                .thenReturn(Optional.of(CANDIDATE_BYTES));

        assertThat(service.validate(EVENT_ID, EXPORT_ID, validationConfirmation()))
                .isEqualTo(validated);

        verify(fileStore).deleteVerified(
                path("candidate"),
                CANDIDATE_SHA,
                CANDIDATE_BYTES.length);
    }

    @Test
    void truncatesGeneratedTimestampsToPostgresqlMicrosecondPrecision() {
        Instant highPrecision = Instant.parse("2026-08-19T10:05:00.123456789Z");
        Instant expected = Instant.parse("2026-08-19T10:05:00.123456Z");
        J7CanonicalExportService highPrecisionService = new J7CanonicalExportService(
                selectionReader,
                assembler,
                guard,
                manifestStore,
                fileStore,
                Clock.fixed(highPrecision, ZoneOffset.UTC),
                () -> EXPORT_ID,
                "0.1.0-SNAPSHOT");
        J7AssembledEnvelope candidate = new J7AssembledEnvelope(
                EXPORT_ID,
                EVENT_ID,
                J7ExportStatus.COHERENCE_CHECKED,
                expected,
                Optional.empty(),
                CANDIDATE_BYTES,
                DATA_SHA,
                SOURCE_SET_SHA,
                List.of(),
                "[]",
                "[]");
        J7ExportManifest inserted = manifest(
                J7ExportStatus.COHERENCE_CHECKED,
                expected);
        when(selectionReader.load(EVENT_ID)).thenReturn(Optional.of(selection));
        when(assembler.assembleCandidate(EXPORT_ID, expected, "0.1.0-SNAPSHOT", selection))
                .thenReturn(candidate);
        when(guard.verify(CANDIDATE_BYTES, EVENT_ID, EXPORT_ID))
                .thenReturn(verified(
                        J7ExportStatus.COHERENCE_CHECKED,
                        CANDIDATE_SHA,
                        CANDIDATE_BYTES,
                        expected,
                        Optional.empty(),
                        Optional.empty()));
        when(fileStore.writeNewOrVerify(path("candidate"), CANDIDATE_BYTES))
                .thenReturn(new J7StoredFile(
                        path("candidate"), CANDIDATE_SHA, CANDIDATE_BYTES.length));
        when(fileStore.readVerified(
                path("candidate"), CANDIDATE_SHA, CANDIDATE_BYTES.length))
                .thenReturn(CANDIDATE_BYTES);
        when(manifestStore.insertCandidate(any())).thenReturn(inserted);

        assertThat(highPrecisionService.createCandidate(EVENT_ID)).isEqualTo(inserted);

        ArgumentCaptor<com.bettingproject.sofascorelocal.domain.export.J7ExportManifestDraft>
                draft = ArgumentCaptor.forClass(
                com.bettingproject.sofascorelocal.domain.export.J7ExportManifestDraft.class);
        verify(manifestStore).insertCandidate(draft.capture());
        assertThat(draft.getValue().generatedAt()).isEqualTo(expected);
    }

    @Test
    void removesTheVerifiedCandidateWhenTheDatabaseConfirmsTheInsertDidNotCommit() {
        stubCandidateCreation(SOURCE_SET_SHA);
        when(manifestStore.insertCandidate(any()))
                .thenThrow(new DataAccessResourceFailureException("commit result unknown"));
        when(manifestStore.findByExportId(EXPORT_ID)).thenReturn(Optional.empty());

        assertError(
                () -> service.createCandidate(EVENT_ID),
                J7ExportError.DATABASE_UNAVAILABLE);

        verify(fileStore).deleteVerified(
                path("candidate"), CANDIDATE_SHA, CANDIDATE_BYTES.length);
    }

    @Test
    void preservesTheVerifiedCandidateWhileTheDatabaseResultRemainsAmbiguous() {
        stubCandidateCreation(SOURCE_SET_SHA);
        DataAccessResourceFailureException original =
                new DataAccessResourceFailureException("commit result unknown");
        when(manifestStore.insertCandidate(any())).thenThrow(original);
        when(manifestStore.findByExportId(EXPORT_ID))
                .thenThrow(new DataAccessResourceFailureException("recovery unavailable"));

        assertError(
                () -> service.createCandidate(EVENT_ID),
                J7ExportError.DATABASE_UNAVAILABLE);

        verify(fileStore, never()).deleteVerified(
                path("candidate"), CANDIDATE_SHA, CANDIDATE_BYTES.length);
        assertThat(original.getSuppressed()).hasSize(1);
    }

    @Test
    void aNormalRetryRecoversAnAmbiguousCandidateOrphanWithoutRewritingIt() {
        J7ExportManifest recovered = manifest(
                J7ExportStatus.COHERENCE_CHECKED,
                DECIDED_AT);
        stubCandidateCreation(SOURCE_SET_SHA);
        DataAccessResourceFailureException firstInsertFailure =
                new DataAccessResourceFailureException("commit result unknown");
        when(fileStore.findCandidatePaths(EVENT_ID))
                .thenReturn(List.of(), List.of(path("candidate")));
        when(fileStore.readExisting(path("candidate")))
                .thenReturn(Optional.of(CANDIDATE_BYTES));
        when(fileStore.readVerified(
                path("candidate"), CANDIDATE_SHA, CANDIDATE_BYTES.length))
                .thenReturn(CANDIDATE_BYTES);
        when(manifestStore.insertCandidate(any()))
                .thenThrow(firstInsertFailure)
                .thenReturn(recovered);
        when(manifestStore.findByExportId(EXPORT_ID))
                .thenThrow(new DataAccessResourceFailureException("recovery unavailable"))
                .thenReturn(Optional.empty());

        assertError(
                () -> service.createCandidate(EVENT_ID),
                J7ExportError.DATABASE_UNAVAILABLE);

        assertThat(service.createCandidate(EVENT_ID)).isEqualTo(recovered);

        verify(fileStore, times(1)).writeNewOrVerify(
                path("candidate"),
                CANDIDATE_BYTES);
        verify(manifestStore, times(2)).insertCandidate(any());
        verify(fileStore, never()).deleteVerified(
                path("candidate"), CANDIDATE_SHA, CANDIDATE_BYTES.length);
    }

    @Test
    void removesTheExactRecoveredOrphanWhenAnotherCandidateWinsTheInsertRace() {
        UUID winnerExportId = UUID.fromString(
                "72222222-2222-4222-8222-222222222222");
        String winnerPath = "j7-" + EVENT_ID + "-" + winnerExportId
                + ".candidate.json";
        J7ExportManifest winner = new J7ExportManifest(
                8,
                winnerExportId,
                EVENT_ID,
                J7ExportContract.SCHEMA_ID,
                J7ExportContract.SCHEMA_VERSION,
                DECIDED_AT,
                DATA_SHA,
                SOURCE_SET_SHA,
                CANDIDATE_SHA,
                CANDIDATE_SHA,
                CANDIDATE_BYTES.length,
                winnerPath,
                "[]",
                List.of(),
                "[]",
                J7ExportStatus.COHERENCE_CHECKED,
                Optional.empty(),
                Optional.empty());
        stubCandidateCreation(SOURCE_SET_SHA);
        when(fileStore.findCandidatePaths(EVENT_ID))
                .thenReturn(List.of(path("candidate")));
        when(fileStore.readExisting(path("candidate")))
                .thenReturn(Optional.of(CANDIDATE_BYTES));
        when(manifestStore.findPending(
                EVENT_ID,
                J7ExportContract.SCHEMA_VERSION))
                .thenReturn(Optional.empty(), Optional.of(winner));
        when(manifestStore.insertCandidate(any()))
                .thenThrow(new DataIntegrityViolationException("pending winner"));
        when(fileStore.readVerified(
                winnerPath, CANDIDATE_SHA, CANDIDATE_BYTES.length))
                .thenReturn(CANDIDATE_BYTES);
        when(guard.verify(CANDIDATE_BYTES, EVENT_ID, winnerExportId))
                .thenReturn(new J7VerifiedEnvelope(
                        winnerExportId,
                        EVENT_ID,
                        J7ExportStatus.COHERENCE_CHECKED,
                        DATA_SHA,
                        SOURCE_SET_SHA,
                        CANDIDATE_SHA,
                        CANDIDATE_BYTES.length,
                        envelope(
                                J7ExportStatus.COHERENCE_CHECKED,
                                DECIDED_AT,
                                Optional.empty(),
                                Optional.empty())));

        assertThat(service.createCandidate(EVENT_ID)).isEqualTo(winner);

        verify(fileStore).deleteVerified(
                path("candidate"), CANDIDATE_SHA, CANDIDATE_BYTES.length);
    }

    @Test
    void refusesASchemaValidLookingOrphanThatCannotBeReconstructedByteForByte() {
        byte[] forged = "{\"x\":1}\n".getBytes(StandardCharsets.UTF_8);
        byte[] canonical = "{\"x\":2}\n".getBytes(StandardCharsets.UTF_8);
        when(selectionReader.load(EVENT_ID)).thenReturn(Optional.of(selection));
        when(assembler.assembleCandidate(
                EXPORT_ID, DECIDED_AT, "0.1.0-SNAPSHOT", selection))
                .thenReturn(assembled(
                        J7ExportStatus.COHERENCE_CHECKED,
                        SOURCE_SET_SHA,
                        CANDIDATE_BYTES,
                        DECIDED_AT));
        when(guard.verify(CANDIDATE_BYTES, EVENT_ID, EXPORT_ID))
                .thenReturn(verified(
                        J7ExportStatus.COHERENCE_CHECKED,
                        CANDIDATE_SHA,
                        CANDIDATE_BYTES,
                        DECIDED_AT,
                        Optional.empty(),
                        Optional.empty()));
        when(fileStore.findCandidatePaths(EVENT_ID))
                .thenReturn(List.of(path("candidate")));
        when(fileStore.readExisting(path("candidate")))
                .thenReturn(Optional.of(forged));
        when(guard.verify(forged, EVENT_ID, EXPORT_ID))
                .thenReturn(verified(
                        J7ExportStatus.COHERENCE_CHECKED,
                        Sha256.hex(forged),
                        forged,
                        GENERATED_AT,
                        Optional.empty(),
                        Optional.empty()));
        when(assembler.assembleCandidate(
                EXPORT_ID, GENERATED_AT, "0.1.0-SNAPSHOT", selection))
                .thenReturn(assembled(
                        J7ExportStatus.COHERENCE_CHECKED,
                        SOURCE_SET_SHA,
                        canonical,
                        GENERATED_AT));

        assertError(
                () -> service.createCandidate(EVENT_ID),
                J7ExportError.FILE_TAMPERED);

        verify(manifestStore, never()).insertCandidate(any());
        verify(fileStore, never()).writeNewOrVerify(any(), any());
        verify(assembler).assembleCandidate(
                EXPORT_ID,
                GENERATED_AT,
                "0.1.0-SNAPSHOT",
                selection);
    }

    @Test
    void ignoresAValidOldOrphanWhoseSourceSetIsNoLongerCurrent() {
        String staleSourceSet = "c".repeat(64);
        String orphanPath = "j7-" + EVENT_ID + "-" + ORPHAN_EXPORT_ID
                + ".candidate.json";
        byte[] staleContent = "{\"old\":true}\n".getBytes(StandardCharsets.UTF_8);
        J7ExportManifest inserted = manifest(
                J7ExportStatus.COHERENCE_CHECKED,
                DECIDED_AT);
        stubCandidateCreation(SOURCE_SET_SHA);
        when(fileStore.findCandidatePaths(EVENT_ID))
                .thenReturn(List.of(orphanPath));
        when(manifestStore.findByExportId(ORPHAN_EXPORT_ID))
                .thenReturn(Optional.empty());
        when(fileStore.readExisting(orphanPath))
                .thenReturn(Optional.of(staleContent));
        when(guard.verify(staleContent, EVENT_ID, ORPHAN_EXPORT_ID))
                .thenReturn(new J7VerifiedEnvelope(
                        ORPHAN_EXPORT_ID,
                        EVENT_ID,
                        J7ExportStatus.COHERENCE_CHECKED,
                        DATA_SHA,
                        staleSourceSet,
                        Sha256.hex(staleContent),
                        staleContent.length,
                        envelope(
                                J7ExportStatus.COHERENCE_CHECKED,
                                GENERATED_AT,
                                Optional.empty(),
                                Optional.empty())));
        when(manifestStore.insertCandidate(any())).thenReturn(inserted);
        when(fileStore.readVerified(
                path("candidate"), CANDIDATE_SHA, CANDIDATE_BYTES.length))
                .thenReturn(CANDIDATE_BYTES);

        assertThat(service.createCandidate(EVENT_ID)).isEqualTo(inserted);

        verify(fileStore).writeNewOrVerify(path("candidate"), CANDIDATE_BYTES);
        ArgumentCaptor<com.bettingproject.sofascorelocal.domain.export.J7ExportManifestDraft>
                draft = ArgumentCaptor.forClass(
                com.bettingproject.sofascorelocal.domain.export.J7ExportManifestDraft.class);
        verify(manifestStore).insertCandidate(draft.capture());
        assertThat(draft.getValue().exportId()).isEqualTo(EXPORT_ID);
    }

    @Test
    void returnsTheCommittedCandidateAfterAnAmbiguousInsertResponse() {
        J7ExportManifest inserted = manifest(J7ExportStatus.COHERENCE_CHECKED, DECIDED_AT);
        stubCandidateCreation(SOURCE_SET_SHA);
        when(manifestStore.insertCandidate(any()))
                .thenThrow(new DataAccessResourceFailureException("response lost"));
        when(manifestStore.findByExportId(EXPORT_ID)).thenReturn(Optional.of(inserted));
        when(fileStore.readVerified(
                path("candidate"), CANDIDATE_SHA, CANDIDATE_BYTES.length))
                .thenReturn(CANDIDATE_BYTES);

        assertThat(service.createCandidate(EVENT_ID)).isEqualTo(inserted);

        verify(fileStore, never()).deleteVerified(
                path("candidate"), CANDIDATE_SHA, CANDIDATE_BYTES.length);
    }

    @Test
    void reusesAHumanValidatedExportWhenDataAndSourceSetHashesBothMatch() {
        J7ExportManifest validated = manifest(J7ExportStatus.HUMAN_VALIDATED);
        stubCandidateCreation(SOURCE_SET_SHA);
        when(manifestStore.findValidatedByDataSha256(
                EVENT_ID, J7ExportContract.SCHEMA_VERSION, DATA_SHA))
                .thenReturn(Optional.of(validated));
        when(fileStore.readVerified(
                validated.relativePath(), TERMINAL_SHA, TERMINAL_BYTES.length))
                .thenReturn(TERMINAL_BYTES);
        when(guard.verify(TERMINAL_BYTES, EVENT_ID, EXPORT_ID))
                .thenReturn(verified(
                        J7ExportStatus.HUMAN_VALIDATED,
                        TERMINAL_SHA,
                        TERMINAL_BYTES,
                        GENERATED_AT,
                        Optional.of(DECIDED_AT),
                        Optional.empty()));

        assertThat(service.createCandidate(EVENT_ID)).isSameAs(validated);

        verify(fileStore, never()).writeNewOrVerify(any(), any());
        verify(manifestStore, never()).insertCandidate(any());
    }

    @Test
    void doesNotReuseAHumanValidatedExportWhenDataMatchesButSourceSetWasRefreshed() {
        String refreshedSourceSet = "e".repeat(64);
        J7ExportManifest olderValidated = manifest(J7ExportStatus.HUMAN_VALIDATED);
        stubCandidateCreation(refreshedSourceSet);
        when(manifestStore.findValidatedByDataSha256(
                EVENT_ID, J7ExportContract.SCHEMA_VERSION, DATA_SHA))
                .thenReturn(Optional.of(olderValidated));

        assertError(
                () -> service.createCandidate(EVENT_ID),
                J7ExportError.IDENTICAL_EXPORT_ALREADY_VALIDATED);

        verify(fileStore, never()).readVerified(
                olderValidated.relativePath(), TERMINAL_SHA, TERMINAL_BYTES.length);
        verify(fileStore, never()).writeNewOrVerify(any(), any());
        verify(manifestStore, never()).insertCandidate(any());
    }

    private void stubCandidateCreation(String sourceSetSha) {
        when(selectionReader.load(EVENT_ID)).thenReturn(Optional.of(selection));
        when(assembler.assembleCandidate(
                EXPORT_ID, DECIDED_AT, "0.1.0-SNAPSHOT", selection))
                .thenReturn(assembled(
                        J7ExportStatus.COHERENCE_CHECKED,
                        sourceSetSha,
                        CANDIDATE_BYTES,
                        DECIDED_AT));
        when(guard.verify(CANDIDATE_BYTES, EVENT_ID, EXPORT_ID))
                .thenReturn(verified(
                        J7ExportStatus.COHERENCE_CHECKED,
                        CANDIDATE_SHA,
                        CANDIDATE_BYTES,
                        DECIDED_AT,
                        Optional.empty(),
                        Optional.empty(),
                        sourceSetSha));
        when(fileStore.writeNewOrVerify(path("candidate"), CANDIDATE_BYTES))
                .thenReturn(new J7StoredFile(
                        path("candidate"), CANDIDATE_SHA, CANDIDATE_BYTES.length));
    }

    private void stubCandidateRead(J7ExportManifest candidate) {
        when(manifestStore.findByExportId(EXPORT_ID)).thenReturn(Optional.of(candidate));
        when(fileStore.readVerified(
                candidate.relativePath(), CANDIDATE_SHA, CANDIDATE_BYTES.length))
                .thenReturn(CANDIDATE_BYTES);
        when(guard.verify(CANDIDATE_BYTES, EVENT_ID, EXPORT_ID))
                .thenReturn(verified(
                        J7ExportStatus.COHERENCE_CHECKED,
                        CANDIDATE_SHA,
                        CANDIDATE_BYTES,
                        GENERATED_AT,
                        Optional.empty(),
                        Optional.empty()));
    }

    private static J7AssembledEnvelope assembled(
            J7ExportStatus status,
            String sourceSetSha,
            byte[] content) {
        return assembled(status, sourceSetSha, content, GENERATED_AT);
    }

    private static J7AssembledEnvelope assembled(
            J7ExportStatus status,
            String sourceSetSha,
            byte[] content,
            Instant generatedAt) {
        return new J7AssembledEnvelope(
                EXPORT_ID,
                EVENT_ID,
                status,
                generatedAt,
                status.isTerminal() ? Optional.of(DECIDED_AT) : Optional.empty(),
                content,
                DATA_SHA,
                sourceSetSha,
                List.of(),
                "[]",
                "[]");
    }

    private static J7VerifiedEnvelope verified(
            J7ExportStatus status,
            String contentSha,
            byte[] content,
            Instant generatedAt,
            Optional<Instant> decidedAt,
            Optional<String> reason) {
        return verified(
                status,
                contentSha,
                content,
                generatedAt,
                decidedAt,
                reason,
                SOURCE_SET_SHA);
    }

    private static J7VerifiedEnvelope verified(
            J7ExportStatus status,
            String contentSha,
            byte[] content,
            Instant generatedAt,
            Optional<Instant> decidedAt,
            Optional<String> reason,
            String sourceSetSha) {
        return new J7VerifiedEnvelope(
                EXPORT_ID,
                EVENT_ID,
                status,
                DATA_SHA,
                sourceSetSha,
                contentSha,
                content.length,
                envelope(status, generatedAt, decidedAt, reason));
    }

    private static ObjectNode envelope(
            J7ExportStatus status,
            Instant generatedAt,
            Optional<Instant> decidedAt,
            Optional<String> reason) {
        var mapper = JsonMapper.builder().build();
        ObjectNode validation = mapper.createObjectNode();
        validation.put("status", status.name());
        decidedAt.ifPresentOrElse(
                value -> validation.put("decidedAt", value.toString()),
                () -> validation.putNull("decidedAt"));
        reason.ifPresentOrElse(
                value -> validation.put("rejectionReason", value),
                () -> validation.putNull("rejectionReason"));
        ObjectNode manifest = mapper.createObjectNode();
        manifest.put("generatedAt", generatedAt.toString());
        ArrayNode sources = mapper.createArrayNode();
        ArrayNode warnings = mapper.createArrayNode();
        manifest.set("sources", sources);
        manifest.set("warnings", warnings);
        manifest.set("validation", validation);
        ObjectNode root = mapper.createObjectNode();
        root.set("manifest", manifest);
        return root;
    }

    private static J7ExportManifest manifest(J7ExportStatus status) {
        return manifest(status, GENERATED_AT);
    }

    private static J7ExportManifest manifest(
            J7ExportStatus status,
            Instant generatedAt) {
        boolean terminal = status.isTerminal();
        String currentSha = terminal ? TERMINAL_SHA : CANDIDATE_SHA;
        long size = terminal ? TERMINAL_BYTES.length : CANDIDATE_BYTES.length;
        return new J7ExportManifest(
                7,
                EXPORT_ID,
                EVENT_ID,
                J7ExportContract.SCHEMA_ID,
                J7ExportContract.SCHEMA_VERSION,
                generatedAt,
                DATA_SHA,
                SOURCE_SET_SHA,
                CANDIDATE_SHA,
                currentSha,
                size,
                path(switch (status) {
                    case COHERENCE_CHECKED -> "candidate";
                    case HUMAN_VALIDATED -> "validated";
                    case REJECTED -> "rejected";
                }),
                "[]",
                List.of(),
                "[]",
                status,
                terminal ? Optional.of(DECIDED_AT) : Optional.empty(),
                status == J7ExportStatus.REJECTED
                        ? Optional.of("Motif contrôlé")
                        : Optional.empty());
    }

    private static String validationConfirmation() {
        return "VALIDER EXPORT J7 " + EXPORT_ID + " " + DATA_SHA;
    }

    private static String rejectionConfirmation() {
        return "REJETER EXPORT J7 " + EXPORT_ID;
    }

    private static String path(String suffix) {
        return "j7-" + EVENT_ID + "-" + EXPORT_ID + "." + suffix + ".json";
    }

    private static void assertError(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable action,
            J7ExportError expected) {
        assertThatThrownBy(action)
                .isInstanceOfSatisfying(J7ExportException.class, exception ->
                        assertThat(exception.error()).isEqualTo(expected));
    }
}
