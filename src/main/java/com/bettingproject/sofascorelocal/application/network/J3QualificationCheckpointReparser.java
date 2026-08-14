package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsParseStatus;
import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsV1Parser;
import com.bettingproject.sofascorelocal.domain.provider.J3StoredQualificationPage;
import com.bettingproject.sofascorelocal.port.J3QualificationCheckpointStore;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Reparses local qualification snapshots without transport or persistence mutations.
 * Historical classifications remain evidence and are returned separately.
 */
@Component
public final class J3QualificationCheckpointReparser {

    private final J3QualificationCheckpointStore checkpointStore;
    private final ScheduledEventsV1Parser parser;

    @Autowired
    public J3QualificationCheckpointReparser(
            J3QualificationCheckpointStore checkpointStore) {
        this(checkpointStore, new ScheduledEventsV1Parser());
    }

    J3QualificationCheckpointReparser(
            J3QualificationCheckpointStore checkpointStore,
            ScheduledEventsV1Parser parser) {
        this.checkpointStore = Objects.requireNonNull(checkpointStore, "checkpointStore");
        this.parser = Objects.requireNonNull(parser, "parser");
    }

    public List<J3CheckpointReparseResult> reparseStoredPages(LocalDate qualificationDate) {
        return checkpointStore.findStoredPages(qualificationDate).stream()
                .sorted(Comparator.comparingInt(J3StoredQualificationPage::page))
                .map(this::reparse)
                .toList();
    }

    private J3CheckpointReparseResult reparse(J3StoredQualificationPage checkpoint) {
        var parsing = parser.parseTransportResponse(checkpoint.toTransportResponse());
        if (parsing.status() != ScheduledEventsParseStatus.PARSED) {
            return new J3CheckpointReparseResult(
                    checkpoint.snapshotId(),
                    checkpoint.page(),
                    checkpoint.historicalSchemaStatus(),
                    parsing.status(),
                    null,
                    0);
        }
        var page = parsing.page().orElseThrow();
        return new J3CheckpointReparseResult(
                checkpoint.snapshotId(),
                checkpoint.page(),
                checkpoint.historicalSchemaStatus(),
                parsing.status(),
                page.hasNextPage(),
                page.scheduledTournaments().size());
    }
}
