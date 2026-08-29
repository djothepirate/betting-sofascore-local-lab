package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsParseStatus;
import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsV1Parser;
import com.bettingproject.sofascorelocal.domain.provider.J3CircuitReason;
import com.bettingproject.sofascorelocal.domain.provider.J3CircuitState;
import com.bettingproject.sofascorelocal.domain.provider.J3ManualCallIntentState;
import com.bettingproject.sofascorelocal.domain.provider.J3MinimizedCollectionEvidence;
import com.bettingproject.sofascorelocal.domain.provider.J3MinimizedPageEvidence;
import com.bettingproject.sofascorelocal.domain.provider.J3PageResolutionSource;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotAcquisitionMode;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotInspectionSource;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotInspectionSummary;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;
import com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsTransportResponse;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.domain.scheduledevents.J3TournamentCatalog;
import com.bettingproject.sofascorelocal.domain.scheduledevents.J3TournamentCatalogOption;
import com.bettingproject.sofascorelocal.domain.scheduledevents.J3TournamentCatalogStatus;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEventsPage;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTournamentAvailability;
import com.bettingproject.sofascorelocal.port.RawSnapshotInspectionStore;
import com.bettingproject.sofascorelocal.security.Sha256;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneRules;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Rebuilds an actionable tournament catalogue exclusively from the exact raw
 * snapshots referenced by the latest terminal J3 collection evidence.
 */
@Service
public class J3TournamentCatalogService {

    /** PostgreSQL stores timestamptz values with microsecond resolution. */
    private static final Duration PERSISTED_TIMESTAMP_RESOLUTION = Duration.ofNanos(1_000);
    private static final ZoneId CATALOG_ZONE =
            TournamentScheduledEventsProjectionService.DEFAULT_ZONE;

    private final J3ManualCollectionEvidenceService evidenceService;
    private final RawSnapshotInspectionStore snapshotStore;
    private final ScheduledEventsV1Parser parser;

    @Autowired
    public J3TournamentCatalogService(
            J3ManualCollectionEvidenceService evidenceService,
            RawSnapshotInspectionStore snapshotStore) {
        this(evidenceService, snapshotStore, new ScheduledEventsV1Parser());
    }

    J3TournamentCatalogService(
            J3ManualCollectionEvidenceService evidenceService,
            RawSnapshotInspectionStore snapshotStore,
            ScheduledEventsV1Parser parser) {
        this.evidenceService = Objects.requireNonNull(evidenceService, "evidenceService");
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore");
        this.parser = Objects.requireNonNull(parser, "parser");
    }

