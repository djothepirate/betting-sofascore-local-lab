package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsV1Parser;
import com.bettingproject.sofascorelocal.domain.provider.J3ManualCallIntentState;
import com.bettingproject.sofascorelocal.domain.scheduledevents.*;
import com.bettingproject.sofascorelocal.port.J3CollectionStore;
import com.bettingproject.sofascorelocal.port.RawSnapshotInspectionStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class J3TournamentCatalogService {
    private final J3ManualCollectionEvidenceService evidence;
    private final J3CatalogProjector projector;
    private final RawSnapshotInspectionStore snapshots;
    private J3CollectionStore store;
    @Autowired public J3TournamentCatalogService(J3ManualCollectionEvidenceService evidence,
            RawSnapshotInspectionStore snapshots, ObjectProvider<J3CollectionStore> stores) {
        this(evidence,snapshots); this.store=stores.getIfAvailable();
    }
    public J3TournamentCatalogService(J3ManualCollectionEvidenceService evidence,RawSnapshotInspectionStore snapshots) {
        this(evidence,snapshots,new ScheduledEventsV1Parser());
    }
    J3TournamentCatalogService(J3ManualCollectionEvidenceService evidence,RawSnapshotInspectionStore snapshots,ScheduledEventsV1Parser parser) {
        this.evidence=Objects.requireNonNull(evidence); this.snapshots=Objects.requireNonNull(snapshots); this.projector=new J3CatalogProjector(snapshots,parser);
    }
    public J3TournamentCatalog latest() {
        if (store!=null) {
            var durable=store.latest();
            if (durable.isPresent()) return durable.orElseThrow().catalog();
            return unavailable(null,J3TournamentCatalogStatus.NO_COLLECTION_EVIDENCE);
        }
        var document=evidence.latestDocument();
        if (document.isEmpty()) return unavailable(null,J3TournamentCatalogStatus.NO_COLLECTION_EVIDENCE);
        var proof=document.orElseThrow().evidence();
        if (proof.terminalState()!=J3ManualCallIntentState.COMPLETED)
            return unavailable(proof.collectionDate(),J3TournamentCatalogStatus.COLLECTION_NOT_COMPLETED);
        return projector.project(proof.collectionDate(),proof.pageAttempts()).catalog();
    }
    public J3TournamentCatalog forDate(LocalDate date) {
        return store==null ? latest() : store.latest(date).map(J3CollectionData.Collection::catalog)
                .orElseGet(()->unavailable(date,J3TournamentCatalogStatus.NO_COLLECTION_EVIDENCE));
    }
    public J3TournamentCatalog forCollection(UUID id,LocalDate date) {
        if (store==null) return unavailable(date,J3TournamentCatalogStatus.NO_COLLECTION_EVIDENCE);
        return store.find(id).filter(it->it.date().equals(date)).map(J3CollectionData.Collection::catalog)
                .orElseGet(()->unavailable(date,J3TournamentCatalogStatus.NO_COLLECTION_EVIDENCE));
    }
    public Optional<J3TournamentCatalogOption> resolve(long id) { return latest().findByTournamentId(id); }
    public java.util.List<J3TournamentCatalogOption> menuOptions(J3TournamentCatalog catalog, boolean includeAmateur) {
        return J3TournamentMenu.options(catalog, snapshots, includeAmateur);
    }
    public java.util.List<J3TournamentCatalogOption> menuOptions(J3TournamentCatalog catalog,
            boolean includeAmateur, boolean includeQualification) {
        return J3TournamentMenu.options(catalog, snapshots, includeAmateur, includeQualification);
    }
    private static J3TournamentCatalog unavailable(LocalDate date,J3TournamentCatalogStatus status) {
        return J3TournamentCatalog.unavailable(status,Optional.ofNullable(date));
    }
}
