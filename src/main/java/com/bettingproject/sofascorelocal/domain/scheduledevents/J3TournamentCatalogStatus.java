package com.bettingproject.sofascorelocal.domain.scheduledevents;

/**
 * Outcome of rebuilding the actionable tournament catalogue from the latest
 * terminal J3 collection evidence.
 */
public enum J3TournamentCatalogStatus {
    AVAILABLE,
    NO_COLLECTION_EVIDENCE,
    COLLECTION_NOT_COMPLETED,
    COLLECTION_EVIDENCE_INVALID,
    SNAPSHOT_NOT_FOUND,
    SNAPSHOT_METADATA_MISMATCH,
    SNAPSHOT_INTEGRITY_FAILURE,
    SNAPSHOT_PARSE_INCOMPATIBLE,
    TOURNAMENT_CONFLICT
}
