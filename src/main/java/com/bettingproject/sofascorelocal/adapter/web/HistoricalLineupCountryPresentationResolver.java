package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.adapter.sofascore.ProviderPeopleParserSupport;
import com.bettingproject.sofascorelocal.adapter.sofascore.eventdata.EventLineupsV3Parser;
import com.bettingproject.sofascorelocal.domain.event.EventSourceKind;
import com.bettingproject.sofascorelocal.domain.event.ProviderCountry;
import com.bettingproject.sofascorelocal.domain.eventdata.EventLineups;
import com.bettingproject.sofascorelocal.domain.eventdata.J5EventDataObservationView;
import com.bettingproject.sofascorelocal.domain.eventdata.LineupSide;
import com.bettingproject.sofascorelocal.domain.eventdata.TeamLineup;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotInspectionSource;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.port.RawSnapshotInspectionStore;
import com.bettingproject.sofascorelocal.security.SensitiveContentScanner;
import com.bettingproject.sofascorelocal.security.Sha256;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reads one already-persisted V3 lineup snapshot to supplement only its browser projection.
 * The returned overlay is deliberately detached from normalized observations and provenance.
 */
@Component
public final class HistoricalLineupCountryPresentationResolver implements LineupCountryOverlayResolver {

    private static final int MAXIMUM_PLAYERS_PER_SIDE = 128;
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private final RawSnapshotInspectionStore snapshots;

    public HistoricalLineupCountryPresentationResolver(RawSnapshotInspectionStore snapshots) {
        this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
    }

    @Override
    public LineupCountryOverlay resolve(J5EventDataObservationView observation) {
        if (!eligible(observation)) {
            return LineupCountryOverlay.empty();
        }
        long snapshotId = observation.source().snapshotId().orElseThrow();
        try {
            Optional<RawSnapshotInspectionSource> snapshot = snapshots.findById(snapshotId);
            if (snapshot == null || snapshot.isEmpty()) {
                return LineupCountryOverlay.empty();
            }
            return fromSnapshot(observation, (EventLineups) observation.data(), snapshot.orElseThrow());
        }
        catch (DataAccessException | IllegalArgumentException | IllegalStateException exception) {
            return LineupCountryOverlay.empty();
        }
    }

    private static boolean eligible(J5EventDataObservationView observation) {
        if (observation == null || !(observation.data() instanceof EventLineups lineups)) {
            return false;
        }
        var source = observation.source();
        return lineups.endpointType() == SofascoreEndpointType.EVENT_LINEUPS
                && source.kind() == EventSourceKind.PROVIDER_SNAPSHOT
                && source.snapshotId().isPresent()
                && EventLineupsV3Parser.PARSER_VERSION.equals(source.parserVersion());
    }

    private static LineupCountryOverlay fromSnapshot(
            J5EventDataObservationView observation,
            EventLineups lineups,
            RawSnapshotInspectionSource snapshot) {
        var summary = snapshot.summary();
        var source = observation.source();
        byte[] payload = snapshot.payloadRaw();
        if (summary.snapshotId() != source.snapshotId().orElseThrow()
                || !SofascoreEndpointType.EVENT_LINEUPS.name().equals(summary.logicalEndpoint())
                || !expectedRequestKey(lineups.providerEventId()).equals(summary.requestKey())
                || summary.httpStatus() != 200
                || summary.schemaStatus() != RawSnapshotSchemaStatus.PARSED
                || !EventLineupsV3Parser.PARSER_VERSION.equals(summary.parserVersion())
                || !source.payloadSha256().equals(summary.payloadSha256())
                || summary.payloadSizeBytes() != payload.length
                || !summary.payloadSha256().equals(Sha256.hex(payload))
                || !SensitiveContentScanner.findings(payload).isEmpty()) {
            return LineupCountryOverlay.empty();
        }
        try {
            JsonNode root = JSON.readTree(payload);
            return countries(lineups, root);
        }
        catch (JacksonException | IllegalArgumentException | IllegalStateException exception) {
            return LineupCountryOverlay.empty();
        }
    }

    private static LineupCountryOverlay countries(EventLineups lineups, JsonNode root) {
        if (root == null || !root.isObject()
                || !matchesConfirmed(root.get("confirmed"), lineups.confirmed())) {
            return LineupCountryOverlay.empty();
        }
        if (!lineups.confirmed() && root.get("home") == null && root.get("away") == null) {
            return LineupCountryOverlay.empty();
        }
        Set<LineupCountryOverlay.PlayerKey> expected = expectedPlayers(lineups);
        if (expected.isEmpty()) {
            return LineupCountryOverlay.empty();
        }
        var countries = new HashMap<LineupCountryOverlay.PlayerKey, ProviderCountry>();
        var conflicted = new HashSet<LineupCountryOverlay.PlayerKey>();
        if (!readTeam(root.get("home"), lineups.home(), expected, countries, conflicted)
                || !readTeam(root.get("away"), lineups.away(), expected, countries, conflicted)) {
            return LineupCountryOverlay.empty();
        }
        return LineupCountryOverlay.of(countries);
    }

