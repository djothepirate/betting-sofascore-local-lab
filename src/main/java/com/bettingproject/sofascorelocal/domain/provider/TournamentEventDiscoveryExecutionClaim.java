package com.bettingproject.sofascorelocal.domain.provider;

import com.bettingproject.sofascorelocal.domain.scheduledevents.J3TournamentCatalogOption;

import java.net.URI;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record TournamentEventDiscoveryExecutionClaim(
        UUID requestId,
        URI providerOrigin,
        LocalDate collectionDate,
        J3TournamentCatalogOption selection) implements TournamentEventDiscoveryClaim {

    public TournamentEventDiscoveryExecutionClaim {
        Objects.requireNonNull(requestId, "requestId");
        EventDetailsProviderRequest.parseExactProviderOrigin(
                Objects.requireNonNull(providerOrigin, "providerOrigin").toString());
        Objects.requireNonNull(collectionDate, "collectionDate");
        Objects.requireNonNull(selection, "selection");
    }
}
