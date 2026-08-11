package com.bettingproject.sofascorelocal.fixture;

import java.util.Objects;

public class FixtureLoadingException extends RuntimeException {

    private final Reason reason;

    public FixtureLoadingException(Reason reason, String message) {
        super(message);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason {
        INVALID_RESOURCE_PATH,
        RESOURCE_NOT_FOUND,
        RESOURCE_READ_FAILED,
        MANIFEST_TOO_LARGE,
        PAYLOAD_TOO_LARGE,
        INVALID_MANIFEST,
        INVALID_JSON,
        SENSITIVE_CONTENT,
        RAW_HASH_MISMATCH,
        CANONICAL_HASH_MISMATCH
    }
}
