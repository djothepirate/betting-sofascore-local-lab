package com.bettingproject.sofascorelocal.domain.benchmark;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

final class J8BenchmarkValidation {

    private static final Pattern SAFE_CODE = Pattern.compile("[A-Z0-9_]+");
    private static final Pattern PARSER_VERSION = Pattern.compile("[A-Za-z0-9._-]+");

    private J8BenchmarkValidation() {
    }

    static Optional<String> optionalSafeCode(Optional<String> value, String name) {
        Optional<String> normalized = Objects.requireNonNull(value, name)
                .map(String::trim);
        if (normalized.isPresent()
                && (normalized.orElseThrow().isEmpty()
                        || normalized.orElseThrow().length() > 96
                        || !SAFE_CODE.matcher(normalized.orElseThrow()).matches())) {
            throw new IllegalArgumentException(
                    name + " must be a bounded uppercase safe code");
        }
        return normalized;
    }

    static Optional<String> optionalParserVersion(Optional<String> value) {
        Optional<String> normalized = Objects.requireNonNull(value, "parserVersion")
                .map(String::trim);
        if (normalized.isPresent()
                && (normalized.orElseThrow().isEmpty()
                        || normalized.orElseThrow().length() > 32
                        || !PARSER_VERSION.matcher(normalized.orElseThrow()).matches())) {
            throw new IllegalArgumentException(
                    "parserVersion must be a bounded safe identifier");
        }
        return normalized;
    }
}