    private static boolean matchesConfirmed(JsonNode node, boolean expected) {
        return node != null && node.isBoolean() && node.booleanValue() == expected;
    }

    private static Set<LineupCountryOverlay.PlayerKey> expectedPlayers(EventLineups lineups) {
        var expected = new HashSet<LineupCountryOverlay.PlayerKey>();
        expected.addAll(expectedPlayers(lineups.home()));
        expected.addAll(expectedPlayers(lineups.away()));
        return expected;
    }

    private static Set<LineupCountryOverlay.PlayerKey> expectedPlayers(TeamLineup lineup) {
        var expected = new HashSet<LineupCountryOverlay.PlayerKey>();
        lineup.players().forEach(player -> expected.add(
                LineupCountryOverlay.PlayerKey.roster(lineup.side(), player.providerPlayerId())));
        lineup.missingPlayers().ifPresent(players -> players.forEach(player -> expected.add(
                LineupCountryOverlay.PlayerKey.missingPlayer(lineup.side(), player.providerPlayerId()))));
        return expected;
    }

    private static boolean readTeam(
            JsonNode node,
            TeamLineup lineup,
            Set<LineupCountryOverlay.PlayerKey> expected,
            Map<LineupCountryOverlay.PlayerKey, ProviderCountry> countries,
            Set<LineupCountryOverlay.PlayerKey> conflicted) {
        if (node == null || !node.isObject()) {
            return false;
        }
        JsonNode players = node.get("players");
        if (!readEntries(players, lineup.side(), false, expected, countries, conflicted)) {
            return false;
        }
        JsonNode missing = node.get("missingPlayers");
        if (missing == null || missing.isNull()) {
            return lineup.missingPlayers().isEmpty();
        }
        return lineup.missingPlayers().isPresent()
                && readEntries(missing, lineup.side(), true, expected, countries, conflicted);
    }

    private static boolean readEntries(
            JsonNode entries,
            LineupSide side,
            boolean missingPlayer,
            Set<LineupCountryOverlay.PlayerKey> expected,
            Map<LineupCountryOverlay.PlayerKey, ProviderCountry> countries,
            Set<LineupCountryOverlay.PlayerKey> conflicted) {
        if (entries == null || !entries.isArray() || entries.size() > MAXIMUM_PLAYERS_PER_SIDE) {
            return false;
        }
        for (int index = 0; index < entries.size(); index++) {
            JsonNode entry = entries.get(index);
            if (entry == null || !entry.isObject()) {
                return false;
            }
            JsonNode player = entry.get("player");
            Long providerPlayerId = positiveId(player == null ? null : player.get("id"));
            if (player == null || !player.isObject() || providerPlayerId == null) {
                return false;
            }
            var key = missingPlayer
                    ? LineupCountryOverlay.PlayerKey.missingPlayer(side, providerPlayerId)
                    : LineupCountryOverlay.PlayerKey.roster(side, providerPlayerId);
            if (!expected.contains(key)) {
                return false;
            }
            ProviderPeopleParserSupport.country(
                            player.get("country"),
                            "$." + side.name().toLowerCase() + (missingPlayer ? ".missingPlayers" : ".players")
                                    + "[" + index + "].player.country",
                            ignored -> { })
                    .ifPresent(country -> merge(countries, conflicted, key, country));
        }
        return true;
    }

    private static Long positiveId(JsonNode node) {
        if (node == null || !node.isIntegralNumber() || !node.canConvertToLong()) {
            return null;
        }
        long value = node.longValue();
        return value > 0 ? value : null;
    }

    private static void merge(
            Map<LineupCountryOverlay.PlayerKey, ProviderCountry> countries,
            Set<LineupCountryOverlay.PlayerKey> conflicted,
            LineupCountryOverlay.PlayerKey key,
            ProviderCountry country) {
        if (conflicted.contains(key)) {
            return;
        }
        ProviderCountry previous = countries.putIfAbsent(key, country);
        if (previous != null && !previous.equals(country)) {
            countries.remove(key);
            conflicted.add(key);
        }
    }

    private static String expectedRequestKey(long providerEventId) {
        return SofascoreEndpointType.EVENT_LINEUPS.name() + "|eventId=" + providerEventId;
    }
}
