package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsParseStatus;
import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsV1Parser;
import com.bettingproject.sofascorelocal.domain.provider.J3StoredQualificationPage;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;
import com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsProviderPageRequest;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

class J3QualificationCheckpointReparserTest {

    private static final LocalDate DATE = ScheduledEventsProviderPageRequest.QUALIFICATION_DATE;

    @Test
    void reparsesPagesOneAndTwoWithoutReplacingTheirHistoricalClassifications() throws Exception {
        J3StoredQualificationPage pageOne = storedPage(
                1,
                1L,
                Files.readAllBytes(Path.of(
                        "fixtures/scheduled-events/qualified-provider-shape.json")));
        J3StoredQualificationPage pageTwo = storedPage(
                2,
                2L,
                Files.readAllBytes(Path.of(
                        "fixtures/scheduled-events/qualified-page-two-shape.json")));
        var reparser = new J3QualificationCheckpointReparser(
                ignored -> List.of(pageOne, pageTwo),
                new ScheduledEventsV1Parser());

        var results = reparser.reparseStoredPages(DATE);

        assertThat(results)
                .extracting(
                        result -> result.page(),
                        result -> result.historicalSchemaStatus(),
                        result -> result.currentParseStatus(),
                        result -> result.hasNextPage(),
                        result -> result.scheduledTournamentCount())
                .containsExactly(
                        tuple(
                                1,
                                RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE,
                                ScheduledEventsParseStatus.PARSED,
                                true,
                                2),
                        tuple(
                                2,
                                RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE,
                                ScheduledEventsParseStatus.PARSED,
                                true,
                                2));
        assertThat(pageOne.historicalSchemaStatus())
                .isEqualTo(RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE);
        assertThat(pageTwo.historicalSchemaStatus())
                .isEqualTo(RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE);
    }

    private static J3StoredQualificationPage storedPage(
            int page,
            long snapshotId,
            byte[] payload) {
        Instant requestedAt = Instant.parse("2026-08-13T12:00:00Z").plusSeconds(page);
        return new J3StoredQualificationPage(
                snapshotId,
                DATE,
                page,
                SofascoreEndpointType.SCHEDULED_EVENTS.name()
                        + "|date=" + DATE + "|page=" + page,
                requestedAt,
                requestedAt.plusMillis(400),
                200,
                "application/json",
                Duration.ofMillis(400),
                RawSnapshotSchemaStatus.SCHEMA_INCOMPATIBLE,
                RawPayloadEvidence.capture(payload));
    }
}
