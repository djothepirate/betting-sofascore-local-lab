package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.application.live.LiveCampaignService.RuntimeStatus;
import com.bettingproject.sofascorelocal.application.live.LiveCampaignDiagnostic;
import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightTransportDiagnostic;
import com.bettingproject.sofascorelocal.application.event.J4EventResult;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservationView;
import com.bettingproject.sofascorelocal.domain.eventdata.*;
import com.bettingproject.sofascorelocal.domain.live.LiveCampaignData.*;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.port.CanonicalEventStore;
import com.bettingproject.sofascorelocal.port.EventDetailsStore;
import com.bettingproject.sofascorelocal.port.J5EventDataStore;
import com.bettingproject.sofascorelocal.port.LiveDiagnosticStore;
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
import java.util.Optional;
import java.util.UUID;

/** An explicit browser projection: no raw payload, worker identity or transport internals. */
@Component
public class LiveCampaignPresentation {
    public static final int EVENTS_PER_PAGE = 10;
    private final CanonicalEventStore events;
    private final J5EventDataStore data;
    private final EventDetailsStore details;
    private final Clock clock;
    private final LiveDiagnosticStore diagnostics;
    private final LineupCountryOverlayResolver lineupCountries;
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final List<SofascoreEndpointType> FAMILIES = List.of(SofascoreEndpointType.EVENT_DETAILS,
            SofascoreEndpointType.EVENT_STATISTICS, SofascoreEndpointType.EVENT_INCIDENTS,
            SofascoreEndpointType.EVENT_LINEUPS);

    public LiveCampaignPresentation(CanonicalEventStore events, J5EventDataStore data) {
        this(events, data, null, Clock.systemUTC(), null, LineupCountryOverlayResolver.none());
    }

    public LiveCampaignPresentation(CanonicalEventStore events, J5EventDataStore data, EventDetailsStore details) {
        this(events, data, details, Clock.systemUTC(), null, LineupCountryOverlayResolver.none());
    }

    @Autowired
    public LiveCampaignPresentation(CanonicalEventStore events, J5EventDataStore data, EventDetailsStore details,
                                    LiveDiagnosticStore diagnostics, LineupCountryOverlayResolver lineupCountries) {
        this(events, data, details, Clock.systemUTC(), diagnostics, lineupCountries);
    }

    LiveCampaignPresentation(CanonicalEventStore events, J5EventDataStore data, Clock clock) {
        this(events, data, null, clock, null, LineupCountryOverlayResolver.none());
    }

    LiveCampaignPresentation(CanonicalEventStore events, J5EventDataStore data, EventDetailsStore details, Clock clock) {
        this(events, data, details, clock, null, LineupCountryOverlayResolver.none());
    }

    LiveCampaignPresentation(CanonicalEventStore events, J5EventDataStore data, EventDetailsStore details, Clock clock,
                             LiveDiagnosticStore diagnostics) {
        this(events, data, details, clock, diagnostics, LineupCountryOverlayResolver.none());
    }

    LiveCampaignPresentation(CanonicalEventStore events, J5EventDataStore data, EventDetailsStore details, Clock clock,
                             LiveDiagnosticStore diagnostics, LineupCountryOverlayResolver lineupCountries) {
        this.events = events;
        this.data = data;
        this.details = details;
        this.clock = clock;
        this.diagnostics = diagnostics;
        this.lineupCountries = lineupCountries == null ? LineupCountryOverlayResolver.none() : lineupCountries;
    }

    public Campaign state(CampaignView view) {
        return state(view, null);
    }

    public Campaign state(CampaignView view, RuntimeStatus runtimeStatus) {
        return state(view, runtimeStatus, view.events(), null);
    }

    /** Slice before building rich event projections; campaign totals still use the whole ledger. */
    public Campaign page(CampaignView view, RuntimeStatus runtimeStatus, int requestedPage) {
        if (requestedPage < 1) throw new IllegalArgumentException("LIVE_PAGE_INVALID");
        int total = view.events().size();
        int pages = Math.max(1, (total + EVENTS_PER_PAGE - 1) / EVENTS_PER_PAGE);
        int number = Math.min(requestedPage, pages);
        int from = (number - 1) * EVENTS_PER_PAGE;
        // The store returns the frozen manifest's target_order, including stopped/finished events.
        return state(view, runtimeStatus, view.events().subList(from, Math.min(from + EVENTS_PER_PAGE, total)),
                new Pagination(number, EVENTS_PER_PAGE, total, pages));
    }

