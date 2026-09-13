package com.bettingproject.sofascorelocal.application.live;

import com.bettingproject.sofascorelocal.adapter.sofascore.live.LiveNormalizedPayload;
import com.bettingproject.sofascorelocal.adapter.sofascore.live.LivePayloadNormalizer;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderResponse;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservation;
import com.bettingproject.sofascorelocal.domain.event.EventSourceTrace;
import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessReport;
import com.bettingproject.sofascorelocal.domain.eventdata.J5EventDataObservation;
import com.bettingproject.sofascorelocal.domain.eventdata.J5UnavailableFamily;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventDetailObservation;
import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.NormalizedReferences;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceOutcome;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceResult;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.port.CanonicalEventStore;
import com.bettingproject.sofascorelocal.port.EventDetailsStore;
import com.bettingproject.sofascorelocal.port.J5EventDataStore;
import com.bettingproject.sofascorelocal.port.RawManualCallSnapshotStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** One response, no transport, no cache, no campaign lifecycle and no implicit transaction. */
@Service
public class LiveResponseProcessor {
    private static final Set<SofascoreEndpointType> ENDPOINTS = Set.of(
            SofascoreEndpointType.EVENT_DETAILS, SofascoreEndpointType.EVENT_STATISTICS,
            SofascoreEndpointType.EVENT_INCIDENTS, SofascoreEndpointType.EVENT_LINEUPS);

    private final CanonicalEventStore eventStore;
    private final EventDetailsStore detailsStore;
    private final J5EventDataStore dataStore;
    private final RawManualCallSnapshotStore rawStore;
    private final LivePayloadNormalizer normalizer;

    @Autowired
    public LiveResponseProcessor(CanonicalEventStore eventStore, EventDetailsStore detailsStore,
                                 J5EventDataStore dataStore, RawManualCallSnapshotStore rawStore) {
        this(eventStore, detailsStore, dataStore, rawStore, new LivePayloadNormalizer());
    }

    LiveResponseProcessor(CanonicalEventStore eventStore, EventDetailsStore detailsStore,
                          J5EventDataStore dataStore, RawManualCallSnapshotStore rawStore,
                          LivePayloadNormalizer normalizer) {
        this.eventStore = Objects.requireNonNull(eventStore);
        this.detailsStore = Objects.requireNonNull(detailsStore);
        this.dataStore = Objects.requireNonNull(dataStore);
        this.rawStore = Objects.requireNonNull(rawStore);
        this.normalizer = Objects.requireNonNull(normalizer);
    }

    /** Raw evidence is already committed. This method is pure and never calls a persistence port. */
    public LiveProcessedResponse process(CanonicalEventIdentity identity,
                                         SofascoreEndpointType endpoint,
                                         PlaywrightProviderResponse response,
                                         RawSnapshotPersistenceResult raw) {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(response, "response");
        Objects.requireNonNull(raw, "raw");
        if (!ENDPOINTS.contains(endpoint)) {
            throw new IllegalArgumentException("endpoint outside the live allowlist");
        }
        if (!CanonicalEventIdentity.SOFASCORE.equals(identity.provider())
                || identity.providerEventId() > 999_999_999L) {
            return failed(identity, endpoint, response, raw, "EVENT_ID_MISMATCH");
        }
        if (raw.outcome() == RawSnapshotPersistenceOutcome.CACHE_HIT
                || raw.occurrenceId().isEmpty()
                || !raw.payloadSha256().equals(response.payload().sha256())
                || raw.payloadSizeBytes() != response.payload().sizeBytes()) {
            return failed(identity, endpoint, response, raw, "RAW_RESPONSE_MISMATCH");
        }
        if (response.httpStatus() == 404) {
            boolean j4 = endpoint == SofascoreEndpointType.EVENT_DETAILS;
            return new LiveProcessedResponse(identity, endpoint, raw, response.receivedAt(),
                    LiveProcessedResponse.Outcome.ENDPOINT_UNAVAILABLE,
                    j4 ? LiveProcessedResponse.FailureScope.EVENT : LiveProcessedResponse.FailureScope.NONE,
                    "HTTP_404", Optional.empty(), List.of(), "{}",
                    j4 ? LivePayloadNormalizer.parserVersion(endpoint) : J5UnavailableFamily.normalizerVersion(endpoint),
                    LivePayloadNormalizer.projectionVersion(endpoint),
                    j4 ? Optional.empty() : Optional.of(J5CompletenessReport.unavailable()),
                    Optional.empty(),
                    j4 ? Optional.empty() : Optional.of(J5UnavailableFamily.emptyObservation(endpoint, identity.providerEventId())));
        }
        if (response.httpStatus() < 200 || response.httpStatus() >= 300) {
            return failed(identity, endpoint, response, raw, "HTTP_" + response.httpStatus());
        }
        if (!isJson(response.contentType())) {
            return failed(identity, endpoint, response, raw, "UNEXPECTED_CONTENT");
        }
        try {
            LiveNormalizedPayload parsed = Objects.requireNonNull(normalizer.normalize(
                    identity.providerEventId(), endpoint, raw.snapshotId(),
                    raw.occurrenceId().orElseThrow(), response.payload(), response.receivedAt()));
            LiveProcessedResponse.Outcome outcome = switch (parsed.status()) {
                case PARSED -> LiveProcessedResponse.Outcome.PARSED;
                case SCHEMA_INCOMPATIBLE -> LiveProcessedResponse.Outcome.SCHEMA_INCOMPATIBLE;
                case UNEXPECTED_CONTENT, IDENTITY_MISMATCH -> LiveProcessedResponse.Outcome.FAILED;
            };
            LiveProcessedResponse.FailureScope scope = switch (parsed.status()) {
                case PARSED -> LiveProcessedResponse.FailureScope.NONE;
                case SCHEMA_INCOMPATIBLE -> LiveProcessedResponse.FailureScope.EVENT;
                case UNEXPECTED_CONTENT, IDENTITY_MISMATCH -> LiveProcessedResponse.FailureScope.CAMPAIGN;
            };
            return new LiveProcessedResponse(identity, endpoint, raw, response.receivedAt(), outcome, scope,
                    parsed.code(), parsed.details().map(details -> details.status().type()), parsed.signals(),
                    parsed.projectionJson(), parsed.parserVersion(), parsed.projectionVersion(),
                    parsed.completeness(), parsed.details(), parsed.eventData(), parsed.j4Controls());
        }
        catch (RuntimeException exception) {
            return failed(identity, endpoint, response, raw, "PARSER_FAILURE");
        }
    }

