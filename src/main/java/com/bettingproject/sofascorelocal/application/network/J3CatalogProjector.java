package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsParseStatus;
import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsV1Parser;
import com.bettingproject.sofascorelocal.domain.provider.*;
import com.bettingproject.sofascorelocal.domain.scheduledevents.*;
import com.bettingproject.sofascorelocal.domain.scheduledevents.J3CollectionData.*;
import com.bettingproject.sofascorelocal.port.RawSnapshotInspectionStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/** One projection algorithm for historical evidence, imports and direct/automatic collections. */
@Service
public final class J3CatalogProjector {
    private final RawSnapshotInspectionStore snapshots;
    private final ScheduledEventsV1Parser parser;
    @Autowired public J3CatalogProjector(RawSnapshotInspectionStore snapshots) { this(snapshots, new ScheduledEventsV1Parser()); }
    J3CatalogProjector(RawSnapshotInspectionStore snapshots, ScheduledEventsV1Parser parser) {
        this.snapshots=Objects.requireNonNull(snapshots); this.parser=Objects.requireNonNull(parser);
    }
    public Projection project(LocalDate date, List<J3MinimizedPageEvidence> pages) {
        if (!J3CollectionData.complete(pages)) return unavailable(date,J3TournamentCatalogStatus.COLLECTION_EVIDENCE_INVALID);
        var candidates = new LinkedHashMap<Long,ScheduledTournamentAvailability>();
        var sources = new HashMap<Long,LinkedHashSet<Long>>();
        for (var evidence : pages) {
            var selected = evidence.occurrenceId()==null ? snapshots.findById(evidence.snapshotId())
                    : snapshots.findOccurrence(evidence.snapshotId(),evidence.occurrenceId());
            if (selected.isEmpty()) return unavailable(date,J3TournamentCatalogStatus.SNAPSHOT_NOT_FOUND);
            var source = selected.orElseThrow(); var s = source.summary();
            String key = "SCHEDULED_EVENTS|date="+date+"|page="+evidence.page();
            var mode = evidence.resolutionSource() == J3PageResolutionSource.LOCAL_JSON_IMPORT
                    ? RawSnapshotAcquisitionMode.MANUAL_LOCAL_JSON_IMPORT : RawSnapshotAcquisitionMode.DIRECT_LOCAL_ENDPOINT;
            if (s.snapshotId()!=evidence.snapshotId() || s.acquisitionMode()!=mode
                    || !"SCHEDULED_EVENTS".equals(s.logicalEndpoint()) || !key.equals(s.requestKey())
                    || s.receivedAt()==null || Duration.between(s.receivedAt(),evidence.receivedAt()).abs().compareTo(Duration.ofNanos(1000))>=0
                    || !Objects.equals(s.httpStatus(),evidence.httpStatus())
                    || !ScheduledEventsV1Parser.PARSER_VERSION.equals(s.parserVersion()) || s.schemaStatus()!=RawSnapshotSchemaStatus.PARSED)
                return unavailable(date,J3TournamentCatalogStatus.SNAPSHOT_METADATA_MISMATCH);
            RawPayloadEvidence payload;
            try { payload=RawPayloadEvidence.capture(source.payloadRaw()); }
            catch (IllegalArgumentException failure) { return unavailable(date,J3TournamentCatalogStatus.SNAPSHOT_INTEGRITY_FAILURE); }
            if (s.payloadSizeBytes()!=evidence.payloadSizeBytes() || s.payloadSizeBytes()!=payload.sizeBytes()
                    || !s.payloadSha256().equals(evidence.payloadSha256()) || !s.payloadSha256().equals(payload.sha256()))
                return unavailable(date,J3TournamentCatalogStatus.SNAPSHOT_INTEGRITY_FAILURE);
            var parsed=parser.parseTransportResponse(new ScheduledEventsTransportResponse(key,s.receivedAt(),s.receivedAt(),
                    s.httpStatus(),s.contentType(),Duration.ZERO,payload));
            if (parsed.status()!=ScheduledEventsParseStatus.PARSED || parsed.page().isEmpty()
                    || parsed.page().orElseThrow().payloadShape()!=ScheduledEventsPage.PayloadShape.SCHEDULED_TOURNAMENT_LIST
                    || !parsed.page().orElseThrow().events().isEmpty()
                    || parsed.page().orElseThrow().hasNextPage()!=evidence.hasNextPage())
                return unavailable(date,J3TournamentCatalogStatus.SNAPSHOT_PARSE_INCOMPATIBLE);
            for (var candidate : parsed.page().orElseThrow().scheduledTournaments()) {
                long id=candidate.tournament().providerTournamentId();
                var previous=candidates.putIfAbsent(id,candidate);
                if (previous!=null && !previous.equals(candidate)) return unavailable(date,J3TournamentCatalogStatus.TOURNAMENT_CONFLICT);
                sources.computeIfAbsent(id,ignored->new LinkedHashSet<>()).add(evidence.snapshotId());
            }
        }
        var offsets=offsets(date); var entries=new ArrayList<Entry>();
        try {
            for (var candidate : candidates.values()) {
                long id=candidate.tournament().providerTournamentId();
                String category=candidate.tournamentCategoryName().orElse(null);
                Long uniqueId=candidate.uniqueTournament().map(it->it.providerTournamentId()).orElse(null);
                String uniqueName=candidate.uniqueTournament().map(it->it.name()).orElse(null);
                String excluded=uniqueId==null ? "UNIQUE_TOURNAMENT_ABSENT" : !safe(category) ? "CATEGORY_ABSENT"
                        : !safe(uniqueName) || !safe(candidate.tournament().name()) ? "DISPLAY_NAME_INVALID"
                        : offsets.stream().noneMatch(candidate.timezoneEventCount()::containsKey) ? "DATE_OFFSET_ABSENT" : null;
                entries.add(new Entry(id,candidate.tournament().name(),category,uniqueId,uniqueName,
                        candidate.timezoneEventCount(),List.copyOf(sources.get(id)),excluded));
            }
            var options=entries.stream().filter(Entry::eligible).map(Entry::option).toList();
            return new Projection(J3TournamentCatalog.available(date,pages.stream().map(J3MinimizedPageEvidence::snapshotId).toList(),
                    options,entries.size()-options.size()),entries);
        } catch (IllegalArgumentException failure) { return unavailable(date,J3TournamentCatalogStatus.SNAPSHOT_PARSE_INCOMPATIBLE); }
    }
    private static Projection unavailable(LocalDate date,J3TournamentCatalogStatus status) {
        return new Projection(J3TournamentCatalog.unavailable(status,Optional.of(date)),List.of());
    }
    private static boolean safe(String value) { return value!=null && !value.isBlank() && value.chars().noneMatch(Character::isISOControl); }
    static Set<Integer> offsets(LocalDate date) {
        var zone=ZoneId.of("Europe/Paris"); var rules=zone.getRules();
        Instant start=date.atStartOfDay(zone).toInstant(),end=date.plusDays(1).atStartOfDay(zone).toInstant();
        Set<Integer> result=new LinkedHashSet<>(); result.add(rules.getOffset(start).getTotalSeconds());
        for(var t=rules.nextTransition(start);t!=null && t.getInstant().isBefore(end);t=rules.nextTransition(t.getInstant().plusNanos(1))) {
            result.add(t.getOffsetBefore().getTotalSeconds()); result.add(t.getOffsetAfter().getTotalSeconds());
        }
        result.add(rules.getOffset(end.minusNanos(1)).getTotalSeconds()); return Set.copyOf(result);
    }
}
