package com.bettingproject.sofascorelocal.domain.provider;

public enum RawSnapshotSchemaStatus {
    RAW_ONLY,
    PARSED,
    ENDPOINT_UNAVAILABLE,
    SCHEMA_INCOMPATIBLE,
    UNEXPECTED_CONTENT,
    TRANSPORT_ERROR
}
