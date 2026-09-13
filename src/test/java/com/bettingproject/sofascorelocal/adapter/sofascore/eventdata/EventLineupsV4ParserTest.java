package com.bettingproject.sofascorelocal.adapter.sofascore.eventdata;

import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.event.EventSourceTrace;
import com.bettingproject.sofascorelocal.domain.eventdata.J5EventDataObservation;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

class EventLineupsV4ParserTest {
    private static final Instant RECEIVED = Instant.parse("2026-09-09T10:00:00Z");
    private static final String JSON = """
        {"confirmed":true,"home":{"formation":"4-3-3","players":[
          {"player":{"id":101,"name":"Synthetic player","country":{"name":"Brazil","alpha2":"br"}},
           "jerseyNumber":9,"position":"F","substitute":false,"captain":true,
           "statistics":{"goals":2,"goalAssist":1,"minutesPlayed":0}}]},
         "away":{"formation":"4-4-2","players":[]}}
        """;

    @Test
    void preservesGoalsAssistsCountryAndCompletenessWithSeparateV4Provenance() {
        var raw = payload(JSON);
        var result = parse(raw);
        var legacy = new EventLineupsV3Parser().parse(46, 46001, raw, RECEIVED);
        assertThat(result.status()).isEqualTo(J5ParseStatus.PARSED);
        assertThat(result.completeness()).isEqualTo(legacy.completeness());
        assertThat(result.evidence().rawSha256()).isEqualTo(raw.sha256());
        assertThat(result.evidence().recordedAt()).isEqualTo(RECEIVED);
        assertThat(result.evidence().parserVersion()).isEqualTo("event-lineups-v4");
        var player = result.data().orElseThrow().home().players().getFirst();
        assertThat(player.country().orElseThrow().name()).contains("Brazil");
        assertThat(player.country().orElseThrow().alpha2()).contains("BR");
        assertThat(player.statistics().orElseThrow().values()).containsEntry("goals", new BigDecimal("2"))
                .containsEntry("goalAssist", BigDecimal.ONE).containsEntry("minutesPlayed", BigDecimal.ZERO);
        assertThat(legacy.data().orElseThrow().home().players().getFirst().country()).isEmpty();
        var source = EventSourceTrace.providerSnapshot(46, raw.sha256(), "event-lineups-v4", RECEIVED);
        var identity = CanonicalEventIdentity.sofascore(46001);
        var observation = J5EventDataObservation.from(identity, result.data().orElseThrow(), source, result.completeness().orElseThrow());
        var changed = parse(payload(JSON.replace("\"br\"", "\"AR\"")));
        assertThat(J5EventDataObservation.from(identity, changed.data().orElseThrow(), source,
                changed.completeness().orElseThrow()).normalizedSha256()).isNotEqualTo(observation.normalizedSha256());
        assertThatThrownBy(() -> J5EventDataObservation.from(identity, result.data().orElseThrow(),
                EventSourceTrace.providerSnapshot(46, raw.sha256(), "event-lineups-v3", RECEIVED), result.completeness().orElseThrow()))
                .hasMessageContaining("player countries require");
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "{}", "[]", "{\"alpha2\":\"BRA\"}", "{\"name\":17}"})
    void invalidOrUnknownOptionalCountryNeverCreatesAnInventedCountryOrAParsingFailure(String country) {
        var raw = payload(JSON.replace("{\"name\":\"Brazil\",\"alpha2\":\"br\"}", country));
        var result = parse(raw);
        assertThat(result.status()).isEqualTo(J5ParseStatus.PARSED);
        assertThat(result.data().orElseThrow().home().players().getFirst().country()).isEmpty();
        assertThat(result.completeness()).isEqualTo(new EventLineupsV3Parser().parse(46, 46001, raw, RECEIVED).completeness());
    }

    @Test
    void keepsAbsentEmptyMetadataOnlyAndZeroStatisticsDistinctForThePresentation() {
        String statistics = "\"statistics\":{\"goals\":2,\"goalAssist\":1,\"minutesPlayed\":0}";
        var absent = parse(payload(JSON.replace(statistics, "\"statistics\":null"))).data().orElseThrow().home().players().getFirst();
        assertThat(absent.statistics()).isEmpty();
        for (String object : new String[]{"{}", "{\"statisticsType\":{\"sportSlug\":\"football\",\"statisticsType\":\"player\"}}"}) {
            var player = parse(payload(JSON.replace(statistics, "\"statistics\":" + object))).data().orElseThrow().home().players().getFirst();
            assertThat(player.statistics()).isPresent();
            assertThat(player.statistics().orElseThrow().values()).isEmpty();
        }
        var zero = parse(payload(JSON.replace(statistics, "\"statistics\":{\"goals\":0}"))).data().orElseThrow().home().players().getFirst();
        assertThat(zero.statistics().orElseThrow().values()).containsEntry("goals", BigDecimal.ZERO);
    }

    @Test
    void missingPlayersUseTheSameOptionalCountryContractWithoutRewritingV3() {
        String json = JSON.replace("\"formation\":\"4-4-2\",\"players\":[]", """
            "formation":"4-4-2","players":[],"missingPlayers":[
              {"player":{"id":301,"name":"Synthetic unavailable","country":{"name":"France","alpha2":"fr"}},"description":"Knee Injury"},
              {"player":{"id":302,"name":"Unknown country","country":{"alpha2":"INVALID"}}}]
            """);
        var raw = payload(json);
        var result = parse(raw);
        var legacy = new EventLineupsV3Parser().parse(46, 46001, raw, RECEIVED);
        assertThat(result.status()).isEqualTo(J5ParseStatus.PARSED);
        assertThat(result.completeness()).isEqualTo(legacy.completeness());
        var missing = result.data().orElseThrow().away().missingPlayers().orElseThrow();
        assertThat(missing.getFirst().country().orElseThrow().name()).contains("France");
        assertThat(missing.getFirst().country().orElseThrow().alpha2()).contains("FR");
        assertThat(missing.get(1).country()).isEmpty();
        assertThat(legacy.data().orElseThrow().away().missingPlayers().orElseThrow()).allSatisfy(player -> assertThat(player.country()).isEmpty());
        // Remove countries from available players so this exercises missing-player-only provenance/hash.
        var onlyMissing = parse(payload(json.replace(",\"country\":{\"name\":\"Brazil\",\"alpha2\":\"br\"}", "")));
        var source = EventSourceTrace.providerSnapshot(46, raw.sha256(), "event-lineups-v4", RECEIVED);
        var identity = CanonicalEventIdentity.sofascore(46001);
        var original = J5EventDataObservation.from(identity, onlyMissing.data().orElseThrow(), source, onlyMissing.completeness().orElseThrow());
        var changed = parse(payload(json.replace(",\"country\":{\"name\":\"Brazil\",\"alpha2\":\"br\"}", "").replace("\"fr\"", "\"BE\"")));
        assertThat(J5EventDataObservation.from(identity, changed.data().orElseThrow(), source, changed.completeness().orElseThrow()).normalizedSha256())
                .isNotEqualTo(original.normalizedSha256());
        assertThatThrownBy(() -> J5EventDataObservation.from(identity, onlyMissing.data().orElseThrow(),
                EventSourceTrace.providerSnapshot(46, raw.sha256(), "event-lineups-v3", RECEIVED), onlyMissing.completeness().orElseThrow()))
                .hasMessageContaining("player countries require");
    }

    @Test
    void refusesMalformedCoreAndKeepsValidUnconfirmedEmptyShape() {
        for (String json : new String[]{JSON + " {}", JSON.replace("\"goals\":2", "\"goals\":true"),
                JSON.replace("\"id\":101", "\"id\":101,\"id\":102")}) {
            var raw = payload(json);
            var result = parse(raw);
            var legacy = new EventLineupsV3Parser().parse(46, 46001, raw, RECEIVED);
            assertThat(result.data()).isEmpty();
            assertThat(result.status()).isEqualTo(legacy.status());
        }
        assertThat(parse(payload("{\"confirmed\":false}")).data().orElseThrow().home().players()).isEmpty();
    }

    private static J5ParseResult<com.bettingproject.sofascorelocal.domain.eventdata.EventLineups> parse(RawPayloadEvidence raw) {
        return new EventLineupsV4Parser().parse(46, 46001, raw, RECEIVED);
    }
    private static RawPayloadEvidence payload(String json) { return RawPayloadEvidence.capture(json.getBytes(StandardCharsets.UTF_8)); }
}
