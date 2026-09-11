package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.domain.eventdata.EventIncident;
import com.bettingproject.sofascorelocal.domain.eventdata.EventIncidents;
import com.bettingproject.sofascorelocal.domain.eventdata.LineupSide;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Read-only display facts reconstructed from the readable J5 incidents observation.
 *
 * <p>The overlay is deliberately keyed by both lineup side and provider player id. It does not
 * create a roster entry, infer a persistent player state, or alter the underlying lineup or
 * incident observations. Callers merge it by fact: direct J5 lineup metrics retain precedence,
 * while cards and entry/exit substitutions remain readable incident facts when lineup metrics do
 * not represent them.</p>
 */
public final class LineupIncidentOverlay {

    private static final String SOURCE = "EVENT_INCIDENTS";
    private static final LineupIncidentOverlay EMPTY = new LineupIncidentOverlay(Map.of());

    private final Map<PlayerKey, List<Decoration>> decorations;

    private LineupIncidentOverlay(Map<PlayerKey, List<Decoration>> decorations) {
        this.decorations = Map.copyOf(decorations);
    }

    public static LineupIncidentOverlay empty() {
        return EMPTY;
    }

    /**
     * Keeps only observed goal, assist, card, and complete substitution facts. Rescinded
     * incidents and any fact without an observed side, minute, or required player id are ignored.
     */
    public static LineupIncidentOverlay from(EventIncidents incidents) {
        Objects.requireNonNull(incidents, "incidents");
        var values = new LinkedHashMap<PlayerKey, List<Decoration>>();
        for (EventIncident incident : incidents.incidents()) {
            if (incident.rescinded().orElse(false)) {
                continue;
            }
            Optional<LineupSide> side = side(incident);
            Optional<String> minute = minute(incident);
            if (side.isEmpty() || minute.isEmpty()) {
                continue;
            }
            switch (incident.incidentType()) {
                case "goal" -> goal(values, incident, side.orElseThrow(), minute.orElseThrow());
                case "card" -> card(values, incident, side.orElseThrow(), minute.orElseThrow());
                case "substitution" -> substitution(values, incident, side.orElseThrow(), minute.orElseThrow());
                default -> { }
            }
        }
        if (values.isEmpty()) {
            return EMPTY;
        }
        var immutable = new LinkedHashMap<PlayerKey, List<Decoration>>();
        values.forEach((key, value) -> immutable.put(key, List.copyOf(value)));
        return new LineupIncidentOverlay(immutable);
    }

    public List<Decoration> decorations(LineupSide side, long providerPlayerId) {
        return decorations.getOrDefault(new PlayerKey(side, providerPlayerId), List.of());
    }

    private static Optional<LineupSide> side(EventIncident incident) {
        return incident.home().map(home -> home ? LineupSide.HOME : LineupSide.AWAY);
    }

    private static Optional<String> minute(EventIncident incident) {
        return incident.minute().map(ignored -> incident.minuteLabel());
    }

    private static void goal(Map<PlayerKey, List<Decoration>> values, EventIncident incident,
                             LineupSide side, String minute) {
        decoration(values, side, incident.playerProviderId(),
                new Decoration("goal", "But", "⚽", minute, SOURCE));
        decoration(values, side, incident.assistProviderId(),
                new Decoration("assist", "Passe décisive", "👟", minute, SOURCE));
    }

    private static void card(Map<PlayerKey, List<Decoration>> values, EventIncident incident,
                             LineupSide side, String minute) {
        Card card = switch (incident.incidentClass().orElse("")) {
            case "yellow" -> new Card("yellow-card", "Carton jaune", "🟨");
            case "red" -> new Card("red-card", "Carton rouge", "🟥");
            case "yellowRed" -> new Card("yellow-red-card", "Second carton jaune · Expulsion", "🟨🟥");
            default -> null;
        };
        if (card != null) {
            decoration(values, side, incident.playerProviderId(),
                    new Decoration(card.key(), card.label(), card.icon(), minute, SOURCE));
        }
    }

    private static void substitution(Map<PlayerKey, List<Decoration>> values, EventIncident incident,
                                     LineupSide side, String minute) {
        // A partial substitution must not decorate just one card: the two observed player ids are
        // the proof that the event has the semantics claimed by its two display facts.
        if (incident.playerInProviderId().isEmpty() || incident.playerOutProviderId().isEmpty()) {
            return;
        }
        decoration(values, side, incident.playerInProviderId(),
                new Decoration("substitution-in", "Entrée observée", "↗", minute, SOURCE));
        decoration(values, side, incident.playerOutProviderId(),
                new Decoration("substitution-out", "Sortie observée", "↘", minute, SOURCE));
    }

    private static void decoration(Map<PlayerKey, List<Decoration>> values, LineupSide side,
                                   Optional<Long> providerPlayerId, Decoration decoration) {
        providerPlayerId.ifPresent(id -> values.computeIfAbsent(new PlayerKey(side, id), ignored -> new ArrayList<>())
                .add(decoration));
    }

    public record Decoration(String key, String label, String icon, String minuteLabel, String source) {
        public Decoration {
            key = bounded(key, "key", 64);
            label = bounded(label, "label", 128);
            icon = bounded(icon, "icon", 16);
            minuteLabel = bounded(minuteLabel, "minuteLabel", 32);
            source = bounded(source, "source", 64);
        }
    }

    private record PlayerKey(LineupSide side, long providerPlayerId) {
        private PlayerKey {
            side = Objects.requireNonNull(side, "side");
            if (providerPlayerId < 1) {
                throw new IllegalArgumentException("providerPlayerId must be positive");
            }
        }
    }

    private record Card(String key, String label, String icon) { }

    private static String bounded(String value, String name, int maximumLength) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty() || normalized.length() > maximumLength
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(name + " must be bounded non-control text");
        }
        return normalized;
    }
}
