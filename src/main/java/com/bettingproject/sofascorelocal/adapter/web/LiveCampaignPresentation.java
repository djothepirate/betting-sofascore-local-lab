package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservationView;
import com.bettingproject.sofascorelocal.domain.eventdata.*;
import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.*;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.port.CanonicalEventStore;
import com.bettingproject.sofascorelocal.port.J5EventDataStore;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** An explicit browser projection: no raw payload, worker identity or transport internals. */
@Component
public class LiveCampaignPresentation {
    private final CanonicalEventStore events;
    private final J5EventDataStore data;
    private final Clock clock;
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final List<SofascoreEndpointType> FAMILIES = List.of(SofascoreEndpointType.EVENT_DETAILS,
            SofascoreEndpointType.EVENT_STATISTICS, SofascoreEndpointType.EVENT_INCIDENTS,
            SofascoreEndpointType.EVENT_LINEUPS);

    @Autowired
    public LiveCampaignPresentation(CanonicalEventStore events, J5EventDataStore data) {
        this(events, data, Clock.systemUTC());
    }

    LiveCampaignPresentation(CanonicalEventStore events, J5EventDataStore data, Clock clock) {
        this.events = events;
        this.data = data;
        this.clock = clock;
    }

    public Campaign state(CampaignView view) {
        Instant observedAt = clock.instant();
        return new Campaign(view.manifest().campaignId(), view.revision(), view.state(), view.reason(),
                view.manifest().preparedAt(), view.startedAt(), view.endsAt(), view.reservedCalls(),
                view.manifest().maximumCalls(), view.receivedBytes(), view.manifest().maximumBytes(),
                view.events().stream().map(event -> event(view, event, observedAt)).toList());
    }

    private Event event(CampaignView campaign, EventView event, Instant observedAt) {
        FamilyCursor j4 = event.families().stream()
                .filter(f -> f.endpoint() == SofascoreEndpointType.EVENT_DETAILS).findFirst().orElse(null);
        long observationId = j4 != null && j4.normalized() != null
                && j4.normalized().canonicalObservationId() != null
                ? j4.normalized().canonicalObservationId() : event.target().sourceObservationId();
        CanonicalEventObservationView identity = events.findByObservationId(
                event.target().canonicalEventId(), observationId).orElse(null);
        String sportStatus = identity == null ? "UNKNOWN" : identity.status().type();
        if (j4 != null && j4.latestSuccessfulResult() != null
                && j4.latestSuccessfulResult().publication().sportStatus() != null) {
            sportStatus = j4.latestSuccessfulResult().publication().sportStatus();
        }
        Long sourceSnapshot = j4 == null ? null : campaign.attempts().stream()
                .filter(a -> a.attempt().attemptId().equals(j4.lastSuccessfulAttemptId()))
                .map(AttemptView::snapshotId).filter(java.util.Objects::nonNull).findFirst().orElse(null);
        if (sourceSnapshot == null && identity != null && identity.source().snapshotId().isPresent()) {
            sourceSnapshot = identity.source().snapshotId().orElseThrow();
        }
        Instant sourceReceivedAt = j4 != null && j4.lastSuccessfulAt() != null ? j4.lastSuccessfulAt()
                : identity == null ? null : identity.source().receivedAt();
        var latestCanonical = events.findLatestByCanonicalId(event.target().canonicalEventId());
        boolean canonicalCurrent = latestCanonical.isEmpty() || sourceReceivedAt != null
                && !sourceReceivedAt.isBefore(latestCanonical.orElseThrow().source().receivedAt());
        return new Event(event.target().canonicalEventId(), event.target().providerEventId(),
                identity == null ? Long.toString(event.target().providerEventId())
                        : identity.homeTeam().name() + " — " + identity.awayTeam().name(),
                identity == null ? "—" : identity.tournament().map(t -> t.name()).orElse("—"),
                identity == null ? "—" : identity.startsAt().atZone(ZoneId.of("Europe/Paris")).toString(),
                sportStatus, score(j4), event.state(), "STOPPED_ALREADY_FINISHED".equals(event.reason())
                        ? "Rencontre déjà terminée dans les observations locales au lancement ; aucun appel fournisseur."
                        : event.reason(), event.nextDueAt(), event.reservedCalls(),
                campaign.manifest().maximumCallsPerEvent(), event.receivedBytes(), event.missedCycles(),
                event.finalComplete(), sourceSnapshot, sourceReceivedAt, canonicalCurrent,
                FAMILIES.stream().map(endpoint -> event.families().stream()
                        .filter(f -> f.endpoint() == endpoint).findFirst().orElseGet(() ->
                                new FamilyCursor(endpoint, null, null, null, null, null, null, null,
                                        NormalizedReferences.none(), null, null)))
                        .map(f -> family(campaign, event, f, observedAt)).toList());
    }

