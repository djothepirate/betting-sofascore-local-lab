package com.bettingproject.sofascorelocal.application.network;

import java.util.Objects;

public final class TournamentEventDiscoveryControlException extends RuntimeException {

    private final TournamentEventDiscoveryControlError error;

    public TournamentEventDiscoveryControlException(
            TournamentEventDiscoveryControlError error) {
        super("tournament event discovery control rejected the operation: "
                + Objects.requireNonNull(error, "error"));
        this.error = error;
    }

    public TournamentEventDiscoveryControlError error() {
        return error;
    }
}