    private Campaign state(CampaignView view, RuntimeStatus runtimeStatus,
                           List<EventView> displayedEvents, Pagination pagination) {
        Instant observedAt = clock.instant();
        return new Campaign(view.manifest().campaignId(), view.revision(), view.state(), reason(view.reason()),
                view.manifest().preparedAt(), view.startedAt(), view.endsAt(), view.reservedCalls(),
                view.manifest().maximumCalls(), view.receivedBytes(), view.manifest().maximumBytes(),
                displayedEvents.stream().map(event -> event(view, event, observedAt)).toList(),
                runtimeStatus == null ? null : new RuntimeObservation(runtimeStatus.state(), runtimeStatus.reason(),
                        runtimeStatus.collectionStopped(), runtimeStatus.cleanupPending(), runtimeStatus.cleanupInProgress(),
                        runtimeStatus.cleanupInProgress() ? "Collecte arrêtée / clôture locale en cours."
                                : runtimeStatus.cleanupPending() ? "Collecte arrêtée / clôture locale requise."
                                : runtimeStatus.collectionStopped() ? "Collecte arrêtée."
                                : "Collecte en cours ; un incident a été enregistré.", runtimeStatus.firstFailure(), runtimeStatus.cleanupFailure()),
                cadence(view, observedAt, runtimeStatus), pagination);
    }

    private static Cadence cadence(CampaignView view, Instant now, RuntimeStatus runtimeStatus) {
        String policyVersion = view.manifest().policyVersion();
        if (!grouped(policyVersion)) return null;
        long interval = view.manifest().cycleInterval().toSeconds();
        boolean minutePolicy = "live-v7".equals(policyVersion) || "live-v8".equals(policyVersion);
        // Prematch V7/V8 is sparse; one J4/minute at kickoff is the conservative waiting rate.
        double waitingRate = minutePolicy ? 1 : 120.0 / interval;
        double playingRate = minutePolicy ? 240.0 / interval : 180.0 / interval + 0.2;
        List<EventView> active = view.events().stream().filter(e -> !terminal(e.state())).toList();
        boolean stopped = terminal(view.state()) || runtimeStatus != null && runtimeStatus.collectionStopped();
        double rate = stopped ? 0 : active.stream().mapToDouble(e -> "WAITING_START".equals(e.state()) ? waitingRate : playingRate).sum();
        long seconds = 0;
        if (rate > 0 && !terminal(view.state())) {
            // Four calls per active match remain reserved for a last status/final-family pass.
            seconds = (long) (Math.max(0, view.manifest().maximumCalls() - view.reservedCalls() - 4 * active.size()) * 60 / rate);
            for (EventView event : active) {
                double eventRate = "WAITING_START".equals(event.state()) ? waitingRate : playingRate;
                seconds = Math.min(seconds, (long) (Math.max(0,
                        view.manifest().maximumCallsPerEvent() - event.reservedCalls() - 4) * 60 / eventRate));
            }
            seconds = Math.min(seconds, view.endsAt() == null ? view.manifest().duration().toSeconds()
                    : Math.max(0, Duration.between(now, view.endsAt()).toSeconds()));
        }
        return new Cadence(policyVersion, interval, minutePolicy ? 60 : 300,
                view.manifest().qualifiedMatchCapacity(), seconds, rate);
    }

