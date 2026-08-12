package com.bettingproject.sofascorelocal.fixture;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;

public final class FixtureContentClassifier {

    private FixtureContentClassifier() {
    }

    public static FixtureContentKind classify(byte[] payload, String declaredContentType) {
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(declaredContentType, "declaredContentType");

        int prefixLength = Math.min(payload.length, 1024);
        String prefix = new String(payload, 0, prefixLength, StandardCharsets.UTF_8)
                .stripLeading();
        if (prefix.startsWith("\uFEFF")) {
            prefix = prefix.substring(1).stripLeading();
        }

        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
        String lowerContentType = declaredContentType.toLowerCase(Locale.ROOT);
        if (lowerPrefix.startsWith("<!doctype html")
                || lowerPrefix.startsWith("<html")
                || lowerPrefix.startsWith("<head")
                || lowerPrefix.startsWith("<body")) {
            return FixtureContentKind.HTML;
        }
        if (prefix.startsWith("{") || prefix.startsWith("[")) {
            return FixtureContentKind.JSON;
        }
        if (lowerContentType.contains("text/html")) {
            return FixtureContentKind.HTML;
        }
        if (lowerContentType.contains("application/json")
                || lowerContentType.contains("+json")) {
            return FixtureContentKind.JSON;
        }
        return FixtureContentKind.OTHER;
    }
}
