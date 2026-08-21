package com.bettingproject.sofascorelocal.domain.scheduledevents;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * One server-derived J3 selection option. Provider names are display metadata;
 * only the positive numeric identities may be used for later resolution.
 */
public record J3TournamentCatalogOption(
        long tournamentId,
        String tournamentName,
        String tournamentCategoryName,
        long uniqueTournamentId,
        String uniqueTournamentName,
        Map<Integer, Integer> timezoneEventCount,
        List<Long> sourceSnapshotIds) {

    public J3TournamentCatalogOption {
        if (tournamentId < 1 || uniqueTournamentId < 1) {
            throw new IllegalArgumentException("tournament identities must be positive");
        }
        tournamentName = requireDisplayName(tournamentName, "tournamentName");
        tournamentCategoryName = requireDisplayName(
                tournamentCategoryName,
                "tournamentCategoryName");
        uniqueTournamentName = requireDisplayName(
                uniqueTournamentName,
                "uniqueTournamentName");
        Objects.requireNonNull(timezoneEventCount, "timezoneEventCount");
        timezoneEventCount = Map.copyOf(new TreeMap<>(timezoneEventCount));
        if (timezoneEventCount.entrySet().stream().anyMatch(entry ->
                entry.getKey() == null || entry.getValue() == null || entry.getValue() < 0)) {
            throw new IllegalArgumentException(
                    "timezoneEventCount must contain non-negative counts");
        }

        sourceSnapshotIds = List.copyOf(
                Objects.requireNonNull(sourceSnapshotIds, "sourceSnapshotIds"));
        if (sourceSnapshotIds.isEmpty()
                || sourceSnapshotIds.stream().anyMatch(id -> id == null || id < 1)
                || new HashSet<>(sourceSnapshotIds).size() != sourceSnapshotIds.size()) {
            throw new IllegalArgumentException(
                    "sourceSnapshotIds must contain distinct positive identities");
        }
    }

    public String displayLabel() {
        return tournamentName + " - " + tournamentCategoryName;
    }

    private static String requireDisplayName(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank() || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(fieldName + " must be safe non-blank text");
        }
        return value;
    }
}
