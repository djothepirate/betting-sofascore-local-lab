package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.domain.eventdata.EventIncident;
import com.bettingproject.sofascorelocal.domain.eventdata.EventIncidents;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.stream.IntStream;

/** Shared display only: provider order, normalized evidence and the technical table stay unchanged. */
public final class IncidentPresentation {
    private IncidentPresentation() {}

    public static View from(EventIncidents incidents) {
        return from(incidents, null, null);
    }

    public static View from(EventIncidents incidents, String homeName, String awayName) {
        var grouped = new LinkedHashMap<String, List<Integer>>();
        for (int index = 0; index < incidents.incidents().size(); index++) {
            grouped.computeIfAbsent(periodKey(incidents.incidents().get(index)), ignored -> new ArrayList<>()).add(index);
        }
        return new View(incidents.incidents().stream().map(incident -> item(incident, homeName, awayName)).toList(),
                grouped.entrySet().stream().map(entry -> new Period(entry.getKey(), periodLabel(entry.getKey()), entry.getValue())).toList());
    }

    private static String periodKey(EventIncident incident) {
        if (incident.incidentType().equals("penaltyShootout") || incident.periodText().filter("PEN"::equals).isPresent())
            return "SHOOTOUT";
        if (incident.minute().isEmpty()) return "OTHER";
        // Added time belongs to its regulation period: 45+N remains the first half.
        int minute = incident.minute().orElseThrow();
        if (minute <= 45) return "FIRST_HALF";
        if (minute <= 90) return "SECOND_HALF";
        if (minute <= 105) return "EXTRA_FIRST";
        if (minute <= 120) return "EXTRA_SECOND";
        return "OTHER";
    }

    private static String periodLabel(String key) {
        return switch (key) {
            case "FIRST_HALF" -> "Première mi-temps";
            case "SECOND_HALF" -> "Seconde mi-temps";
            case "EXTRA_FIRST" -> "Prolongation · première période";
            case "EXTRA_SECOND" -> "Prolongation · seconde période";
            case "SHOOTOUT" -> "Séance de tirs au but";
            default -> "Autres repères";
        };
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
        if ("card".equals(incident.incidentType()) && incident.description().isEmpty()) {
            return incident.reason().map(reason -> switch (reason) {
                case "Professional handball" -> "Main volontaire";
                case "Professional foul last man" -> "Faute volontaire du dernier défenseur";
                default -> reason;
            }).orElseGet(incident::motifLabel);
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
            case "Other reason" -> "Autre motif";
            default -> label;
        };
    }

    public record View(List<Item> incidents, List<Period> periods) {
        public View { incidents = List.copyOf(incidents); periods = List.copyOf(periods); }
        public View(List<Item> incidents) {
            this(incidents, incidents.isEmpty() ? List.of() : List.of(new Period("ALL", "Incidents",
                    IntStream.range(0, incidents.size()).boxed().toList())));
        }
    }
    public record Period(String key, String label, List<Integer> incidentIndexes) {
        public Period { incidentIndexes = List.copyOf(incidentIndexes); }
    }
    public record Item(String minuteLabel, String typeLabel, String icon, String tone,
                       String teamLabel, String teamSide, String playerLabel, String scoreLabel,
                       String detailLabel, String motifLabel, String playerInLabel, String playerOutLabel) { }
    private record Visual(String label, String icon, String tone) { }
}
