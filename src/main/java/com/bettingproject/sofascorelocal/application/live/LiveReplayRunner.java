package com.bettingproject.sofascorelocal.application.live;

import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderResponse;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.live.LiveCadence;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceOutcome;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotPersistenceResult;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.security.Sha256;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * Deterministic synthetic replay of the production schedule and pure normalization path.
 * No bean, transport, persistence, wall clock, sleeping or process launch is used here.
 * Synthetic snapshot/occurrence numbers below are local trace positions, never database IDs.
 */
public final class LiveReplayRunner {
    public static final String SOURCE = "SYNTHETIC_REPLAY";
    private final LiveResponseProcessor processor;

    public LiveReplayRunner(LiveResponseProcessor processor) { this.processor = Objects.requireNonNull(processor); }

    public record Reply(long providerEventId, SofascoreEndpointType endpoint, int httpStatus,
                        String contentType, String body, String bodySha256, Duration elapsed) {
        public Reply {
            CanonicalEventIdentity.sofascore(providerEventId);
            if (!LiveProviderSession.ENDPOINTS.contains(endpoint) || httpStatus < 100 || httpStatus > 599
                    || elapsed == null || elapsed.isNegative()) throw new IllegalArgumentException("invalid replay reply");
            Objects.requireNonNull(contentType); Objects.requireNonNull(body);
            if (!sha(body).equals(bodySha256)) throw new IllegalArgumentException("REPLAY_PAYLOAD_HASH_MISMATCH");
            if (body.getBytes(StandardCharsets.UTF_8).length > RawPayloadEvidence.MAXIMUM_BYTES)
                throw new IllegalArgumentException("REPLAY_PAYLOAD_TOO_LARGE");
        }
    }

    /** A null event ID means an explicit operator stop of the whole replay campaign. */
    public record OperatorStop(Instant at, UUID eventId) {
        public OperatorStop { Objects.requireNonNull(at); }
    }

    public record ReplayInput(String fixtureId, Instant startsAt, Duration duration,
                              List<Long> providerEventIds, List<Reply> replies, List<OperatorStop> operatorStops,
                              int maximumCallsPerEvent, int maximumCalls, long maximumBytes) {
        public ReplayInput {
            if (fixtureId == null || !fixtureId.matches("[a-z0-9]+(?:-[a-z0-9]+)*"))
                throw new IllegalArgumentException("invalid replay fixture identifier");
            Objects.requireNonNull(startsAt); Objects.requireNonNull(duration);
            providerEventIds = List.copyOf(providerEventIds); replies = List.copyOf(replies);
            operatorStops = operatorStops.stream().sorted(Comparator.comparing(OperatorStop::at)).toList();
            if (duration.isZero() || duration.isNegative() || duration.compareTo(Duration.ofHours(4)) > 0
                    || providerEventIds.isEmpty() || providerEventIds.size() > LiveCadence.MAXIMUM_SELECTION_SIZE
                    || providerEventIds.stream().distinct().count() != providerEventIds.size()
                    || maximumCallsPerEvent < 4 || maximumCallsPerEvent > 1000
                    || maximumCalls < 4 * providerEventIds.size() || maximumCalls > 3000 || maximumBytes < 1)
                throw new IllegalArgumentException("replay is outside bounded live policy");
            var targets = providerEventIds.stream().map(CanonicalEventIdentity::sofascore).map(CanonicalEventIdentity::value).toList();
            for (var stop : operatorStops) if (stop.at().isBefore(startsAt)
                    || stop.eventId() != null && !targets.contains(stop.eventId()))
                throw new IllegalArgumentException("invalid replay operator stop");
        }
    }

    public record Trace(int sequence, Instant requestedAt, Instant receivedAt, UUID eventId,
                        long providerEventId, SofascoreEndpointType endpoint, long cycle, String kind,
                        boolean finalCycle, String payloadSha256, String outcome, String scope, String code,
                        String sportStatus, List<String> signalKeys, String projectionVersion, String projectionJson,
                        int eventCalls, int totalCalls, long receivedBytes) {
        public Trace { signalKeys = List.copyOf(signalKeys); }
    }

