package com.bettingproject.sofascorelocal.adapter.sofascore.eventdetails;

import com.bettingproject.sofascorelocal.domain.eventdetails.EventDetails;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record EventDetailsParseResult(
        EventDetailsParseStatus status,
        EventDetailsParseEvidence evidence,
        Optional<EventDetails> details,
        List<EventDetailsParseWarning> warnings,
        List<EventDetailsParseProblem> problems) {

    public EventDetailsParseResult {
        status = Objects.requireNonNull(status, "status");
        evidence = Objects.requireNonNull(evidence, "evidence");
        details = Objects.requireNonNull(details, "details");
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
        problems = List.copyOf(Objects.requireNonNull(problems, "problems"));
        if (status == EventDetailsParseStatus.PARSED) {
            if (details.isEmpty() || !problems.isEmpty()) {
                throw new IllegalArgumentException(
                        "a parsed result requires details and no problems");
            }
        }
        else if (details.isPresent() || problems.isEmpty()) {
            throw new IllegalArgumentException(
                    "a failed result requires problems and no partial details");
        }
    }

    public static EventDetailsParseResult parsed(
            EventDetailsParseEvidence evidence,
            EventDetails details,
            List<EventDetailsParseWarning> warnings) {
        return new EventDetailsParseResult(
                EventDetailsParseStatus.PARSED,
                evidence,
                Optional.of(details),
                warnings,
                List.of());
    }

    public static EventDetailsParseResult failed(
            EventDetailsParseStatus status,
            EventDetailsParseEvidence evidence,
            List<EventDetailsParseWarning> warnings,
            List<EventDetailsParseProblem> problems) {
        return new EventDetailsParseResult(
                status,
                evidence,
                Optional.empty(),
                warnings,
                problems);
    }
}