    private static String score(FamilyCursor j4) {
        if (j4 == null || j4.latestSuccessfulResult() == null) return "—";
        String projection = j4.latestSuccessfulResult().publication().projectionJson();
        if (projection == null) return "—";
        try {
            JsonNode root = JSON.readTree(projection);
            return scoreSide(root.path("homeScore")) + " – " + scoreSide(root.path("awayScore"));
        } catch (JacksonException exception) {
            return "—";
        }
    }

    private static String scoreSide(JsonNode side) {
        if (!"VALUE".equals(side.path("presence").asText())) return "—";
        JsonNode current = side.path("value").path("current");
        return "VALUE".equals(current.path("presence").asText()) && current.path("value").isIntegralNumber()
                ? current.path("value").asText() : "—";
    }

    private Family family(CampaignView campaign, EventView event, FamilyCursor cursor, Instant observedAt) {
        Publication latest = cursor.latestResult() == null ? null : cursor.latestResult().publication();
        Publication successful = cursor.latestSuccessfulResult() == null
                ? null : cursor.latestSuccessfulResult().publication();
        AttemptView received = campaign.attempts().stream()
                .filter(a -> a.attempt().attemptId().equals(cursor.lastReceivedAttemptId()))
                .findFirst().orElse(null);
        AttemptView successfulAttempt = campaign.attempts().stream()
                .filter(a -> a.attempt().attemptId().equals(cursor.lastSuccessfulAttemptId()))
                .findFirst().orElse(null);
        AttemptView attempted = campaign.attempts().stream()
                .filter(a -> a.attempt().attemptId().equals(cursor.lastAttemptId()))
                .findFirst().orElse(null);
        Table table = new Table(List.of(), List.of());
        String payloadSha256 = null;
        if (cursor.normalized() != null && cursor.normalized().j5ObservationId() != null) {
            var observation = data.findByObservationId(event.target().canonicalEventId(), cursor.endpoint(),
                    cursor.normalized().j5ObservationId());
            if (observation.isPresent()) {
                table = table(observation.orElseThrow().data());
                payloadSha256 = observation.orElseThrow().source().payloadSha256();
            }
        } else if (cursor.normalized() != null && cursor.normalized().canonicalObservationId() != null) {
            payloadSha256 = events.findByObservationId(event.target().canonicalEventId(),
                    cursor.normalized().canonicalObservationId()).map(e -> e.source().payloadSha256()).orElse(null);
        }
        boolean pending = cursor.latestResult() == null
                || !cursor.latestResult().attemptId().equals(cursor.lastAttemptId());
        return new Family(cursor.endpoint().name(), label(cursor.endpoint()),
                cursor.lastAttemptId() == null ? "NOT_REQUESTED" : pending ? "PENDING" : latest.outcome(),
                pending ? null : latest.code(),
                pending ? null : latest.scope(),
                attempted == null ? null : attempted.attempt().reservedAt(),
                attempted == null || attempted.dispatchAuthorizedAt() == null ? null
                        : Math.max(0, java.time.Duration.between(attempted.attempt().dueAt(),
                                attempted.dispatchAuthorizedAt()).toMillis()),
                cursor.lastReceivedAt(), cursor.lastSuccessfulAt(), cursor.lastChangedAt(),
                received == null ? null : received.snapshotId(),
                received == null ? null : received.occurrenceId(),
                successfulAttempt == null ? null : successfulAttempt.snapshotId(),
                successful == null ? null : successful.parserVersion(), payloadSha256,
                cursor.normalized() == null ? null : cursor.normalized().normalizedSha256(),
                pending ? null : latest.completenessStatus(),
                pending ? null : latest.completenessScore(),
                cursor.lastSuccessfulAttemptId() != null
                        && !cursor.lastSuccessfulAttemptId().equals(cursor.lastAttemptId()),
                freshness(campaign, event, cursor, observedAt), table);
    }