    private static boolean grouped(String policyVersion) {
        return "live-v4".equals(policyVersion) || "live-v5".equals(policyVersion)
                || "live-v6".equals(policyVersion) || "live-v7".equals(policyVersion)
                || "live-v8".equals(policyVersion);
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
        J4EventResult result = result(j4, identity);
        return new Event(event.target().canonicalEventId(), event.target().providerEventId(),
                identity == null ? Long.toString(event.target().providerEventId())
                        : identity.homeTeam().name() + " — " + identity.awayTeam().name(),
                identity == null ? "—" : identity.tournament().map(t -> t.name()).orElse("—"),
                identity == null ? "—" : identity.startsAt().atZone(ZoneId.of("Europe/Paris")).toString(),
                sportStatus, "finished".equals(sportStatus) && result.awarded() ? "Victoire sur tapis vert"
                        : sportStatusLabel(sportStatus, identity), result.score(), event.state(), reason(event.reason()),
                event.nextDueAt(), event.reservedCalls(),
                campaign.manifest().maximumCallsPerEvent(), event.receivedBytes(), event.missedCycles(),
                event.finalComplete(), sourceSnapshot, sourceReceivedAt, canonicalCurrent,
                campaign.blocksSelection(event.target().canonicalEventId()),
                FAMILIES.stream().map(endpoint -> event.families().stream()
                        .filter(f -> f.endpoint() == endpoint).findFirst().orElseGet(() ->
                                new FamilyCursor(endpoint, null, null, null, null, null, null, null,
                                        NormalizedReferences.none(), null, null)))
                        .map(f -> family(campaign, event, f, observedAt, identity)).toList());
    }

    private static String sportStatusLabel(String sportStatus, CanonicalEventObservationView identity) {
        // Use the same persisted observation as the campaign, never a newer manual observation.
        return "inprogress".equals(sportStatus) && identity != null
                && sportStatus.equals(identity.status().type())
                ? identity.status().description().orElse(sportStatus) : sportStatus;
    }

    private static String reason(String reason) {
        if (reason == null) return null;
        return switch (reason) {
            case "PREPARATION_CANCELLED" -> "Préparation annulée. Aucune collecte n’a été lancée ; la sélection et son historique restent consultables.";
            case "STOPPED_ALREADY_FINISHED" -> "Rencontre déjà terminée dans les observations locales au lancement ; aucun appel fournisseur.";
            case "STOPPED_ALREADY_POSTPONED" -> "Rencontre reportée dans les observations locales au lancement ; aucun appel fournisseur.";
            case "STOPPED_POSTPONED" -> "Rencontre reportée selon J4 ; suivi arrêté pour cette rencontre.";
            default -> reason;
        };
    }

    private J4EventResult result(FamilyCursor j4, CanonicalEventObservationView identity) {
        if (details != null && identity != null) {
            var detail = j4 != null && j4.normalized() != null && j4.normalized().detailObservationId() != null
                    ? details.findByObservationId(identity.identity().value(), j4.normalized().detailObservationId())
                    : details.findLatest(identity.identity().value());
            var matching = detail.filter(d -> d.source().sourceReference().equals(identity.source().sourceReference())
                    && d.source().payloadSha256().equals(identity.source().payloadSha256())
                    && d.details().status().equals(identity.status())
                    && List.of("event-details-v3", "event-details-v4").contains(d.source().parserVersion()));
            if (matching.isPresent()) return J4EventResult.from(matching.orElseThrow().details());
        }
        if (j4 == null || j4.latestSuccessfulResult() == null) return J4EventResult.absent();
        String projection = j4.latestSuccessfulResult().publication().projectionJson();
        if (projection == null) return J4EventResult.absent();
        try {
            JsonNode root = JSON.readTree(projection);
            JsonNode awarded = root.path("isAwarded");
            return new J4EventResult("VALUE".equals(awarded.path("presence").asText())
                    && awarded.path("value").isBoolean() && awarded.path("value").booleanValue(),
                    scoreSide(root.path("homeScore")), scoreSide(root.path("awayScore")));
        } catch (JacksonException exception) {
            return J4EventResult.absent();
        }
    }

    private static Optional<Integer> scoreSide(JsonNode side) {
        if (!"VALUE".equals(side.path("presence").asText())) return Optional.empty();
        JsonNode display = side.path("value").path("display");
        return "VALUE".equals(display.path("presence").asText()) && display.path("value").isIntegralNumber()
                && display.path("value").canConvertToInt() && display.path("value").intValue() >= 0
                && display.path("value").intValue() <= 999
                ? Optional.of(display.path("value").intValue()) : Optional.empty();
    }

