package com.bettingproject.sofascorelocal.domain.live;

import java.util.Objects;

/**
 * Small, typed subset of a J4 event observation used to decide whether a local
 * live scheduler may offer a J5 family.  It deliberately retains absence and
 * JSON null separately: absence is one of the provider contracts, while a
 * null or an unfamiliar value must never be silently treated as permission.
 */
public record LiveJ4ControlFacts(
        BooleanFact finalResultOnly,
        DetailIdFact detailId,
        BooleanFact hasEventPlayerStatistics,
        BooleanFact tournamentHasEventPlayerStatistics,
        StatusDescription statusDescription,
        TextFact statusReason) {

    public enum BooleanFact { ABSENT, NULL, TRUE, FALSE }
    public enum DetailIdFact { ABSENT, NULL, ONE, OTHER }
    public enum StatusDescription { ABSENT, NULL, HALFTIME, SECOND_HALF, OTHER }
    public enum TextPresence { ABSENT, NULL, VALUE }

    /**
     * Textual J4 controls retain the provider distinction between an omitted
     * property, an explicit JSON null, and a usable string.  A reason is not a
     * scheduling permission; it is kept as a reviewable observation and may
     * only be presented while the corresponding J4 status is suspended.
     */
    public record TextFact(TextPresence presence, String value) {
        public TextFact {
            Objects.requireNonNull(presence, "presence");
            if (presence == TextPresence.VALUE) {
                if (value == null || value.isBlank()) {
                    throw new IllegalArgumentException("value is required when presence is VALUE");
                }
            } else if (value != null) {
                throw new IllegalArgumentException("value is only allowed when presence is VALUE");
            }
        }

        public static TextFact absent() {
            return new TextFact(TextPresence.ABSENT, null);
        }

        public static TextFact nullValue() {
            return new TextFact(TextPresence.NULL, null);
        }

        public static TextFact value(String value) {
            return new TextFact(TextPresence.VALUE, value);
        }
    }

    public LiveJ4ControlFacts {
        Objects.requireNonNull(finalResultOnly, "finalResultOnly");
        Objects.requireNonNull(detailId, "detailId");
        Objects.requireNonNull(hasEventPlayerStatistics, "hasEventPlayerStatistics");
        Objects.requireNonNull(tournamentHasEventPlayerStatistics, "tournamentHasEventPlayerStatistics");
        Objects.requireNonNull(statusDescription, "statusDescription");
        Objects.requireNonNull(statusReason, "statusReason");
    }

    /** An explicit provider truth is required before a selected event is excluded. */
    public boolean isFinalResultOnly() {
        return finalResultOnly == BooleanFact.TRUE;
    }

    /** The documented special branch applies only when detailId is genuinely absent. */
    public boolean hasMissingDetailId() {
        return detailId == DetailIdFact.ABSENT;
    }

    /** A known detailId other than 1 (or JSON null) is not a documented live contract. */
    public boolean hasSupportedDetailId() {
        return detailId == DetailIdFact.ONE || detailId == DetailIdFact.ABSENT;
    }

    /** J5 lineups is allowed when the source flag is true or genuinely absent. */
    public boolean lineupsCallable() {
        return hasEventPlayerStatistics == BooleanFact.TRUE
                || hasEventPlayerStatistics == BooleanFact.ABSENT;
    }

    /** Player details remain opt-in: absence and null never create a clickable card. */
    public boolean playerCardsClickable() {
        return tournamentHasEventPlayerStatistics == BooleanFact.TRUE;
    }

    public boolean isHalftime(String sportStatus) {
        return "inprogress".equals(sportStatus) && statusDescription == StatusDescription.HALFTIME;
    }

    public boolean isSecondHalf(String sportStatus) {
        return "inprogress".equals(sportStatus) && statusDescription == StatusDescription.SECOND_HALF;
    }
}
