package com.bettingproject.sofascorelocal.application.history;

import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservationView;
import com.bettingproject.sofascorelocal.domain.event.EventSourceKind;
import com.bettingproject.sofascorelocal.domain.event.EventSourceTrace;
import com.bettingproject.sofascorelocal.domain.eventdata.J5EventDataObservationView;
import com.bettingproject.sofascorelocal.domain.eventdetails.EventDetailObservationView;
import com.bettingproject.sofascorelocal.domain.history.J6ComparedVersion;
import com.bettingproject.sofascorelocal.domain.history.J6CompletenessSummary;
import com.bettingproject.sofascorelocal.domain.history.J6HistoryClassification;
import com.bettingproject.sofascorelocal.domain.history.J6HistoryComparison;
import com.bettingproject.sofascorelocal.domain.history.J6HistoryPage;
import com.bettingproject.sofascorelocal.domain.history.J6HistoryStream;
import com.bettingproject.sofascorelocal.domain.history.J6HistoryVersion;
import com.bettingproject.sofascorelocal.domain.history.J6Score;
import com.bettingproject.sofascorelocal.domain.history.J6SemanticChange;
import com.bettingproject.sofascorelocal.domain.history.J6SnapshotTrace;
import com.bettingproject.sofascorelocal.domain.history.J6VersionSignature;
import com.bettingproject.sofascorelocal.port.CanonicalEventStore;
import com.bettingproject.sofascorelocal.port.EventDetailsStore;
import com.bettingproject.sofascorelocal.port.J5EventDataStore;
import com.bettingproject.sofascorelocal.port.J6SnapshotHistoryStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

@Service
public class J6HistoryQueryService {

    public static final int DEFAULT_PAGE_SIZE = 25;
    public static final int MAXIMUM_PAGE_SIZE = 100;

    private static final Comparator<LoadedVersion> CHRONOLOGICAL =
            Comparator.comparing((LoadedVersion value) -> value.source().receivedAt())
                    .thenComparingLong(LoadedVersion::observationId);

    private static final Comparator<J6HistoryVersion> NEWEST_FIRST =
            Comparator.comparing((J6HistoryVersion value) -> value.source().receivedAt())
                    .reversed()
                    .thenComparing(
                            J6HistoryVersion::observationId,
                            Comparator.reverseOrder())
                    .thenComparing(J6HistoryVersion::stream);

    private final CanonicalEventStore canonicalEventStore;
    private final EventDetailsStore eventDetailsStore;
    private final J5EventDataStore eventDataStore;
    private final J6SnapshotHistoryStore snapshotHistoryStore;
    private final J6SemanticDiffService diffService;
    private final J6HistoryClassifier classifier;

    public J6HistoryQueryService(
            CanonicalEventStore canonicalEventStore,
            EventDetailsStore eventDetailsStore,
            J5EventDataStore eventDataStore,
            J6SnapshotHistoryStore snapshotHistoryStore,
            J6SemanticDiffService diffService,
            J6HistoryClassifier classifier) {
        this.canonicalEventStore = Objects.requireNonNull(
                canonicalEventStore,
                "canonicalEventStore");
        this.eventDetailsStore = Objects.requireNonNull(
                eventDetailsStore,
                "eventDetailsStore");
        this.eventDataStore = Objects.requireNonNull(eventDataStore, "eventDataStore");
        this.snapshotHistoryStore = Objects.requireNonNull(
                snapshotHistoryStore,
                "snapshotHistoryStore");
        this.diffService = Objects.requireNonNull(diffService, "diffService");
        this.classifier = Objects.requireNonNull(classifier, "classifier");
    }

