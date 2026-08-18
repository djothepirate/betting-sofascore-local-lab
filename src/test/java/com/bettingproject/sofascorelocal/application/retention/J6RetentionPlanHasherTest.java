package com.bettingproject.sofascorelocal.application.retention;

import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;
import com.bettingproject.sofascorelocal.domain.retention.J6RetentionCandidate;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class J6RetentionPlanHasherTest {

    @Test
    void hashesTheExactOrderedCandidateEvidence() {
        Instant cutoff = Instant.parse("2026-07-19T12:00:00Z");
        J6RetentionCandidate first = candidate(1, 'a');
        J6RetentionCandidate second = candidate(2, 'b');

        String hash = J6RetentionPlanHasher.calculate(30, cutoff, List.of(first, second));

        assertThat(hash).matches("[0-9a-f]{64}");
        assertThat(J6RetentionPlanHasher.calculate(30, cutoff, List.of(first, second)))
                .isEqualTo(hash);
        assertThat(J6RetentionPlanHasher.calculate(30, cutoff, List.of(second, first)))
                .isNotEqualTo(hash);
        assertThat(J6RetentionPlanHasher.calculate(
                30,
                cutoff.plusSeconds(1),
                List.of(first, second))).isNotEqualTo(hash);
    }

    private static J6RetentionCandidate candidate(long id, char hash) {
        return new J6RetentionCandidate(
                id,
                Instant.parse("2026-06-01T12:00:00Z").plusSeconds(id),
                "SCHEDULED_EVENTS",
                RawSnapshotSchemaStatus.PARSED,
                id * 10,
                Character.toString(hash).repeat(64));
    }
}
