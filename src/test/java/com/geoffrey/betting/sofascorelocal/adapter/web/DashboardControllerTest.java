package com.geoffrey.betting.sofascorelocal.adapter.web;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class DashboardControllerTest {

    @Test
    void rendersTheDashboardModel() throws Exception {
        DashboardService dashboardService = mock(DashboardService.class);
        DashboardView dashboardView = new DashboardView(
                "2026-08-08T00:00:00Z",
                "EXPERIMENTAL",
                "LOCKED_OFFLINE_J1",
                false,
                "127.0.0.1:8087",
                "NON_CONFIGURED",
                1,
                "3 s",
                "AVAILABLE",
                "1",
                0L,
                0L,
                null,
                List.of());
        when(dashboardService.load()).thenReturn(dashboardView);

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new DashboardController(dashboardService))
                .build();

        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(model().attribute("dashboard", dashboardView));
    }
}