    public J3TournamentCatalog latest() {
        Optional<J3ManualCollectionEvidenceService.EvidenceDocument> document =
                evidenceService.latestDocument();
        if (document.isEmpty()) {
            return unavailable(J3TournamentCatalogStatus.NO_COLLECTION_EVIDENCE, Optional.empty());
        }

        J3MinimizedCollectionEvidence evidence = document.orElseThrow().evidence();
        Optional<LocalDate> collectionDate = Optional.of(evidence.collectionDate());
        if (evidence.terminalState() != J3ManualCallIntentState.COMPLETED) {
            return unavailable(
                    J3TournamentCatalogStatus.COLLECTION_NOT_COMPLETED,
                    collectionDate);
        }
        if (!isCoherentCompletedEvidence(evidence)) {
            return unavailable(
                    J3TournamentCatalogStatus.COLLECTION_EVIDENCE_INVALID,
                    collectionDate);
        }

        List<Long> pageSnapshotIds = evidence.pageAttempts().stream()
                .map(J3MinimizedPageEvidence::snapshotId)
                .toList();
        Map<Long, CandidateAccumulator> candidates = new LinkedHashMap<>();

        for (int index = 0; index < evidence.pageAttempts().size(); index++) {
            J3MinimizedPageEvidence attempt = evidence.pageAttempts().get(index);
            long snapshotId = attempt.snapshotId();
            Optional<RawSnapshotInspectionSource> selected = snapshotStore.findById(snapshotId);
            if (selected.isEmpty()) {
                return unavailable(
                        J3TournamentCatalogStatus.SNAPSHOT_NOT_FOUND,
                        collectionDate);
            }

            RawSnapshotInspectionSource source = selected.orElseThrow();
            String expectedRequestKey = requestKey(evidence.collectionDate(), attempt.page());
            if (!metadataMatches(source.summary(), attempt, expectedRequestKey)) {
                return unavailable(
                        J3TournamentCatalogStatus.SNAPSHOT_METADATA_MISMATCH,
                        collectionDate);
            }

            RawPayloadEvidence payload;
            try {
                payload = RawPayloadEvidence.capture(source.payloadRaw());
            }
            catch (IllegalArgumentException exception) {
                return unavailable(
                        J3TournamentCatalogStatus.SNAPSHOT_INTEGRITY_FAILURE,
                        collectionDate);
            }
            if (!integrityMatches(source, attempt, payload)) {
                return unavailable(
                        J3TournamentCatalogStatus.SNAPSHOT_INTEGRITY_FAILURE,
                        collectionDate);
            }

            var parseResult = parser.parseTransportResponse(new ScheduledEventsTransportResponse(
                    expectedRequestKey,
                    source.summary().receivedAt(),
                    source.summary().receivedAt(),
                    source.summary().httpStatus(),
                    source.summary().contentType(),
                    Duration.ZERO,
                    payload));
            boolean expectedHasNextPage = index < evidence.pageAttempts().size() - 1;
            if (parseResult.status() != ScheduledEventsParseStatus.PARSED
                    || parseResult.page().isEmpty()
                    || parseResult.page().orElseThrow().payloadShape()
                            != ScheduledEventsPage.PayloadShape.SCHEDULED_TOURNAMENT_LIST
                    || !parseResult.page().orElseThrow().events().isEmpty()
                    || parseResult.page().orElseThrow().hasNextPage() != expectedHasNextPage
                    || parseResult.page().orElseThrow().hasNextPage()
                            != attempt.hasNextPage()) {
                return unavailable(
                        J3TournamentCatalogStatus.SNAPSHOT_PARSE_INCOMPATIBLE,
                        collectionDate);
            }

            for (ScheduledTournamentAvailability availability
                    : parseResult.page().orElseThrow().scheduledTournaments()) {
                long tournamentId = availability.tournament().providerTournamentId();
                CandidateAccumulator existing = candidates.get(tournamentId);
                if (existing == null) {
                    candidates.put(
                            tournamentId,
                            new CandidateAccumulator(availability, snapshotId));
                }
                else if (!existing.matches(availability)) {
                    return unavailable(
                            J3TournamentCatalogStatus.TOURNAMENT_CONFLICT,
                            collectionDate);
                }
                else {
                    existing.addSource(snapshotId);
                }
            }
        }

        List<J3TournamentCatalogOption> options = new ArrayList<>();
        int excludedNonActionableCount = 0;
        Set<Integer> applicableOffsetSeconds = applicableOffsetSeconds(
                evidence.collectionDate(),
                CATALOG_ZONE);
        try {
            for (CandidateAccumulator candidate : candidates.values()) {
                ScheduledTournamentAvailability availability = candidate.availability();
                if (availability.uniqueTournament().isEmpty()
                        || availability.tournamentCategoryName().isEmpty()
                        || !isSafeDisplayName(availability.tournament().name())
                        || !isSafeDisplayName(
                                availability.tournamentCategoryName().orElseThrow())
                        || !isSafeDisplayName(
                                availability.uniqueTournament().orElseThrow().name())
                        || applicableOffsetSeconds.stream().noneMatch(
                                availability.timezoneEventCount()::containsKey)) {
                    excludedNonActionableCount++;
                    continue;
                }
                var uniqueTournament = availability.uniqueTournament().orElseThrow();
                options.add(new J3TournamentCatalogOption(
                        availability.tournament().providerTournamentId(),
                        availability.tournament().name(),
                        availability.tournamentCategoryName().orElseThrow(),
                        uniqueTournament.providerTournamentId(),
                        uniqueTournament.name(),
                        availability.timezoneEventCount(),
                        candidate.sourceSnapshotIds()));
            }
        }
        catch (IllegalArgumentException exception) {
            return unavailable(
                    J3TournamentCatalogStatus.SNAPSHOT_PARSE_INCOMPATIBLE,
                    collectionDate);
        }

        return J3TournamentCatalog.available(
                evidence.collectionDate(),
                pageSnapshotIds,
                options,
                excludedNonActionableCount);
    }

