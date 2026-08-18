package com.bettingproject.sofascorelocal.domain.eventdata;

import java.util.Objects;
import java.util.Optional;

public record EventIncident(
        int sequence,
        String incidentType,
        Optional<Integer> minute,
        Optional<Integer> addedTime,
        Optional<Boolean> home,
        Optional<Long> participantProviderId,
        Optional<Long> playerProviderId,
        Optional<String> playerName,
        Optional<Long> playerInProviderId,
        Optional<String> playerInName,
        Optional<Long> playerOutProviderId,
        Optional<String> playerOutName,
        Optional<Integer> homeScore,
        Optional<Integer> awayScore,
        Optional<String> incidentClass,
        Optional<String> reason,
        Optional<String> periodText,
        Optional<Boolean> injury,
        Optional<Long> assistProviderId,
        Optional<String> assistName,
        Optional<String> goalOrigin,
        Optional<Integer> injuryTimeLength,
        Optional<Boolean> varConfirmed,
        Optional<Boolean> rescinded,
        Optional<String> description,
        Optional<Integer> shootoutSequence) {

    public EventIncident {
        if (sequence < 0) {
            throw new IllegalArgumentException("sequence cannot be negative");
        }
        incidentType = boundedText(incidentType, "incidentType", 64);
        minute = boundedInteger(minute, "minute", 0, 300);
        addedTime = boundedInteger(addedTime, "addedTime", 0, 300);
        home = Objects.requireNonNull(home, "home");
        participantProviderId = positiveLong(participantProviderId, "participantProviderId");
        playerProviderId = positiveLong(playerProviderId, "playerProviderId");
        playerName = Objects.requireNonNull(playerName, "playerName")
                .map(value -> boundedText(value, "playerName", 200));
        requireNameWhenIdPresent(playerProviderId, playerName, "player");
        playerInProviderId = positiveLong(playerInProviderId, "playerInProviderId");
        playerInName = boundedOptionalText(playerInName, "playerInName", 200);
        requireNameWhenIdPresent(playerInProviderId, playerInName, "incoming player");
        playerOutProviderId = positiveLong(playerOutProviderId, "playerOutProviderId");
        playerOutName = boundedOptionalText(playerOutName, "playerOutName", 200);
        requireNameWhenIdPresent(playerOutProviderId, playerOutName, "outgoing player");
        homeScore = boundedInteger(homeScore, "homeScore", 0, 99);
        awayScore = boundedInteger(awayScore, "awayScore", 0, 99);
        if (homeScore.isPresent() != awayScore.isPresent()) {
            throw new IllegalArgumentException("home and away scores must be present together");
        }
        incidentClass = boundedOptionalText(incidentClass, "incidentClass", 64);
        reason = boundedOptionalText(reason, "reason", 200);
        periodText = boundedOptionalText(periodText, "periodText", 64);
        injury = Objects.requireNonNull(injury, "injury");
        assistProviderId = positiveLong(assistProviderId, "assistProviderId");
        assistName = boundedOptionalText(assistName, "assistName", 200);
        requireNameWhenIdPresent(assistProviderId, assistName, "assist");
        goalOrigin = boundedOptionalText(goalOrigin, "goalOrigin", 64);
        injuryTimeLength = boundedInteger(injuryTimeLength, "injuryTimeLength", 0, 300);
        varConfirmed = Objects.requireNonNull(varConfirmed, "varConfirmed");
        rescinded = Objects.requireNonNull(rescinded, "rescinded");
        description = boundedOptionalText(description, "description", 200);
        shootoutSequence = boundedInteger(shootoutSequence, "shootoutSequence", 1, 999);
        boolean minuteMayBeAbsent = "penaltyShootout".equals(incidentType)
                || ("period".equals(incidentType) && periodText.filter("PEN"::equals).isPresent());
        if (minute.isEmpty() && !minuteMayBeAbsent) {
            throw new IllegalArgumentException(
                    "minute may be absent only for a penalty shootout or its PEN marker");
        }
        if (minute.isEmpty() && addedTime.isPresent()) {
            throw new IllegalArgumentException("addedTime requires a normalized minute");
        }
    }

    public EventIncident(
            int sequence,
            String incidentType,
            int minute,
            Optional<Integer> addedTime,
            Optional<Boolean> home,
            Optional<Long> participantProviderId,
            Optional<Long> playerProviderId,
            Optional<String> playerName,
            Optional<Long> playerInProviderId,
            Optional<String> playerInName,
            Optional<Long> playerOutProviderId,
            Optional<String> playerOutName,
            Optional<Integer> homeScore,
            Optional<Integer> awayScore,
            Optional<String> incidentClass,
            Optional<String> reason,
            Optional<String> periodText,
            Optional<Boolean> injury,
            Optional<Long> assistProviderId,
            Optional<String> assistName,
            Optional<String> goalOrigin,
            Optional<Integer> injuryTimeLength,
            Optional<Boolean> varConfirmed,
            Optional<Boolean> rescinded,
            Optional<String> description,
            Optional<Integer> shootoutSequence) {
        this(
                sequence,
                incidentType,
                Optional.of(minute),
                addedTime,
                home,
                participantProviderId,
                playerProviderId,
                playerName,
                playerInProviderId,
                playerInName,
                playerOutProviderId,
                playerOutName,
                homeScore,
                awayScore,
                incidentClass,
                reason,
                periodText,
                injury,
                assistProviderId,
                assistName,
                goalOrigin,
                injuryTimeLength,
                varConfirmed,
                rescinded,
                description,
                shootoutSequence);
    }

    public EventIncident(
            int sequence,
            String incidentType,
            int minute,
            Optional<Integer> addedTime,
            Optional<Boolean> home,
            Optional<Long> participantProviderId,
            Optional<Long> playerProviderId,
            Optional<String> playerName,
            Optional<Long> playerInProviderId,
            Optional<String> playerInName,
            Optional<Long> playerOutProviderId,
            Optional<String> playerOutName,
            Optional<Integer> homeScore,
            Optional<Integer> awayScore,
            Optional<String> incidentClass,
            Optional<String> reason) {
        this(
                sequence,
                incidentType,
                minute,
                addedTime,
                home,
                participantProviderId,
                playerProviderId,
                playerName,
                playerInProviderId,
                playerInName,
                playerOutProviderId,
                playerOutName,
                homeScore,
                awayScore,
                incidentClass,
                reason,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    public EventIncident(
            int sequence,
            String incidentType,
            int minute,
            Optional<Integer> addedTime,
            Optional<Boolean> home,
            Optional<Long> participantProviderId,
            Optional<Long> playerProviderId,
            Optional<String> playerName,
            Optional<Long> playerInProviderId,
            Optional<String> playerInName,
            Optional<Long> playerOutProviderId,
            Optional<String> playerOutName,
            Optional<Integer> homeScore,
            Optional<Integer> awayScore) {
        this(
                sequence,
                incidentType,
                minute,
                addedTime,
                home,
                participantProviderId,
                playerProviderId,
                playerName,
                playerInProviderId,
                playerInName,
                playerOutProviderId,
                playerOutName,
                homeScore,
                awayScore,
                Optional.empty(),
                Optional.empty());
    }

    public EventIncident(
            int sequence,
            String incidentType,
            int minute,
            Optional<Integer> addedTime,
            Optional<Boolean> home,
            Optional<Long> participantProviderId,
            Optional<Long> playerProviderId,
            Optional<String> playerName,
            Optional<Integer> homeScore,
            Optional<Integer> awayScore) {
        this(
                sequence,
                incidentType,
                minute,
                addedTime,
                home,
                participantProviderId,
                playerProviderId,
                playerName,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                homeScore,
                awayScore);
    }

    public boolean hasReplacementPlayers() {
        return playerInName.isPresent() || playerOutName.isPresent();
    }

    public boolean hasComprehensiveDetails() {
        return periodText.isPresent()
                || injury.isPresent()
                || assistName.isPresent()
                || goalOrigin.isPresent()
                || injuryTimeLength.isPresent()
                || varConfirmed.isPresent()
                || rescinded.isPresent()
                || description.isPresent()
                || shootoutSequence.isPresent();
    }

    public String detailLabel() {
        if (periodText.isPresent()) {
            return periodText.orElseThrow();
        }
        if ("substitution".equals(incidentType) && injury.orElse(false)) {
            return "Remplacement sur blessure";
        }
        if (goalOrigin.isPresent()) {
            return goalOrigin.orElseThrow();
        }
        if (injuryTimeLength.isPresent()) {
            return injuryTimeLength.orElseThrow() + " min";
        }
        if (varConfirmed.isPresent()) {
            return varConfirmed.orElseThrow()
                    ? "Décision confirmée"
                    : "Décision rejetée";
        }
        if (rescinded.orElse(false)) {
            return "Carton annulé";
        }
        return "—";
    }

    /**
     * Human-readable business reason shown in the incident table.
     *
     * <p>Cards expose their provider {@code reason}; penalty incidents expose the more readable
     * provider {@code description}. Keeping this choice in the domain view avoids duplicating the
     * same penalty value in both the Motif and Détail columns.</p>
     */
    public String motifLabel() {
        return description.or(() -> reason).orElse("—");
    }

    public String minuteLabel() {
        return minute
                .map(value -> addedTime
                        .map(additional -> value + "+" + additional)
                        .orElse(Integer.toString(value)))
                .orElse("—");
    }

    public String sideLabel() {
        return home.map(value -> value ? "HOME" : "AWAY").orElse("UNKNOWN");
    }

    private static Optional<Long> positiveLong(Optional<Long> value, String name) {
        Optional<Long> normalized = Objects.requireNonNull(value, name);
        if (normalized.isPresent() && normalized.orElseThrow() < 1) {
            throw new IllegalArgumentException(name + " must be positive when present");
        }
        return normalized;
    }

    private static Optional<String> boundedOptionalText(
            Optional<String> value,
            String name,
            int maximumLength) {
        return Objects.requireNonNull(value, name)
                .map(item -> boundedText(item, name, maximumLength));
    }

    private static void requireNameWhenIdPresent(
            Optional<Long> providerId,
            Optional<String> name,
            String label) {
        if (providerId.isPresent() && name.isEmpty()) {
            throw new IllegalArgumentException(label + " name is required with its id");
        }
    }

    private static Optional<Integer> boundedInteger(
            Optional<Integer> value,
            String name,
            int minimum,
            int maximum) {
        Optional<Integer> normalized = Objects.requireNonNull(value, name);
        if (normalized.isPresent()) {
            int item = normalized.orElseThrow();
            if (item < minimum || item > maximum) {
                throw new IllegalArgumentException(name + " is outside the accepted range");
            }
        }
        return normalized;
    }

    private static String boundedText(String value, String name, int maximumLength) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()
                || normalized.length() > maximumLength
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(name + " must be bounded non-control text");
        }
        return normalized;
    }
}