    public Optional<J6HistoryPage> findHistory(
            UUID canonicalEventId,
            Optional<J6HistoryStream> selectedStream,
            int page,
            int size) {
        Objects.requireNonNull(canonicalEventId, "canonicalEventId");
        selectedStream = Objects.requireNonNull(selectedStream, "selectedStream");
        requirePage(page, size);
        Optional<CanonicalEventObservationView> current =
                canonicalEventStore.findLatestByCanonicalId(canonicalEventId);
        if (current.isEmpty()) {
            return Optional.empty();
        }

        List<CanonicalEventObservationView> stateHistory = chronologicalStates(
                canonicalEventStore.findHistory(canonicalEventId));
        List<LoadedVersion> loaded = new ArrayList<>();
        if (selectedStream.isPresent()) {
            loaded.addAll(loadStream(
                    canonicalEventId,
                    selectedStream.orElseThrow(),
                    stateHistory));
        }
        else {
            for (J6HistoryStream stream : J6HistoryStream.values()) {
                loaded.addAll(loadStream(canonicalEventId, stream, stateHistory));
            }
        }

        Map<Long, J6SnapshotTrace> traces = traces(loaded);
        Map<J6HistoryStream, List<LoadedVersion>> byStream =
                new EnumMap<>(J6HistoryStream.class);
        for (LoadedVersion version : loaded) {
            byStream.computeIfAbsent(version.stream(), ignored -> new ArrayList<>())
                    .add(version);
        }
        List<J6HistoryVersion> versions = new ArrayList<>();
        for (Map.Entry<J6HistoryStream, List<LoadedVersion>> entry : byStream.entrySet()) {
            List<LoadedVersion> streamVersions = entry.getValue();
            streamVersions.sort(CHRONOLOGICAL);
            LoadedVersion previous = null;
            for (LoadedVersion version : streamVersions) {
                List<J6SemanticChange> changes = previous == null
                        ? List.of()
                        : compareValues(previous, version);
                J6HistoryClassification classification = classifier.classify(
                        previous == null
                                ? Optional.empty()
                                : Optional.of(previous.signature()),
                        version.signature(),
                        previous != null && terminalBefore(previous, stateHistory),
                        changes);
                Optional<J6SnapshotTrace> trace = trace(version, traces);
                versions.add(new J6HistoryVersion(
                        version.stream(),
                        version.observationId(),
                        version.source(),
                        version.normalizedSha256(),
                        version.completeness(),
                        version.score(),
                        classification,
                        J6HistoryVersion.classifications(classification, trace),
                        previous == null
                                ? OptionalLong.empty()
                                : OptionalLong.of(previous.observationId()),
                        changes,
                        trace));
                previous = version;
            }
        }
        versions.sort(NEWEST_FIRST);
        long totalVersions = versions.size();
        int totalPages = totalVersions == 0
                ? 0
                : Math.toIntExact((totalVersions + size - 1) / size);
        if (totalPages > 0 && page >= totalPages) {
            throw new IllegalArgumentException("page exceeds the available J6 history");
        }
        if (totalPages == 0 && page > 0) {
            throw new IllegalArgumentException("page exceeds the empty J6 history");
        }
        int fromIndex = Math.min(Math.multiplyExact(page, size), versions.size());
        int toIndex = Math.min(fromIndex + size, versions.size());
        return Optional.of(new J6HistoryPage(
                current.orElseThrow(),
                selectedStream,
                page,
                size,
                totalVersions,
                totalPages,
                versions.subList(fromIndex, toIndex)));
    }

