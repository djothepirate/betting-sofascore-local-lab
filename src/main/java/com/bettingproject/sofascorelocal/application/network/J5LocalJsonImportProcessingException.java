package com.bettingproject.sofascorelocal.application.network;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** Safe, bounded failure metadata for a reusable local J5 processing attempt. */
public final class J5LocalJsonImportProcessingException extends RuntimeException {

    private static final Pattern SAFE_CODE = Pattern.compile("[A-Z0-9_]+");

    private final String code;
    private final int localJsonImports;
    private final List<J5RealEndpointResult> endpoints;

    J5LocalJsonImportProcessingException(
            String code,
            int localJsonImports,
            List<J5RealEndpointResult> endpoints) {
        this(code, localJsonImports, endpoints, null);
    }

    J5LocalJsonImportProcessingException(
            String code,
            int localJsonImports,
            List<J5RealEndpointResult> endpoints,
            RuntimeException cause) {
        super(requireSafeCode(code), cause);
        this.code = requireSafeCode(code);
        this.endpoints = List.copyOf(Objects.requireNonNull(endpoints, "endpoints"));
        if (localJsonImports < 0
                || localJsonImports > 3
                || this.endpoints.size() > localJsonImports) {
            throw new IllegalArgumentException(
                    "local import failure counts are inconsistent");
        }
        this.localJsonImports = localJsonImports;
    }

    public String code() {
        return code;
    }

    public int localJsonImports() {
        return localJsonImports;
    }

    public List<J5RealEndpointResult> endpoints() {
        return endpoints;
    }

    private static String requireSafeCode(String value) {
        String normalized = Objects.requireNonNull(value, "code").trim();
        if (normalized.isEmpty()
                || normalized.length() > 96
                || !SAFE_CODE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("code must be a bounded safe code");
        }
        return normalized;
    }
}