    private static Freshness freshness(CampaignView campaign, EventView event, FamilyCursor cursor, Instant now) {
        boolean finalJ4 = cursor.endpoint() == SofascoreEndpointType.EVENT_DETAILS
                && "FINALIZING".equals(event.state());
        boolean frozen = terminal(event.state()) || terminal(campaign.state()) || finalJ4;
        Instant baseline = campaign.startedAt() == null ? campaign.manifest().preparedAt() : campaign.startedAt();
        Instant phaseStarted = campaign.transitions().stream()
                .filter(t -> event.target().canonicalEventId().equals(t.canonicalEventId())
                        && event.state().equals(t.state()))
                .map(Transition::changedAt).max(Instant::compareTo).orElse(baseline);
        Instant ageAsOf = now;
        if (frozen) {
            ageAsOf = campaign.transitions().stream()
                    .filter(t -> event.target().canonicalEventId().equals(t.canonicalEventId())
                            && (terminal(t.state()) || finalJ4 && "FINALIZING".equals(t.state())))
                    .map(Transition::changedAt).min(Instant::compareTo)
                    .orElseGet(() -> campaign.transitions().stream()
                            .filter(t -> t.canonicalEventId() == null && terminal(t.state()))
                            .map(Transition::changedAt).min(Instant::compareTo).orElse(baseline));
        }
        long interval = 0;
        if (!frozen && "RUNNING".equals(campaign.state())) {
            if (cursor.endpoint() == SofascoreEndpointType.EVENT_DETAILS) {
                interval = "COLLECTING".equals(event.state()) ? 300
                        : "WAITING_START".equals(event.state()) || "CHECKING_FINISH".equals(event.state()) ? 60 : 0;
            } else if ("COLLECTING".equals(event.state()) || "CHECKING_FINISH".equals(event.state())) interval = 60;
        }
        Instant success = cursor.lastSuccessfulAt();
        Instant anchor = success == null ? phaseStarted : success;
        if (cursor.endpoint() == SofascoreEndpointType.EVENT_DETAILS && "CHECKING_FINISH".equals(event.state())
                && anchor.isBefore(phaseStarted)) anchor = phaseStarted;
        String state = frozen ? "FROZEN" : interval == 0 ? "NOT_EXPECTED"
                : now.isAfter(anchor.plusSeconds(interval * 2)) ? "STALE"
                : success == null ? "AWAITING_SUCCESS" : "FRESH";
        String label = switch (state) {
            case "FROZEN" -> "Âge figé à la fin du suivi";
            case "NOT_EXPECTED" -> "Aucune collecte périodique attendue dans cette phase";
            case "STALE" -> "En retard / périmée : aucun succès depuis plus de deux intervalles";
            case "AWAITING_SUCCESS" -> "En attente du premier succès";
            default -> "Dans l’intervalle attendu";
        };
        Long age = cursor.lastReceivedAt() == null ? null
                : Math.max(0, Duration.between(cursor.lastReceivedAt(), ageAsOf).toSeconds());
        return new Freshness(state, label, interval, age, ageAsOf, frozen);
    }

