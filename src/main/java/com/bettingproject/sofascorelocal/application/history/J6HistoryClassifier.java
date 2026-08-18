package com.bettingproject.sofascorelocal.application.history;

import com.bettingproject.sofascorelocal.domain.event.EventSourceKind;
import com.bettingproject.sofascorelocal.domain.event.EventSourceTrace;
import com.bettingproject.sofascorelocal.domain.history.J6ChangeKind;
import com.bettingproject.sofascorelocal.domain.history.J6HistoryClassification;
import com.bettingproject.sofascorelocal.domain.history.J6SemanticChange;
import com.bettingproject.sofascorelocal.domain.history.J6VersionSignature;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
public class J6HistoryClassifier {

    private static final Set<String> TERMINAL_STATUSES = Set.of(
            "finished",
            "canceled",
            "cancelled",
            "abandoned",
            "walkover");

    public J6HistoryClassification classify(
            Optional<J6VersionSignature> before,
            J6VersionSignature after,
            boolean terminalBefore,
            List<J6SemanticChange> changes) {
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
        changes = List.copyOf(Objects.requireNonNull(changes, "changes"));
        if (before.isEmpty()) {
            return J6HistoryClassification.BASELINE;
        }
        J6VersionSignature previous = before.orElseThrow();
        EventSourceTrace oldSource = previous.source();
        EventSourceTrace newSource = after.source();

        if (sameProviderSnapshot(oldSource, newSource)
                && oldSource.parserVersion().equals(newSource.parserVersion())
                && previous.normalizedSha256().equals(after.normalizedSha256())) {
            return J6HistoryClassification.TECHNICAL_DUPLICATE;
        }
        if (oldSource.payloadSha256().equals(newSource.payloadSha256())
                && (!oldSource.parserVersion().equals(newSource.parserVersion())
                        || !previous.normalizedSha256().equals(after.normalizedSha256()))) {
            return J6HistoryClassification.LOCAL_REPARSE;
        }
        if (!oldSource.payloadSha256().equals(newSource.payloadSha256())
                && previous.normalizedSha256().equals(after.normalizedSha256())) {
            return J6HistoryClassification.SEMANTICALLY_UNCHANGED;
        }
        if (newSource.kind() == EventSourceKind.SYNTHETIC_FIXTURE) {
            return J6HistoryClassification.SYNTHETIC_CHANGE;
        }
        if (!terminalBefore) {
            return J6HistoryClassification.PROVIDER_UPDATE;
        }
        return isAdditionsOnly(changes)
                ? J6HistoryClassification.LATE_ENRICHMENT
                : J6HistoryClassification.LATE_CORRECTION;
    }

    public boolean isTerminalStatus(String statusType) {
        if (statusType == null) {
            return false;
        }
        return TERMINAL_STATUSES.contains(statusType.trim().toLowerCase(Locale.ROOT));
    }

    private static boolean sameProviderSnapshot(
            EventSourceTrace before,
            EventSourceTrace after) {
        return before.kind() == EventSourceKind.PROVIDER_SNAPSHOT
                && after.kind() == EventSourceKind.PROVIDER_SNAPSHOT
                && before.snapshotId().isPresent()
                && after.snapshotId().isPresent()
                && before.snapshotId().getAsLong() == after.snapshotId().getAsLong();
    }

    private static boolean isAdditionsOnly(List<J6SemanticChange> changes) {
        List<J6SemanticChange> substantive = changes.stream()
                .filter(change -> !change.field().startsWith("completeness."))
                .filter(change -> !change.field().startsWith("score."))
                .toList();
        return !substantive.isEmpty()
                && substantive.stream().allMatch(change -> change.kind() == J6ChangeKind.ADDED);
    }
}
