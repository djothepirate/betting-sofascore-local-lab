package com.bettingproject.sofascorelocal.application.benchmark;

import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkCampaignType;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkOutcomeType;
import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessStatus;
import com.bettingproject.sofascorelocal.domain.history.J6SnapshotOccurrenceOutcome;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;

public record J8DirectObservationCohorts(
        List<ObservationCohort> observations,
        List<HistoricalObservationCohort> historicalObservations) {

    public J8DirectObservationCohorts {
        observations = List.copyOf(Objects.requireNonNull(observations, "observations"));
        historicalObservations = List.copyOf(Objects.requireNonNull(
                historicalObservations, "historicalObservations"));
    }

    public J8DirectObservationCohorts(List<ObservationCohort> observations) {
        this(observations, List.of());
    }

    public record ObservationCohort(
            long unitId,
            J8BenchmarkCampaignType campaignType,
            long providerEventId,
            Optional<UUID> canonicalEventId,
            SofascoreEndpointType endpoint,
            boolean providerAttempted,
            boolean declaredInWindow,
            Optional<J8BenchmarkOutcomeType> outcomeType,
            Optional<RawSnapshotSchemaStatus> schemaStatus,
            Optional<String> parserVersion,
            Optional<J5CompletenessStatus> completenessStatus,
            OptionalInt completenessScore,
            OptionalInt presentSignals,
            OptionalInt expectedSignals,
            boolean normalizedObservationPresent,
            boolean canonicalStatePresent,
            DirectComponentState currentCanonicalState,
            DirectComponentState currentEventDetails,
            DirectComponentState currentStatistics,
            DirectComponentState currentIncidents,
            DirectComponentState currentLineups,
            Optional<J5CompletenessStatus> currentStatisticsCompleteness,
            Optional<J5CompletenessStatus> currentIncidentsCompleteness,
            Optional<J5CompletenessStatus> currentLineupsCompleteness,
            CurrentDirectEvidence currentEvidence,
            Optional<String> competition,
            Optional<String> season,
            Optional<String> eventStatus) {

        public ObservationCohort {
            if (unitId < 1 || providerEventId < 1) {
                throw new IllegalArgumentException("cohort identifiers must be positive");
            }
            campaignType = Objects.requireNonNull(campaignType, "campaignType");
            canonicalEventId = Objects.requireNonNull(canonicalEventId, "canonicalEventId");
            endpoint = Objects.requireNonNull(endpoint, "endpoint");
            outcomeType = Objects.requireNonNull(outcomeType, "outcomeType");
            schemaStatus = Objects.requireNonNull(schemaStatus, "schemaStatus");
            parserVersion = bounded(parserVersion, "parserVersion", 32);
            completenessStatus = Objects.requireNonNull(
                    completenessStatus, "completenessStatus");
            completenessScore = Objects.requireNonNull(
                    completenessScore, "completenessScore");
            if (completenessScore.isPresent()
                    && (completenessScore.orElseThrow() < 0
                            || completenessScore.orElseThrow() > 100)) {
                throw new IllegalArgumentException(
                        "completenessScore must be between 0 and 100");
            }
            presentSignals = Objects.requireNonNull(presentSignals, "presentSignals");
            expectedSignals = Objects.requireNonNull(expectedSignals, "expectedSignals");
            if (presentSignals.isPresent() != expectedSignals.isPresent()) {
                throw new IllegalArgumentException(
                        "completeness signal counts must both be present or absent");
            }
            currentCanonicalState = Objects.requireNonNull(
                    currentCanonicalState, "currentCanonicalState");
            currentEventDetails = Objects.requireNonNull(
                    currentEventDetails, "currentEventDetails");
            currentStatistics = Objects.requireNonNull(
                    currentStatistics, "currentStatistics");
            currentIncidents = Objects.requireNonNull(
                    currentIncidents, "currentIncidents");
            currentLineups = Objects.requireNonNull(currentLineups, "currentLineups");
            currentStatisticsCompleteness = Objects.requireNonNull(
                    currentStatisticsCompleteness, "currentStatisticsCompleteness");
            currentIncidentsCompleteness = Objects.requireNonNull(
                    currentIncidentsCompleteness, "currentIncidentsCompleteness");
            currentLineupsCompleteness = Objects.requireNonNull(
                    currentLineupsCompleteness, "currentLineupsCompleteness");
            currentEvidence = Objects.requireNonNull(currentEvidence, "currentEvidence");
            if (presentSignals.isPresent()) {
                int present = presentSignals.orElseThrow();
                int expected = expectedSignals.orElseThrow();
                if (present < 0 || expected < 0
                        || (expected > 0 && present > expected)) {
                    throw new IllegalArgumentException(
                            "completeness signal counts are inconsistent");
                }
            }
            competition = bounded(competition, "competition", 200);
            season = bounded(season, "season", 100);
            eventStatus = bounded(eventStatus, "eventStatus", 64);
        }
    }

    public enum DirectComponentState {
        AVAILABLE,
        UNAVAILABLE,
        INCOMPATIBLE,
        ABSENT
    }

    public record CurrentDirectEvidence(
            OptionalLong correlatedObservationId,
            OptionalLong canonicalObservationId,
            ComponentEvidence eventDetails,
            ComponentEvidence statistics,
            ComponentEvidence incidents,
            ComponentEvidence lineups) {

        public CurrentDirectEvidence {
            correlatedObservationId = positiveOptional(
                    correlatedObservationId, "correlatedObservationId");
            canonicalObservationId = positiveOptional(
                    canonicalObservationId, "canonicalObservationId");
            eventDetails = Objects.requireNonNull(eventDetails, "eventDetails");
            statistics = Objects.requireNonNull(statistics, "statistics");
            incidents = Objects.requireNonNull(incidents, "incidents");
            lineups = Objects.requireNonNull(lineups, "lineups");
        }

        public static CurrentDirectEvidence none() {
            return new CurrentDirectEvidence(
                    OptionalLong.empty(),
                    OptionalLong.empty(),
                    ComponentEvidence.none(),
                    ComponentEvidence.none(),
                    ComponentEvidence.none(),
                    ComponentEvidence.none());
        }
    }

    public record ComponentEvidence(
            OptionalLong snapshotOccurrenceId,
            OptionalLong snapshotId,
            OptionalLong normalizedObservationId) {

        public ComponentEvidence {
            snapshotOccurrenceId = positiveOptional(
                    snapshotOccurrenceId, "snapshotOccurrenceId");
            snapshotId = positiveOptional(snapshotId, "snapshotId");
            normalizedObservationId = positiveOptional(
                    normalizedObservationId, "normalizedObservationId");
        }

        public static ComponentEvidence none() {
            return new ComponentEvidence(
                    OptionalLong.empty(), OptionalLong.empty(), OptionalLong.empty());
        }
    }

    public record HistoricalObservationCohort(
            long occurrenceId,
            long snapshotId,
            OptionalLong normalizedObservationId,
            OptionalLong stateObservationId,
            OptionalLong detailObservationId,
            J6SnapshotOccurrenceOutcome persistenceOutcome,
            SofascoreEndpointType endpoint,
            Instant requestedAt,
            Optional<J5CompletenessStatus> completenessStatus,
            OptionalInt presentSignals,
            OptionalInt expectedSignals,
            boolean normalizedObservationPresent,
            Optional<String> competition,
            Optional<String> season,
            Optional<String> eventStatus) {

        public HistoricalObservationCohort {
            if (occurrenceId < 1 || snapshotId < 1) {
                throw new IllegalArgumentException(
                        "historical cohort identifiers must be positive");
            }
            normalizedObservationId = positiveOptional(
                    normalizedObservationId, "normalizedObservationId");
            stateObservationId = positiveOptional(
                    stateObservationId, "stateObservationId");
            detailObservationId = positiveOptional(
                    detailObservationId, "detailObservationId");
            persistenceOutcome = Objects.requireNonNull(
                    persistenceOutcome, "persistenceOutcome");
            endpoint = Objects.requireNonNull(endpoint, "endpoint");
            requestedAt = Objects.requireNonNull(requestedAt, "requestedAt");
            completenessStatus = Objects.requireNonNull(
                    completenessStatus, "completenessStatus");
            presentSignals = Objects.requireNonNull(presentSignals, "presentSignals");
            expectedSignals = Objects.requireNonNull(expectedSignals, "expectedSignals");
            if (presentSignals.isPresent() != expectedSignals.isPresent()) {
                throw new IllegalArgumentException(
                        "historical completeness signal counts must both be present or absent");
            }
            if (presentSignals.isPresent()) {
                int present = presentSignals.orElseThrow();
                int expected = expectedSignals.orElseThrow();
                if (present < 0 || expected < 0
                        || (expected > 0 && present > expected)) {
                    throw new IllegalArgumentException(
                            "historical completeness signal counts are inconsistent");
                }
            }
            competition = bounded(competition, "competition", 200);
            season = bounded(season, "season", 100);
            eventStatus = bounded(eventStatus, "eventStatus", 64);
        }
    }

    private static Optional<String> bounded(
            Optional<String> value,
            String name,
            int maximumLength) {
        Objects.requireNonNull(value, name);
        return value.map(text -> {
            String normalized = text.trim();
            if (normalized.isEmpty()
                    || normalized.length() > maximumLength
                    || normalized.chars().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException(name + " is not bounded safe text");
            }
            return normalized;
        });
    }

    private static OptionalLong positiveOptional(OptionalLong value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isPresent() && value.orElseThrow() < 1) {
            throw new IllegalArgumentException(name + " must be positive when present");
        }
        return value;
    }
}
