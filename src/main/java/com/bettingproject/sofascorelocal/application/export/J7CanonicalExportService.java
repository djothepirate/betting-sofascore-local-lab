package com.bettingproject.sofascorelocal.application.export;

import com.bettingproject.sofascorelocal.domain.export.J7ExportDecision;
import com.bettingproject.sofascorelocal.domain.export.J7ExportError;
import com.bettingproject.sofascorelocal.domain.export.J7ExportException;
import com.bettingproject.sofascorelocal.domain.export.J7ExportManifest;
import com.bettingproject.sofascorelocal.domain.export.J7ExportManifestDraft;
import com.bettingproject.sofascorelocal.domain.export.J7ExportStatus;
import com.bettingproject.sofascorelocal.domain.export.J7StoredFile;
import com.bettingproject.sofascorelocal.port.J7ExportFileStore;
import com.bettingproject.sofascorelocal.port.J7ExportManifestStore;
import com.bettingproject.sofascorelocal.security.Sha256;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class J7CanonicalExportService {

    private static final Logger LOGGER = LoggerFactory.getLogger(J7CanonicalExportService.class);
    private static final String FALLBACK_GENERATOR_VERSION = "0.1.0-SNAPSHOT";

    private final J7CurrentEventSelectionReader selectionReader;
    private final J7EnvelopeAssembler assembler;
    private final J7ExportIntegrityGuard integrityGuard;
    private final J7ExportManifestStore manifestStore;
    private final J7ExportFileStore fileStore;
    private final Clock clock;
    private final Supplier<UUID> exportIdSupplier;
    private final String generatorVersion;
    private final ObjectMapper mapper = JsonMapper.builder().build();

    @Autowired
    public J7CanonicalExportService(
            J7CurrentEventSelectionReader selectionReader,
            J7EnvelopeAssembler assembler,
            J7ExportIntegrityGuard integrityGuard,
            J7ExportManifestStore manifestStore,
            J7ExportFileStore fileStore,
            ObjectProvider<BuildProperties> buildProperties) {
        this(
                selectionReader,
                assembler,
                integrityGuard,
                manifestStore,
                fileStore,
                Clock.systemUTC(),
                UUID::randomUUID,
                Optional.ofNullable(buildProperties.getIfAvailable())
                        .map(BuildProperties::getVersion)
                        .filter(value -> !value.isBlank())
                        .orElse(FALLBACK_GENERATOR_VERSION));
    }

    J7CanonicalExportService(
            J7CurrentEventSelectionReader selectionReader,
            J7EnvelopeAssembler assembler,
            J7ExportIntegrityGuard integrityGuard,
            J7ExportManifestStore manifestStore,
            J7ExportFileStore fileStore,
            Clock clock,
            Supplier<UUID> exportIdSupplier,
            String generatorVersion) {
        this.selectionReader = Objects.requireNonNull(selectionReader, "selectionReader");
        this.assembler = Objects.requireNonNull(assembler, "assembler");
        this.integrityGuard = Objects.requireNonNull(integrityGuard, "integrityGuard");
        this.manifestStore = Objects.requireNonNull(manifestStore, "manifestStore");
        this.fileStore = Objects.requireNonNull(fileStore, "fileStore");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.exportIdSupplier = Objects.requireNonNull(exportIdSupplier, "exportIdSupplier");
        this.generatorVersion = Objects.requireNonNull(generatorVersion, "generatorVersion");
    }

    public J7ExportHistory history(UUID canonicalEventId) {
        J7CurrentEventSelection selection = selection(canonicalEventId);
        try {
            return new J7ExportHistory(
                    selection.eventState(),
                    manifestStore.findByCanonicalEventId(canonicalEventId));
        }
        catch (DataAccessException exception) {
            throw databaseUnavailable(exception);
        }
    }

    public synchronized J7ExportManifest createCandidate(UUID canonicalEventId) {
        J7CurrentEventSelection selection = selection(canonicalEventId);
        UUID exportId = Objects.requireNonNull(exportIdSupplier.get(), "generated exportId");
        J7AssembledEnvelope assembled = assembler.assembleCandidate(
                exportId,
                now(),
                generatorVersion,
                selection);
        J7VerifiedEnvelope verified = integrityGuard.verify(
                assembled.content(),
                canonicalEventId,
                exportId);
        requireAssembledMatchesVerified(assembled, verified);

        try {
            Optional<J7ExportManifest> validated = manifestStore.findValidatedByDataSha256(
                    canonicalEventId,
                    J7ExportContract.SCHEMA_VERSION,
                    assembled.dataSha256());
            if (validated.isPresent()) {
                J7ExportManifest existing = validated.orElseThrow();
                if (!existing.sourceSetSha256().equals(assembled.sourceSetSha256())) {
                    throw new J7ExportException(
                            J7ExportError.IDENTICAL_EXPORT_ALREADY_VALIDATED);
                }
                readAndVerify(existing);
                return existing;
            }
            Optional<J7ExportManifest> pending = manifestStore.findPending(
                    canonicalEventId,
                    J7ExportContract.SCHEMA_VERSION);
            if (pending.isPresent()) {
                J7ExportManifest existing = pending.orElseThrow();
                if (existing.dataSha256().equals(assembled.dataSha256())
                        && existing.sourceSetSha256().equals(assembled.sourceSetSha256())) {
                    readAndVerify(existing);
                    return existing;
                }
                throw new J7ExportException(J7ExportError.CANDIDATE_ALREADY_PENDING);
            }
        }
        catch (J7ExportException exception) {
            throw exception;
        }
        catch (DataAccessException exception) {
            throw databaseUnavailable(exception);
        }

        Optional<J7ExportManifest> recovered = recoverCandidateOrphan(
                canonicalEventId,
                selection,
                assembled);
        if (recovered.isPresent()) {
            return recovered.orElseThrow();
        }

        String relativePath = fileName(
                canonicalEventId,
                exportId,
                J7ExportStatus.COHERENCE_CHECKED);
        J7StoredFile stored = fileStore.writeNewOrVerify(relativePath, assembled.content());
        if (!stored.sha256().equals(verified.contentSha256())
                || stored.sizeBytes() != verified.contentSizeBytes()) {
            throw new J7ExportException(J7ExportError.FILE_TAMPERED);
        }
        J7ExportManifestDraft draft = candidateDraft(
                assembled,
                verified,
                relativePath);
        try {
            J7ExportManifest inserted = manifestStore.insertCandidate(draft);
            requireCandidateManifestMatches(inserted, assembled, verified);
            readAndVerify(inserted);
            LOGGER.info(
                    "J7 export candidate created canonicalEventId={} exportId={} status={} size={} contentSha256={}",
                    canonicalEventId,
                    exportId,
                    inserted.status(),
                    inserted.currentSizeBytes(),
                    inserted.currentContentSha256());
            return inserted;
        }
        catch (DataIntegrityViolationException exception) {
            deleteCandidateAfterDeterministicInsertFailure(relativePath, stored);
            return existingAfterCandidateConflict(canonicalEventId, assembled);
        }
        catch (DataAccessException exception) {
            boolean databaseConfirmedAbsent = false;
            try {
                Optional<J7ExportManifest> committed = manifestStore.findByExportId(exportId);
                if (committed.isPresent()) {
                    J7ExportManifest inserted = committed.orElseThrow();
                    requireCandidateManifestMatches(inserted, assembled, verified);
                    readAndVerify(inserted);
                    return inserted;
                }
                databaseConfirmedAbsent = true;
            }
            catch (J7ExportException recoveryException) {
                throw recoveryException;
            }
            catch (DataAccessException recoveryException) {
                exception.addSuppressed(recoveryException);
            }
            if (databaseConfirmedAbsent) {
                deleteCandidateAfterDeterministicInsertFailure(relativePath, stored);
                LOGGER.warn(
                        "J7 candidate insert failed and absence was confirmed exportId={} size={} contentSha256={}",
                        exportId,
                        stored.sizeBytes(),
                        stored.sha256());
                throw databaseUnavailable(exception);
            }
            LOGGER.warn(
                    "J7 candidate file preserved after ambiguous database result exportId={} size={} contentSha256={}",
                    exportId,
                    stored.sizeBytes(),
                    stored.sha256());
            throw databaseUnavailable(exception);
        }
    }

    private Optional<J7ExportManifest> recoverCandidateOrphan(
            UUID canonicalEventId,
            J7CurrentEventSelection selection,
            J7AssembledEnvelope currentProbe) {
        for (String relativePath : fileStore.findCandidatePaths(canonicalEventId)) {
            UUID orphanExportId = candidateExportId(canonicalEventId, relativePath);
            Optional<J7ExportManifest> persisted;
            try {
                persisted = manifestStore.findByExportId(orphanExportId);
            }
            catch (DataAccessException exception) {
                throw databaseUnavailable(exception);
            }
            if (persisted.isPresent()) {
                J7ExportManifest existing = persisted.orElseThrow();
                if (!existing.canonicalEventId().equals(canonicalEventId)
                        || !existing.schemaId().equals(J7ExportContract.SCHEMA_ID)
                        || !existing.schemaVersion().equals(J7ExportContract.SCHEMA_VERSION)) {
                    throw new J7ExportException(J7ExportError.FILE_TAMPERED);
                }
                if (existing.status() == J7ExportStatus.COHERENCE_CHECKED) {
                    if (!existing.dataSha256().equals(currentProbe.dataSha256())
                            || !existing.sourceSetSha256().equals(
                            currentProbe.sourceSetSha256())
                            || !existing.relativePath().equals(relativePath)) {
                        throw new J7ExportException(J7ExportError.CANDIDATE_ALREADY_PENDING);
                    }
                    readAndVerify(existing);
                    return Optional.of(existing);
                }
                continue;
            }

            Optional<byte[]> existingContent = fileStore.readExisting(relativePath);
            if (existingContent.isEmpty()) {
                continue;
            }
            byte[] orphanContent = existingContent.orElseThrow();
            J7VerifiedEnvelope orphan = integrityGuard.verify(
                    orphanContent,
                    canonicalEventId,
                    orphanExportId);
            if (orphan.status() != J7ExportStatus.COHERENCE_CHECKED) {
                throw new J7ExportException(J7ExportError.FILE_TAMPERED);
            }
            if (!orphan.dataSha256().equals(currentProbe.dataSha256())
                    || !orphan.sourceSetSha256().equals(currentProbe.sourceSetSha256())) {
                continue;
            }

            Instant generatedAt = candidateGeneratedAt(orphan, currentProbe.generatedAt());
            // Recovery deliberately uses the currently running generator version. An
            // orphan produced by another binary version is accepted only if this version
            // can reproduce its complete canonical bytes; otherwise recovery fails closed.
            J7AssembledEnvelope reconstructed = assembler.assembleCandidate(
                    orphanExportId,
                    generatedAt,
                    generatorVersion,
                    selection);
            requireAssembledMatchesVerified(reconstructed, orphan);
            if (!Arrays.equals(reconstructed.content(), orphanContent)
                    || !relativePath.equals(fileName(
                    canonicalEventId,
                    orphanExportId,
                    J7ExportStatus.COHERENCE_CHECKED))) {
                throw new J7ExportException(J7ExportError.FILE_TAMPERED);
            }

            J7ExportManifestDraft draft = candidateDraft(
                    reconstructed,
                    orphan,
                    relativePath);
            try {
                J7ExportManifest recovered = manifestStore.insertCandidate(draft);
                requireCandidateManifestMatches(recovered, reconstructed, orphan);
                readAndVerify(recovered);
                LOGGER.info(
                        "J7 orphan candidate recovered canonicalEventId={} exportId={} status={} size={} contentSha256={}",
                        canonicalEventId,
                        orphanExportId,
                        recovered.status(),
                        recovered.currentSizeBytes(),
                        recovered.currentContentSha256());
                return Optional.of(recovered);
            }
            catch (DataIntegrityViolationException exception) {
                J7ExportManifest winner = existingAfterCandidateConflict(
                        canonicalEventId,
                        reconstructed);
                if (!winner.exportId().equals(orphanExportId)) {
                    fileStore.deleteVerified(
                            relativePath,
                            orphan.contentSha256(),
                            orphan.contentSizeBytes());
                }
                return Optional.of(winner);
            }
            catch (DataAccessException exception) {
                try {
                    Optional<J7ExportManifest> committed = manifestStore.findByExportId(
                            orphanExportId);
                    if (committed.isPresent()) {
                        J7ExportManifest recovered = committed.orElseThrow();
                        requireCandidateManifestMatches(recovered, reconstructed, orphan);
                        readAndVerify(recovered);
                        return Optional.of(recovered);
                    }
                }
                catch (J7ExportException recoveryException) {
                    throw recoveryException;
                }
                catch (DataAccessException recoveryException) {
                    exception.addSuppressed(recoveryException);
                }
                LOGGER.warn(
                        "J7 orphan candidate remains preserved after database failure exportId={} size={} contentSha256={}",
                        orphanExportId,
                        orphan.contentSizeBytes(),
                        orphan.contentSha256());
                throw databaseUnavailable(exception);
            }
        }
        return Optional.empty();
    }

    private static Instant candidateGeneratedAt(
            J7VerifiedEnvelope orphan,
            Instant retryGeneratedAt) {
        try {
            Instant generatedAt = Instant.parse(object(
                    orphan.envelope(),
                    "manifest").required("generatedAt").stringValue());
            if (!generatedAt.equals(generatedAt.truncatedTo(ChronoUnit.MICROS))
                    || generatedAt.isAfter(retryGeneratedAt)) {
                throw new J7ExportException(J7ExportError.FILE_TAMPERED);
            }
            return generatedAt;
        }
        catch (DateTimeException | IllegalArgumentException exception) {
            throw new J7ExportException(J7ExportError.FILE_TAMPERED, exception);
        }
    }

    private static UUID candidateExportId(
            UUID canonicalEventId,
            String relativePath) {
        String prefix = "j7-" + canonicalEventId + "-";
        String suffix = ".candidate.json";
        if (relativePath == null
                || !relativePath.startsWith(prefix)
                || !relativePath.endsWith(suffix)) {
            throw new J7ExportException(J7ExportError.STORAGE_UNAVAILABLE);
        }
        try {
            return UUID.fromString(relativePath.substring(
                    prefix.length(),
                    relativePath.length() - suffix.length()));
        }
        catch (IllegalArgumentException exception) {
            throw new J7ExportException(J7ExportError.STORAGE_UNAVAILABLE, exception);
        }
    }

    private static J7ExportManifestDraft candidateDraft(
            J7AssembledEnvelope assembled,
            J7VerifiedEnvelope verified,
            String relativePath) {
        return new J7ExportManifestDraft(
                assembled.exportId(),
                assembled.canonicalEventId(),
                J7ExportContract.SCHEMA_ID,
                J7ExportContract.SCHEMA_VERSION,
                assembled.generatedAt(),
                assembled.dataSha256(),
                assembled.sourceSetSha256(),
                verified.contentSha256(),
                verified.contentSizeBytes(),
                relativePath,
                assembled.sourcesJson(),
                assembled.sourceSnapshotIds(),
                assembled.warningsJson());
    }

    private J7ExportManifest existingAfterCandidateConflict(
            UUID canonicalEventId,
            J7AssembledEnvelope assembled) {
        try {
            Optional<J7ExportManifest> validated = manifestStore.findValidatedByDataSha256(
                    canonicalEventId,
                    J7ExportContract.SCHEMA_VERSION,
                    assembled.dataSha256());
            if (validated.isPresent()) {
                J7ExportManifest existing = validated.orElseThrow();
                if (!existing.sourceSetSha256().equals(assembled.sourceSetSha256())) {
                    throw new J7ExportException(
                            J7ExportError.IDENTICAL_EXPORT_ALREADY_VALIDATED);
                }
                readAndVerify(existing);
                return existing;
            }
            Optional<J7ExportManifest> pending = manifestStore.findPending(
                    canonicalEventId,
                    J7ExportContract.SCHEMA_VERSION);
            if (pending.isPresent()
                    && pending.orElseThrow().dataSha256().equals(assembled.dataSha256())
                    && pending.orElseThrow().sourceSetSha256().equals(
                    assembled.sourceSetSha256())) {
                J7ExportManifest existing = pending.orElseThrow();
                readAndVerify(existing);
                return existing;
            }
            throw new J7ExportException(J7ExportError.CANDIDATE_ALREADY_PENDING);
        }
        catch (J7ExportException exception) {
            throw exception;
        }
        catch (DataAccessException exception) {
            throw databaseUnavailable(exception);
        }
    }

    private void requireCandidateManifestMatches(
            J7ExportManifest manifest,
            J7AssembledEnvelope assembled,
            J7VerifiedEnvelope verified) {
        requireAssembledMatchesVerified(assembled, verified);
        if (manifest.status() != J7ExportStatus.COHERENCE_CHECKED
                || !manifest.canonicalEventId().equals(assembled.canonicalEventId())
                || !manifest.exportId().equals(assembled.exportId())
                || !manifest.schemaId().equals(J7ExportContract.SCHEMA_ID)
                || !manifest.schemaVersion().equals(J7ExportContract.SCHEMA_VERSION)
                || !manifest.generatedAt().equals(assembled.generatedAt())
                || !manifest.dataSha256().equals(assembled.dataSha256())
                || !manifest.sourceSetSha256().equals(assembled.sourceSetSha256())
                || !manifest.candidateContentSha256().equals(verified.contentSha256())
                || !manifest.currentContentSha256().equals(verified.contentSha256())
                || manifest.currentSizeBytes() != verified.contentSizeBytes()
                || !manifest.relativePath().equals(fileName(
                manifest.canonicalEventId(),
                manifest.exportId(),
                J7ExportStatus.COHERENCE_CHECKED))
                || !manifest.sourceSnapshotIds().equals(assembled.sourceSnapshotIds())) {
            throw new J7ExportException(J7ExportError.FILE_TAMPERED);
        }
        requireJsonEvidenceEquals(
                manifest.sourceObservationsJson(),
                parseJson(assembled.sourcesJson()));
        requireJsonEvidenceEquals(
                manifest.warningsJson(),
                parseJson(assembled.warningsJson()));
    }

    public J7ExportPreview preview(UUID canonicalEventId, UUID exportId) {
        J7ExportManifest manifest = manifest(canonicalEventId, exportId);
        VerifiedFile verified = readAndVerify(manifest);
        if (manifest.status().isTerminal()) {
            cleanupCandidateBestEffort(manifest);
        }
        try {
            return new J7ExportPreview(
                    manifest,
                    mapper.writerWithDefaultPrettyPrinter()
                            .writeValueAsString(verified.envelope()),
                    validationConfirmation(manifest),
                    rejectionConfirmation(manifest));
        }
        catch (JacksonException exception) {
            throw new J7ExportException(J7ExportError.INVALID_SCHEMA, exception);
        }
    }

    @Transactional
    public synchronized J7ExportManifest validate(
            UUID canonicalEventId,
            UUID exportId,
            String confirmation) {
        lockCanonicalEvent(canonicalEventId);
        J7ExportManifest manifest = manifest(canonicalEventId, exportId);
        requireExactConfirmation(confirmation, validationConfirmation(manifest));
        if (manifest.status() == J7ExportStatus.HUMAN_VALIDATED) {
            readAndVerify(manifest);
            cleanupCandidateBestEffort(manifest);
            return manifest;
        }
        if (manifest.status() != J7ExportStatus.COHERENCE_CHECKED) {
            throw new J7ExportException(J7ExportError.INVALID_TRANSITION);
        }
        VerifiedFile candidate = readAndVerify(manifest);

        J7CurrentEventSelection current = selection(canonicalEventId);
        J7AssembledEnvelope fresh = assembler.assembleCandidate(
                exportId,
                manifest.generatedAt(),
                generatorVersion,
                current);
        if (!manifest.sourceSetSha256().equals(fresh.sourceSetSha256())) {
            abandonDecisionIntentIfPresent(manifest);
            throw new J7ExportException(J7ExportError.SOURCE_SET_CHANGED);
        }

        return terminalDecision(
                manifest,
                candidate.content(),
                J7ExportStatus.HUMAN_VALIDATED,
                Optional.empty());
    }

    @Transactional
    public synchronized J7ExportManifest reject(
            UUID canonicalEventId,
            UUID exportId,
            String confirmation,
            String reason) {
        String normalizedReason = normalizeReason(reason);
        lockCanonicalEvent(canonicalEventId);
        J7ExportManifest manifest = manifest(canonicalEventId, exportId);
        requireExactConfirmation(confirmation, rejectionConfirmation(manifest));
        if (manifest.status() == J7ExportStatus.REJECTED
                && manifest.decisionReason().filter(normalizedReason::equals).isPresent()) {
            readAndVerify(manifest);
            cleanupCandidateBestEffort(manifest);
            return manifest;
        }
        if (manifest.status() != J7ExportStatus.COHERENCE_CHECKED) {
            throw new J7ExportException(J7ExportError.INVALID_TRANSITION);
        }
        VerifiedFile candidate = readAndVerify(manifest);
        return terminalDecision(
                manifest,
                candidate.content(),
                J7ExportStatus.REJECTED,
                Optional.of(normalizedReason));
    }

    public J7ExportDownload download(UUID canonicalEventId, UUID exportId) {
        J7ExportManifest manifest = manifest(canonicalEventId, exportId);
        if (manifest.status() != J7ExportStatus.HUMAN_VALIDATED) {
            throw new J7ExportException(J7ExportError.NOT_DOWNLOADABLE);
        }
        VerifiedFile verified = readAndVerify(manifest);
        cleanupCandidateBestEffort(manifest);
        LOGGER.info(
                "J7 export download verified canonicalEventId={} exportId={} status={} size={} contentSha256={}",
                canonicalEventId,
                exportId,
                manifest.status(),
                manifest.currentSizeBytes(),
                manifest.currentContentSha256());
        return new J7ExportDownload(
                exportId,
                manifest.relativePath(),
                manifest.currentContentSha256(),
                verified.content());
    }

    private J7ExportManifest terminalDecision(
            J7ExportManifest candidateManifest,
            byte[] candidateBytes,
            J7ExportStatus terminalStatus,
            Optional<String> reason) {
        String terminalPath = fileName(
                candidateManifest.canonicalEventId(),
                candidateManifest.exportId(),
                terminalStatus);
        Optional<J7ExportDecision> existingIntent = decisionIntent(
                candidateManifest.exportId());
        if (existingIntent.isPresent()
                && (!existingIntent.orElseThrow().status().equals(terminalStatus)
                || !existingIntent.orElseThrow().reason().equals(reason))) {
            abandonDecisionIntent(
                    candidateManifest.exportId(),
                    existingIntent.orElseThrow());
            existingIntent = Optional.empty();
        }
        J7AssembledEnvelope terminal = assembler.decide(
                candidateBytes,
                terminalStatus,
                existingIntent.map(J7ExportDecision::decidedAt).orElseGet(this::now),
                reason.orElse(null));
        J7VerifiedEnvelope verified = integrityGuard.verify(
                terminal.content(),
                candidateManifest.canonicalEventId(),
                candidateManifest.exportId());
        requireAssembledMatchesVerified(terminal, verified);
        requireOnlyValidationChanged(candidateBytes, verified);
        if (!candidateManifest.dataSha256().equals(verified.dataSha256())
                || !candidateManifest.sourceSetSha256().equals(verified.sourceSetSha256())) {
            throw new J7ExportException(J7ExportError.INVALID_HASH);
        }

        J7ExportDecision expectedDecision = new J7ExportDecision(
                terminalStatus,
                terminal.decidedAt().orElseThrow(() ->
                        new J7ExportException(J7ExportError.INVALID_SCHEMA)),
                reason,
                terminalPath,
                verified.contentSha256(),
                verified.contentSizeBytes());
        if (existingIntent.isPresent()) {
            if (!existingIntent.orElseThrow().equals(expectedDecision)) {
                throw new J7ExportException(J7ExportError.FILE_TAMPERED);
            }
        }
        else {
            J7ExportDecision recorded = recordDecisionIntent(
                    candidateManifest.exportId(),
                    expectedDecision);
            if (!recorded.equals(expectedDecision)) {
                throw new J7ExportException(J7ExportError.FILE_TAMPERED);
            }
        }

        J7StoredFile stored = fileStore.writeNewOrVerify(terminalPath, terminal.content());
        if (!stored.sha256().equals(verified.contentSha256())
                || stored.sizeBytes() != verified.contentSizeBytes()) {
            throw new J7ExportException(J7ExportError.FILE_TAMPERED);
        }
        try {
            J7ExportManifest decided = manifestStore.decide(
                    candidateManifest.exportId(),
                    expectedDecision);
            cleanupCandidateAfterCommit(candidateManifest);
            LOGGER.info(
                    "J7 export decision recorded canonicalEventId={} exportId={} status={} size={} contentSha256={}",
                    decided.canonicalEventId(),
                    decided.exportId(),
                    decided.status(),
                    decided.currentSizeBytes(),
                    decided.currentContentSha256());
            return decided;
        }
        catch (J7ExportException exception) {
            throw exception;
        }
        catch (DataAccessException exception) {
            throw databaseUnavailable(exception);
        }
    }

    private Optional<J7ExportDecision> decisionIntent(UUID exportId) {
        try {
            return manifestStore.findDecisionIntent(exportId);
        }
        catch (DataAccessException exception) {
            throw databaseUnavailable(exception);
        }
    }

    private J7ExportDecision recordDecisionIntent(
            UUID exportId,
            J7ExportDecision decision) {
        try {
            return manifestStore.recordDecisionIntent(exportId, decision);
        }
        catch (J7ExportException exception) {
            throw exception;
        }
        catch (DataAccessException exception) {
            throw databaseUnavailable(exception);
        }
    }

    private void abandonDecisionIntentIfPresent(J7ExportManifest manifest) {
        decisionIntent(manifest.exportId()).ifPresent(intent ->
                abandonDecisionIntent(manifest.exportId(), intent));
    }

    private void abandonDecisionIntent(UUID exportId, J7ExportDecision intent) {
        fileStore.readExisting(intent.relativePath()).ifPresent(content -> {
            if (content.length != intent.contentSizeBytes()
                    || !Sha256.hex(content).equals(intent.contentSha256())) {
                throw new J7ExportException(J7ExportError.FILE_TAMPERED);
            }
            fileStore.deleteVerified(
                    intent.relativePath(),
                    intent.contentSha256(),
                    intent.contentSizeBytes());
        });
        try {
            manifestStore.clearDecisionIntent(exportId, intent);
        }
        catch (J7ExportException exception) {
            throw exception;
        }
        catch (DataAccessException exception) {
            throw databaseUnavailable(exception);
        }
    }

    private void requireOnlyValidationChanged(
            byte[] candidateBytes,
            J7VerifiedEnvelope terminal) {
        J7VerifiedEnvelope candidate = integrityGuard.verify(
                candidateBytes,
                terminal.canonicalEventId(),
                terminal.exportId());
        if (candidate.status() != J7ExportStatus.COHERENCE_CHECKED) {
            throw new J7ExportException(J7ExportError.FILE_TAMPERED);
        }
        ObjectNode expected = candidate.envelope();
        ObjectNode actual = terminal.envelope();
        ObjectNode expectedManifest = object(expected, "manifest");
        ObjectNode actualManifest = object(actual, "manifest");
        expectedManifest.set(
                "validation",
                object(actualManifest, "validation").deepCopy());
        if (!expected.equals(actual)) {
            throw new J7ExportException(J7ExportError.FILE_TAMPERED);
        }
    }

    private J7CurrentEventSelection selection(UUID canonicalEventId) {
        Objects.requireNonNull(canonicalEventId, "canonicalEventId");
        try {
            return selectionReader.load(canonicalEventId)
                    .orElseThrow(() -> new J7ExportException(J7ExportError.EVENT_NOT_FOUND));
        }
        catch (J7ExportException exception) {
            throw exception;
        }
        catch (DataAccessException exception) {
            throw databaseUnavailable(exception);
        }
    }

    private void lockCanonicalEvent(UUID canonicalEventId) {
        Objects.requireNonNull(canonicalEventId, "canonicalEventId");
        try {
            manifestStore.lockCanonicalEvent(canonicalEventId);
        }
        catch (J7ExportException exception) {
            throw exception;
        }
        catch (DataAccessException exception) {
            throw databaseUnavailable(exception);
        }
    }

    private J7ExportManifest manifest(UUID canonicalEventId, UUID exportId) {
        Objects.requireNonNull(canonicalEventId, "canonicalEventId");
        Objects.requireNonNull(exportId, "exportId");
        try {
            J7ExportManifest manifest = manifestStore.findByExportId(exportId)
                    .orElseThrow(() -> new J7ExportException(J7ExportError.EXPORT_NOT_FOUND));
            if (!manifest.canonicalEventId().equals(canonicalEventId)) {
                throw new J7ExportException(J7ExportError.EXPORT_NOT_FOUND);
            }
            return manifest;
        }
        catch (J7ExportException exception) {
            throw exception;
        }
        catch (DataAccessException exception) {
            throw databaseUnavailable(exception);
        }
    }

    private VerifiedFile readAndVerify(J7ExportManifest manifest) {
        byte[] content = fileStore.readVerified(
                manifest.relativePath(),
                manifest.currentContentSha256(),
                manifest.currentSizeBytes());
        J7VerifiedEnvelope verified = integrityGuard.verify(
                content,
                manifest.canonicalEventId(),
                manifest.exportId());
        if (verified.status() != manifest.status()
                || !verified.dataSha256().equals(manifest.dataSha256())
                || !verified.sourceSetSha256().equals(manifest.sourceSetSha256())
                || !verified.contentSha256().equals(manifest.currentContentSha256())
                || verified.contentSizeBytes() != manifest.currentSizeBytes()) {
            throw new J7ExportException(J7ExportError.FILE_TAMPERED);
        }
        ObjectNode envelope = verified.envelope();
        ObjectNode manifestNode = object(envelope, "manifest");
        if (!manifest.generatedAt().equals(Instant.parse(
                manifestNode.required("generatedAt").stringValue()))) {
            throw new J7ExportException(J7ExportError.FILE_TAMPERED);
        }
        requireJsonEvidenceEquals(
                manifest.sourceObservationsJson(),
                manifestNode.required("sources"));
        requireJsonEvidenceEquals(manifest.warningsJson(), manifestNode.required("warnings"));
        if (!manifest.sourceSnapshotIds().equals(snapshotIds(
                manifestNode.required("sources")))) {
            throw new J7ExportException(J7ExportError.FILE_TAMPERED);
        }
        ObjectNode validation = object(manifestNode, "validation");
        Optional<Instant> decidedAt = validation.required("decidedAt").isNull()
                ? Optional.empty()
                : Optional.of(Instant.parse(validation.required("decidedAt").stringValue()));
        Optional<String> reason = validation.required("rejectionReason").isNull()
                ? Optional.empty()
                : Optional.of(validation.required("rejectionReason").stringValue());
        if (!decidedAt.equals(manifest.decidedAt())
                || !reason.equals(manifest.decisionReason())) {
            throw new J7ExportException(J7ExportError.FILE_TAMPERED);
        }
        if (manifest.status() == J7ExportStatus.COHERENCE_CHECKED
                && !manifest.currentContentSha256().equals(
                manifest.candidateContentSha256())) {
            throw new J7ExportException(J7ExportError.FILE_TAMPERED);
        }
        return new VerifiedFile(content, envelope);
    }

    private void requireJsonEvidenceEquals(String persistedJson, JsonNode envelopeNode) {
        try {
            JsonNode persisted = mapper.readTree(persistedJson);
            if (!envelopeNode.equals(persisted)) {
                throw new J7ExportException(J7ExportError.FILE_TAMPERED);
            }
        }
        catch (JacksonException exception) {
            throw new J7ExportException(J7ExportError.FILE_TAMPERED, exception);
        }
    }

    private static List<Long> snapshotIds(JsonNode sources) {
        List<Long> values = new ArrayList<>();
        sources.valueStream().forEach(source -> {
            JsonNode snapshotId = source.get("snapshotId");
            if (snapshotId != null && !snapshotId.isNull()) {
                values.add(snapshotId.longValue());
            }
        });
        return values.stream().distinct().sorted().toList();
    }

    private static ObjectNode object(ObjectNode parent, String name) {
        JsonNode value = parent.required(name);
        if (value instanceof ObjectNode objectNode) {
            return objectNode;
        }
        throw new J7ExportException(J7ExportError.INVALID_SCHEMA);
    }

    private static void requireAssembledMatchesVerified(
            J7AssembledEnvelope assembled,
            J7VerifiedEnvelope verified) {
        if (!assembled.exportId().equals(verified.exportId())
                || !assembled.canonicalEventId().equals(verified.canonicalEventId())
                || assembled.status() != verified.status()
                || !assembled.dataSha256().equals(verified.dataSha256())
                || !assembled.sourceSetSha256().equals(verified.sourceSetSha256())
                || assembled.sizeBytes() != verified.contentSizeBytes()) {
            throw new J7ExportException(J7ExportError.INVALID_HASH);
        }
    }

    private static String validationConfirmation(J7ExportManifest manifest) {
        return "VALIDER EXPORT J7 " + manifest.exportId() + " " + manifest.dataSha256();
    }

    private static String rejectionConfirmation(J7ExportManifest manifest) {
        return "REJETER EXPORT J7 " + manifest.exportId();
    }

    private static void requireExactConfirmation(String submitted, String expected) {
        if (!Objects.equals(submitted, expected)) {
            throw new J7ExportException(J7ExportError.INVALID_CONFIRMATION);
        }
    }

    private static String normalizeReason(String reason) {
        if (reason == null) {
            throw new J7ExportException(J7ExportError.INVALID_REJECTION_REASON);
        }
        String normalized = reason.trim();
        if (normalized.isEmpty()
                || normalized.length() > 500
                || normalized.chars().anyMatch(value ->
                Character.isISOControl(value) || value == 0x7f)) {
            throw new J7ExportException(J7ExportError.INVALID_REJECTION_REASON);
        }
        return normalized;
    }

    private void cleanupCandidateBestEffort(J7ExportManifest manifest) {
        String candidatePath = fileName(
                manifest.canonicalEventId(),
                manifest.exportId(),
                J7ExportStatus.COHERENCE_CHECKED);
        try {
            if (manifest.status() == J7ExportStatus.COHERENCE_CHECKED) {
                fileStore.deleteVerified(
                        candidatePath,
                        manifest.candidateContentSha256(),
                        manifest.currentSizeBytes());
            }
            else {
                fileStore.readExisting(candidatePath).ifPresent(content -> {
                    if (!Sha256.hex(content).equals(manifest.candidateContentSha256())) {
                        throw new J7ExportException(J7ExportError.FILE_TAMPERED);
                    }
                    fileStore.deleteVerified(
                            candidatePath,
                            manifest.candidateContentSha256(),
                            content.length);
                });
            }
        }
        catch (J7ExportException exception) {
            LOGGER.warn(
                    "J7 candidate cleanup deferred exportId={} candidateSha256={} size={}",
                    manifest.exportId(),
                    manifest.candidateContentSha256(),
                    manifest.currentSizeBytes());
        }
    }

    private void cleanupCandidateAfterCommit(J7ExportManifest candidateManifest) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            cleanupCandidateBestEffort(candidateManifest);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        cleanupCandidateBestEffort(candidateManifest);
                    }
                });
    }

    private void deleteCandidateAfterDeterministicInsertFailure(
            String relativePath,
            J7StoredFile stored) {
        try {
            fileStore.deleteVerified(relativePath, stored.sha256(), stored.sizeBytes());
        }
        catch (J7ExportException cleanupException) {
            LOGGER.warn(
                    "J7 candidate cleanup after deterministic insert failure deferred contentSha256={} size={}",
                    stored.sha256(),
                    stored.sizeBytes());
        }
    }

    private static String fileName(
            UUID canonicalEventId,
            UUID exportId,
            J7ExportStatus status) {
        String suffix = switch (status) {
            case COHERENCE_CHECKED -> "candidate";
            case HUMAN_VALIDATED -> "validated";
            case REJECTED -> "rejected";
        };
        return "j7-" + canonicalEventId + "-" + exportId + "." + suffix + ".json";
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private JsonNode parseJson(String json) {
        try {
            return mapper.readTree(json);
        }
        catch (JacksonException exception) {
            throw new J7ExportException(J7ExportError.FILE_TAMPERED, exception);
        }
    }

    private static J7ExportException databaseUnavailable(DataAccessException exception) {
        return new J7ExportException(J7ExportError.DATABASE_UNAVAILABLE, exception);
    }

    private record VerifiedFile(byte[] content, ObjectNode envelope) {

        private VerifiedFile {
            content = content.clone();
            envelope = envelope.deepCopy();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }

        @Override
        public ObjectNode envelope() {
            return envelope.deepCopy();
        }
    }
}
