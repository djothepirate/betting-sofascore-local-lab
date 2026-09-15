package com.bettingproject.sofascorelocal.port;

import com.bettingproject.sofascorelocal.domain.scheduledevents.J3AutomationData.*;
import com.bettingproject.sofascorelocal.domain.scheduledevents.J3CollectionData.Trigger;
import java.time.*;
import java.util.*;
import java.util.function.Predicate;

public interface J3AutomationStore {
    Settings settings();
    Settings configure(long expectedRevision,boolean enabled,Mode mode,LocalTime time,Instant now);
    boolean lead(Owner owner,Predicate<Owner> isAlive);
    void release(Owner owner);
    Order schedule(UUID ruleId,int revision,LocalDate date,Instant dueAt,Instant now);
    void cancel(UUID orderId,Instant now);
    Order manual(UUID id,LocalDate date,Trigger trigger,String inputSha256,Owner owner,Instant now);
    /** Last tick and continuity are local monotonic-clock observations, not inferred from wall time. */
    void tick(Owner owner,Instant previousTick,Instant now,boolean continuous,String unavailableReason);
    Optional<Order> claim(Owner owner,Instant now);
    void finish(UUID id,Owner owner,OrderState state,String reason,Instant now);
    List<Order> recent(int limit);
    Optional<Order> find(UUID id);
    List<Order> interrupted(Owner current);
}
