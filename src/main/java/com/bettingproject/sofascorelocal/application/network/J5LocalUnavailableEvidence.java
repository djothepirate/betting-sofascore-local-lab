package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;

import java.nio.charset.StandardCharsets;

/** Canonical local evidence produced after an operator explicitly observes an HTTP 404. */
public final class J5LocalUnavailableEvidence {

    public static final String OPERATOR_MARKER = "LOCAL_OPERATOR_DECLARED_HTTP_404";

    private static final RawPayloadEvidence DECLARED_HTTP_404 = RawPayloadEvidence.capture(
            ("{\"error\":{\"code\":404,\"message\":\""
                    + OPERATOR_MARKER
                    + "\"}}")
                    .getBytes(StandardCharsets.UTF_8));

    private J5LocalUnavailableEvidence() { }

    public static RawPayloadEvidence declared404() {
        return DECLARED_HTTP_404;
    }
}
