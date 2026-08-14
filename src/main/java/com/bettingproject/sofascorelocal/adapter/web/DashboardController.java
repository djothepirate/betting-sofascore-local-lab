package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.application.network.J3ManualCallControlService;
import com.bettingproject.sofascorelocal.application.network.J3ManualCollectionEvidenceService;
import com.bettingproject.sofascorelocal.security.LocalFormTokenService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

    private final DashboardService dashboardService;
    private final J3ManualCallControlService manualCallControlService;
    private final J3ManualCollectionEvidenceService collectionEvidenceService;
    private final LocalFormTokenService formTokenService;

    public DashboardController(
            DashboardService dashboardService,
            J3ManualCallControlService manualCallControlService,
            J3ManualCollectionEvidenceService collectionEvidenceService,
            LocalFormTokenService formTokenService) {
        this.dashboardService = dashboardService;
        this.manualCallControlService = manualCallControlService;
        this.collectionEvidenceService = collectionEvidenceService;
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
        model.addAttribute("localFormToken", formTokenService.issue(session));
        return "dashboard";
    }
}
