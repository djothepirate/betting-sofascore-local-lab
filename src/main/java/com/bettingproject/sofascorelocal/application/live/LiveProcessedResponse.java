package com.bettingproject.sofascorelocal.application.live;

import com.bettingproject.sofascorelocal.adapter.sofascore.live.LivePhaseSignal;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessReport;
import com.bettingproject.sofascorelocal.domain.eventdata.J5EventData;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventDetails;
import com.bettingproject.sofascorelocal.domain.live.LiveJ4ControlFacts;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceResult;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Pure normalization plan. IDs are assigned only by the enclosing live publication transaction. */
public record LiveProcessedResponse(
        CanonicalEventIdentity identity,
        SofascoreEndpointType endpoint,
        RawSnapshotPersistenceResult raw,
        Instant receivedAt,
        Outcome outcome,
        FailureScope scope,
        String code,
        Optional<String> sportStatus,
        List<LivePhaseSignal> signals,
        String projectionJson,
        String parserVersion,
        String projectionVersion,
        Optional<J5CompletenessReport> completeness,
        Optional<EventDetails> eventDetails,
        Optional<J5EventData> eventData,
        Optional<LiveJ4ControlFacts> j4Controls) {

    public enum Outcome { PARSED, ENDPOINT_UNAVAILABLE, SCHEMA_INCOMPATIBLE, FAILED }
    public enum FailureScope { NONE, EVENT, CAMPAIGN }

    public LiveProcessedResponse {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(raw, "raw");
        Objects.requireNonNull(receivedAt, "receivedAt");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(code, "code");
        sportStatus = Objects.requireNonNull(sportStatus, "sportStatus");
        signals = List.copyOf(signals);
        Objects.requireNonNull(projectionJson, "projectionJson");
        Objects.requireNonNull(parserVersion, "parserVersion");
        Objects.requireNonNull(projectionVersion, "projectionVersion");
        completeness = Objects.requireNonNull(completeness, "completeness");
        eventDetails = Objects.requireNonNull(eventDetails, "eventDetails");
        eventData = Objects.requireNonNull(eventData, "eventData");
        j4Controls = Objects.requireNonNull(j4Controls, "j4Controls");
        if (outcome == Outcome.PARSED && (scope != FailureScope.NONE
                || eventDetails.isPresent() == eventData.isPresent())) {
            throw new IllegalArgumentException("parsed live response requires one normalized family");
        }
        if (eventData.isPresent() != completeness.isPresent()
                || sportStatus.isPresent() != eventDetails.isPresent()) {
            throw new IllegalArgumentException("normalized data and metadata must agree");
        }
        if ((outcome == Outcome.FAILED || outcome == Outcome.SCHEMA_INCOMPATIBLE)
                && (eventDetails.isPresent() || eventData.isPresent() || !signals.isEmpty() || j4Controls.isPresent())) {
            throw new IllegalArgumentException("failed live response cannot publish partial normalized data");
        }
        if (j4Controls.isPresent() && eventDetails.isEmpty()) {
            throw new IllegalArgumentException("J4 controls require event details");
        }
    }

    /** Compatibility constructor retained for historical direct processor fixtures. */
    public LiveProcessedResponse(
            CanonicalEventIdentity identity, SofascoreEndpointType endpoint, RawSnapshotPersistenceResult raw,
            Instant receivedAt, Outcome outcome, FailureScope scope, String code, Optional<String> sportStatus,
            List<LivePhaseSignal> signals, String projectionJson, String parserVersion, String projectionVersion,
            Optional<J5CompletenessReport> completeness, Optional<EventDetails> eventDetails,
            Optional<J5EventData> eventData) {
        this(identity, endpoint, raw, receivedAt, outcome, scope, code, sportStatus, signals, projectionJson,
                parserVersion, projectionVersion, completeness, eventDetails, eventData, Optional.empty());
    }

    public boolean stopEvent() { return scope == FailureScope.EVENT; }
    public boolean stopCampaign() { return scope == FailureScope.CAMPAIGN; }
}
