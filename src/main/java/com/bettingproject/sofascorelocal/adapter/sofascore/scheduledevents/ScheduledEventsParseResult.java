package com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents;

import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEventsPage;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ScheduledEventsParseResult(
        ScheduledEventsParseStatus status,
        ScheduledEventsParseEvidence evidence,
        Optional<ScheduledEventsPage> page,
        List<ScheduledEventsParseWarning> warnings,
        List<ScheduledEventsParseProblem> problems) {

    public ScheduledEventsParseResult {
        status = Objects.requireNonNull(status, "status");
        evidence = Objects.requireNonNull(evidence, "evidence");
        page = Objects.requireNonNull(page, "page");
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
        problems = List.copyOf(Objects.requireNonNull(problems, "problems"));

        if (status == ScheduledEventsParseStatus.PARSED) {
            if (page.isEmpty() || !problems.isEmpty()) {
                throw new IllegalArgumentException(
                        "A parsed result requires a page and no problems");
            }
        }
        else if (page.isPresent() || problems.isEmpty()) {
            throw new IllegalArgumentException(
                    "A failed result requires problems and must not expose a page");
        }
    }

    public static ScheduledEventsParseResult parsed(
            ScheduledEventsParseEvidence evidence,
            ScheduledEventsPage page,
            List<ScheduledEventsParseWarning> warnings) {
        return new ScheduledEventsParseResult(
                ScheduledEventsParseStatus.PARSED,
                evidence,
                Optional.of(page),
                warnings,
                List.of());
    }

    public static ScheduledEventsParseResult failed(
            ScheduledEventsParseStatus status,
            ScheduledEventsParseEvidence evidence,
            List<ScheduledEventsParseWarning> warnings,
            List<ScheduledEventsParseProblem> problems) {
        if (status == ScheduledEventsParseStatus.PARSED) {
            throw new IllegalArgumentException("A failed result cannot have PARSED status");
        }
        return new ScheduledEventsParseResult(
                status,
                evidence,
                Optional.empty(),
                warnings,
                problems);
    }
}
