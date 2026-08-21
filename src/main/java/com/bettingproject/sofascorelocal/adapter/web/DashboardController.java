package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.application.network.J3ManualCallControlService;
import com.bettingproject.sofascorelocal.application.network.J3ManualCollectionEvidenceService;
import com.bettingproject.sofascorelocal.application.network.J3TournamentCatalogService;
import com.bettingproject.sofascorelocal.application.network.TournamentEventDiscoveryControlService;
import com.bettingproject.sofascorelocal.application.retention.J6RawPayloadRetentionService;
import com.bettingproject.sofascorelocal.application.snapshot.RawSnapshotJsonInspectionService;
import com.bettingproject.sofascorelocal.security.LocalFormTokenService;
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

    @GetMapping({"/", "/dashboard"})
    public String dashboard(Model model, HttpSession session) {
        model.addAttribute("dashboard", dashboardService.load());
        model.addAttribute(
                "manualCall",
                ManualCallControlView.from(manualCallControlService.snapshot()));
        model.addAttribute(
                "collectionEvidence",
                collectionEvidenceService.latestDocument().orElse(null));
        try {
            model.addAttribute("tournamentCatalog", tournamentCatalogService.latest());
        }
        catch (DataAccessException | IllegalArgumentException | IllegalStateException exception) {
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
        catch (DataAccessException | IllegalArgumentException | IllegalStateException exception) {
            model.addAttribute("retentionPreviewUnavailable", true);
        }
        model.addAttribute("localFormToken", formTokenService.issue(session));
        return "dashboard";
    }
}
