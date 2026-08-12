package com.bettingproject.sofascorelocal.adapter.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(DashboardController.class)
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService dashboardService;

    @MockitoBean
    private CacheManager cacheManager;

    @Test
    void rendersTheDashboardModel() throws Exception {
        DashboardView dashboardView = new DashboardView(
                "2026-08-08T00:00:00Z",
                "EXPERIMENTAL",
                "LOCKED_OFFLINE_J3_POLICY",
                false,
                "127.0.0.1:8087",
                "NON_CONFIGURED",
                1,
                "3 s",
                "AVAILABLE",
                "1",
                0L,
                0L,
                new DashboardView.FixtureCorpusView(
                        "AVAILABLE_OFFLINE",
                        "SCHEDULED_EVENTS",
                        "SYNTHETIC",
                        false,
                        "scheduled-events-v1",
                        9,
                        9,
                        5,
                        3,
                        1,
                        0),
                null,
                List.of());
        when(dashboardService.load()).thenReturn(dashboardView);

        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(model().attribute("dashboard", dashboardView))
                .andExpect(content().string(containsString("LOCKED_OFFLINE_J3_POLICY")))
                .andExpect(content().string(containsString("9 / 9 disponibles")))
                .andExpect(content().string(containsString("scheduled-events-v1")))
                .andExpect(content().string(containsString("NON VALIDÉ")));
    }
}