    private static boolean isSafeDisplayName(String value) {
        return !value.isBlank()
                && value.chars().noneMatch(Character::isISOControl);
    }

    /**
     * Returns every UTC offset that is actually in force during the requested
     * local civil day. A normal day has one value; a DST transition day has two.
     */
    private static Set<Integer> applicableOffsetSeconds(LocalDate date, ZoneId zone) {
        Instant fromInclusive = date.atStartOfDay(zone).toInstant();
        Instant toExclusive = date.plusDays(1).atStartOfDay(zone).toInstant();
        ZoneRules rules = zone.getRules();
        LinkedHashSet<Integer> offsets = new LinkedHashSet<>();
        offsets.add(rules.getOffset(fromInclusive).getTotalSeconds());

        Instant cursor = fromInclusive;
        ZoneOffsetTransition transition = rules.nextTransition(cursor);
        while (transition != null && transition.getInstant().isBefore(toExclusive)) {
            offsets.add(transition.getOffsetBefore().getTotalSeconds());
            offsets.add(transition.getOffsetAfter().getTotalSeconds());
            cursor = transition.getInstant().plusNanos(1);
            transition = rules.nextTransition(cursor);
        }

        offsets.add(rules.getOffset(toExclusive.minusNanos(1)).getTotalSeconds());
        return Set.copyOf(offsets);
    }

    /**
     * Resolves a submitted identity against a freshly rebuilt server-side catalogue.
     */
    public Optional<J3TournamentCatalogOption> resolve(long tournamentId) {
        if (tournamentId < 1) {
            throw new IllegalArgumentException("tournamentId must be positive");
        }
        return latest().findByTournamentId(tournamentId);
    }

    private static boolean isCoherentCompletedEvidence(
            J3MinimizedCollectionEvidence evidence) {
        List<J3MinimizedPageEvidence> attempts = evidence.pageAttempts();
        if (attempts.isEmpty()
                || evidence.initialCompletedPages() != 0
                || evidence.completedPages() != attempts.size()
                || evidence.failedPage() != null
                || !"NONE".equals(evidence.terminalCode())
                || !evidence.globalStopActive()
                || evidence.finalCircuitState() != J3CircuitState.LOCKED
                || evidence.finalCircuitReason()
                        != J3CircuitReason.MANUAL_COLLECTION_TERMINAL_LOCK) {
            return false;
        }

        HashSet<Long> snapshotIds = new HashSet<>();
        for (int index = 0; index < attempts.size(); index++) {
            J3MinimizedPageEvidence attempt = attempts.get(index);
            boolean expectedHasNextPage = index < attempts.size() - 1;
            if (attempt.page() != index + 1
                    || !attempt.snapshotRecorded()
                    || attempt.snapshotId() == null
                    || !snapshotIds.add(attempt.snapshotId())
                    || attempt.httpStatus() == null
                    || attempt.httpStatus() < 200
                    || attempt.httpStatus() >= 300
                    || attempt.schemaStatus() != RawSnapshotSchemaStatus.PARSED
                    || attempt.payloadSizeBytes() == null
                    || attempt.payloadSha256() == null
                    || attempt.receivedAt() == null
                    || attempt.hasNextPage() == null
                    || attempt.hasNextPage() != expectedHasNextPage
                    || attempt.terminalCode() != null) {
                return false;
            }
        }
        return true;
    }

