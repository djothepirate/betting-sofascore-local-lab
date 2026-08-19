package com.bettingproject.sofascorelocal.port;

import com.bettingproject.sofascorelocal.domain.export.J7ExportDecision;
import com.bettingproject.sofascorelocal.domain.export.J7ExportManifest;
import com.bettingproject.sofascorelocal.domain.export.J7ExportManifestDraft;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface J7ExportManifestStore {

    void lockCanonicalEvent(UUID canonicalEventId);

    J7ExportManifest insertCandidate(J7ExportManifestDraft draft);

    Optional<J7ExportManifest> findByExportId(UUID exportId);

    List<J7ExportManifest> findByCanonicalEventId(UUID canonicalEventId);

    Optional<J7ExportManifest> findPending(
            UUID canonicalEventId,
            String schemaVersion);

    Optional<J7ExportManifest> findValidatedByDataSha256(
            UUID canonicalEventId,
            String schemaVersion,
            String dataSha256);

    Optional<J7ExportDecision> findDecisionIntent(UUID exportId);

    J7ExportDecision recordDecisionIntent(UUID exportId, J7ExportDecision decision);

    void clearDecisionIntent(UUID exportId, J7ExportDecision expectedDecision);

    J7ExportManifest decide(UUID exportId, J7ExportDecision decision);
}
