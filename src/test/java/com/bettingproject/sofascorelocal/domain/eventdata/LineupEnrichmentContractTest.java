package com.bettingproject.sofascorelocal.domain.eventdata;

import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.event.EventSourceTrace;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LineupEnrichmentContractTest {
    @Test
    void preservesLegacyHashAndSeparatesEveryNewPresenceState() {
        var absent = lineups(Optional.empty(), Optional.empty(), Optional.empty());
        // Frozen pre-enrichment binary contract: two named players, no number/position/formation, both starters.
        assertThat(hash(absent)).isEqualTo("8e6d6cea653700ddba67c2532cc50bb8db4d79047a3908a37573621512d8376d");
        assertThat(observation(absent, "event-lineups-v2").normalizedSha256()).isEqualTo(hash(absent));
        var explicitFalse = lineups(Optional.of(false), Optional.empty(), Optional.empty());
        var explicitTrue = lineups(Optional.of(true), Optional.empty(), Optional.empty());
        var emptyStatistics = lineups(Optional.empty(), Optional.of(new PlayerMatchStatistics(Map.of(), Map.of())), Optional.empty());
        var emptyMissing = lineups(Optional.empty(), Optional.empty(), Optional.of(List.of()));
        var observedZero = lineups(Optional.empty(), Optional.of(new PlayerMatchStatistics(Map.of("goals", BigDecimal.ZERO), Map.of())), Optional.empty());
        assertThat(List.of(hash(absent), hash(explicitFalse), hash(explicitTrue), hash(emptyStatistics),
                hash(emptyMissing), hash(observedZero))).doesNotHaveDuplicates();
        for (var enriched : List.of(explicitFalse, explicitTrue, emptyStatistics, emptyMissing, observedZero)) {
            assertThatThrownBy(() -> observation(enriched, "event-lineups-v2")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> J5EventDataObservation.from(CanonicalEventIdentity.sofascore(900001), enriched,
                    EventSourceTrace.syntheticFixture("lineups-legacy", "a".repeat(64), "event-lineups-v1", Instant.EPOCH),
                    J5CompletenessReport.measured(1, 1, List.of()))).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void canonicalizesExactDecimalsAndMapOrderForEqualityAndHashWithoutLosingPrecision() {
        Map<String, BigDecimal> original = new LinkedHashMap<>();
        original.put("rating", new BigDecimal("7.4000"));
        original.put("expectedGoals", new BigDecimal("0.01852610000000000001"));
        original.put("minutesPlayed", new BigDecimal("0.00"));
        var first = new PlayerMatchStatistics(original, Map.of("original", new BigDecimal("7.400")));
        var second = new PlayerMatchStatistics(Map.of("minutesPlayed", BigDecimal.ZERO,
                "expectedGoals", new BigDecimal("0.01852610000000000001"), "rating", new BigDecimal("7.4")),
                Map.of("original", new BigDecimal("7.4")));
        assertThat(first).isEqualTo(second);
        assertThat(first.values().keySet()).containsExactly("expectedGoals", "minutesPlayed", "rating");
        assertThat(hash(lineups(Optional.empty(), Optional.of(first), Optional.empty())))
                .isEqualTo(hash(lineups(Optional.empty(), Optional.of(second), Optional.empty())));
        original.clear();
        assertThat(first.values()).hasSize(3);
        assertThatThrownBy(() -> first.values().put("goals", BigDecimal.ONE)).isInstanceOf(UnsupportedOperationException.class);
        assertThat(first.values().get("expectedGoals").toPlainString()).isEqualTo("0.01852610000000000001");
        assertThat(new PlayerMatchStatistics(Map.of("goals", new BigDecimal("1E+2")), Map.of()))
                .isEqualTo(new PlayerMatchStatistics(Map.of("goals", new BigDecimal("100.000")), Map.of()));
    }

    @Test
    void preservesMissingPlayerOrderCodesAndOffsetInTheHashAndMakesCollectionsImmutable() {
        var first = missing(301, "First", 1, "2026-09-16T00:00:00+02:00");
        var second = missing(302, "Second", -2, "2026-09-16T00:00:00+02:00");
        var list = new ArrayList<>(List.of(first, second));
        var source = lineups(Optional.empty(), Optional.empty(), Optional.of(list));
        list.clear();
        assertThat(source.home().missingPlayers().orElseThrow()).containsExactly(first, second);
        assertThatThrownBy(() -> source.home().missingPlayers().orElseThrow().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(hash(source)).isNotEqualTo(hash(lineups(Optional.empty(), Optional.empty(), Optional.of(List.of(second, first)))));
        assertThat(hash(source)).isNotEqualTo(hash(lineups(Optional.empty(), Optional.empty(), Optional.of(List.of(
                missing(301, "First", 2, "2026-09-16T00:00:00+02:00"), second)))));
        assertThat(hash(source)).isNotEqualTo(hash(lineups(Optional.empty(), Optional.empty(), Optional.of(List.of(
                missing(301, "First", 1, "2026-09-15T22:00:00Z"), second)))));
    }

    @Test
    void refusesUnboundedStatisticsWithoutReinterpretingValues() {
        assertThatThrownBy(() -> new PlayerMatchStatistics(Map.of("not a key", BigDecimal.ONE), Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlayerMatchStatistics(Map.of("rating", new BigDecimal("1e100")), Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        Map<String, BigDecimal> tooMany = new LinkedHashMap<>();
        for (int i = 0; i < 129; i++) tooMany.put("value" + i, BigDecimal.ZERO);
        assertThatThrownBy(() -> new PlayerMatchStatistics(tooMany, Map.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlayerMatchStatistics(Map.of(), tooMany)).isInstanceOf(IllegalArgumentException.class);
    }

    private static EventLineups lineups(Optional<Boolean> captain, Optional<PlayerMatchStatistics> statistics,
            Optional<List<MissingLineupPlayer>> missing) {
        return new EventLineups(900001, true,
                new TeamLineup(LineupSide.HOME, Optional.empty(), List.of(
                        new EventLineupPlayer(101, "Home", Optional.empty(), Optional.empty(), true, captain, statistics)), missing),
                new TeamLineup(LineupSide.AWAY, Optional.empty(), List.of(
                        new EventLineupPlayer(201, "Away", Optional.empty(), Optional.empty(), true))));
    }

    private static MissingLineupPlayer missing(long id, String name, int reason, String date) {
        return new MissingLineupPlayer(id, name, Optional.empty(), Optional.empty(), Optional.of("missing"),
                Optional.of(reason), Optional.of("Source description"), Optional.of(5), Optional.of(OffsetDateTime.parse(date)));
    }

    private static String hash(EventLineups value) { return observation(value, "event-lineups-v3").normalizedSha256(); }

    private static J5EventDataObservation observation(EventLineups value, String parser) {
        return J5EventDataObservation.from(CanonicalEventIdentity.sofascore(900001), value,
                EventSourceTrace.providerSnapshot(1, "a".repeat(64), parser, Instant.EPOCH),
                J5CompletenessReport.measured(1, 1, List.of()));
    }
}
