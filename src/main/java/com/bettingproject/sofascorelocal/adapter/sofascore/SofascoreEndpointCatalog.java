package com.bettingproject.sofascorelocal.adapter.sofascore;

import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointDefinition;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class SofascoreEndpointCatalog {

    private final Map<SofascoreEndpointType, SofascoreEndpointDefinition> definitions;

    public SofascoreEndpointCatalog() {
        EnumMap<SofascoreEndpointType, SofascoreEndpointDefinition> values =
                new EnumMap<>(SofascoreEndpointType.class);
        register(values, SofascoreEndpointType.SCHEDULED_EVENTS, Duration.ofMinutes(10),
                "Recherche manuelle par date et contrôle des identités");
        register(values, SofascoreEndpointType.EVENT_DETAILS, Duration.ofMinutes(15),
                "Détail d'un événement retenu");
        register(values, SofascoreEndpointType.EVENT_STATISTICS, Duration.ofMinutes(30),
                "Statistiques d'équipe selon disponibilité");
        register(values, SofascoreEndpointType.EVENT_INCIDENTS, Duration.ofMinutes(15),
                "Buts, cartons, remplacements et incidents");
        register(values, SofascoreEndpointType.EVENT_LINEUPS, Duration.ofMinutes(15),
                "Titulaires, bancs et formations");
        register(values, SofascoreEndpointType.TOURNAMENT_STANDINGS, Duration.ofHours(6),
                "Contexte de classement");
        register(values, SofascoreEndpointType.TEAM_RECENT_EVENTS, Duration.ofHours(1),
                "Forme récente sans collecte historique massive");
        definitions = Map.copyOf(values);
    }

    private static void register(
            Map<SofascoreEndpointType, SofascoreEndpointDefinition> target,
            SofascoreEndpointType type,
            Duration cacheTtl,
            String purpose) {
        target.put(type, new SofascoreEndpointDefinition(
                type,
                cacheTtl,
                true,
                false,
                false,
                purpose));
    }

    public SofascoreEndpointDefinition get(SofascoreEndpointType type) {
        SofascoreEndpointDefinition definition = definitions.get(type);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown endpoint type: " + type);
        }
        return definition;
    }

    public List<SofascoreEndpointDefinition> list() {
        return definitions.values().stream()
                .sorted((left, right) -> left.type().compareTo(right.type()))
                .toList();
    }
}
