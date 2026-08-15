package com.bettingproject.sofascorelocal.domain.provider;

import java.net.URI;
import java.util.List;
import java.util.Objects;

public record J4EventDetailsQualificationSnapshot(
        boolean available,
        URI providerOrigin,
        List<String> blockers) {

    public J4EventDetailsQualificationSnapshot {
        blockers = List.copyOf(Objects.requireNonNull(blockers, "blockers"));
        if (available != blockers.isEmpty()) {
            throw new IllegalArgumentException(
                    "availability and blockers must be mutually exclusive");
        }
        if (available && providerOrigin == null) {
            throw new IllegalArgumentException("available qualification requires an origin");
        }
        if (!available && providerOrigin != null) {
            throw new IllegalArgumentException("blocked qualification cannot expose an origin");
        }
    }

    public static J4EventDetailsQualificationSnapshot available(URI providerOrigin) {
        return new J4EventDetailsQualificationSnapshot(true, providerOrigin, List.of());
    }

    public static J4EventDetailsQualificationSnapshot blocked(List<String> blockers) {
        if (Objects.requireNonNull(blockers, "blockers").isEmpty()) {
            throw new IllegalArgumentException("blocked qualification requires blockers");
        }
        return new J4EventDetailsQualificationSnapshot(false, null, blockers);
    }
}
