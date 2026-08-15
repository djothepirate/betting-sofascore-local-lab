package com.bettingproject.sofascorelocal.port;

import com.bettingproject.sofascorelocal.domain.provider.EventDetailsProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.EventDetailsTransportResponse;
import com.bettingproject.sofascorelocal.domain.provider.J4CachedEventDetails;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceResult;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public interface J4EventDetailsCache {

    Optional<J4CachedEventDetails> findFreshParsed(
            EventDetailsProviderRequest request,
            Instant evaluatedAt,
            Duration timeToLive,
            String parserVersion);

    void recordParsed(
            EventDetailsProviderRequest request,
            EventDetailsTransportResponse response,
            RawSnapshotPersistenceResult persistence,
            String parserVersion);
}
