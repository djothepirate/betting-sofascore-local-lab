package com.bettingproject.sofascorelocal.application.history;

import com.bettingproject.sofascorelocal.domain.event.EventSourceTrace;
import com.bettingproject.sofascorelocal.domain.history.J6ChangeKind;
import com.bettingproject.sofascorelocal.domain.history.J6HistoryClassification;
import com.bettingproject.sofascorelocal.domain.history.J6SemanticChange;
import com.bettingproject.sofascorelocal.domain.history.J6VersionSignature;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class J6HistoryClassifierTest {

    private static final J6HistoryClassifier CLASSIFIER = new J6HistoryClassifier();

    @Test
    void appliesTheClassificationPriorityWithoutConflatingRawAndSemanticChanges() {
        J6VersionSignature first = signature(provider(1, 'a', "parser-v1"), 'b');

        assertThat(CLASSIFIER.classify(Optional.empty(), first, false, List.of()))
                .isEqualTo(J6HistoryClassification.BASELINE);
        assertThat(CLASSIFIER.classify(
                Optional.of(first),
                signature(provider(1, 'a', "parser-v1"), 'b'),
                false,
                List.of()))
                .isEqualTo(J6HistoryClassification.TECHNICAL_DUPLICATE);
        assertThat(CLASSIFIER.classify(
                Optional.of(first),
                signature(provider(1, 'a', "parser-v2"), 'c'),
                false,
                List.of(changed())))
                .isEqualTo(J6HistoryClassification.LOCAL_REPARSE);
        assertThat(CLASSIFIER.classify(
                Optional.of(first),
                signature(provider(2, 'c', "parser-v1"), 'b'),
                false,
                List.of()))
                .isEqualTo(J6HistoryClassification.SEMANTICALLY_UNCHANGED);
        assertThat(CLASSIFIER.classify(
                Optional.of(first),
                signature(fixture('d'), 'e'),
                false,
                List.of(changed())))
                .isEqualTo(J6HistoryClassification.SYNTHETIC_CHANGE);
        assertThat(CLASSIFIER.classify(
                Optional.of(first),
                signature(provider(2, 'd', "parser-v1"), 'e'),
                false,
                List.of(changed())))
                .isEqualTo(J6HistoryClassification.PROVIDER_UPDATE);
    }

    @Test
    void separatesLateEnrichmentFromReplacementOrRemoval() {
        J6VersionSignature first = signature(provider(1, 'a', "parser-v1"), 'b');
        J6VersionSignature second = signature(provider(2, 'c', "parser-v1"), 'd');
        J6SemanticChange added = new J6SemanticChange(
                "incidents",
                Optional.empty(),
                Optional.of("goal"),
                J6ChangeKind.ADDED);
        J6SemanticChange completeness = new J6SemanticChange(
                "completeness.presentSignals",
                Optional.of("10"),
                Optional.of("11"),
                J6ChangeKind.CHANGED);

        assertThat(CLASSIFIER.classify(
                Optional.of(first),
                second,
                true,
                List.of(added, completeness)))
                .isEqualTo(J6HistoryClassification.LATE_ENRICHMENT);
        assertThat(CLASSIFIER.classify(
                Optional.of(first),
                second,
                true,
                List.of(changed())))
                .isEqualTo(J6HistoryClassification.LATE_CORRECTION);
        assertThat(CLASSIFIER.classify(
                Optional.of(first),
                second,
                true,
                List.of(new J6SemanticChange(
                        "incidents",
                        Optional.of("card"),
                        Optional.empty(),
                        J6ChangeKind.REMOVED))))
                .isEqualTo(J6HistoryClassification.LATE_CORRECTION);
    }

    @Test
    void recognizesOnlyTheExplicitTerminalVocabularyCaseInsensitively() {
        assertThat(List.of("finished", "FINISHED", "Canceled", "cancelled", "abandoned", "walkover"))
                .allSatisfy(value -> assertThat(CLASSIFIER.isTerminalStatus(value)).isTrue());
        assertThat(CLASSIFIER.isTerminalStatus("inprogress")).isFalse();
        assertThat(CLASSIFIER.isTerminalStatus("unknown")).isFalse();
        assertThat(CLASSIFIER.isTerminalStatus(null)).isFalse();
    }

    private static J6SemanticChange changed() {
        return new J6SemanticChange(
                "status.type",
                Optional.of("before"),
                Optional.of("after"),
                J6ChangeKind.CHANGED);
    }

    private static J6VersionSignature signature(EventSourceTrace source, char normalizedHash) {
        return new J6VersionSignature(source, hash(normalizedHash));
    }

    private static EventSourceTrace provider(long snapshotId, char payloadHash, String parser) {
        return EventSourceTrace.providerSnapshot(
                snapshotId,
                hash(payloadHash),
                parser,
                Instant.parse("2026-08-18T10:00:00Z").plusSeconds(snapshotId));
    }

    private static EventSourceTrace fixture(char payloadHash) {
        return EventSourceTrace.syntheticFixture(
                "j6-history-fixture",
                hash(payloadHash),
                "fixture-v1",
                Instant.parse("2026-08-18T10:00:00Z"));
    }

    private static String hash(char value) {
        return Character.toString(value).repeat(64);
    }
}
