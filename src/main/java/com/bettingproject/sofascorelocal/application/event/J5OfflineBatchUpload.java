package com.bettingproject.sofascorelocal.application.event;

import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;

import java.util.Objects;

public record J5OfflineBatchUpload(
        String fileName,
        RawPayloadEvidence payload) {

    public J5OfflineBatchUpload {
        fileName = Objects.requireNonNull(fileName, "fileName");
        payload = Objects.requireNonNull(payload, "payload");
    }
}
