package com.bettingproject.sofascorelocal.domain.scheduledevents;

import com.bettingproject.sofascorelocal.domain.scheduledevents.J3CollectionData.Trigger;
import java.time.*;
import java.util.*;

/** Scheduling identities use civil dates in Paris and store resolved instants separately. */
public final class J3AutomationData {
    public static final ZoneId ZONE=ZoneId.of("Europe/Paris");
    public static final Duration ORDER_DEADLINE=Duration.ofMinutes(20);
    public static final int MAXIMUM_PLANS=100;
    private J3AutomationData() { }
    public enum Mode { STARTUP_OR_DAY_CHANGE, DAILY_AT }
    public enum OrderState { FUTURE, QUEUED, RUNNING, COMPLETED, FAILED, CANCELLED, INTERRUPTED, MISSED, SKIPPED }
    public record Settings(boolean enabled,Mode mode,LocalTime dailyTime,long revision,Instant updatedAt) {
        public Settings {
            Objects.requireNonNull(mode);Objects.requireNonNull(updatedAt);
            if(revision<1 || (mode==Mode.DAILY_AT)!=(dailyTime!=null)
                    || dailyTime!=null && (dailyTime.getSecond()!=0 || dailyTime.getNano()!=0))
                throw new IllegalArgumentException("J3_SETTINGS_INVALID");
        }
    }
    public record Owner(UUID id,long pid,Instant processStartedAt) {
        public Owner {Objects.requireNonNull(id);Objects.requireNonNull(processStartedAt);if(pid<1)throw new IllegalArgumentException("J3_OWNER_INVALID");}
    }
    public record Order(UUID id,String occurrenceKey,UUID ruleId,int ruleRevision,LocalDate date,Trigger trigger,
                        Instant dueAt,Instant createdAt,OrderState state,Instant admittedAt,Instant deadline,
                        Instant finishedAt,String reason,Owner owner,String inputSha256) {
        public Order {
            Objects.requireNonNull(id);Objects.requireNonNull(occurrenceKey);Objects.requireNonNull(date);
            Objects.requireNonNull(trigger);Objects.requireNonNull(dueAt);Objects.requireNonNull(createdAt);Objects.requireNonNull(state);
        }
        public boolean automatic() {return trigger.automatic();}
        public boolean terminal() {return state!=OrderState.FUTURE && state!=OrderState.QUEUED && state!=OrderState.RUNNING;}
    }
    /** One-shot wall times must exist; an overlap requires an explicit valid offset. */
    public static Instant resolveOneShot(LocalDateTime value,ZoneOffset selectedOffset) {
        Objects.requireNonNull(value);requireMinute(value.toLocalTime());
        var offsets=ZONE.getRules().getValidOffsets(value);
        if(offsets.isEmpty())throw new IllegalArgumentException("J3_TIME_DOES_NOT_EXIST");
        if(offsets.size()>1 && selectedOffset==null)throw new IllegalArgumentException("J3_TIME_OFFSET_REQUIRED");
        ZoneOffset offset=selectedOffset==null?offsets.getFirst():selectedOffset;
        if(!offsets.contains(offset))throw new IllegalArgumentException("J3_TIME_OFFSET_INVALID");
        return value.toInstant(offset);
    }
    /** A recurring gap advances to its transition; overlaps use the first instant exactly once. */
    public static Instant dailyOccurrence(LocalDate date,LocalTime time) {
        Objects.requireNonNull(date);requireMinute(time);
        var local=date.atTime(time);var rules=ZONE.getRules();var offsets=rules.getValidOffsets(local);
        return offsets.isEmpty()?rules.getTransition(local).getInstant():local.toInstant(offsets.getFirst());
    }
    public static String dailyKey(LocalDate date) {return "DAILY|"+date;}
    public static String timedKey(Settings settings,LocalDate date) {
        return "DAILY_AT|"+settings.revision()+"|"+date+"|"+dailyOccurrence(date,settings.dailyTime());
    }
    private static void requireMinute(LocalTime time) {
        Objects.requireNonNull(time);
        if(time.getSecond()!=0 || time.getNano()!=0)throw new IllegalArgumentException("J3_TIME_MINUTE_REQUIRED");
    }
}
