package com.bettingproject.sofascorelocal.domain.provider;

import org.junit.jupiter.api.Test;

import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RawSnapshotPersistenceResultTest {

    private static final String SHA256 = "a".repeat(64);

    @Test
    void requiresAnOccurrenceForEveryInsertOrDeduplication() {
        var inserted = new RawSnapshotPersistenceResult(
                1L,
                RawSnapshotPersistenceOutcome.INSERTED,
                SHA256,
                2,
                OptionalLong.of(11L));
        var deduplicated = new RawSnapshotPersistenceResult(
                1L,
                RawSnapshotPersistenceOutcome.DEDUPLICATED,
                SHA256,
                2,
                OptionalLong.of(12L));

        assertThat(inserted.occurrenceId()).hasValue(11L);
        assertThat(deduplicated.occurrenceId()).hasValue(12L);
        assertThatThrownBy(() -> new RawSnapshotPersistenceResult(
                1L,
                RawSnapshotPersistenceOutcome.INSERTED,
                SHA256,
                2,
                OptionalLong.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("require their occurrenceId");
    }

    @Test
    void cacheHitsCannotClaimANewOccurrence() {
        var cacheHit = new RawSnapshotPersistenceResult(
                1L,
                RawSnapshotPersistenceOutcome.CACHE_HIT,
                SHA256,
                2,
                OptionalLong.empty());

        assertThat(cacheHit.occurrenceId()).isEmpty();
        assertThatThrownBy(() -> new RawSnapshotPersistenceResult(
                1L,
                RawSnapshotPersistenceOutcome.CACHE_HIT,
                SHA256,
                2,
                OptionalLong.of(11L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cache hit cannot create an occurrence");
    }
}
