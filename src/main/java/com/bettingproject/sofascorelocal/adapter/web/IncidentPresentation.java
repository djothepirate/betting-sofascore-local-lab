package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.domain.eventdata.EventIncident;
import com.bettingproject.sofascorelocal.domain.eventdata.EventIncidents;

import java.util.ArrayList;
import java.util.List;

/** Shared display only: provider order, normalized evidence and the technical table stay unchanged. */
public final class IncidentPresentation {
    private IncidentPresentation() {}

    public static View from(EventIncidents incidents) {
        return from(incidents, null, null);
    }

    public static View from(EventIncidents incidents, String homeName, String awayName) {
        return new View(incidents.incidents().stream().map(incident -> item(incident, homeName, awayName)).toList());
    }

    private static Item item(EventIncident incident, String homeName, String awayName) {
        Visual visual = visual(incident);
        String side = incident.sideLabel();
        String team = switch (side) {
            case "HOME" -> teamName(homeName, "Domicile");
            case "AWAY" -> teamName(awayName, "Extérieur");
            default -> "Équipe non renseignée";
        };
        return new Item(incident.minuteLabel(), visual.label(), visual.icon(), visual.tone(), team, side,
                incident.playerName().orElse("—"),
                incident.homeScore().map(value -> value + "–" + incident.awayScore().orElseThrow()).orElse("—"),
                detailLabel(incident), graphicMotifLabel(incident),
                incident.playerInName().orElse("—"), incident.playerOutName().orElse("—"));
    }

    private static String teamName(String name, String fallback) {
        return name == null || name.isBlank() ? fallback : name;
    }

    private static Visual visual(EventIncident incident) {
        String category = incident.incidentClass().orElse("");
        return switch (incident.incidentType()) {
            case "goal" -> new Visual("ownGoal".equals(category) || incident.goalOrigin().filter("ownGoal"::equals).isPresent()
                    ? "But contre son camp" : "penalty".equals(category) || incident.goalOrigin().filter("penalty"::equals).isPresent()
                    ? "But sur penalty" : "But", "⚽", "goal");
            case "card" -> switch (category) {
                case "yellow" -> new Visual("Carton jaune", "🟨", "yellow");
                case "red" -> new Visual("Carton rouge", "🟥", "red");
                case "yellowRed" -> new Visual("Second carton jaune · Expulsion", "🟨🟥", "red");
                default -> new Visual("Carton", "▱", "neutral");
            };
            case "substitution" -> new Visual("Remplacement", "🏃🏻‍♂️", "substitution");
            case "inGamePenalty" -> switch (category) {
                case "awarded" -> new Visual("Penalty accordé", "🥅", "penalty");
                case "missed" -> savedPenalty(incident)
                        ? new Visual("Penalty arrêté", "🧤", "penalty")
                        : new Visual("Penalty manqué", "❌", "penalty");
                default -> new Visual("Penalty", "🥅", "penalty");
            };
            case "penaltyShootout" -> switch (category) {
                case "scored" -> new Visual("Tir au but marqué", "⚽", "goal");
                case "missed" -> savedPenalty(incident)
                        ? new Visual("Tir au but arrêté", "🧤", "penalty")
                        : new Visual("Tir au but manqué", "❌", "penalty");
                default -> new Visual("Tir au but", "🥅", "penalty");
            };
            case "period" -> new Visual("Repère de période", "⏱", "period");
            case "injuryTime" -> new Visual("Temps additionnel", "⏱", "period");
            case "varDecision" -> new Visual("Décision VAR", "📺", "var");
            default -> new Visual("Incident non reconnu · " + incident.incidentType(), "•", "neutral");
        };
    }

    private static boolean savedPenalty(EventIncident incident) {
        // The incident belongs to the shooting side; do not invent a goalkeeper or reverse its team.
        return incident.reason().filter("goalkeeperSave"::equals).isPresent()
                || incident.description().filter("Goalkeeper save"::equals).isPresent();
    }

    private static String detailLabel(EventIncident incident) {
        List<String> details = new ArrayList<>();
        incident.periodText().ifPresent(value -> details.add(switch (value) {
            case "First half" -> "Première mi-temps";
            case "Second half" -> "Seconde mi-temps";
            case "Extra time" -> "Prolongation";
            case "HT" -> "Mi-temps (HT)";
            case "FT" -> "Repère FT";
            case "ET" -> "Repère ET";
            case "PEN" -> "Séance de tirs au but (PEN)";
            default -> value;
        }));
        if ("substitution".equals(incident.incidentType()) && incident.injury().orElse(false))
            details.add("Remplacement sur blessure");
        incident.goalOrigin().ifPresent(value -> details.add(switch (value) {
            case "penalty" -> "Sur penalty";
            case "ownGoal" -> "Contre son camp";
            default -> value;
        }));
        incident.assistName().ifPresent(value -> details.add("Passe décisive : " + value));
        incident.injuryTimeLength().ifPresent(value -> details.add("Temps ajouté : " + value + " min"));
        if ("varDecision".equals(incident.incidentType())) incident.incidentClass().ifPresent(value -> details.add(switch (value) {
            case "goalNotAwarded" -> "But non accordé";
            case "goalAwarded" -> "But accordé";
            case "penaltyAwarded" -> "Penalty accordé";
            case "penaltyNotAwarded" -> "Penalty non accordé";
            case "redCardGiven" -> "Carton rouge attribué";
            case "cardUpgrade" -> "Sanction aggravée";
            case "review" -> "Vérification vidéo";
            default -> value;
        }));
        incident.varConfirmed().ifPresent(value -> details.add(value ? "Décision confirmée" : "Décision rejetée"));
        if (incident.rescinded().orElse(false)) details.add("Carton annulé");
        incident.shootoutSequence().ifPresent(value -> details.add("Tir n° " + value));
        return details.isEmpty() ? "—" : String.join(" · ", details);
    }

    public static String motifLabel(EventIncident incident) {
        if ("card".equals(incident.incidentType()) && incident.description().isEmpty()
                && incident.reason().filter("Professional handball"::equals).isPresent()) {
            return "Main volontaire";
        }
        return incident.motifLabel();
    }

    private static String graphicMotifLabel(EventIncident incident) {
        String label = motifLabel(incident);
        if (!"card".equals(incident.incidentType())) return label;
        return switch (label) {
            case "Foul" -> "Faute";
            case "Argument" -> "Contestation";
            case "Violent conduct" -> "Comportement violent";
            default -> label;
        };
    }

    public record View(List<Item> incidents) {
        public View { incidents = List.copyOf(incidents); }
    }
    public record Item(String minuteLabel, String typeLabel, String icon, String tone,
                       String teamLabel, String teamSide, String playerLabel, String scoreLabel,
                       String detailLabel, String motifLabel, String playerInLabel, String playerOutLabel) { }
    private record Visual(String label, String icon, String tone) { }
}
