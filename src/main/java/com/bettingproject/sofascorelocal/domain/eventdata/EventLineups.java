package com.bettingproject.sofascorelocal.domain.eventdata;

import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;

import java.util.Objects;

public record EventLineups(
        long providerEventId,
        boolean confirmed,
        TeamLineup home,
        TeamLineup away) implements J5EventData {

    public EventLineups {
        if (providerEventId < 1) {
            throw new IllegalArgumentException("providerEventId must be positive");
        }
        home = Objects.requireNonNull(home, "home");
        away = Objects.requireNonNull(away, "away");
        if (home.side() != LineupSide.HOME || away.side() != LineupSide.AWAY) {
            throw new IllegalArgumentException("lineup sides must be HOME then AWAY");
        }
    }

    @Override
    public SofascoreEndpointType endpointType() {
        return SofascoreEndpointType.EVENT_LINEUPS;
    }
}
