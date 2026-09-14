package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.application.network.J3ManualCallControlService;
import com.bettingproject.sofascorelocal.application.network.J3ManualCollectionEvidenceService;
import com.bettingproject.sofascorelocal.application.network.J3TournamentCatalogService;
import com.bettingproject.sofascorelocal.application.network.TournamentEventDiscoveryControlService;
import com.bettingproject.sofascorelocal.application.retention.J6RawPayloadRetentionService;
import com.bettingproject.sofascorelocal.application.retention.J6RetentionError;
import com.bettingproject.sofascorelocal.application.retention.J6RetentionException;
import com.bettingproject.sofascorelocal.application.snapshot.RawSnapshotJsonInspectionService;
import com.bettingproject.sofascorelocal.security.LocalFormTokenService;
import com.bettingproject.sofascorelocal.domain.scheduledevents.J3DatePolicy;
import jakarta.servlet.http.HttpSession;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

    private final DashboardService dashboardService;
    private final J3ManualCallControlService manualCallControlService;
    private final J3ManualCollectionEvidenceService collectionEvidenceService;
    private final J3TournamentCatalogService tournamentCatalogService;
    private final TournamentEventDiscoveryControlService tournamentDiscoveryControlService;
    private final RawSnapshotJsonInspectionService snapshotInspectionService;
    private final J6RawPayloadRetentionService retentionService;
    private final LocalFormTokenService formTokenService;
    private com.bettingproject.sofascorelocal.application.network.J3RuntimeService j3Runtime;
    private com.bettingproject.sofascorelocal.port.J3CollectionStore j3Collections;
    @org.springframework.beans.factory.annotation.Autowired
    void configureJ3(com.bettingproject.sofascorelocal.application.network.J3RuntimeService runtime,
                     com.bettingproject.sofascorelocal.port.J3CollectionStore collections) {
        this.j3Runtime=runtime;this.j3Collections=collections;
    }

    public DashboardController(
            DashboardService dashboardService,
            J3ManualCallControlService manualCallControlService,
            J3ManualCollectionEvidenceService collectionEvidenceService,
            J3TournamentCatalogService tournamentCatalogService,
            TournamentEventDiscoveryControlService tournamentDiscoveryControlService,
            RawSnapshotJsonInspectionService snapshotInspectionService,
            J6RawPayloadRetentionService retentionService,
            LocalFormTokenService formTokenService) {
        this.dashboardService = dashboardService;
        this.manualCallControlService = manualCallControlService;
        this.collectionEvidenceService = collectionEvidenceService;
        this.tournamentCatalogService = tournamentCatalogService;
        this.tournamentDiscoveryControlService = tournamentDiscoveryControlService;
        this.snapshotInspectionService = snapshotInspectionService;
        this.retentionService = retentionService;
        this.formTokenService = formTokenService;
    }

    public String dashboard(Model model, HttpSession session) {
        return dashboard(model,session,null);
    }
    public String dashboard(Model model, HttpSession session, java.time.LocalDate j3Date) {
        return dashboard(model, session, j3Date, false);
    }

    @GetMapping({"/", "/dashboard"})
    public String dashboard(Model model, HttpSession session,
            @org.springframework.web.bind.annotation.RequestParam(required=false) java.time.LocalDate j3Date,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue="false") boolean includeAmateur) {
        var selectedDate=j3Date==null?java.time.LocalDate.now(
                com.bettingproject.sofascorelocal.domain.scheduledevents.J3AutomationData.ZONE):j3Date;
        var now=java.time.Instant.now();
        model.addAttribute("includeAmateur", includeAmateur);
        model.addAttribute("j3MenuDate", selectedDate);
        model.addAttribute("j3MinimumDate",J3DatePolicy.MINIMUM_COLLECTION_DATE);
        model.addAttribute("j3MaximumDate",J3DatePolicy.maximumDate(now));
        model.addAttribute("j3PlanMinimumTime",java.time.LocalDate.ofInstant(now,
                com.bettingproject.sofascorelocal.domain.scheduledevents.J3AutomationData.ZONE).atStartOfDay());
        model.addAttribute("j3PlanMaximumTime",J3DatePolicy.maximumDate(now).atTime(23,59));
        model.addAttribute("dashboard", dashboardService.load());
        model.addAttribute(
                "manualCall",
                ManualCallControlView.from(manualCallControlService.snapshot()));
        model.addAttribute(
                "collectionEvidence",
                collectionEvidenceService.latestDocument().orElse(null));
        try {
            var catalog = j3Collections==null?tournamentCatalogService.latest():tournamentCatalogService.forDate(selectedDate);
            model.addAttribute("tournamentCatalog", catalog);
            model.addAttribute("tournamentMenuOptions", tournamentCatalogService.menuOptions(catalog, includeAmateur));
            if(j3Runtime!=null) {
                var saved=j3Collections.latest(selectedDate).orElse(null);
                model.addAttribute("j3Date",selectedDate);
                model.addAttribute("j3Collection",saved);
                model.addAttribute("j3HistoryUnavailable",saved==null && j3Collections.hasSuccess(selectedDate));
                model.addAttribute("j3Dates",j3Collections.dates(3660));
                model.addAttribute("j3Settings",j3Runtime.settings());
                model.addAttribute("j3Orders",j3Runtime.orders());
                model.addAttribute("j3OrderId",java.util.UUID.randomUUID());
                model.addAttribute("j3RuleId",java.util.UUID.randomUUID());
                String providerReason=j3Runtime.providerUnavailableReason();
                model.addAttribute("j3ProviderReady",providerReason==null && j3Runtime.runtimeReason()==null);
                model.addAttribute("j3ProviderReason",J3Presentation.reason(providerReason));
                model.addAttribute("j3RuntimeReason",J3Presentation.reason(j3Runtime.runtimeReason()));
                model.addAttribute("j3CleanupPending",j3Runtime.cleanupPending());
            }
        }
        catch (DataAccessException | IllegalArgumentException | IllegalStateException exception) {
            model.addAttribute("j3Settings",null);
            model.addAttribute("j3ProviderReady",false);
            model.addAttribute("tournamentCatalogUnavailable", true);
        }
        model.addAttribute(
                "tournamentDiscoveryControl",
                tournamentDiscoveryControlService.snapshot());
        model.addAttribute(
                "snapshotInspectionCatalog",
                snapshotInspectionService.loadCatalog());
        try {
            var retentionPreview = retentionService.preview();
            model.addAttribute("retentionPreview", retentionPreview);
            model.addAttribute(
                    "retentionPreviewCandidates",
                    retentionPreview.candidates().stream().limit(25).toList());
        }
        catch (J6RetentionException exception) {
            if (exception.error() != J6RetentionError.PROVIDER_CAMPAIGN_ACTIVE) throw exception;
            model.addAttribute("retentionPreviewUnavailable", true);
            model.addAttribute("retentionPreviewProviderBusy", true);
        }
        catch (DataAccessException exception) {
            model.addAttribute("retentionPreviewUnavailable", true);
        }
        model.addAttribute("localFormToken", formTokenService.issue(session));
        return "dashboard";
    }
}
