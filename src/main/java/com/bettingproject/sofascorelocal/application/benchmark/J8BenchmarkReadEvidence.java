package com.bettingproject.sofascorelocal.application.benchmark;

import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkCampaignTerminalState;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkCampaignType;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkExecutionMode;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkOutcomeType;
import com.bettingproject.sofascorelocal.domain.benchmark.J8BenchmarkResolutionSource;
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

public record J8BenchmarkReadEvidence(
        List<CampaignEvidence> campaigns,
        List<UnitEvidence> units,
        List<LegacyResponseEvidence> legacyResponses,
        long manualImportOccurrenceCount,
        long excludedSyntheticObservationCount) {

    public J8BenchmarkReadEvidence {
        campaigns = List.copyOf(Objects.requireNonNull(campaigns, "campaigns"));
        units = List.copyOf(Objects.requireNonNull(units, "units"));
        legacyResponses = List.copyOf(Objects.requireNonNull(
                legacyResponses, "legacyResponses"));
        if (manualImportOccurrenceCount < 0 || excludedSyntheticObservationCount < 0) {
            throw new IllegalArgumentException(
                    "excluded evidence counts cannot be negative");
        }
    }

    public record CampaignEvidence(
            UUID campaignId,
            J8BenchmarkCampaignType campaignType,
            J8BenchmarkExecutionMode executionMode,
            Instant startedAt,
            int maximumUnits,
            Optional<Instant> finishedAt,
            Optional<J8BenchmarkCampaignTerminalState> terminalState,
            OptionalInt completedUnits) {

        public CampaignEvidence {
            campaignId = Objects.requireNonNull(campaignId, "campaignId");
            campaignType = Objects.requireNonNull(campaignType, "campaignType");
            executionMode = Objects.requireNonNull(executionMode, "executionMode");
            startedAt = Objects.requireNonNull(startedAt, "startedAt");
            if (!campaignType.acceptsStoredMaximumUnits(maximumUnits)) {
                throw new IllegalArgumentException("campaign maximum is inconsistent");
            }
            finishedAt = Objects.requireNonNull(finishedAt, "finishedAt");
            terminalState = Objects.requireNonNull(terminalState, "terminalState");
            completedUnits = Objects.requireNonNull(completedUnits, "completedUnits");
            if (finishedAt.isPresent() != terminalState.isPresent()
                    || finishedAt.isPresent() != completedUnits.isPresent()) {
                throw new IllegalArgumentException("campaign result fields are inconsistent");
            }
            if (finishedAt.isPresent()
                    && finishedAt.orElseThrow().isBefore(startedAt)) {
                throw new IllegalArgumentException("campaign finish cannot predate its start");
            }
        }
    }

    public record UnitEvidence(
            UUID campaignId,
            J8BenchmarkCampaignType campaignType,
            J8BenchmarkExecutionMode executionMode,
            long unitId,
            int unitOrdinal,
            SofascoreEndpointType endpoint,
            Optional<UUID> canonicalEventId,
            OptionalLong providerEventId,
            Instant declaredAt,
            OptionalLong attemptId,
            Optional<Instant> attemptStartedAt,
            Optional<Instant> resolvedAt,
            Optional<J8BenchmarkResolutionSource> resolutionSource,
            Optional<J8BenchmarkOutcomeType> outcomeType,
            boolean responseReceived,
            OptionalInt httpStatus,
            OptionalLong latencyMillis,
            OptionalLong snapshotId,
            OptionalLong snapshotOccurrenceId,
            boolean deduplicatedResponse,
            Optional<String> parserVersion,
            Optional<RawSnapshotSchemaStatus> schemaStatus,
            int parserWarningCount,
            Optional<J5CompletenessStatus> completenessStatus,
            OptionalInt completenessScore) {

        public UnitEvidence {
            campaignId = Objects.requireNonNull(campaignId, "campaignId");
            campaignType = Objects.requireNonNull(campaignType, "campaignType");
            executionMode = Objects.requireNonNull(executionMode, "executionMode");
            if (unitId < 1 || unitOrdinal < 1 || parserWarningCount < 0) {
                throw new IllegalArgumentException("unit evidence identifiers are invalid");
            }
            endpoint = Objects.requireNonNull(endpoint, "endpoint");
            canonicalEventId = Objects.requireNonNull(canonicalEventId, "canonicalEventId");
            providerEventId = Objects.requireNonNull(providerEventId, "providerEventId");
            declaredAt = Objects.requireNonNull(declaredAt, "declaredAt");
            attemptId = Objects.requireNonNull(attemptId, "attemptId");
            attemptStartedAt = Objects.requireNonNull(
                    attemptStartedAt, "attemptStartedAt");
            if (attemptId.isPresent() != attemptStartedAt.isPresent()) {
                throw new IllegalArgumentException("attempt fields are inconsistent");
            }
            resolvedAt = Objects.requireNonNull(resolvedAt, "resolvedAt");
            resolutionSource = Objects.requireNonNull(
                    resolutionSource, "resolutionSource");
            outcomeType = Objects.requireNonNull(outcomeType, "outcomeType");
            if (resolvedAt.isPresent() != resolutionSource.isPresent()
                    || resolvedAt.isPresent() != outcomeType.isPresent()) {
                throw new IllegalArgumentException("unit result fields are inconsistent");
            }
            httpStatus = Objects.requireNonNull(httpStatus, "httpStatus");
            latencyMillis = Objects.requireNonNull(latencyMillis, "latencyMillis");
            snapshotId = Objects.requireNonNull(snapshotId, "snapshotId");
            snapshotOccurrenceId = Objects.requireNonNull(
                    snapshotOccurrenceId, "snapshotOccurrenceId");
            parserVersion = boundedOptional(parserVersion, "parserVersion", 32);
            schemaStatus = Objects.requireNonNull(schemaStatus, "schemaStatus");
            completenessStatus = Objects.requireNonNull(
                    completenessStatus, "completenessStatus");
            completenessScore = Objects.requireNonNull(
                    completenessScore, "completenessScore");
        }
    }

    public record LegacyResponseEvidence(
            long occurrenceId,
            long snapshotId,
            List<Long> normalizedObservationIds,
            SofascoreEndpointType endpoint,
            Instant requestedAt,
            Optional<Instant> receivedAt,
            OptionalInt httpStatus,
            OptionalLong latencyMillis,
            Optional<String> parserVersion,
            Optional<RawSnapshotSchemaStatus> schemaStatus,
            J6SnapshotOccurrenceOutcome persistenceOutcome) {

        public LegacyResponseEvidence {
            if (occurrenceId < 1 || snapshotId < 1) {
                throw new IllegalArgumentException(
                        "legacy response identifiers must be positive");
            }
            normalizedObservationIds = List.copyOf(Objects.requireNonNull(
                    normalizedObservationIds, "normalizedObservationIds"));
            if (normalizedObservationIds.stream().anyMatch(id -> id == null || id < 1)
                    || normalizedObservationIds.stream().distinct().count()
                            != normalizedObservationIds.size()) {
                throw new IllegalArgumentException(
                        "normalized observation ids must be positive and distinct");
            }
            normalizedObservationIds = normalizedObservationIds.stream().sorted().toList();
            endpoint = Objects.requireNonNull(endpoint, "endpoint");
            requestedAt = Objects.requireNonNull(requestedAt, "requestedAt");
            receivedAt = Objects.requireNonNull(receivedAt, "receivedAt");
            httpStatus = Objects.requireNonNull(httpStatus, "httpStatus");
            latencyMillis = Objects.requireNonNull(latencyMillis, "latencyMillis");
            parserVersion = boundedOptional(parserVersion, "parserVersion", 32);
            schemaStatus = Objects.requireNonNull(schemaStatus, "schemaStatus");
            if (schemaStatus.filter(status -> status != RawSnapshotSchemaStatus.PARSED
                    && status != RawSnapshotSchemaStatus.ENDPOINT_UNAVAILABLE).isPresent()) {
                throw new IllegalArgumentException(
                        "legacy schema evidence must be append-only derived");
            }
            if ((schemaStatus.orElse(null) == RawSnapshotSchemaStatus.PARSED)
                    != !normalizedObservationIds.isEmpty()) {
                throw new IllegalArgumentException(
                        "legacy PARSED evidence requires normalized observation ids");
            }
            persistenceOutcome = Objects.requireNonNull(
                    persistenceOutcome, "persistenceOutcome");
        }
    }

    private static Optional<String> boundedOptional(
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
}
