package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.application.snapshot.RawSnapshotInspectionError;
import com.bettingproject.sofascorelocal.application.snapshot.RawSnapshotInspectionException;
import com.bettingproject.sofascorelocal.application.snapshot.RawSnapshotJsonInspection;
import com.bettingproject.sofascorelocal.application.snapshot.RawSnapshotJsonInspectionService;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotInspectionSummary;
import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotSchemaStatus;
import com.bettingproject.sofascorelocal.security.LocalFormTokenService;

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(SnapshotInspectionController.class)
class SnapshotInspectionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RawSnapshotJsonInspectionService inspectionService;

    @MockitoBean
    private LocalFormTokenService formTokenService;

    @MockitoBean
    private CacheManager cacheManager;

    @Test
    void rendersEscapedJsonOnlyAfterConsumingTheExplicitLocalAction() throws Exception {
        RawSnapshotJsonInspection inspection = new RawSnapshotJsonInspection(
                summary(),
                Instant.parse("2026-08-14T10:00:00Z"),
                "{\n  \"team\" : \"<script>alert(1)</script>\"\n}");
        when(inspectionService.inspect(41L)).thenReturn(inspection);

        mockMvc.perform(post("/snapshot-inspection")
                        .param("localFormToken", "one-use-token")
                        .param("snapshotId", "41"))
                .andExpect(status().isOk())
                .andExpect(view().name("snapshot-inspection"))
                .andExpect(model().attribute("inspection", inspection))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")))
                .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"))
                .andExpect(header().string("X-Robots-Tag", "noindex, nofollow, noarchive"))
                .andExpect(content().string(containsString(
                        "&lt;script&gt;alert(1)&lt;/script&gt;")))
                .andExpect(content().string(not(containsString(
                        "<script>alert(1)</script>"))));

        verify(formTokenService).consume(
                org.mockito.ArgumentMatchers.any(HttpSession.class),
                org.mockito.ArgumentMatchers.eq("one-use-token"));
        verify(inspectionService).inspect(41L);
    }

    @Test
    void exposesOnlyASafeErrorWhenTheSnapshotDoesNotExist() throws Exception {
        when(inspectionService.inspect(999L)).thenThrow(
                new RawSnapshotInspectionException(
                        RawSnapshotInspectionError.SNAPSHOT_NOT_FOUND));

        mockMvc.perform(post("/snapshot-inspection")
                        .param("localFormToken", "one-use-token")
                        .param("snapshotId", "999"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("snapshot-inspection"))
                .andExpect(model().attribute(
                        "inspectionErrorCode",
                        "SNAPSHOT_NOT_FOUND"))
                .andExpect(content().string(containsString(
                        "Le snapshot brut demandé n’est pas disponible localement.")))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")));
    }

    private static RawSnapshotInspectionSummary summary() {
        return new RawSnapshotInspectionSummary(
                41L,
                "SCHEDULED_EVENTS",
                "SCHEDULED_EVENTS|date=2026-08-14|page=1",
                Instant.parse("2026-08-14T09:31:23Z"),
                200,
                "application/json; charset=utf-8",
                39L,
                "a".repeat(64),
                "scheduled-events-v1",
                RawSnapshotSchemaStatus.PARSED);
    }
}