    private Family family(CampaignView campaign, EventView event, FamilyCursor cursor, Instant observedAt,
                          CanonicalEventObservationView identity) {
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
        StatisticsPresentation.View statistics = null;
        LineupsPresentation.View lineups = null;
        IncidentPresentation.View incidents = null;
        EventDetailsPresentation.View eventDetails = null;
        String payloadSha256 = null;
        if (details != null && cursor.endpoint() == SofascoreEndpointType.EVENT_DETAILS
                && cursor.normalized() != null && cursor.normalized().detailObservationId() != null) {
            eventDetails = details.findByObservationId(event.target().canonicalEventId(),
                    cursor.normalized().detailObservationId())
                    .map(observation -> EventDetailsPresentation.from(observation.details())).orElse(null);
        }
        if (cursor.normalized() != null && cursor.normalized().j5ObservationId() != null) {
            var observation = data.findByObservationId(event.target().canonicalEventId(), cursor.endpoint(),
                    cursor.normalized().j5ObservationId());
            if (observation.isPresent()) {
                table = table(observation.orElseThrow().data());
                if (observation.orElseThrow().data() instanceof EventStatistics values) {
                    statistics = StatisticsPresentation.from(values);
                }
                if (observation.orElseThrow().data() instanceof EventLineups values
                        && observation.orElseThrow().completeness().status() != J5CompletenessStatus.UNAVAILABLE) {
                    lineups = LineupsPresentation.from(values,
                            identity == null ? "Domicile" : identity.homeTeam().name(),
                            identity == null ? "Extérieur" : identity.awayTeam().name(),
                            lineupCountries.resolve(observation.orElseThrow()));
                }
                if (observation.orElseThrow().data() instanceof EventIncidents values
                        && observation.orElseThrow().completeness().status() != J5CompletenessStatus.UNAVAILABLE) {
                    incidents = IncidentPresentation.from(values,
                            identity == null ? "Domicile" : identity.homeTeam().name(),
                            identity == null ? "Extérieur" : identity.awayTeam().name());
                }
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
                freshness(campaign, event, cursor, observedAt), table, statistics,
                cursor.schedule() == null ? null : new CollectionSchedule(
                        terminal(event.state()) || terminal(campaign.state()) ? null : cursor.schedule().nextDueAt(),
                        cursor.schedule().intervalSeconds(), cursor.schedule().missedCycles(),
                        cursor.schedule().nextDueAt() == null || terminal(event.state()) || terminal(campaign.state())
                                ? 0 : Math.max(0, Duration.between(cursor.schedule().nextDueAt(), observedAt).toMillis())),
                lineups, incidents,
                !pending && latest.code() != null && latest.code().startsWith("PLAYWRIGHT_TIMEOUT")
                        && diagnostics != null && attempted != null
                        ? diagnostics.findTransport(campaign.manifest().campaignId(), attempted.attempt().attemptId()).orElse(null)
                        : null, eventDetails);
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
        long cycleSeconds = campaign.manifest().cycleInterval().toSeconds();
        if (!frozen && "RUNNING".equals(campaign.state())) {
            if (grouped(campaign.manifest().policyVersion())) {
                interval = cursor.schedule() == null || cursor.schedule().nextDueAt() == null ? 0 : cursor.schedule().intervalSeconds();
            } else if (cursor.endpoint() == SofascoreEndpointType.EVENT_DETAILS) {
                interval = "COLLECTING".equals(event.state()) ? Math.max(300, cycleSeconds)
                        : "WAITING_START".equals(event.state()) || "CHECKING_FINISH".equals(event.state()) ? cycleSeconds : 0;
            } else if ("COLLECTING".equals(event.state()) || "CHECKING_FINISH".equals(event.state())
                    || cursor.endpoint() == SofascoreEndpointType.EVENT_LINEUPS
                        && "WAITING_START".equals(event.state())
                        && "live-v3".equals(campaign.manifest().policyVersion())) interval = cycleSeconds;
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
                || "COMPLETED".equals(state) || "CLEANUP_REQUIRED".equals(state) || "INTERRUPTED".equals(state);
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
                                    + i.awayScore().orElseThrow() : "—", i.detailLabel(),
                            IncidentPresentation.motifLabel(i))).toList());
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
                           long receivedBytes, long maximumBytes, List<Event> events, RuntimeObservation runtimeStatus,
                           Cadence cadence, Pagination pagination) {
        public Campaign(UUID campaignId, long revision, String state, String reason, Instant preparedAt,
                Instant startedAt, Instant endsAt, int reservedCalls, int maximumCalls, long receivedBytes,
                long maximumBytes, List<Event> events, RuntimeObservation runtimeStatus, Cadence cadence) {
            this(campaignId, revision, state, reason, preparedAt, startedAt, endsAt, reservedCalls,
                    maximumCalls, receivedBytes, maximumBytes, events, runtimeStatus, cadence, null);
        }
        public Campaign(UUID campaignId, long revision, String state, String reason, Instant preparedAt,
                Instant startedAt, Instant endsAt, int reservedCalls, int maximumCalls, long receivedBytes,
                long maximumBytes, List<Event> events, RuntimeObservation runtimeStatus) {
            this(campaignId, revision, state, reason, preparedAt, startedAt, endsAt, reservedCalls,
                    maximumCalls, receivedBytes, maximumBytes, events, runtimeStatus, null);
        }
    }
    public record Pagination(int number, int size, int totalElements, int totalPages) {
        public int firstElement() { return totalElements == 0 ? 0 : (number - 1) * size + 1; }
        public int lastElement() { return Math.min(number * size, totalElements); }
    }
    public record Cadence(String policyVersion, long targetSeconds, long lineupSeconds, int qualifiedCapacity,
                          long estimatedRemainingSeconds, double estimatedCallsPerMinute) {
        public long estimatedMinutes() { return estimatedRemainingSeconds / 60; }
    }
    /** Local process observation kept separate from the persisted campaign and event states. */
    public record RuntimeObservation(String state, String reason, boolean collectionStopped,
                                     boolean cleanupPending, boolean cleanupInProgress, String label,
                                     LiveCampaignDiagnostic firstFailure, LiveCampaignDiagnostic cleanupFailure) {
        public RuntimeObservation(String state, String reason, boolean collectionStopped,
                                  boolean cleanupPending, boolean cleanupInProgress, String label) {
            this(state, reason, collectionStopped, cleanupPending, cleanupInProgress, label, null, null);
        }
    }
    public record Event(UUID canonicalEventId, long providerEventId, String title, String competition,
                        String startsAtParis, String sportStatus, String sportStatusLabel, String score, String state, String reason,
                        Instant nextDueAt, int reservedCalls, int maximumCalls, long receivedBytes,
                        long missedCycles, boolean finalComplete, Long sourceSnapshotId,
                        Instant sourceReceivedAt, boolean canonicalCurrent, boolean selectionBlocked, List<Family> families) { }
    public record Family(String endpoint, String label, String outcome, String code, String scope,
                         Instant lastAttemptAt, Long authorizationDelayMillis,
                         Instant lastReceivedAt, Instant lastSuccessfulAt, Instant lastChangedAt,
                         Long receivedSnapshotId, Long receivedOccurrenceId, Long dataSnapshotId,
                         String parserVersion, String payloadSha256,
                         String normalizedSha256, String completeness, Integer completenessScore,
                         boolean previousData, Freshness freshness, Table table,
                         StatisticsPresentation.View statistics, CollectionSchedule schedule,
                         LineupsPresentation.View lineups, IncidentPresentation.View incidents,
                         PlaywrightTransportDiagnostic transport, EventDetailsPresentation.View eventDetails) {
        public Family(String endpoint, String label, String outcome, String code, String scope,
                Instant lastAttemptAt, Long authorizationDelayMillis, Instant lastReceivedAt,
                Instant lastSuccessfulAt, Instant lastChangedAt, Long receivedSnapshotId, Long receivedOccurrenceId,
                Long dataSnapshotId, String parserVersion, String payloadSha256, String normalizedSha256,
                String completeness, Integer completenessScore, boolean previousData, Freshness freshness,
                Table table, StatisticsPresentation.View statistics, CollectionSchedule schedule,
                LineupsPresentation.View lineups, IncidentPresentation.View incidents,
                PlaywrightTransportDiagnostic transport) {
            this(endpoint, label, outcome, code, scope, lastAttemptAt, authorizationDelayMillis, lastReceivedAt,
                    lastSuccessfulAt, lastChangedAt, receivedSnapshotId, receivedOccurrenceId, dataSnapshotId,
                    parserVersion, payloadSha256, normalizedSha256, completeness, completenessScore,
                    previousData, freshness, table, statistics, schedule, lineups, incidents, transport, null);
        }
        public Family(String endpoint, String label, String outcome, String code, String scope,
                Instant lastAttemptAt, Long authorizationDelayMillis, Instant lastReceivedAt,
                Instant lastSuccessfulAt, Instant lastChangedAt, Long receivedSnapshotId, Long receivedOccurrenceId,
                Long dataSnapshotId, String parserVersion, String payloadSha256, String normalizedSha256,
                String completeness, Integer completenessScore, boolean previousData, Freshness freshness,
                Table table, StatisticsPresentation.View statistics, CollectionSchedule schedule,
                LineupsPresentation.View lineups, IncidentPresentation.View incidents) {
            this(endpoint, label, outcome, code, scope, lastAttemptAt, authorizationDelayMillis, lastReceivedAt,
                    lastSuccessfulAt, lastChangedAt, receivedSnapshotId, receivedOccurrenceId, dataSnapshotId,
                    parserVersion, payloadSha256, normalizedSha256, completeness, completenessScore,
                    previousData, freshness, table, statistics, schedule, lineups, incidents, null);
        }
        public Family(String endpoint, String label, String outcome, String code, String scope,
                Instant lastAttemptAt, Long authorizationDelayMillis, Instant lastReceivedAt,
                Instant lastSuccessfulAt, Instant lastChangedAt, Long receivedSnapshotId, Long receivedOccurrenceId,
                Long dataSnapshotId, String parserVersion, String payloadSha256, String normalizedSha256,
                String completeness, Integer completenessScore, boolean previousData, Freshness freshness,
                Table table, StatisticsPresentation.View statistics, CollectionSchedule schedule,
                LineupsPresentation.View lineups) {
            this(endpoint, label, outcome, code, scope, lastAttemptAt, authorizationDelayMillis, lastReceivedAt,
                    lastSuccessfulAt, lastChangedAt, receivedSnapshotId, receivedOccurrenceId, dataSnapshotId,
                    parserVersion, payloadSha256, normalizedSha256, completeness, completenessScore,
                    previousData, freshness, table, statistics, schedule, lineups, null);
        }
        public Family(String endpoint, String label, String outcome, String code, String scope,
                Instant lastAttemptAt, Long authorizationDelayMillis, Instant lastReceivedAt,
                Instant lastSuccessfulAt, Instant lastChangedAt, Long receivedSnapshotId, Long receivedOccurrenceId,
                Long dataSnapshotId, String parserVersion, String payloadSha256, String normalizedSha256,
                String completeness, Integer completenessScore, boolean previousData, Freshness freshness,
                Table table, StatisticsPresentation.View statistics, CollectionSchedule schedule) {
            this(endpoint, label, outcome, code, scope, lastAttemptAt, authorizationDelayMillis, lastReceivedAt,
                    lastSuccessfulAt, lastChangedAt, receivedSnapshotId, receivedOccurrenceId, dataSnapshotId,
                    parserVersion, payloadSha256, normalizedSha256, completeness, completenessScore,
                    previousData, freshness, table, statistics, schedule, null);
        }
        public Family(String endpoint, String label, String outcome, String code, String scope,
                Instant lastAttemptAt, Long authorizationDelayMillis, Instant lastReceivedAt,
                Instant lastSuccessfulAt, Instant lastChangedAt, Long receivedSnapshotId, Long receivedOccurrenceId,
                Long dataSnapshotId, String parserVersion, String payloadSha256, String normalizedSha256,
                String completeness, Integer completenessScore, boolean previousData, Freshness freshness,
                Table table, StatisticsPresentation.View statistics) {
            this(endpoint, label, outcome, code, scope, lastAttemptAt, authorizationDelayMillis, lastReceivedAt,
                    lastSuccessfulAt, lastChangedAt, receivedSnapshotId, receivedOccurrenceId, dataSnapshotId,
                    parserVersion, payloadSha256, normalizedSha256, completeness, completenessScore,
                    previousData, freshness, table, statistics, null);
        }
    }
    public record CollectionSchedule(Instant nextDueAt, long intervalSeconds, long missedCycles, long latenessMillis) { }
    public record Freshness(String state, String label, long expectedIntervalSeconds,
                            Long receivedAgeSeconds, Instant ageAsOf, boolean frozen) { }
    public record Table(List<String> columns, List<List<String>> rows) { }
}
