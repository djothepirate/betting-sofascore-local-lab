package com.bettingproject.sofascorelocal.port;

import com.bettingproject.sofascorelocal.domain.provider.TournamentScheduledEventsProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.TournamentScheduledEventsTransportResponse;

public interface TournamentScheduledEventsProviderTransport {

    TournamentScheduledEventsTransportResponse execute(
            TournamentScheduledEventsProviderRequest request);
}