    /**
     * Called inside LiveCampaignStore.publishResult's callback. The caller's transaction owns
     * observations, supplemental projection, ledger and current pointers together. Failures escape
     * so the transaction rolls back; the already committed raw reception remains intact.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public NormalizedReferences persistProcessed(LiveProcessedResponse processed) {
        Objects.requireNonNull(processed, "processed");
        if ("RAW_RESPONSE_MISMATCH".equals(processed.code())) {
            return NormalizedReferences.none();
        }
        EventSourceTrace source = EventSourceTrace.providerSnapshot(
                processed.raw().snapshotId(), processed.raw().payloadSha256(),
                processed.parserVersion(), processed.receivedAt());
        NormalizedReferences references = NormalizedReferences.none();
        if (processed.eventDetails().isPresent()) {
            var details = processed.eventDetails().orElseThrow();
            CanonicalEventObservation observation = CanonicalEventObservation.from(details.asScheduledEvent(), source);
            EventDetailObservation detail = EventDetailObservation.from(processed.identity(), details, source);
            var eventSaved = Objects.requireNonNull(eventStore.save(observation));
            var detailsSaved = Objects.requireNonNull(detailsStore.save(detail));
            if (!eventSaved.canonicalEventId().equals(processed.identity().value())
                    || !detailsSaved.canonicalEventId().equals(processed.identity().value())) {
                throw new IllegalStateException("NORMALIZATION_IDENTITY_MISMATCH");
            }
            references = new NormalizedReferences(
                    eventSaved.observationId(), detailsSaved.observationId(), null, detail.normalizedSha256());
        }
        else if (processed.eventData().isPresent()) {
            J5EventDataObservation observation = J5EventDataObservation.from(processed.identity(),
                    processed.eventData().orElseThrow(), source, processed.completeness().orElseThrow());
            var saved = Objects.requireNonNull(dataStore.save(observation));
            if (!saved.canonicalEventId().equals(processed.identity().value())
                    || saved.endpointType() != processed.endpoint()) {
                throw new IllegalStateException("NORMALIZATION_IDENTITY_MISMATCH");
            }
            references = new NormalizedReferences(null, null, saved.observationId(), observation.normalizedSha256());
        }
        if (processed.raw().outcome() == RawSnapshotPersistenceOutcome.INSERTED) {
            // A deduplicated snapshot is immutable historical evidence. Its new result is in the live ledger.
            // HTTP_404 belongs to the attempt ledger; an unavailable raw snapshot has no schema error.
            rawStore.classify(processed.raw().snapshotId(), schemaStatus(processed),
                    processed.outcome() == LiveProcessedResponse.Outcome.PARSED
                            || processed.outcome() == LiveProcessedResponse.Outcome.ENDPOINT_UNAVAILABLE
                            ? null : processed.code());
        }
        return references;
    }

    private static RawSnapshotSchemaStatus schemaStatus(LiveProcessedResponse result) {
        return switch (result.outcome()) {
            case PARSED -> RawSnapshotSchemaStatus.PARSED;
            case ENDPOINT_UNAVAILABLE -> RawSnapshotSchemaStatus.ENDPOINT_UNAVAILABLE;
            case SCHEMA_INCOMPATIBLE -> RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE;
            case FAILED -> switch (result.code()) {
                case "INVALID_JSON", "UNEXPECTED_CONTENT" -> RawSnapshotSchemaStatus.UNEXPECTED_CONTENT;
                case "EVENT_ID_MISMATCH", "IDENTITY_NOT_VERIFIABLE" -> RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE;
                case "PARSER_FAILURE" -> RawSnapshotSchemaStatus.RAW_ONLY;
                default -> RawSnapshotSchemaStatus.TRANSPORT_ERROR;
            };
        };
    }

    private static LiveProcessedResponse failed(CanonicalEventIdentity identity, SofascoreEndpointType endpoint,
                                                PlaywrightProviderResponse response,
                                                RawSnapshotPersistenceResult raw, String code) {
        return new LiveProcessedResponse(identity, endpoint, raw, response.receivedAt(),
                LiveProcessedResponse.Outcome.FAILED, LiveProcessedResponse.FailureScope.CAMPAIGN, code,
                Optional.empty(), List.of(), "{}", LivePayloadNormalizer.parserVersion(endpoint),
                LivePayloadNormalizer.projectionVersion(endpoint), Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static boolean isJson(String contentType) {
        String value = contentType.toLowerCase(Locale.ROOT).split(";", 2)[0].trim();
        return "application/json".equals(value) || value.startsWith("application/") && value.endsWith("+json");
    }
}