    public Optional<J6HistoryComparison> compare(
            UUID canonicalEventId,
            J6HistoryStream stream,
            long fromObservationId,
            long toObservationId) {
        Objects.requireNonNull(canonicalEventId, "canonicalEventId");
        Objects.requireNonNull(stream, "stream");
        if (fromObservationId < 1 || toObservationId < 1) {
            throw new IllegalArgumentException("comparison observation ids must be positive");
        }
        if (fromObservationId == toObservationId) {
            throw new IllegalArgumentException("comparison observations must be distinct");
        }
        if (canonicalEventStore.findLatestByCanonicalId(canonicalEventId).isEmpty()) {
            return Optional.empty();
        }
        Optional<LoadedVersion> before = findVersion(
                canonicalEventId,
                stream,
                fromObservationId);
        Optional<LoadedVersion> after = findVersion(
                canonicalEventId,
                stream,
                toObservationId);
        if (before.isEmpty() || after.isEmpty()) {
            return Optional.empty();
        }
        LoadedVersion oldVersion = before.orElseThrow();
        LoadedVersion newVersion = after.orElseThrow();
        if (CHRONOLOGICAL.compare(oldVersion, newVersion) >= 0) {
            throw new IllegalArgumentException(
                    "fromObservationId must precede toObservationId chronologically");
        }
        List<CanonicalEventObservationView> stateHistory = chronologicalStates(
                canonicalEventStore.findHistory(canonicalEventId));
        List<J6SemanticChange> changes = compareValues(oldVersion, newVersion);
        J6HistoryClassification classification = classifier.classify(
                Optional.of(oldVersion.signature()),
                newVersion.signature(),
                terminalBefore(oldVersion, stateHistory),
                changes);
        Map<Long, J6SnapshotTrace> traces = traces(List.of(oldVersion, newVersion));
        return Optional.of(new J6HistoryComparison(
                stream,
                comparedVersion(oldVersion, traces),
                comparedVersion(newVersion, traces),
                classification,
                changes));
    }

    private List<LoadedVersion> loadStream(
            UUID canonicalEventId,
            J6HistoryStream stream,
            List<CanonicalEventObservationView> stateHistory) {
        return switch (stream) {
            case EVENT_STATE -> stateHistory.stream().map(LoadedVersion::state).toList();
            case EVENT_DETAILS -> eventDetailsStore.findHistory(canonicalEventId).stream()
                    .map(LoadedVersion::details)
                    .toList();
            case EVENT_STATISTICS, EVENT_INCIDENTS, EVENT_LINEUPS ->
                    eventDataStore.findHistory(
                                    canonicalEventId,
                                    stream.endpointType().orElseThrow())
                            .stream()
                            .map(value -> LoadedVersion.eventData(stream, value, diffService))
                            .toList();
        };
    }

    private Optional<LoadedVersion> findVersion(
            UUID canonicalEventId,
            J6HistoryStream stream,
            long observationId) {
        return switch (stream) {
            case EVENT_STATE -> canonicalEventStore.findByObservationId(
                            canonicalEventId,
                            observationId)
                    .map(LoadedVersion::state);
            case EVENT_DETAILS -> eventDetailsStore.findByObservationId(
                            canonicalEventId,
                            observationId)
                    .map(LoadedVersion::details);
            case EVENT_STATISTICS, EVENT_INCIDENTS, EVENT_LINEUPS ->
                    eventDataStore.findByObservationId(
                                    canonicalEventId,
                                    stream.endpointType().orElseThrow(),
                                    observationId)
                            .map(value -> LoadedVersion.eventData(stream, value, diffService));
        };
    }

    private List<J6SemanticChange> compareValues(
            LoadedVersion before,
            LoadedVersion after) {
        if (before.stream() != after.stream()) {
            throw new IllegalArgumentException("history versions must share a stream");
        }
        return switch (before.value()) {
            case CanonicalEventObservationView oldValue -> diffService.compareState(
                    oldValue,
                    (CanonicalEventObservationView) after.value());
            case EventDetailObservationView oldValue -> diffService.compareDetails(
                    oldValue,
                    (EventDetailObservationView) after.value());
            case J5EventDataObservationView oldValue -> diffService.compareEventData(
                    oldValue,
                    (J5EventDataObservationView) after.value());
            default -> throw new IllegalStateException("unsupported J6 history version");
        };
    }

    private boolean terminalBefore(
            LoadedVersion before,
            List<CanonicalEventObservationView> stateHistory) {
        return switch (before.value()) {
            case CanonicalEventObservationView value ->
                    classifier.isTerminalStatus(value.status().type());
            case EventDetailObservationView value ->
                    classifier.isTerminalStatus(value.details().status().type());
            case J5EventDataObservationView ignored -> stateHistory.stream()
                    .filter(state -> !state.source().receivedAt()
                            .isAfter(before.source().receivedAt()))
                    .max(Comparator.comparing(
                                    (CanonicalEventObservationView state) ->
                                            state.source().receivedAt())
                            .thenComparingLong(
                                    CanonicalEventObservationView::observationId))
                    .map(state -> classifier.isTerminalStatus(state.status().type()))
                    .orElse(false);
            default -> false;
        };
    }

