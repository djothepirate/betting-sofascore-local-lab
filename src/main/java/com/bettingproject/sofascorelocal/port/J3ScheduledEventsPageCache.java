package com.bettingproject.sofascorelocal.port;

import com.bettingproject.sofascorelocal.domain.provider.J3CachedScheduledEventsPage;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceResult;
import com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsProviderPageRequest;
import com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsTransportResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public interface J3ScheduledEventsPageCache {

    Optional<J3CachedScheduledEventsPage> findFreshParsed(
            ScheduledEventsProviderPageRequest request,
            Instant evaluatedAt,
            Duration timeToLive,
            String parserVersion);

    void recordParsed(
            ScheduledEventsProviderPageRequest request,
            ScheduledEventsTransportResponse response,
            RawSnapshotPersistenceResult persistence,
            String parserVersion);
}
