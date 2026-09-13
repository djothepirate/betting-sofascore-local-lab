package com.bettingproject.sofascorelocal.adapter.sofascore.eventdata;

import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessStatus;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class EventLineupsV3ParserTest {
    private final EventLineupsV3Parser parser = new EventLineupsV3Parser();

    @Test
    void preservesCaptainExactNumbersAndOrderedMissingPlayersWithoutExpandingCompleteness() {
        var payload = payload("""
                ,"captain":true,"statistics":{"rating":7.40,"minutesPlayed":0,
                "expectedGoals":0.01852610000000000001,"ratingVersions":{"original":7.40,"alternative":7.8},
                "statisticsType":{"sportSlug":"football","statisticsType":"player"},
                "futureNumeric":42,"futureObject":{"score":3},"totalPass":null}
                """, """
                ,"missingPlayers":[
                {"player":{"id":301,"name":"Missing first","jerseyNumber":"8","position":"M"},
                 "type":"missing","reason":1,"externalType":5,"description":"Source description",
                 "expectedEndDate":"2026-09-16T00:00:00+02:00"},
                {"player":{"id":301,"name":"Missing second"},"type":"doubtful","reason":-1}]
                """);
        var result = parse(payload);
        assertThat(result.status()).isEqualTo(J5ParseStatus.PARSED);
        assertThat(result.evidence().parserVersion()).isEqualTo("event-lineups-v3");
        assertThat(result.evidence().rawSha256()).isEqualTo(payload.sha256());
        assertThat(result.completeness().orElseThrow().status()).isEqualTo(J5CompletenessStatus.COMPLETE);
        assertThat(result.completeness()).isEqualTo(new EventLineupsV2Parser()
                .parse(51, 900001, payload, receivedAt()).completeness());
        var home = result.data().orElseThrow().home();
        var first = home.players().getFirst();
        assertThat(first.captain()).contains(true);
        var statistics = first.statistics().orElseThrow();
        assertThat(statistics.values()).containsEntry("minutesPlayed", BigDecimal.ZERO)
                .containsEntry("rating", new BigDecimal("7.4"))
                .containsEntry("expectedGoals", new BigDecimal("0.01852610000000000001"))
                .containsEntry("futureNumeric", new BigDecimal("42"))
                .doesNotContainKeys("statisticsType", "futureObject", "totalPass");
        assertThat(statistics.ratingVersions()).containsEntry("original", new BigDecimal("7.4"));
        assertThat(statistics.values().keySet()).containsExactly("expectedGoals", "futureNumeric", "minutesPlayed", "rating");
        assertThat(result.warnings()).extracting(J5ParseWarning::path)
                .contains("$.home.players[0].statistics.futureNumeric", "$.home.players[0].statistics.futureObject");
        var missing = home.missingPlayers().orElseThrow();
        assertThat(missing).extracting(player -> player.name()).containsExactly("Missing first", "Missing second");
        assertThat(missing.getFirst().shirtNumber()).contains(8);
        assertThat(missing.getFirst().reason()).contains(1);
        assertThat(missing.get(1).reason()).contains(-1);
        assertThat(missing.getFirst().externalType()).contains(5);
        assertThat(missing.getFirst().expectedEndDate()).contains(OffsetDateTime.parse("2026-09-16T00:00:00+02:00"));
        assertThat(result.data().orElseThrow().away().missingPlayers()).isEmpty();
        assertThat(result.data().orElseThrow().away().players().getFirst().captain()).isEmpty();
    }

    @Test
    void distinguishesAbsentNullFalseAndExplicitEmptyCollections() {
        for (String extra : List.of("", ",\"captain\":null,\"statistics\":null")) {
            var player = parse(payload(extra, "")).data().orElseThrow().home().players().getFirst();
            assertThat(player.captain()).isEmpty();
            assertThat(player.statistics()).isEmpty();
        }
        var empty = parse(payload(",\"captain\":false,\"statistics\":{}", ",\"missingPlayers\":[]"))
                .data().orElseThrow().home();
        assertThat(empty.players().getFirst().captain()).contains(false);
        assertThat(empty.players().getFirst().statistics()).isPresent();
        assertThat(empty.players().getFirst().statistics().orElseThrow().values()).isEmpty();
        assertThat(empty.missingPlayers()).isPresent();
        assertThat(empty.missingPlayers().orElseThrow()).isEmpty();
        assertThat(parse(payload("", ",\"missingPlayers\":null")).data().orElseThrow().home().missingPlayers()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"captain\":\"true\"", "\"captain\":1", "\"statistics\":[]",
            "\"statistics\":{\"rating\":\"7.4\"}", "\"statistics\":{\"goals\":true}",
            "\"statistics\":{\"ratingVersions\":{\"original\":\"7.4\"}}",
            "\"statistics\":{\"ratingVersions\":{\"original\":{}}}",
            "\"statistics\":{\"statisticsType\":[]}",
            "\"statistics\":{\"statisticsType\":{\"sportSlug\":7}}",
            "\"statistics\":{\"rating\":1e100}", "\"statistics\":{\"rating\":1e-100}"})
    void rejectsWrongTypesAndUnboundedKnownNumericValues(String fields) {
        var result = parse(payload("," + fields, ""));
        assertThat(result.status()).isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(result.data()).isEmpty();
        assertThat(result.problems()).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "[1]", "[{\"player\":{\"id\":\"301\",\"name\":\"Missing\"}}]",
            "[{\"player\":{\"id\":301,\"name\":\"Missing\"},\"reason\":\"1\"}]",
            "[{\"player\":{\"id\":301,\"name\":\"Missing\"},\"expectedEndDate\":\"2026-09-16\"}]"})
    void rejectsIncompatibleMissingPlayersWithoutReturningPartialData(String missing) {
        var result = parse(payload("", ",\"missingPlayers\":" + missing));
        assertThat(result.status()).isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(result.data()).isEmpty();
    }

    @Test
    void rejectsCollectionOverrunsAndKeepsStrictJsonParsing() {
        String values = IntStream.range(0, 129).mapToObj(index -> "\"future" + index + "\":0").collect(Collectors.joining(","));
        assertThat(parse(payload(",\"statistics\":{" + values + "}", "")).status()).isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
        String versions = IntStream.range(0, 17).mapToObj(index -> "\"v" + index + "\":0").collect(Collectors.joining(","));
        assertThat(parse(payload(",\"statistics\":{\"ratingVersions\":{" + versions + "}}", "")).status())
                .isEqualTo(J5ParseStatus.SCHEMA_INCOMPATIBLE);
        assertThat(parse(payload(",\"captain\":true,\"captain\":false", "")).status()).isEqualTo(J5ParseStatus.UNEXPECTED_CONTENT);
        var trailing = RawPayloadEvidence.capture((new String(payload("", "").bytes(), StandardCharsets.UTF_8) + " {}").getBytes(StandardCharsets.UTF_8));
        assertThat(parse(trailing).status()).isEqualTo(J5ParseStatus.UNEXPECTED_CONTENT);
    }

    @Test
    void preservesTheLegacyParserAndUnconfirmedEmptyShape() {
        var raw = payload(",\"captain\":true,\"statistics\":{\"rating\":7.4}", ",\"missingPlayers\":[]");
        var old = new EventLineupsV2Parser().parse(51, 900001, raw, receivedAt()).data().orElseThrow();
        assertThat(old.home().players().getFirst().captain()).isEmpty();
        assertThat(old.home().players().getFirst().statistics()).isEmpty();
        assertThat(old.home().missingPlayers()).isEmpty();
        var empty = parse(RawPayloadEvidence.capture("{\"confirmed\":false}".getBytes(StandardCharsets.UTF_8)));
        assertThat(empty.completeness().orElseThrow().status()).isEqualTo(J5CompletenessStatus.EMPTY_VALID);
        assertThat(empty.data().orElseThrow().home().players()).isEmpty();
    }

    private J5ParseResult<com.bettingproject.sofascorelocal.domain.eventdata.EventLineups> parse(RawPayloadEvidence payload) {
        return parser.parse(51, 900001, payload, receivedAt());
    }

    private static RawPayloadEvidence payload(String playerFields, String homeFields) {
        return RawPayloadEvidence.capture(("""
                {"confirmed":true,"home":{"formation":"4-3-3","players":[
                {"player":{"id":101,"name":"Home","position":"G"},"jerseyNumber":"1","substitute":false%s}]%s},
                "away":{"formation":"4-4-2","players":[
                {"player":{"id":201,"name":"Away","position":"D"},"jerseyNumber":"4","substitute":false}]}}
                """).formatted(playerFields, homeFields).getBytes(StandardCharsets.UTF_8));
    }

    private static Instant receivedAt() { return Instant.parse("2026-09-09T01:00:00Z"); }
}
