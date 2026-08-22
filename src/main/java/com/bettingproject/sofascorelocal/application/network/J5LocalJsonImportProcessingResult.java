package com.bettingproject.sofascorelocal.application.network;

import java.util.List;
import java.util.Objects;

/** Successful local processing metadata; raw payload content is deliberately absent. */
public record J5LocalJsonImportProcessingResult(
        int localJsonImports,
        List<J5RealEndpointResult> endpoints) {

    public J5LocalJsonImportProcessingResult {
        endpoints = List.copyOf(Objects.requireNonNull(endpoints, "endpoints"));
        if (localJsonImports < 0
                || localJsonImports > 3
                || endpoints.size() != localJsonImports) {
            throw new IllegalArgumentException("local import result counts are inconsistent");
        }
    }
}
