package com.bettingproject.sofascorelocal.application.snapshot;

public enum RawSnapshotInspectionError {
    INVALID_SELECTION,
    SNAPSHOT_NOT_FOUND,
    LOCAL_DATABASE_UNAVAILABLE,
    PAYLOAD_INTEGRITY_FAILURE,
    SENSITIVE_CONTENT_BLOCKED,
    INVALID_JSON
}
