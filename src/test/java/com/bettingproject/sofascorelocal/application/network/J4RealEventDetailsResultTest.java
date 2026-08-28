package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class J4RealEventDetailsResultTest {

    private static final UUID REQUEST_ID = UUID.fromString(
            "90000000-0000-0000-0000-000000000014");

    @Test
    void aCompletedPhaseOneRequiresTwoExactlyAccountedDistinctTargets() {
        assertThatThrownBy(() -> new J4RealEventDetailsPhase1Result(
                REQUEST_ID,
                true,
                "COMPLETED",
                1,
                0,
                List.of(),
                List.of(unavailable(16386245L, 1L), unavailable(16421052L, 2L))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("phase-1 outcomes exceed resolved requests");

        assertThatThrownBy(() -> new J4RealEventDetailsPhase1Result(
                REQUEST_ID,
                true,
                "COMPLETED",
                2,
                0,
                List.of(),
                List.of(unavailable(16386245L, 1L), unavailable(16386245L, 2L))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("phase-1 outcomes exceed resolved requests");
    }

    @Test
    void unavailableEvidencePreservesTheExact404Status() {
        assertThatThrownBy(() -> new J4RealEventDetailsUnavailableResult(
                16386245L,
                1L,
                403,
                "a".repeat(64),
                12,
                RawSnapshotSchemaStatus.ENDPOINT_UNAVAILABLE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unavailable event result must preserve HTTP 404");
    }

    private static J4RealEventDetailsUnavailableResult unavailable(
            long eventId,
            long snapshotId) {
        return new J4RealEventDetailsUnavailableResult(
                eventId,
                snapshotId,
                404,
                "a".repeat(64),
                12,
                RawSnapshotSchemaStatus.ENDPOINT_UNAVAILABLE);
    }
}