    private static boolean metadataMatches(
            RawSnapshotInspectionSummary summary,
            J3MinimizedPageEvidence attempt,
            String expectedRequestKey) {
        return summary.snapshotId() == attempt.snapshotId()
                && summary.acquisitionMode()
                        == expectedAcquisitionMode(attempt.resolutionSource())
                && summary.logicalEndpoint().equals(SofascoreEndpointType.SCHEDULED_EVENTS.name())
                && summary.requestKey().equals(expectedRequestKey)
                && samePersistedTimestamp(summary.receivedAt(), attempt.receivedAt())
                && summary.httpStatus().equals(attempt.httpStatus())
                && summary.httpStatus() >= 200
                && summary.httpStatus() < 300
                && summary.parserVersion().equals(ScheduledEventsV1Parser.PARSER_VERSION)
                && summary.schemaStatus() == RawSnapshotSchemaStatus.PARSED;
    }

    private static RawSnapshotAcquisitionMode expectedAcquisitionMode(
            J3PageResolutionSource resolutionSource) {
        return resolutionSource == J3PageResolutionSource.LOCAL_JSON_IMPORT
                ? RawSnapshotAcquisitionMode.MANUAL_LOCAL_JSON_IMPORT
                : RawSnapshotAcquisitionMode.DIRECT_LOCAL_ENDPOINT;
    }

    private static boolean samePersistedTimestamp(Instant persisted, Instant inMemory) {
        return Duration.between(persisted, inMemory).abs()
                .compareTo(PERSISTED_TIMESTAMP_RESOLUTION) < 0;
    }

    private static boolean integrityMatches(
            RawSnapshotInspectionSource source,
            J3MinimizedPageEvidence attempt,
            RawPayloadEvidence payload) {
        byte[] raw = source.payloadRaw();
        return source.summary().payloadSizeBytes() == raw.length
                && source.summary().payloadSizeBytes() == attempt.payloadSizeBytes()
                && payload.sizeBytes() == raw.length
                && source.summary().payloadSha256().equals(attempt.payloadSha256())
                && source.summary().payloadSha256().equals(payload.sha256())
                && source.summary().payloadSha256().equals(Sha256.hex(raw));
    }

    private static String requestKey(LocalDate date, int page) {
        return SofascoreEndpointType.SCHEDULED_EVENTS.name()
                + "|date=" + date + "|page=" + page;
    }

    private static J3TournamentCatalog unavailable(
            J3TournamentCatalogStatus status,
            Optional<LocalDate> collectionDate) {
        return J3TournamentCatalog.unavailable(status, collectionDate);
    }

    private static final class CandidateAccumulator {

        private final ScheduledTournamentAvailability availability;
        private final LinkedHashSet<Long> sourceSnapshotIds = new LinkedHashSet<>();

        private CandidateAccumulator(
                ScheduledTournamentAvailability availability,
                long sourceSnapshotId) {
            this.availability = Objects.requireNonNull(availability, "availability");
            addSource(sourceSnapshotId);
        }

        private ScheduledTournamentAvailability availability() {
            return availability;
        }

        private boolean matches(ScheduledTournamentAvailability candidate) {
            return availability.equals(candidate);
        }

        private void addSource(long snapshotId) {
            if (snapshotId < 1) {
                throw new IllegalArgumentException("snapshotId must be positive");
            }
            sourceSnapshotIds.add(snapshotId);
        }

        private List<Long> sourceSnapshotIds() {
            return List.copyOf(sourceSnapshotIds);
        }
    }
}
