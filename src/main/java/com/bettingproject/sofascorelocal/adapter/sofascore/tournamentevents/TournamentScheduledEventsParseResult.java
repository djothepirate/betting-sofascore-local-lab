package com.bettingproject.sofascorelocal.adapter.sofascore.tournamentevents;

import com.bettingproject.sofascorelocal.domain.scheduledevents.TournamentScheduledEventCandidate;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record TournamentScheduledEventsParseResult(
        TournamentScheduledEventsParseStatus status,
        TournamentScheduledEventsParseEvidence evidence,
        Optional<List<TournamentScheduledEventCandidate>> candidates,
        List<TournamentScheduledEventsParseWarning> warnings,
        List<TournamentScheduledEventsParseProblem> problems) {

    public TournamentScheduledEventsParseResult {
        status = Objects.requireNonNull(status, "status");
        evidence = Objects.requireNonNull(evidence, "evidence");
        candidates = Objects.requireNonNull(candidates, "candidates").map(List::copyOf);
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
        problems = List.copyOf(Objects.requireNonNull(problems, "problems"));

        if (status == TournamentScheduledEventsParseStatus.PARSED) {
            if (candidates.isEmpty() || !problems.isEmpty()) {
                throw new IllegalArgumentException(
                        "A parsed result requires a complete candidate list and no problems");
            }
        }
        else if (candidates.isPresent() || problems.isEmpty()) {
            throw new IllegalArgumentException(
                    "A failed result requires problems and must not expose candidates");
        }
    }

    public static TournamentScheduledEventsParseResult parsed(
            TournamentScheduledEventsParseEvidence evidence,
            List<TournamentScheduledEventCandidate> candidates,
            List<TournamentScheduledEventsParseWarning> warnings) {
        return new TournamentScheduledEventsParseResult(
                TournamentScheduledEventsParseStatus.PARSED,
                evidence,
                Optional.of(List.copyOf(candidates)),
                warnings,
                List.of());
    }

    public static TournamentScheduledEventsParseResult failed(
            TournamentScheduledEventsParseStatus status,
            TournamentScheduledEventsParseEvidence evidence,
            List<TournamentScheduledEventsParseWarning> warnings,
            List<TournamentScheduledEventsParseProblem> problems) {
        if (status == TournamentScheduledEventsParseStatus.PARSED) {
            throw new IllegalArgumentException("A failed result cannot have PARSED status");
        }
        return new TournamentScheduledEventsParseResult(
                status,
                evidence,
                Optional.empty(),
                warnings,
                problems);
    }
}
