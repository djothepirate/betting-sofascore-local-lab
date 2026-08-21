package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.application.event.TournamentDiscoveredEventView;
import com.bettingproject.sofascorelocal.domain.provider.TournamentEventDiscoverySource;
import com.bettingproject.sofascorelocal.domain.scheduledevents.TournamentEventCountStatus;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record TournamentEventDiscoveryResult(
        UUID requestId,
        boolean completed,
        String terminalCode,
        int providerCallAttempts,
        boolean cacheHit,
        TournamentEventDiscoverySource source,
        long snapshotId,
        String payloadSha256,
        long payloadSizeBytes,
        TournamentEventCountStatus countStatus,
        Integer expectedCount,
        int actualCount,
        int exactDuplicateCount,
        int excludedOtherTournamentCount,
        int excludedOutsideDateCount,
        int insertedObservations,
        int deduplicatedObservations,
        int parserWarningCount,
        List<TournamentDiscoveredEventView> events) {

    public TournamentEventDiscoveryResult {
        Objects.requireNonNull(requestId, "requestId");
        terminalCode = requireSafeCode(terminalCode);
        Objects.requireNonNull(source, "source");
        events = List.copyOf(Objects.requireNonNull(events, "events"));
        if (providerCallAttempts < 0
                || providerCallAttempts > 1
                || snapshotId < 0
                || payloadSizeBytes < 0
                || actualCount < 0
                || exactDuplicateCount < 0
                || excludedOtherTournamentCount < 0
                || excludedOutsideDateCount < 0
                || insertedObservations < 0
                || deduplicatedObservations < 0
                || parserWarningCount < 0) {
            throw new IllegalArgumentException("discovery result counts are outside bounds");
        }
        if (snapshotId == 0 && (payloadSha256 != null || payloadSizeBytes != 0)) {
            throw new IllegalArgumentException("payload evidence requires a snapshot identity");
        }
        if (source == TournamentEventDiscoverySource.CACHE
                && (!cacheHit || providerCallAttempts != 0)) {
            throw new IllegalArgumentException("cache source requires one cache hit and no call");
        }
        if (source == TournamentEventDiscoverySource.LOCAL_JSON_IMPORT
                && (cacheHit || providerCallAttempts != 0)) {
            throw new IllegalArgumentException("local import cannot use provider transport or cache");
        }
        if (source == TournamentEventDiscoverySource.PROVIDER && cacheHit) {
            throw new IllegalArgumentException("provider source cannot be a cache hit");
        }
        if (snapshotId > 0 && (payloadSha256 == null || payloadSha256.isBlank())) {
            throw new IllegalArgumentException("snapshot evidence requires a payload hash");
        }
        if (completed) {
            if (!"COMPLETED".equals(terminalCode)
                    || snapshotId < 1
                    || countStatus == null
                    || countStatus == TournamentEventCountStatus.COUNT_MISMATCH
                    || insertedObservations + deduplicatedObservations != events.size()
                    || actualCount != events.size()) {
                throw new IllegalArgumentException("completed discovery result is inconsistent");
            }
        }
        else if (!events.isEmpty() || insertedObservations != 0 || deduplicatedObservations != 0) {
            throw new IllegalArgumentException("failed discovery cannot expose canonical events");
        }
        if ((countStatus == TournamentEventCountStatus.COUNT_VERIFIED
                || countStatus == TournamentEventCountStatus.COUNT_MISMATCH)
                != (expectedCount != null)) {
            throw new IllegalArgumentException("expected count is inconsistent with its status");
        }
    }

    public TournamentEventDiscoveryResult(
            UUID requestId,
            boolean completed,
            String terminalCode,
            int providerCallAttempts,
            boolean cacheHit,
            long snapshotId,
            String payloadSha256,
            long payloadSizeBytes,
            TournamentEventCountStatus countStatus,
            Integer expectedCount,
            int actualCount,
            int exactDuplicateCount,
            int excludedOtherTournamentCount,
            int excludedOutsideDateCount,
            int insertedObservations,
            int deduplicatedObservations,
            int parserWarningCount,
            List<TournamentDiscoveredEventView> events) {
        this(
                requestId,
                completed,
                terminalCode,
                providerCallAttempts,
                cacheHit,
                cacheHit
                        ? TournamentEventDiscoverySource.CACHE
                        : TournamentEventDiscoverySource.PROVIDER,
                snapshotId,
                payloadSha256,
                payloadSizeBytes,
                countStatus,
                expectedCount,
                actualCount,
                exactDuplicateCount,
                excludedOtherTournamentCount,
                excludedOutsideDateCount,
                insertedObservations,
                deduplicatedObservations,
                parserWarningCount,
                events);
    }

    private static String requireSafeCode(String value) {
        String normalized = Objects.requireNonNull(value, "terminalCode").trim();
        if (normalized.isEmpty()
                || normalized.length() > 96
                || !normalized.matches("[A-Z0-9_]+")) {
            throw new IllegalArgumentException("terminalCode must be a bounded safe identifier");
        }
        return normalized;
    }
}
