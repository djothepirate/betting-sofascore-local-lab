package com.bettingproject.sofascorelocal.domain.scheduledevents;

import com.bettingproject.sofascorelocal.domain.provider.J3MinimizedPageEvidence;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Durable J3 V7 results. The trigger is independent of each page's acquisition source. */
public final class J3CollectionData {
    private J3CollectionData() { }

    public enum Trigger {
        MANUAL_PROVIDER, MANUAL_IMPORT, DAILY, DAILY_AT, SCHEDULED, LEGACY;
        public boolean automatic() { return this == DAILY || this == DAILY_AT || this == SCHEDULED; }
    }
    public enum State { RUNNING, COMPLETED, FAILED, CANCELLED, INTERRUPTED }

    public record Proof(UUID runId, LocalDate date, Trigger trigger, Instant startedAt,
                        Instant finishedAt, State state, String terminalCode,
                        List<J3MinimizedPageEvidence> pages) {
        public Proof {
            Objects.requireNonNull(runId); Objects.requireNonNull(date); Objects.requireNonNull(trigger);
            Objects.requireNonNull(startedAt); Objects.requireNonNull(finishedAt); Objects.requireNonNull(state);
            pages = List.copyOf(pages);
            if (state == State.RUNNING || finishedAt.isBefore(startedAt) || pages.size() > 35
                    || terminalCode == null || !terminalCode.matches("[A-Z0-9_]{1,96}"))
                throw new IllegalArgumentException("Invalid terminal J3 proof");
            for (int i = 0; i < pages.size(); i++)
                if (pages.get(i).page() != i + 1) throw new IllegalArgumentException("Non-contiguous J3 pages");
            if (state == State.COMPLETED && (!"NONE".equals(terminalCode) || !complete(pages)))
                throw new IllegalArgumentException("Incomplete J3 success");
            if (state != State.COMPLETED && "NONE".equals(terminalCode))
                throw new IllegalArgumentException("Missing J3 terminal reason");
        }
        public boolean successful() { return state == State.COMPLETED; }
    }

    public static boolean complete(List<J3MinimizedPageEvidence> pages) {
        if (pages.isEmpty() || pages.size() > 35) return false;
        for (int i = 0; i < pages.size(); i++) {
            var p = pages.get(i);
            if (p.page() != i + 1 || !p.snapshotRecorded() || p.schemaStatus() != RawSnapshotSchemaStatus.PARSED
                    || p.httpStatus() < 200 || p.httpStatus() >= 300 || p.terminalCode() != null
                    || !Boolean.valueOf(i < pages.size() - 1).equals(p.hasNextPage())) return false;
        }
        return pages.stream().map(J3MinimizedPageEvidence::snapshotId).distinct().count() == pages.size();
    }

    public record Entry(long tournamentId, String name, String category, Long uniqueTournamentId,
                        String uniqueTournamentName, Map<Integer, Integer> timezoneEventCount,
                        List<Long> sourceSnapshotIds, String exclusionReason) {
        public Entry {
            if (tournamentId < 1 || name == null || (exclusionReason == null && (name.isBlank()
                    || name.chars().anyMatch(Character::isISOControl)))) throw new IllegalArgumentException("Invalid tournament");
            timezoneEventCount = Map.copyOf(timezoneEventCount); sourceSnapshotIds = List.copyOf(sourceSnapshotIds);
            if (sourceSnapshotIds.isEmpty() || sourceSnapshotIds.stream().anyMatch(id -> id == null || id < 1)
                    || sourceSnapshotIds.stream().distinct().count() != sourceSnapshotIds.size())
                throw new IllegalArgumentException("Invalid tournament sources");
            if (exclusionReason != null && !exclusionReason.matches("[A-Z0-9_]{1,96}"))
                throw new IllegalArgumentException("Invalid exclusion reason");
        }
        public boolean eligible() { return exclusionReason == null; }
        public J3TournamentCatalogOption option() {
            if (!eligible()) throw new IllegalStateException("Tournament is not eligible");
            return new J3TournamentCatalogOption(tournamentId, name, category, uniqueTournamentId,
                    uniqueTournamentName, timezoneEventCount, sourceSnapshotIds);
        }
    }

    public record Projection(J3TournamentCatalog catalog, List<Entry> entries) {
        public Projection { Objects.requireNonNull(catalog); entries = List.copyOf(entries); }
    }
    public record Collection(Proof proof, List<Entry> entries) {
        public Collection { Objects.requireNonNull(proof); entries = List.copyOf(entries); }
        public UUID id() { return proof.runId(); }
        public LocalDate date() { return proof.date(); }
        public J3TournamentCatalog catalog() {
            if (!proof.successful()) return J3TournamentCatalog.unavailable(
                    J3TournamentCatalogStatus.COLLECTION_NOT_COMPLETED, java.util.Optional.of(date()));
            var options = entries.stream().filter(Entry::eligible).map(Entry::option).toList();
            return J3TournamentCatalog.available(date(), proof.pages().stream().map(J3MinimizedPageEvidence::snapshotId).toList(),
                    options, entries.size() - options.size());
        }
    }
    public record Legacy(UUID campaignId, LocalDate date, Instant startedAt, Instant finishedAt,
                         int completedPages, int maximumPages, List<LegacyPage> pages) {
        public Legacy { pages = List.copyOf(pages); }
    }
    public record LegacyPage(int page, String requestKey, String source, String outcome, Long snapshotId,
                             String parserVersion, Instant resolvedAt, Long occurrenceId,
                             Instant requestedAt, Instant receivedAt, Long latencyMillis, String persistenceOutcome) { }
    public record DateSummary(LocalDate date, UUID runId, Instant completedAt, String recoveryStatus) { }
    public record CatalogPage(UUID runId,LocalDate date,Instant completedAt,int page,int pageSize,long total,List<Entry> entries) {
        public CatalogPage {entries=List.copyOf(entries);}
        public long pageCount() {return Math.max(1,(total+pageSize-1)/pageSize);}
    }
}
