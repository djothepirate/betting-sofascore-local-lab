package com.bettingproject.sofascorelocal.domain.provider;

import java.net.URI;
import java.util.List;
import java.util.Objects;

public record TournamentEventDiscoveryQualificationSnapshot(
        boolean available,
        URI providerOrigin,
        List<String> blockers) {

    public TournamentEventDiscoveryQualificationSnapshot {
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

    public static TournamentEventDiscoveryQualificationSnapshot available(URI origin) {
        return new TournamentEventDiscoveryQualificationSnapshot(true, origin, List.of());
    }

    public static TournamentEventDiscoveryQualificationSnapshot blocked(
            List<String> blockers) {
        if (Objects.requireNonNull(blockers, "blockers").isEmpty()) {
            throw new IllegalArgumentException("blocked qualification requires blockers");
        }
        return new TournamentEventDiscoveryQualificationSnapshot(false, null, blockers);
    }
}
