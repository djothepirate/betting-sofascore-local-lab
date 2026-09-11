package com.bettingproject.sofascorelocal.application.network.playwright;

import java.util.Objects;

/**
 * A bounded entity tag that may cross the isolated Playwright IPC boundary.
 *
 * <p>The value is deliberately opaque to the transport. Its string representation is redacted so
 * diagnostics, exception aggregation, and incidental collection logging cannot disclose it.</p>
 */
public final class PlaywrightProviderEntityTag {

    public static final int MAXIMUM_VISIBLE_ASCII_CHARACTERS = 512;

    private final String value;

    private PlaywrightProviderEntityTag(String value) {
        this.value = validate(value);
    }

    public static PlaywrightProviderEntityTag of(String value) {
        return new PlaywrightProviderEntityTag(value);
    }

    /** Returns the exact opaque value only for the next explicitly requested conditional exchange. */
    public String value() {
        return value;
    }

    private static String validate(String value) {
        Objects.requireNonNull(value, "value");
        if (value.isEmpty() || value.length() > MAXIMUM_VISIBLE_ASCII_CHARACTERS
                || value.chars().anyMatch(character -> character < 0x21 || character > 0x7e)) {
            throw new IllegalArgumentException("invalid bounded provider entity tag");
        }
        return value;
    }

    @Override
    public boolean equals(Object candidate) {
        return this == candidate
                || candidate instanceof PlaywrightProviderEntityTag other
                && value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return "PlaywrightProviderEntityTag[redacted]";
    }
}
