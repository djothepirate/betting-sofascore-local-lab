package com.bettingproject.sofascorelocal.adapter.sofascore.live;

import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessReport;
import com.bettingproject.sofascorelocal.domain.eventdata.J5EventData;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventDetails;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Supplemental live projections retain their own version; existing parsers stay unchanged. */
public record LiveNormalizedPayload(
        Status status,
        String code,
        String parserVersion,
        String projectionVersion,
        String projectionJson,
        Optional<EventDetails> details,
        Optional<J5EventData> eventData,
        Optional<J5CompletenessReport> completeness,
        List<LivePhaseSignal> signals) {

    public enum Status { PARSED, SCHEMA_INCOMPATIBLE, UNEXPECTED_CONTENT, IDENTITY_MISMATCH }

    public LiveNormalizedPayload {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(parserVersion, "parserVersion");
        Objects.requireNonNull(projectionVersion, "projectionVersion");
        Objects.requireNonNull(projectionJson, "projectionJson");
        details = Objects.requireNonNull(details, "details");
        eventData = Objects.requireNonNull(eventData, "eventData");
        completeness = Objects.requireNonNull(completeness, "completeness");
        signals = List.copyOf(signals);
        if (status == Status.PARSED) {
            if (details.isPresent() == eventData.isPresent()
                    || eventData.isPresent() != completeness.isPresent()) {
                throw new IllegalArgumentException("parsed payload requires exactly one normalized family");
            }
        }
        else if (details.isPresent() || eventData.isPresent() || completeness.isPresent()
                || !signals.isEmpty()) {
            throw new IllegalArgumentException("failed payload cannot expose partial normalized data");
        }
    }
}
