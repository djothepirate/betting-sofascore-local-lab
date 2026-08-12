package com.bettingproject.sofascorelocal.domain.provider;

public enum RawSnapshotSchemaStatus {
    RAW_ONLY,
    PARSED,
    SCHEMA_INCOMPATIBLE,
    UNEXPECTED_CONTENT,
    TRANSPORT_ERROR
}
