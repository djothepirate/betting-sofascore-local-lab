package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.domain.scheduledevents.J3TournamentCatalogOption;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEvent;
import com.bettingproject.sofascorelocal.domain.scheduledevents.TournamentEventCountStatus;
import com.bettingproject.sofascorelocal.domain.scheduledevents.TournamentScheduledEventCandidate;
import com.bettingproject.sofascorelocal.domain.scheduledevents.TournamentScheduledEventsProjection;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.zone.ZoneOffsetTransition;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;

@Service
public class TournamentScheduledEventsProjectionService {

    public static final ZoneId DEFAULT_ZONE = ZoneId.of("Europe/Paris");

    public TournamentScheduledEventsProjection project(
            LocalDate date,
            J3TournamentCatalogOption selection,
            List<TournamentScheduledEventCandidate> candidates) {
        return project(date, DEFAULT_ZONE, selection, candidates);
    }

    TournamentScheduledEventsProjection project(
            LocalDate date,
            ZoneId zone,
            J3TournamentCatalogOption selection,
            List<TournamentScheduledEventCandidate> candidates) {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(zone, "zone");
        Objects.requireNonNull(selection, "selection");
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));

        LinkedHashMap<Long, TournamentScheduledEventCandidate> unique = new LinkedHashMap<>();
        int exactDuplicates = 0;
        for (TournamentScheduledEventCandidate candidate : candidates) {
            requireSelectedUniqueTournament(selection, candidate);
            TournamentScheduledEventCandidate previous = unique.putIfAbsent(
                    candidate.event().providerEventId(), candidate);
            if (previous != null) {
                if (!sameNormalizedIdentityAndContent(previous, candidate)) {
                    throw rejected(
                            TournamentScheduledEventsProjectionError.CONFLICTING_EVENT_DUPLICATE);
                }
                exactDuplicates++;
            }
        }

        Instant fromInclusive = date.atStartOfDay(zone).toInstant();
        Instant toExclusive = date.plusDays(1).atStartOfDay(zone).toInstant();
        List<ScheduledEvent> retained = new ArrayList<>();
        int otherTournament = 0;
        int outsideDate = 0;
        for (TournamentScheduledEventCandidate candidate : unique.values()) {
            if (candidate.tournament().providerTournamentId() != selection.tournamentId()) {
                otherTournament++;
                continue;
            }
            Instant startsAt = candidate.event().startsAt();
            if (startsAt.isBefore(fromInclusive) || !startsAt.isBefore(toExclusive)) {
                outsideDate++;
                continue;
            }
            retained.add(candidate.event());
        }

        CountAssessment count = assessCount(
                fromInclusive,
                toExclusive,
                zone,
                selection.timezoneEventCount(),
                retained.size());
        return new TournamentScheduledEventsProjection(
                date,
                zone,
                fromInclusive,
                toExclusive,
                retained,
                count.status(),
                count.expectedCount(),
                retained.size(),
                exactDuplicates,
                otherTournament,
                outsideDate);
    }

    private static void requireSelectedUniqueTournament(
            J3TournamentCatalogOption selection,
            TournamentScheduledEventCandidate candidate) {
        if (candidate.uniqueTournament().providerTournamentId()
                != selection.uniqueTournamentId()) {
            throw rejected(
                    TournamentScheduledEventsProjectionError.UNIQUE_TOURNAMENT_ID_MISMATCH);
        }
    }

    private static boolean sameNormalizedIdentityAndContent(
            TournamentScheduledEventCandidate left,
            TournamentScheduledEventCandidate right) {
        ScheduledEvent leftEvent = left.event();
        ScheduledEvent rightEvent = right.event();
        return leftEvent.providerEventId() == rightEvent.providerEventId()
                && leftEvent.startsAt().equals(rightEvent.startsAt())
                && leftEvent.homeTeam().equals(rightEvent.homeTeam())
                && leftEvent.awayTeam().equals(rightEvent.awayTeam())
                && leftEvent.status().equals(rightEvent.status())
                && left.tournament().providerTournamentId()
                        == right.tournament().providerTournamentId()
                && left.uniqueTournament().providerTournamentId()
                        == right.uniqueTournament().providerTournamentId();
    }

    private static CountAssessment assessCount(
            Instant fromInclusive,
            Instant toExclusive,
            ZoneId zone,
            Map<Integer, Integer> counts,
            int actualCount) {
        ZoneOffsetTransition transition = zone.getRules()
                .nextTransition(fromInclusive.minusNanos(1));
        if (transition != null
                && !transition.getInstant().isBefore(fromInclusive)
                && transition.getInstant().isBefore(toExclusive)) {
            return new CountAssessment(
                    TournamentEventCountStatus.COUNT_NOT_VERIFIABLE_DST_TRANSITION,
                    OptionalInt.empty());
        }
        int offsetSeconds = zone.getRules().getOffset(fromInclusive).getTotalSeconds();
        Integer expected = counts.get(offsetSeconds);
        if (expected == null) {
            return new CountAssessment(
                    TournamentEventCountStatus.COUNT_NOT_VERIFIABLE,
                    OptionalInt.empty());
        }
        return new CountAssessment(
                expected == actualCount
                        ? TournamentEventCountStatus.COUNT_VERIFIED
                        : TournamentEventCountStatus.COUNT_MISMATCH,
                OptionalInt.of(expected));
    }

    private static TournamentScheduledEventsProjectionException rejected(
            TournamentScheduledEventsProjectionError error) {
        return new TournamentScheduledEventsProjectionException(error);
    }

    private record CountAssessment(
            TournamentEventCountStatus status,
            OptionalInt expectedCount) {
    }
}
