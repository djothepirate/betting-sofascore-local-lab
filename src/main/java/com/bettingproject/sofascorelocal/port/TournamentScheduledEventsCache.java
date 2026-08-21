package com.bettingproject.sofascorelocal.port;

import com.bettingproject.sofascorelocal.domain.provider.CachedTournamentScheduledEventsResponse;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceResult;
import com.bettingproject.sofascorelocal.domain.provider.TournamentScheduledEventsProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.TournamentScheduledEventsTransportResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public interface TournamentScheduledEventsCache {

    Optional<CachedTournamentScheduledEventsResponse> findFreshParsed(
            TournamentScheduledEventsProviderRequest request,
            Instant evaluatedAt,
            Duration timeToLive,
            String parserVersion);

    void recordParsed(
            TournamentScheduledEventsProviderRequest request,
            TournamentScheduledEventsTransportResponse response,
            RawSnapshotPersistenceResult persistence,
            String parserVersion);
}
