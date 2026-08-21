package com.bettingproject.sofascorelocal.domain.scheduledevents;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable result of rebuilding the catalogue from one coherent J3 collection.
 */
public record J3TournamentCatalog(
        J3TournamentCatalogStatus status,
        Optional<LocalDate> collectionDate,
        List<Long> pageSnapshotIds,
        List<J3TournamentCatalogOption> options,
        int excludedNonActionableCount) {

    public J3TournamentCatalog {
        status = Objects.requireNonNull(status, "status");
        collectionDate = Objects.requireNonNull(collectionDate, "collectionDate");
        pageSnapshotIds = List.copyOf(
                Objects.requireNonNull(pageSnapshotIds, "pageSnapshotIds"));
        options = List.copyOf(Objects.requireNonNull(options, "options"));

        if (excludedNonActionableCount < 0) {
            throw new IllegalArgumentException(
                    "excludedNonActionableCount must be non-negative");
        }
        if (status == J3TournamentCatalogStatus.AVAILABLE) {
            if (collectionDate.isEmpty() || pageSnapshotIds.isEmpty()) {
                throw new IllegalArgumentException(
                        "an available catalogue requires a date and source pages");
            }
            if (pageSnapshotIds.stream().anyMatch(id -> id == null || id < 1)
                    || new HashSet<>(pageSnapshotIds).size() != pageSnapshotIds.size()) {
                throw new IllegalArgumentException(
                        "pageSnapshotIds must contain distinct positive identities");
            }
            if (options.stream().map(J3TournamentCatalogOption::tournamentId).distinct().count()
                    != options.size()) {
                throw new IllegalArgumentException(
                        "catalogue options must have distinct tournament identities");
            }
        }
        else if (!pageSnapshotIds.isEmpty()
                || !options.isEmpty()
                || excludedNonActionableCount != 0) {
            throw new IllegalArgumentException(
                    "an unavailable catalogue cannot expose selections or source pages");
        }
    }

    public static J3TournamentCatalog available(
            LocalDate collectionDate,
            List<Long> pageSnapshotIds,
            List<J3TournamentCatalogOption> options,
            int excludedNonActionableCount) {
        return new J3TournamentCatalog(
                J3TournamentCatalogStatus.AVAILABLE,
                Optional.of(Objects.requireNonNull(collectionDate, "collectionDate")),
                pageSnapshotIds,
                options,
                excludedNonActionableCount);
    }

    public static J3TournamentCatalog unavailable(
            J3TournamentCatalogStatus status,
            Optional<LocalDate> collectionDate) {
        Objects.requireNonNull(status, "status");
        if (status == J3TournamentCatalogStatus.AVAILABLE) {
            throw new IllegalArgumentException("AVAILABLE requires catalogue content");
        }
        return new J3TournamentCatalog(
                status,
                collectionDate,
                List.of(),
                List.of(),
                0);
    }

    public boolean available() {
        return status == J3TournamentCatalogStatus.AVAILABLE;
    }

    public Optional<J3TournamentCatalogOption> findByTournamentId(long tournamentId) {
        if (tournamentId < 1) {
            throw new IllegalArgumentException("tournamentId must be positive");
        }
        if (!available()) {
            return Optional.empty();
        }
        return options.stream()
                .filter(option -> option.tournamentId() == tournamentId)
                .findFirst();
    }
}
