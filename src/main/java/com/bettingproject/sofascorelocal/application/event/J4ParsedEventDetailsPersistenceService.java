package com.bettingproject.sofascorelocal.application.event;

import com.bettingproject.sofascorelocal.adapter.sofascore.eventdetails.EventDetailsV3Parser;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservation;
import com.bettingproject.sofascorelocal.domain.event.EventSourceTrace;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventDetailObservation;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventDetails;
import com.bettingproject.sofascorelocal.domain.provider.EventDetailsProviderRequest;
import com.bettingproject.sofascorelocal.domain.provider.EventDetailsTransportResponse;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceResult;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;
import com.bettingproject.sofascorelocal.port.CanonicalEventStore;
import com.bettingproject.sofascorelocal.port.EventDetailsStore;
import com.bettingproject.sofascorelocal.port.J4EventDetailsCache;
import com.bettingproject.sofascorelocal.port.RawManualCallSnapshotStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class J4ParsedEventDetailsPersistenceService {

    private final RawManualCallSnapshotStore rawSnapshotStore;
    private final CanonicalEventStore canonicalEventStore;
    private final EventDetailsStore eventDetailsStore;
    private final J4EventDetailsCache cache;

    public J4ParsedEventDetailsPersistenceService(
            RawManualCallSnapshotStore rawSnapshotStore,
            CanonicalEventStore canonicalEventStore,
            EventDetailsStore eventDetailsStore,
            J4EventDetailsCache cache) {
        this.rawSnapshotStore = Objects.requireNonNull(rawSnapshotStore, "rawSnapshotStore");
        this.canonicalEventStore = Objects.requireNonNull(
                canonicalEventStore, "canonicalEventStore");
        this.eventDetailsStore = Objects.requireNonNull(eventDetailsStore, "eventDetailsStore");
        this.cache = Objects.requireNonNull(cache, "cache");
    }

    /**
     * The raw snapshot is committed by the caller before this transaction starts.
     * Normalized observations, final classification and cache pointer then commit atomically.
     */
    @Transactional
    public J4ParsedEventDetailsPersistenceResult persistParsed(
            EventDetailsProviderRequest request,
            EventDetailsTransportResponse response,
            RawSnapshotPersistenceResult rawPersistence,
            EventDetails details) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(response, "response");
        Objects.requireNonNull(rawPersistence, "rawPersistence");
        Objects.requireNonNull(details, "details");
        if (request.eventId() != details.providerEventId()) {
            throw new IllegalArgumentException(
                    "parsed provider event id must match the authorized request");
        }
        if (!request.requestKey().equals(response.requestKey())) {
            throw new IllegalArgumentException("request and response keys must match");
        }
        if (!rawPersistence.payloadSha256().equals(response.payload().sha256())) {
            throw new IllegalArgumentException("raw persistence hash must match the response");
        }

        EventSourceTrace source = EventSourceTrace.providerSnapshot(
                rawPersistence.snapshotId(),
                rawPersistence.payloadSha256(),
                EventDetailsV3Parser.PARSER_VERSION,
                response.receivedAt());
        CanonicalEventIdentity identity = CanonicalEventIdentity.sofascore(request.eventId());
        var eventPersistence = canonicalEventStore.save(CanonicalEventObservation.from(
                details.asScheduledEvent(),
                source));
        var detailPersistence = eventDetailsStore.save(EventDetailObservation.from(
                identity,
                details,
                source));
        rawSnapshotStore.classify(
                rawPersistence.snapshotId(),
                RawSnapshotSchemaStatus.PARSED,
                null);
        cache.recordParsed(
                request,
                response,
                rawPersistence,
                EventDetailsV3Parser.PARSER_VERSION);
        return new J4ParsedEventDetailsPersistenceResult(
                identity.value(),
                eventPersistence.observationId(),
                detailPersistence.observationId(),
                eventPersistence.inserted(),
                detailPersistence.inserted());
    }
}
