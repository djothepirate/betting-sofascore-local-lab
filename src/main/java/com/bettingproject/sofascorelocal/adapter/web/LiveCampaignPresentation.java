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
import com.bettingproject.sofascorelocal.port.LiveCampaignPressureReadStore;
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
    private final LiveCampaignPressureReadStore pressure;
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final List<SofascoreEndpointType> FAMILIES = List.of(SofascoreEndpointType.EVENT_DETAILS,
            SofascoreEndpointType.EVENT_STATISTICS, SofascoreEndpointType.EVENT_INCIDENTS,
            SofascoreEndpointType.EVENT_LINEUPS);

    public LiveCampaignPresentation(CanonicalEventStore events, J5EventDataStore data) {
        this(events, data, null, Clock.systemUTC(), null, LineupCountryOverlayResolver.none(),
                LiveCampaignPressureReadStore.none());
    }

    public LiveCampaignPresentation(CanonicalEventStore events, J5EventDataStore data, EventDetailsStore details) {
        this(events, data, details, Clock.systemUTC(), null, LineupCountryOverlayResolver.none(),
                LiveCampaignPressureReadStore.none());
    }

    @Autowired
    public LiveCampaignPresentation(CanonicalEventStore events, J5EventDataStore data, EventDetailsStore details,
                                    LiveDiagnosticStore diagnostics, LineupCountryOverlayResolver lineupCountries,
                                    LiveCampaignPressureReadStore pressure) {
        this(events, data, details, Clock.systemUTC(), diagnostics, lineupCountries, pressure);
    }

    LiveCampaignPresentation(CanonicalEventStore events, J5EventDataStore data, Clock clock) {
        this(events, data, null, clock, null, LineupCountryOverlayResolver.none(), LiveCampaignPressureReadStore.none());
    }

    LiveCampaignPresentation(CanonicalEventStore events, J5EventDataStore data, EventDetailsStore details, Clock clock) {
        this(events, data, details, clock, null, LineupCountryOverlayResolver.none(), LiveCampaignPressureReadStore.none());
    }

    LiveCampaignPresentation(CanonicalEventStore events, J5EventDataStore data, EventDetailsStore details, Clock clock,
                             LiveDiagnosticStore diagnostics) {
        this(events, data, details, clock, diagnostics, LineupCountryOverlayResolver.none(), LiveCampaignPressureReadStore.none());
    }

    LiveCampaignPresentation(CanonicalEventStore events, J5EventDataStore data, EventDetailsStore details, Clock clock,
                             LiveDiagnosticStore diagnostics, LineupCountryOverlayResolver lineupCountries) {
        this(events, data, details, clock, diagnostics, lineupCountries, LiveCampaignPressureReadStore.none());
    }

    LiveCampaignPresentation(CanonicalEventStore events, J5EventDataStore data, EventDetailsStore details, Clock clock,
                             LiveDiagnosticStore diagnostics, LineupCountryOverlayResolver lineupCountries,
                             LiveCampaignPressureReadStore pressure) {
        this.events = events;
        this.data = data;
        this.details = details;
        this.clock = clock;
        this.diagnostics = diagnostics;
        this.lineupCountries = lineupCountries == null ? LineupCountryOverlayResolver.none() : lineupCountries;
        this.pressure = pressure == null ? LiveCampaignPressureReadStore.none() : pressure;
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
                cadence(view, observedAt, runtimeStatus), pagination, pressure(view.manifest().campaignId()));
    }

    private Pressure pressure(UUID campaignId) {
        LiveCampaignPressureReadStore.Pressure observed = pressure.read(campaignId);
        if (observed == null) observed = LiveCampaignPressureReadStore.Pressure.noObservedDepartures();
        return new Pressure(observed.observedDepartures(), observed.firstObservedDepartureAt(),
                observed.lastObservedDepartureAt(), new PressurePeak(observed.oneMinutePeak().observedDepartures(),
                observed.oneMinutePeak().windowEndAt()), new PressurePeak(observed.fiveMinutePeak().observedDepartures(),
                observed.fiveMinutePeak().windowEndAt()), observed.families().stream().map(family ->
                new PressureFamily(family.endpoint().name(), pressureLabel(family.endpoint()), family.observedDepartures())).toList());
    }

    private static String pressureLabel(SofascoreEndpointType endpoint) {
        return switch (endpoint) {
            case EVENT_DETAILS -> "J4 détails";
            case EVENT_STATISTICS -> "J5 statistiques";
            case EVENT_INCIDENTS -> "J5 incidents";
            case EVENT_LINEUPS -> "J5 compositions";
            default -> throw new IllegalArgumentException("endpoint outside live scope");
        };
    }

    private static Cadence cadence(CampaignView view, Instant now, RuntimeStatus runtimeStatus) {
        String policyVersion = view.manifest().policyVersion();
        if (!grouped(policyVersion)) return null;
        long interval = view.manifest().cycleInterval().toSeconds();
        boolean minutePolicy = "live-v7".equals(policyVersion) || "live-v8".equals(policyVersion)
                || "live-v9".equals(policyVersion) || "live-v10".equals(policyVersion);
        // Prematch V7/V8/V9/V10 is sparse; one J4/minute at kickoff is the conservative waiting rate.
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
                || "live-v8".equals(policyVersion) || "live-v9".equals(policyVersion)
                || "live-v10".equals(policyVersion);
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
        boolean playerDetailsAllowed = playerDetailsAllowed(j4);
        LineupIncidentOverlay incidentOverlay = lineupIncidentOverlay(event);
        String policyVersion = campaign.manifest().policyVersion();
        String displayedState = displayedState(policyVersion, event);
        String displayedReason = displayedReason(policyVersion, event);
        String statusReason = suspensionReason(j4, sportStatus);
        return new Event(event.target().canonicalEventId(), event.target().providerEventId(),
                identity == null ? Long.toString(event.target().providerEventId())
                        : identity.homeTeam().name() + " — " + identity.awayTeam().name(),
                identity == null ? "—" : identity.tournament().map(t -> t.name()).orElse("—"),
                identity == null ? "—" : identity.startsAt().atZone(ZoneId.of("Europe/Paris")).toString(),
                sportStatus, "finished".equals(sportStatus) && result.awarded() ? "Victoire sur tapis vert"
                        : sportStatusLabel(sportStatus, identity), result.score(), displayedState, displayedReason, statusReason,
                event.nextDueAt(), event.reservedCalls(),
                campaign.manifest().maximumCallsPerEvent(), event.receivedBytes(), event.missedCycles(),
                event.finalComplete(), sourceSnapshot, sourceReceivedAt, canonicalCurrent,
                campaign.blocksSelection(event.target().canonicalEventId()),
                FAMILIES.stream().map(endpoint -> event.families().stream()
                        .filter(f -> f.endpoint() == endpoint).findFirst().orElseGet(() ->
                                new FamilyCursor(endpoint, null, null, null, null, null, null, null,
                                        NormalizedReferences.none(), null, null)))
                        .map(f -> family(campaign, event, f, observedAt, identity, incidentOverlay,
                                playerDetailsAllowed)).toList());
    }

    /**
     * Live V9 distinguishes a final J4 proof from completion of its final optional J5 cycle. The
     * durable scheduler state remains terminal; only this policy exposes the incomplete cycle.
     */
    private static String displayedState(String policyVersion, EventView event) {
        return ("live-v9".equals(policyVersion) || "live-v10".equals(policyVersion))
                && "FINISHED_CONFIRMED".equals(event.state()) && !event.finalComplete()
                ? "FINISHED_J5_INCOMPLETE" : event.state();
    }

    private static String displayedReason(String policyVersion, EventView event) {
        if (("live-v9".equals(policyVersion) || "live-v10".equals(policyVersion)) && "FINISHED_CONFIRMED".equals(event.state())
                && !event.finalComplete()) {
            return "Résultat final J4 confirmé ; dernier cycle J5 incomplet.";
        }
        return reason(event.reason());
    }

    /**
     * The J4 tournament capability controls whether a card is allowed to disclose details. A
     * missing, malformed, or false fact remains deliberately static even when an old lineup happens
     * to contain player statistics.
     */
    private static boolean playerDetailsAllowed(FamilyCursor j4) {
        if (j4 == null || j4.latestSuccessfulResult() == null) {
            return false;
        }
        String projection = j4.latestSuccessfulResult().publication().projectionJson();
        if (projection == null) {
            return false;
        }
        try {
            JsonNode value = JSON.readTree(projection).path("tournamentHasEventPlayerStatistics");
            return "VALUE".equals(value.path("presence").asText())
                    && value.path("value").isBoolean() && value.path("value").booleanValue();
        } catch (JacksonException exception) {
            return false;
        }
    }

    /**
     * A J4 status reason is local review data, not a durable label for later phases.  It is exposed
     * only when the latest successful J4 result itself still says {@code suspended}, so a following
     * in-progress observation cannot leave a stale suspension reason on screen.
     */
    private static String suspensionReason(FamilyCursor j4, String sportStatus) {
        if (!"suspended".equals(sportStatus) || j4 == null || j4.latestSuccessfulResult() == null
                || !"suspended".equals(j4.latestSuccessfulResult().publication().sportStatus())) {
            return null;
        }
        String projection = j4.latestSuccessfulResult().publication().projectionJson();
        if (projection == null) {
            return null;
        }
        try {
            JsonNode reason = JSON.readTree(projection).path("statusReason");
            if (!"VALUE".equals(reason.path("presence").asText()) || !reason.path("value").isString()) {
                return null;
            }
            String value = reason.path("value").stringValue();
            return value == null || value.isBlank() ? null : value;
        } catch (JacksonException exception) {
            return null;
        }
    }

    private LineupIncidentOverlay lineupIncidentOverlay(EventView event) {
        FamilyCursor lineups = familyCursor(event, SofascoreEndpointType.EVENT_LINEUPS);
        // Player-card facts are sourced per family. A readable EVENT_STATISTICS response does not
        // prove that EVENT_LINEUPS contains a card or a substitution for an individual player, so
        // it must not suppress the independently readable EVENT_INCIDENTS overlay.
        if (lineups == null || unavailable(lineups)
                || lineups.normalized() == null || lineups.normalized().j5ObservationId() == null) {
            return LineupIncidentOverlay.empty();
        }
        FamilyCursor incidents = familyCursor(event, SofascoreEndpointType.EVENT_INCIDENTS);
        if (incidents == null || incidents.normalized() == null || incidents.normalized().j5ObservationId() == null) {
            return LineupIncidentOverlay.empty();
        }
        return data.findByObservationId(event.target().canonicalEventId(), SofascoreEndpointType.EVENT_INCIDENTS,
                        incidents.normalized().j5ObservationId())
                .filter(observation -> observation.completeness().status() != J5CompletenessStatus.UNAVAILABLE)
                .filter(observation -> observation.data() instanceof EventIncidents)
                .map(observation -> LineupIncidentOverlay.from((EventIncidents) observation.data()))
                .orElse(LineupIncidentOverlay.empty());
    }

    private static FamilyCursor familyCursor(EventView event, SofascoreEndpointType endpoint) {
        return event.families().stream().filter(cursor -> cursor.endpoint() == endpoint).findFirst().orElse(null);
    }

    private static boolean unavailable(FamilyCursor cursor) {
        return cursor != null && cursor.latestResult() != null
                && J5CompletenessStatus.UNAVAILABLE.name().equals(
                        cursor.latestResult().publication().completenessStatus());
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
            case "STOPPED_FINAL_RESULT_ONLY" -> "Résultat final uniquement signalé par J4 ; suivi arrêté avant les familles J5.";
            case "STOPPED_DETAIL_ID_UNSUPPORTED" -> "Identifiant de détail J4 non pris en charge ; suivi arrêté sans appel J5.";
            case "WAITING_HALFTIME_HOLD" -> "Mi-temps observée ; J4 reprendra après la période de maintien.";
            case "WAITING_HALFTIME_RECHECK" -> "Mi-temps toujours observée ; vérification J4 maintenue toutes les minutes avant la reprise.";
            case "WAITING_SUSPENDED_RECHECK" -> "Rencontre suspendue ; vérification J4 maintenue toutes les minutes, sans appel J5.";
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
                          CanonicalEventObservationView identity, LineupIncidentOverlay incidentOverlay,
                          boolean playerDetailsAllowed) {
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
                            lineupCountries.resolve(observation.orElseThrow()), incidentOverlay,
                            playerDetailsAllowed);
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
        Instant lastCacheRevalidatedAt = pending ? null : acceptedV9CacheRevalidatedAt(campaign, cursor);
        return new Family(cursor.endpoint().name(), label(cursor.endpoint()),
                cursor.lastAttemptId() == null ? "NOT_REQUESTED" : pending ? "PENDING" : latest.outcome(),
                pending ? null : latest.code(),
                pending ? null : latest.scope(),
                attempted == null ? null : attempted.attempt().reservedAt(),
                attempted == null || attempted.dispatchAuthorizedAt() == null ? null
                        : Math.max(0, java.time.Duration.between(attempted.attempt().dueAt(),
                                attempted.dispatchAuthorizedAt()).toMillis()),
                cursor.lastReceivedAt(), cursor.lastSuccessfulAt(), lastCacheRevalidatedAt, cursor.lastChangedAt(),
                received == null ? null : received.snapshotId(),
                received == null ? null : received.occurrenceId(),
                successfulAttempt == null ? null : successfulAttempt.snapshotId(),
                successful == null ? null : successful.parserVersion(), payloadSha256,
                cursor.normalized() == null ? null : cursor.normalized().normalizedSha256(),
                pending ? null : latest.completenessStatus(),
                pending ? null : latest.completenessScore(),
                cursor.lastSuccessfulAttemptId() != null
                        && !cursor.lastSuccessfulAttemptId().equals(cursor.lastAttemptId()),
                freshness(campaign, event, cursor, lastCacheRevalidatedAt, observedAt), table, statistics,
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

    /**
     * A dispatch-authorized V9 COMPLETE/304 proof confirms that the locally cached projection
     * remains current. It is deliberately separate from actual 2xx data receipt and success timestamps.
     */
    private Instant acceptedV9CacheRevalidatedAt(CampaignView campaign, FamilyCursor cursor) {
        Result result = cursor.latestResult();
        if (!("live-v9".equals(campaign.manifest().policyVersion()) || "live-v10".equals(campaign.manifest().policyVersion())) || result == null
                || cursor.lastAttemptId() == null || !cursor.lastAttemptId().equals(result.attemptId())
                || diagnostics == null) return null;
        AttemptView attempt = campaign.attempts().stream()
                .filter(candidate -> result.attemptId().equals(candidate.attempt().attemptId()))
                .findFirst().orElse(null);
        if (attempt == null || attempt.dispatchAuthorizedAt() == null || attempt.receivedAt() != null) return null;
        Publication publication = result.publication();
        if (publication == null) return null;
        NormalizedReferences references = result.normalized();
        boolean noReferences = references != null && references.canonicalObservationId() == null
                && references.detailObservationId() == null && references.j5ObservationId() == null
                && references.normalizedSha256() == null;
        boolean acceptedNotModified = "NOT_MODIFIED".equals(publication.outcome())
                && "NONE".equals(publication.scope()) && "HTTP_304".equals(publication.code())
                && !publication.successful() && publication.parserVersion() == null
                && publication.projectionJson() == null && publication.projectionVersion() == null
                && publication.completenessStatus() == null && publication.completenessScore() == null;
        if (!acceptedNotModified || !noReferences) return null;
        return diagnostics.findTransport(campaign.manifest().campaignId(), result.attemptId()).filter(diagnostic ->
                diagnostic.phase() == PlaywrightTransportDiagnostic.Phase.COMPLETE
                        && diagnostic.responseComplete() && Integer.valueOf(304).equals(diagnostic.httpStatus())
                        && diagnostic.requestedAt() != null && diagnostic.headersReceivedAt() != null
                        && !attempt.dispatchAuthorizedAt().isAfter(diagnostic.requestedAt())
                        && !diagnostic.headersReceivedAt().isBefore(diagnostic.requestedAt()))
                .map(PlaywrightTransportDiagnostic::headersReceivedAt).orElse(null);
    }

    private static Freshness freshness(CampaignView campaign, EventView event, FamilyCursor cursor,
                                       Instant lastCacheRevalidatedAt, Instant now) {
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
        boolean cacheRevalidationIsNewest = lastCacheRevalidatedAt != null
                && (success == null || !lastCacheRevalidatedAt.isBefore(success));
        Instant anchor = cacheRevalidationIsNewest ? lastCacheRevalidatedAt : success == null ? phaseStarted : success;
        if (cursor.endpoint() == SofascoreEndpointType.EVENT_DETAILS && "CHECKING_FINISH".equals(event.state())
                && anchor.isBefore(phaseStarted)) anchor = phaseStarted;
        String state = frozen ? "FROZEN" : interval == 0 ? "NOT_EXPECTED"
                : now.isAfter(anchor.plusSeconds(interval * 2)) ? "STALE"
                : success == null ? "AWAITING_SUCCESS" : "FRESH";
        String label = switch (state) {
            case "FROZEN" -> "Âge figé à la fin du suivi";
            case "NOT_EXPECTED" -> "Aucune collecte périodique attendue dans cette phase";
            case "STALE" -> "En retard / périmée : aucun contrôle valide depuis plus de deux intervalles";
            case "AWAITING_SUCCESS" -> "En attente du premier succès";
            default -> cacheRevalidationIsNewest
                    ? "Dans l’intervalle attendu ; cache revalidé sans nouvelle donnée."
                    : "Dans l’intervalle attendu";
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
                           Cadence cadence, Pagination pagination, Pressure pressure) {
        public Campaign(UUID campaignId, long revision, String state, String reason, Instant preparedAt,
                Instant startedAt, Instant endsAt, int reservedCalls, int maximumCalls, long receivedBytes,
                long maximumBytes, List<Event> events, RuntimeObservation runtimeStatus, Cadence cadence,
                Pagination pagination) {
            this(campaignId, revision, state, reason, preparedAt, startedAt, endsAt, reservedCalls,
                    maximumCalls, receivedBytes, maximumBytes, events, runtimeStatus, cadence, pagination,
                    Pressure.noObservedDepartures());
        }
        public Campaign(UUID campaignId, long revision, String state, String reason, Instant preparedAt,
                Instant startedAt, Instant endsAt, int reservedCalls, int maximumCalls, long receivedBytes,
                long maximumBytes, List<Event> events, RuntimeObservation runtimeStatus, Cadence cadence) {
            this(campaignId, revision, state, reason, preparedAt, startedAt, endsAt, reservedCalls,
                    maximumCalls, receivedBytes, maximumBytes, events, runtimeStatus, cadence, null,
                    Pressure.noObservedDepartures());
        }
        public Campaign(UUID campaignId, long revision, String state, String reason, Instant preparedAt,
                Instant startedAt, Instant endsAt, int reservedCalls, int maximumCalls, long receivedBytes,
                long maximumBytes, List<Event> events, RuntimeObservation runtimeStatus) {
            this(campaignId, revision, state, reason, preparedAt, startedAt, endsAt, reservedCalls,
                    maximumCalls, receivedBytes, maximumBytes, events, runtimeStatus, null, null,
                    Pressure.noObservedDepartures());
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
    /** A local post-mortem of authenticated worker starts, never a provider quota or total network traffic meter. */
    public record Pressure(int observedDepartures, Instant firstObservedDepartureAt, Instant lastObservedDepartureAt,
                           PressurePeak oneMinutePeak, PressurePeak fiveMinutePeak,
                           List<PressureFamily> families) {
        static Pressure noObservedDepartures() {
            return new Pressure(0, null, null, new PressurePeak(0, null), new PressurePeak(0, null), FAMILIES.stream()
                    .map(endpoint -> new PressureFamily(endpoint.name(), pressureLabel(endpoint), 0)).toList());
        }
    }
    public record PressurePeak(int observedDepartures, Instant windowEndAt) { }
    public record PressureFamily(String endpoint, String label, int observedDepartures) { }
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
                        String statusReason,
                        Instant nextDueAt, int reservedCalls, int maximumCalls, long receivedBytes,
                        long missedCycles, boolean finalComplete, Long sourceSnapshotId,
                        Instant sourceReceivedAt, boolean canonicalCurrent, boolean selectionBlocked, List<Family> families) { }
    public record Family(String endpoint, String label, String outcome, String code, String scope,
                         Instant lastAttemptAt, Long authorizationDelayMillis,
                         Instant lastReceivedAt, Instant lastSuccessfulAt, Instant lastCacheRevalidatedAt,
                         Instant lastChangedAt,
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
                    lastSuccessfulAt, null, lastChangedAt, receivedSnapshotId, receivedOccurrenceId, dataSnapshotId,
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
