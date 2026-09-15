package com.bettingproject.sofascorelocal.port;

import com.bettingproject.sofascorelocal.domain.scheduledevents.J3CollectionData.*;
import com.bettingproject.sofascorelocal.domain.provider.J3MinimizedPageEvidence;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface J3CollectionStore {
    void begin(UUID runId, LocalDate date, Trigger trigger, Instant startedAt);
    void appendPage(UUID runId, J3MinimizedPageEvidence page);
    void publish(Proof proof, List<Entry> entries);
    /** Reconcile a proved-dead owner without replaying a page or asserting a missing response. */
    default void interrupt(UUID runId,Instant now,String reason) {
        throw new UnsupportedOperationException("J3 interruption reconciliation unavailable");
    }
    Optional<Collection> find(UUID runId);
    Optional<Collection> latest(LocalDate date);
    Optional<Collection> latest();
    boolean hasSuccess(LocalDate date);
    List<DateSummary> dates(int limit);
    Optional<CatalogPage> page(UUID runId,LocalDate date,int page,int pageSize);
    List<Legacy> legacyCandidates(int limit);
    void recordRecovery(Legacy source, String status, String reason, Instant recoveredAt);
}
