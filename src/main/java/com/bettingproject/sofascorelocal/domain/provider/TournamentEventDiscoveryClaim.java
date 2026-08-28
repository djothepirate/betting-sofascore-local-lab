package com.bettingproject.sofascorelocal.domain.provider;

import com.bettingproject.sofascorelocal.domain.scheduledevents.J3TournamentCatalogOption;

import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;

public interface TournamentEventDiscoveryClaim {

    UUID requestId();

    URI providerOrigin();

    LocalDate collectionDate();

    J3TournamentCatalogOption selection();
}
