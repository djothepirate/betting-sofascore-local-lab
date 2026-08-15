package com.bettingproject.sofascorelocal.domain.provider;

import java.net.URI;
import java.util.List;
import java.util.Objects;

public record J5RealQualificationSnapshot(
        boolean available,
        URI providerOrigin,
        List<String> blockers) {

    public J5RealQualificationSnapshot {
        blockers = List.copyOf(Objects.requireNonNull(blockers, "blockers"));
        if (available != blockers.isEmpty()) {
            throw new IllegalArgumentException("availability and blockers are inconsistent");
        }
        if (available) {
            EventDetailsProviderRequest.parseExactProviderOrigin(
                    Objects.requireNonNull(providerOrigin, "providerOrigin").toString());
        }
        else if (providerOrigin != null) {
            throw new IllegalArgumentException("blocked qualification cannot expose an origin");
        }
    }

    public static J5RealQualificationSnapshot available(URI origin) {
        return new J5RealQualificationSnapshot(true, origin, List.of());
    }

    public static J5RealQualificationSnapshot blocked(List<String> blockers) {
        if (Objects.requireNonNull(blockers, "blockers").isEmpty()) {
            throw new IllegalArgumentException("blocked qualification requires blockers");
        }
        return new J5RealQualificationSnapshot(false, null, blockers);
    }
}
