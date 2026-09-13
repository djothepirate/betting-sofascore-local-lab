package com.bettingproject.sofascorelocal.application.live;

import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.port.CanonicalEventStore;
import com.bettingproject.sofascorelocal.port.EventDetailsStore;
import com.bettingproject.sofascorelocal.port.J5EventDataStore;
import com.bettingproject.sofascorelocal.port.RawManualCallSnapshotStore;
import com.bettingproject.sofascorelocal.security.Sha256;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static com.bettingproject.sofascorelocal.application.live.LiveReplayRunner.*;
import static com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class LiveReplayRunnerTest {
    private static final long A = 17000001L, B = 17000002L;
    private static final Instant START = Instant.parse("2026-09-07T16:00:00Z");
    private final CanonicalEventStore events = mock(CanonicalEventStore.class);
    private final EventDetailsStore details = mock(EventDetailsStore.class);
    private final J5EventDataStore j5 = mock(J5EventDataStore.class);
    private final RawManualCallSnapshotStore raw = mock(RawManualCallSnapshotStore.class);
    private final LiveReplayRunner runner = new LiveReplayRunner(new LiveResponseProcessor(events, details, j5, raw));

    @Test
    void v3ReplaysPrematchUnavailableThenFreshLineupsAndKeepsOlderPoliciesExplicit() {
        ReplayInput input = input(List.of(A), List.of(
                j4(A, "notstarted"), unavailable(A, EVENT_LINEUPS),
                j4(A, "notstarted"), reply(A, EVENT_LINEUPS, 200, "{\"confirmed\":false}"),
                j4(A, "finished"), unavailable(A, EVENT_STATISTICS), unavailable(A, EVENT_INCIDENTS),
                unavailable(A, EVENT_LINEUPS)), List.of());
        var first = runner.run(input, "live-v3");
        assertThat(runner.run(input, "live-v3")).isEqualTo(first);
        assertThat(first.complete()).isTrue();
        assertThat(first.unusedReplies()).isZero();
        assertThat(first.trace()).hasSize(8);
        var prematch = first.trace().stream().filter(t -> "J5_PREMATCH_LINEUPS".equals(t.kind())).toList();
        assertThat(prematch).hasSize(2);
        assertThat(prematch.getFirst().code()).isEqualTo("HTTP_404");
        assertThat(prematch.getLast().outcome()).isEqualTo("PARSED");
        assertThat(Duration.between(prematch.getFirst().requestedAt(), prematch.getLast().requestedAt()))
                .isGreaterThanOrEqualTo(Duration.ofSeconds(60));
        assertThat(first.trace().get(5).requestedAt()).isAfterOrEqualTo(prematch.getLast().requestedAt().plusSeconds(60));
        assertThat(first.trace().stream().filter(t -> t.finalCycle()).count()).isEqualTo(3);
        assertThatThrownBy(() -> runner.run(input, "live-v2"))
                .hasMessage("REPLAY_REPLY_DOES_NOT_MATCH_NEXT_DISPATCH_2");
        verifyNoInteractions(events, details, j5, raw);
    }

    @Test
    void theSameHashedScriptReplaysIdenticallyWithPeriodThenFinishSignalsAndExplicitFinalCompleteness() {
        ReplayInput input = input(List.of(A), List.of(
                j4(A, "inprogress"), unavailable(A, EVENT_STATISTICS), injury(45), unavailable(A, EVENT_LINEUPS),
                j4(A, "inprogress"), unavailable(A, EVENT_STATISTICS), injury(90), unavailable(A, EVENT_LINEUPS),
                j4(A, "finished"), unavailable(A, EVENT_STATISTICS), unavailable(A, EVENT_INCIDENTS), unavailable(A, EVENT_LINEUPS)), List.of());

        ReplayResult first = runner.run(input);
        ReplayResult repeated = runner.run(input);

        assertThat(first).isEqualTo(repeated);
        assertThat(first.source()).isEqualTo("SYNTHETIC_REPLAY");
        assertThat(first.manifestSha256()).matches("[0-9a-f]{64}");
        assertThat(first.complete()).isTrue();
        assertThat(first.unusedReplies()).isZero();
        assertThat(first.trace()).hasSize(12);
        assertThat(first.trace().stream().filter(t -> t.endpoint() == EVENT_DETAILS).toList())
                .extracting(Trace::requestedAt).containsExactly(START, START.plusSeconds(60), START.plusSeconds(120));
        assertThat(first.trace().stream().filter(t -> t.endpoint() == EVENT_DETAILS).toList())
                .extracting(Trace::kind).containsExactly("J4_INITIAL", "J4_SIGNAL", "J4_FINISH");
        assertThat(first.trace().get(2).signalKeys()).hasSize(1);
        assertThat(first.trace().get(6).signalKeys()).hasSize(1);
        assertThat(first.states()).singleElement().satisfies(state -> {
            assertThat(state.state()).isEqualTo("FINISHED_CONFIRMED");
            assertThat(state.finalComplete()).isFalse(); // Three final 404 receipts are explicit unavailability.
        });
        assertThat(first.trace().getLast().totalCalls()).isEqualTo(12);
        assertThat(first.trace().getLast().receivedBytes()).isEqualTo(input.replies().stream()
                .mapToLong(reply -> reply.body().getBytes(StandardCharsets.UTF_8).length).sum());
        verifyNoInteractions(events, details, j5, raw);
    }

    @Test
    void anIdentityErrorStopsAllTargetsWhereAnIsolableSchemaStopsOnlyItsOwnTarget() {
        var isolated = runner.run(input(List.of(A, B), List.of(
                reply(A, EVENT_DETAILS, 200, "{\"event\":{\"id\":" + A + ",\"status\":null}}"),
                j4(B, "finished"), unavailable(B, EVENT_STATISTICS), unavailable(B, EVENT_INCIDENTS), unavailable(B, EVENT_LINEUPS)), List.of()));
        assertThat(isolated.trace()).hasSize(5);
        assertThat(isolated.states()).extracting(LiveSchedule.EventState::state)
                .containsExactly("STOPPED_SCHEMA_INCOMPATIBLE", "FINISHED_CONFIRMED");

        var global = runner.run(input(List.of(A, B), List.of(
                reply(A, EVENT_DETAILS, 200, "{\"event\":{\"id\":19000000,\"status\":null}}"), j4(B, "finished")), List.of()));
        assertThat(global.trace()).singleElement().satisfies(trace -> {
            assertThat(trace.scope()).isEqualTo("CAMPAIGN");
            assertThat(trace.code()).isEqualTo("EVENT_ID_MISMATCH");
        });
        assertThat(global.states()).extracting(LiveSchedule.EventState::state).containsOnly("STOPPED_ERROR");
        assertThat(global.unusedReplies()).isEqualTo(1);
        verifyNoInteractions(events, details, j5, raw);
    }

    @Test
    void anOperatorStopInsideTheGlobalFencePreventsTheNextSimulatedDispatch() {
        var input = input(List.of(A), List.of(j4(A, "inprogress"), unavailable(A, EVENT_STATISTICS)),
                List.of(new OperatorStop(START.plusSeconds(2), CanonicalEventIdentity.sofascore(A).value())));
        var result = runner.run(input);
        assertThat(result.trace()).hasSize(1);
        assertThat(result.appliedStops()).containsExactlyElementsOf(input.operatorStops());
        assertThat(result.states()).singleElement().satisfies(state -> assertThat(state.state()).isEqualTo("STOPPED_OPERATOR"));
        assertThat(result.complete()).isTrue();
        verifyNoInteractions(events, details, j5, raw);
    }

    @Test
    void anOperatorStopDuringAReplyPreservesItsEvidenceAndPreventsAnotherCall() {
        Reply original = j4(A, "inprogress");
        Reply slow = new Reply(original.providerEventId(), original.endpoint(), original.httpStatus(),
                original.contentType(), original.body(), original.bodySha256(), Duration.ofSeconds(5));
        var result = runner.run(input(List.of(A), List.of(slow, unavailable(A, EVENT_STATISTICS)),
                List.of(new OperatorStop(START.plusSeconds(2), null))));
        assertThat(result.trace()).singleElement().satisfies(trace -> {
            assertThat(trace.receivedAt()).isEqualTo(START.plusSeconds(5));
            assertThat(trace.payloadSha256()).isEqualTo(slow.bodySha256());
            assertThat(trace.outcome()).isEqualTo("PARSED");
        });
        assertThat(result.states()).singleElement().satisfies(state -> assertThat(state.state()).isEqualTo("STOPPED_OPERATOR"));
        verifyNoInteractions(events, details, j5, raw);
    }

    @Test
    void callAndByteBudgetsTerminateTheReplayWithoutInventingReplies() {
        var bounded = new ReplayInput("bounded-final", START, Duration.ofMinutes(4), List.of(A),
                List.of(j4(A, "notstarted")), List.of(), 4, 4, 100_000);
        var result = runner.run(bounded);
        assertThat(result.trace()).singleElement().satisfies(trace -> {
            assertThat(trace.kind()).isEqualTo("J4_FINAL_CHECK");
            assertThat(trace.finalCycle()).isTrue();
        });
        assertThat(result.states()).singleElement().satisfies(state -> assertThat(state.state()).isEqualTo("STOPPED_LIMIT"));

        var noSpace = new ReplayInput("bounded-bytes", START, Duration.ofMinutes(4), List.of(A),
                List.of(j4(A, "inprogress")), List.of(), 1000, 3000, 1);
        assertThat(runner.run(noSpace).trace()).isEmpty();

        var exhausted = runner.run(input(List.of(A), List.of(j4(A, "inprogress")), List.of()));
        assertThat(exhausted.complete()).isFalse();
        assertThat(exhausted.trace()).hasSize(1);
        verifyNoInteractions(events, details, j5, raw);
    }

    @Test
    void modifiedFixtureBytesAndAnOutOfOrderEndpointFailDeterministically() {
        Reply source = j4(A, "inprogress");
        assertThatThrownBy(() -> new Reply(A, EVENT_DETAILS, 200, "application/json", source.body() + " ",
                source.bodySha256(), Duration.ZERO)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("REPLAY_PAYLOAD_HASH_MISMATCH");
        assertThatThrownBy(() -> runner.run(input(List.of(A), List.of(unavailable(A, EVENT_INCIDENTS)), List.of())))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("REPLAY_REPLY_DOES_NOT_MATCH_NEXT_DISPATCH_1");
        verifyNoInteractions(events, details, j5, raw);
    }

    private static ReplayInput input(List<Long> ids, List<Reply> replies, List<OperatorStop> stops) {
        return new ReplayInput("synthetic-live-transitions", START, Duration.ofMinutes(4), ids, replies, stops,
                1000, 3000, 5L * 1024 * 1024 * 3000);
    }
    private static Reply j4(long id, String status) {
        return reply(id, EVENT_DETAILS, 200, """
                {"event":{"id":%d,"startTimestamp":1788796800,
                "homeTeam":{"id":1,"name":"Home"},"awayTeam":{"id":2,"name":"Away"},
                "status":{"type":"%s"},"homeScore":{"current":0},"awayScore":{"current":0}}}
                """.formatted(id, status));
    }
    private static Reply unavailable(long id, SofascoreEndpointType endpoint) { return reply(id, endpoint, 404, "unavailable"); }
    private static Reply injury(int minute) {
        return reply(A, EVENT_INCIDENTS, 200, "{\"incidents\":[{\"incidentType\":\"injuryTime\",\"time\":"
                + minute + ",\"length\":3,\"addedTime\":0}]}");
    }
    private static Reply reply(long id, SofascoreEndpointType endpoint, int status, String body) {
        return new Reply(id, endpoint, status, "application/json", body,
                Sha256.hex(body.getBytes(StandardCharsets.UTF_8)), Duration.ZERO);
    }
}