    private static boolean terminal(String state) {
        return state.startsWith("STOPPED") || "FINISHED_CONFIRMED".equals(state)
                || "COMPLETED".equals(state) || "CLEANUP_REQUIRED".equals(state);
    }

    private static String label(SofascoreEndpointType endpoint) {
        return switch (endpoint) {
            case EVENT_DETAILS -> "Statut J4";
            case EVENT_STATISTICS -> "Statistiques";
            case EVENT_INCIDENTS -> "Incidents";
            case EVENT_LINEUPS -> "Compositions";
            default -> throw new IllegalArgumentException("endpoint outside live scope");
        };
    }

    private static Table table(J5EventData value) {
        if (value instanceof EventStatistics statistics) {
            return new Table(List.of("Période", "Groupe", "Statistique", "Domicile", "Extérieur"),
                    statistics.metrics().stream().map(m -> List.of(m.period(), m.groupName(), m.metricName(),
                            m.homeValue().orElse("—"), m.awayValue().orElse("—"))).toList());
        }
        if (value instanceof EventIncidents incidents) {
            return new Table(List.of("Minute", "Type", "Équipe", "Joueur", "Score", "Détail", "Motif"),
                    incidents.incidents().stream().map(i -> List.of(i.minuteLabel(), i.incidentType(),
                            i.sideLabel(), i.hasReplacementPlayers()
                                    ? i.playerOutName().orElse("—") + " → " + i.playerInName().orElse("—")
                                    : i.playerName().orElse("—"),
                            i.homeScore().isPresent() ? i.homeScore().orElseThrow() + "–"
                                    + i.awayScore().orElseThrow() : "—", i.detailLabel(), i.motifLabel())).toList());
        }
        EventLineups lineups = (EventLineups) value;
        List<List<String>> rows = new ArrayList<>();
        for (TeamLineup side : List.of(lineups.home(), lineups.away())) {
            for (EventLineupPlayer p : side.players()) {
                rows.add(List.of(side.side().name(), side.formation().orElse("—"), p.name(),
                        p.shirtNumber().map(Object::toString).orElse("—"), p.position().orElse("—"),
                        p.starter() ? "Titulaire" : "Remplaçant", lineups.confirmed() ? "Confirmée" : "Provisoire"));
            }
        }
        return new Table(List.of("Équipe", "Formation", "Joueur", "Numéro", "Position", "Rôle", "Composition"), rows);
    }

    public record Campaign(UUID campaignId, long revision, String state, String reason, Instant preparedAt,
                           Instant startedAt, Instant endsAt, int reservedCalls, int maximumCalls,
                           long receivedBytes, long maximumBytes, List<Event> events) { }
    public record Event(UUID canonicalEventId, long providerEventId, String title, String competition,
                        String startsAtParis, String sportStatus, String score, String state, String reason,
                        Instant nextDueAt, int reservedCalls, int maximumCalls, long receivedBytes,
                        long missedCycles, boolean finalComplete, Long sourceSnapshotId,
                        Instant sourceReceivedAt, boolean canonicalCurrent, List<Family> families) { }
    public record Family(String endpoint, String label, String outcome, String code, String scope,
                         Instant lastAttemptAt, Long authorizationDelayMillis,
                         Instant lastReceivedAt, Instant lastSuccessfulAt, Instant lastChangedAt,
                         Long receivedSnapshotId, Long receivedOccurrenceId, Long dataSnapshotId,
                         String parserVersion, String payloadSha256,
                         String normalizedSha256, String completeness, Integer completenessScore,
                         boolean previousData, Freshness freshness, Table table) { }
    public record Freshness(String state, String label, long expectedIntervalSeconds,
                            Long receivedAgeSeconds, Instant ageAsOf, boolean frozen) { }
    public record Table(List<String> columns, List<List<String>> rows) { }
}
