package com.bettingproject.sofascorelocal.domain.provider;

import java.net.URI;
import java.util.List;
import java.util.Objects;

public record J3ProviderQualificationSnapshot(
        boolean available,
        URI providerOrigin,
        int firstPage,
        List<String> blockers) {

    public J3ProviderQualificationSnapshot {
        blockers = List.copyOf(Objects.requireNonNull(blockers, "blockers"));
        if (available) {
            Objects.requireNonNull(providerOrigin, "providerOrigin");
            if (firstPage < ScheduledEventsProviderPageRequest.FIRST_PAGE
                    || firstPage > ScheduledEventsProviderPageRequest.LAST_PAGE) {
                throw new IllegalArgumentException(
                        "an available qualification path requires a valid first page");
            }
            if (!blockers.isEmpty()) {
                throw new IllegalArgumentException(
                        "an available qualification path cannot carry blockers");
            }
        }
        else if (providerOrigin != null || firstPage != 0 || blockers.isEmpty()) {
            throw new IllegalArgumentException(
                    "a blocked qualification path requires blockers and no origin");
        }
    }

    public static J3ProviderQualificationSnapshot available(URI providerOrigin) {
        return available(providerOrigin, ScheduledEventsProviderPageRequest.FIRST_PAGE);
    }

    public static J3ProviderQualificationSnapshot available(
            URI providerOrigin,
            int firstPage) {
        return new J3ProviderQualificationSnapshot(
                true,
                providerOrigin,
                firstPage,
                List.of());
    }

    public static J3ProviderQualificationSnapshot blocked(List<String> blockers) {
        return new J3ProviderQualificationSnapshot(false, null, 0, blockers);
    }
}