    private Map<Long, J6SnapshotTrace> traces(List<LoadedVersion> versions) {
        Set<Long> snapshotIds = versions.stream()
                .map(LoadedVersion::source)
                .filter(source -> source.kind() == EventSourceKind.PROVIDER_SNAPSHOT)
                .map(source -> source.snapshotId().orElseThrow())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return snapshotHistoryStore.findTraces(snapshotIds);
    }

    private static Optional<J6SnapshotTrace> trace(
            LoadedVersion version,
            Map<Long, J6SnapshotTrace> traces) {
        if (version.source().kind() == EventSourceKind.SYNTHETIC_FIXTURE) {
            return Optional.empty();
        }
        long snapshotId = version.source().snapshotId().orElseThrow();
        J6SnapshotTrace trace = traces.get(snapshotId);
        if (trace == null) {
            throw new IllegalStateException(
                    "provider history version has no J6 snapshot occurrence trace");
        }
        return Optional.of(trace);
    }

    private static J6ComparedVersion comparedVersion(
            LoadedVersion version,
            Map<Long, J6SnapshotTrace> traces) {
        return new J6ComparedVersion(
                version.observationId(),
                version.source(),
                version.normalizedSha256(),
                version.completeness(),
                version.score(),
                trace(version, traces));
    }

    private static List<CanonicalEventObservationView> chronologicalStates(
            List<CanonicalEventObservationView> states) {
        return states.stream()
                .sorted(Comparator.comparing(
                                (CanonicalEventObservationView value) ->
                                        value.source().receivedAt())
                        .thenComparingLong(CanonicalEventObservationView::observationId))
                .toList();
    }

    private static void requirePage(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page cannot be negative");
        }
        if (size < 1 || size > MAXIMUM_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }
    }

    private record LoadedVersion(
            J6HistoryStream stream,
            long observationId,
            EventSourceTrace source,
            String normalizedSha256,
            Optional<J6CompletenessSummary> completeness,
            Optional<J6Score> score,
            Object value) {

        private LoadedVersion {
            stream = Objects.requireNonNull(stream, "stream");
            if (observationId < 1) {
                throw new IllegalArgumentException("observationId must be positive");
            }
            source = Objects.requireNonNull(source, "source");
            normalizedSha256 = Objects.requireNonNull(normalizedSha256, "normalizedSha256");
            completeness = Objects.requireNonNull(completeness, "completeness");
            score = Objects.requireNonNull(score, "score");
            value = Objects.requireNonNull(value, "value");
        }

        private static LoadedVersion state(CanonicalEventObservationView value) {
            return new LoadedVersion(
                    J6HistoryStream.EVENT_STATE,
                    value.observationId(),
                    value.source(),
                    value.normalizedSha256(),
                    Optional.empty(),
                    Optional.empty(),
                    value);
        }

        private static LoadedVersion details(EventDetailObservationView value) {
            return new LoadedVersion(
                    J6HistoryStream.EVENT_DETAILS,
                    value.observationId(),
                    value.source(),
                    value.normalizedSha256(),
                    Optional.empty(),
                    Optional.empty(),
                    value);
        }

        private static LoadedVersion eventData(
                J6HistoryStream stream,
                J5EventDataObservationView value,
                J6SemanticDiffService diffService) {
            return new LoadedVersion(
                    stream,
                    value.observationId(),
                    value.source(),
                    value.normalizedSha256(),
                    Optional.of(J6CompletenessSummary.from(value.completeness())),
                    diffService.score(value),
                    value);
        }

        private J6VersionSignature signature() {
            return new J6VersionSignature(source, normalizedSha256);
        }
    }
}