    public record ReplayResult(String source, String fixtureId, String manifestSha256, Instant endedAt,
                               boolean complete, int unusedReplies, List<Trace> trace,
                               List<OperatorStop> appliedStops, List<LiveSchedule.EventState> states) {
        public ReplayResult { trace = List.copyOf(trace); appliedStops = List.copyOf(appliedStops); states = List.copyOf(states); }
    }

    public ReplayResult run(ReplayInput input) {
        return run(input, "live-v2");
    }

    public ReplayResult run(ReplayInput input, String policyVersion) {
        Objects.requireNonNull(input);
        if (!List.of("live-v1", "live-v2", "live-v3").contains(policyVersion))
            throw new IllegalArgumentException("unsupported replay policy");
        List<UUID> targets = input.providerEventIds().stream().map(CanonicalEventIdentity::sofascore)
                .map(CanonicalEventIdentity::value).toList();
        Map<UUID, Long> providerIds = new HashMap<>();
        for (int i = 0; i < targets.size(); i++) providerIds.put(targets.get(i), input.providerEventIds().get(i));
        LiveSchedule schedule = new LiveSchedule(targets, input.startsAt(), input.startsAt().plus(input.duration()),
                "live-v1".equals(policyVersion) ? Duration.ofSeconds(60) : LiveCadence.forMatches(targets.size()),
                policyVersion);
        Map<UUID, Integer> calls = new HashMap<>();
        List<Trace> trace = new ArrayList<>();
        List<OperatorStop> applied = new ArrayList<>();
        Instant now = input.startsAt(), lastReceived = null;
        long bytes = 0;
        int consumed = 0, stopIndex = 0;
        while (!schedule.terminal()) {
            while (stopIndex < input.operatorStops().size() && !input.operatorStops().get(stopIndex).at().isAfter(now)) {
                OperatorStop stop = input.operatorStops().get(stopIndex++); applied.add(stop);
                if (stop.eventId() == null) schedule.stopAll("STOPPED_OPERATOR");
                else schedule.stopEvent(stop.eventId(), "STOPPED_OPERATOR");
            }
            if (schedule.terminal()) break;
            var next = schedule.next(now);
            if (next.isEmpty()) { now = now.plusMillis(100); continue; }
            LiveSchedule.Due due = next.orElseThrow();
            if (lastReceived != null && now.isBefore(lastReceived.plusSeconds(3))) {
                now = lastReceived.plusSeconds(3); continue;
            }
            int eventCalls = calls.getOrDefault(due.eventId(), 0);
            if (!due.finalCycle() && (input.maximumCallsPerEvent() - eventCalls <= 4
                    || input.maximumCalls() - consumed <= 4)) {
                schedule.reserveFinalCheck(due.eventId(), now); continue;
            }
            if (eventCalls >= input.maximumCallsPerEvent() || consumed >= input.maximumCalls()) {
                schedule.stopEvent(due.eventId(), "STOPPED_LIMIT"); continue;
            }
            // An exhausted script is explicitly incomplete; no fabricated response or provider fallback.
            if (consumed >= input.replies().size()) break;
            Reply reply = input.replies().get(consumed);
            long providerId = providerIds.get(due.eventId());
            if (reply.providerEventId() != providerId || reply.endpoint() != due.endpoint())
                throw new IllegalArgumentException("REPLAY_REPLY_DOES_NOT_MATCH_NEXT_DISPATCH_" + (consumed + 1));
            RawPayloadEvidence payload = RawPayloadEvidence.capture(reply.body().getBytes(StandardCharsets.UTF_8));
            if (payload.sizeBytes() > input.maximumBytes() - bytes) { schedule.stopAll("STOPPED_LIMIT"); continue; }
            schedule.started(due, now);
            Instant requested = now;
            now = now.plus(reply.elapsed()); lastReceived = now;
            consumed++; eventCalls++; calls.put(due.eventId(), eventCalls); bytes += payload.sizeBytes();
            // These IDs qualify pure parsing only. persistProcessed is intentionally never invoked.
            var syntheticReceipt = new RawSnapshotPersistenceResult(consumed, RawSnapshotPersistenceOutcome.INSERTED,
                    payload.sha256(), payload.sizeBytes(), OptionalLong.of(consumed));
            var response = new PlaywrightProviderResponse(requested, now, reply.httpStatus(), reply.contentType(), reply.elapsed(), payload);
            LiveProcessedResponse processed = processor.process(CanonicalEventIdentity.sofascore(providerId), due.endpoint(), response, syntheticReceipt);
            // Stops inside a simulated GET retain its receipt but suppress every subsequent dispatch.
            while (stopIndex < input.operatorStops().size() && !input.operatorStops().get(stopIndex).at().isAfter(now)) {
                OperatorStop stop = input.operatorStops().get(stopIndex++); applied.add(stop);
                if (stop.eventId() == null) schedule.stopAll("STOPPED_OPERATOR");
                else schedule.stopEvent(stop.eventId(), "STOPPED_OPERATOR");
            }
            Map<String, Boolean> signals = new LinkedHashMap<>();
            processed.signals().forEach(signal -> signals.put(signal.key(), signal.kind().name().equals("FINISH_CHECK")));
            if (processed.scope() == LiveProcessedResponse.FailureScope.NONE) {
                schedule.completed(due, processed.sportStatus().orElse(null),
                        processed.outcome() == LiveProcessedResponse.Outcome.ENDPOINT_UNAVAILABLE, signals, now);
            } else schedule.failed(due, processed.scope().name(), processed.stopEvent()
                    ? processed.outcome() == LiveProcessedResponse.Outcome.SCHEMA_INCOMPATIBLE
                    ? "STOPPED_SCHEMA_INCOMPATIBLE" : "STOPPED_REVIEW_REQUIRED" : "STOPPED_ERROR");
            trace.add(new Trace(consumed, requested, now, due.eventId(), providerId, due.endpoint(), due.cycle(), due.kind(),
                    due.finalCycle(), payload.sha256(), processed.outcome().name(), processed.scope().name(), processed.code(),
                    processed.sportStatus().orElse(null), List.copyOf(signals.keySet()), processed.projectionVersion(),
                    processed.projectionJson(), eventCalls, consumed, bytes));
        }
        String manifestHash = manifestHash(input);
        if (!"live-v2".equals(policyVersion)) manifestHash = sha(manifestHash + "|" + policyVersion);
        return new ReplayResult(SOURCE, input.fixtureId(), manifestHash, now, schedule.terminal(),
                input.replies().size() - consumed, trace, applied, schedule.states());
    }

    private static String manifestHash(ReplayInput input) {
        StringBuilder material = new StringBuilder(SOURCE).append("|live-replay-v1|").append(input.fixtureId())
                .append('|').append(input.startsAt()).append('|').append(input.duration()).append('|').append(input.providerEventIds())
                .append('|').append(input.maximumCallsPerEvent()).append('|').append(input.maximumCalls()).append('|').append(input.maximumBytes());
        for (Reply reply : input.replies()) material.append('|').append(reply.providerEventId()).append('|').append(reply.endpoint())
                .append('|').append(reply.httpStatus()).append('|').append(reply.contentType().length()).append(':').append(reply.contentType())
                .append('|').append(reply.bodySha256()).append('|').append(reply.elapsed());
        for (OperatorStop stop : input.operatorStops()) material.append('|').append(stop.at()).append('|').append(stop.eventId());
        return sha(material.toString());
    }

    private static String sha(String text) { return Sha256.hex(text.getBytes(StandardCharsets.UTF_8)); }
}
