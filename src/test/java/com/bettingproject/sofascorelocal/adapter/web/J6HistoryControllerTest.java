package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.application.event.J4EventQueryService;
import com.bettingproject.sofascorelocal.application.history.J6HistoryQueryService;
import com.bettingproject.sofascorelocal.application.history.J6OfflineHistoryDemoResult;
import com.bettingproject.sofascorelocal.application.history.J6OfflineHistoryDemoService;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservationView;
import com.bettingproject.sofascorelocal.domain.event.EventSourceTrace;
import com.bettingproject.sofascorelocal.domain.history.J6ChangeKind;
import com.bettingproject.sofascorelocal.domain.history.J6ComparedVersion;
import com.bettingproject.sofascorelocal.domain.history.J6HistoryClassification;
import com.bettingproject.sofascorelocal.domain.history.J6HistoryComparison;
import com.bettingproject.sofascorelocal.domain.history.J6HistoryPage;
import com.bettingproject.sofascorelocal.domain.history.J6HistoryStream;
import com.bettingproject.sofascorelocal.domain.history.J6HistoryVersion;
import com.bettingproject.sofascorelocal.domain.history.J6SemanticChange;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEventStatus;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTeam;
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
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(J6HistoryController.class)
class J6HistoryControllerTest {

    private static final CanonicalEventIdentity IDENTITY =
            CanonicalEventIdentity.sofascore(900_001L);

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    J6HistoryQueryService historyQueryService;

    @MockitoBean
    J6OfflineHistoryDemoService offlineHistoryDemoService;

    @MockitoBean
    J4EventQueryService eventQueryService;

    @MockitoBean
    LocalFormTokenService formTokenService;

    @MockitoBean
    CacheManager cacheManager;

    @Test
    void rendersEscapedNormalizedHistoryWithNoStoreHeaders() throws Exception {
        J6HistoryPage history = historyPage();
        when(eventQueryService.resolveZone("Europe/Paris"))
                .thenReturn(ZoneId.of("Europe/Paris"));
        when(formTokenService.issue(any(HttpSession.class))).thenReturn("local-token");
        when(historyQueryService.findHistory(
                IDENTITY.value(), Optional.empty(), 0, 25))
                .thenReturn(Optional.of(history));

        mockMvc.perform(get("/events/{id}/history", IDENTITY.value())
                        .param("zone", "Europe/Paris"))
                .andExpect(status().isOk())
                .andExpect(view().name("event-history"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(content().string(containsString("Historique local immuable")))
                .andExpect(content().string(containsString("SYNTHETIC_CHANGE")))
                .andExpect(content().string(containsString("status.description")))
                .andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
                .andExpect(content().string(not(containsString("{\"events\""))));
    }

    @Test
    void rendersAnArbitraryComparisonWithoutRawJson() throws Exception {
        when(eventQueryService.resolveZone("Europe/Paris"))
                .thenReturn(ZoneId.of("Europe/Paris"));
        when(historyQueryService.compare(
                IDENTITY.value(), J6HistoryStream.EVENT_STATE, 1, 2))
                .thenReturn(Optional.of(comparison()));

        mockMvc.perform(get("/events/{id}/history/compare", IDENTITY.value())
                        .param("stream", "EVENT_STATE")
                        .param("fromObservationId", "1")
                        .param("toObservationId", "2")
                        .param("zone", "Europe/Paris"))
                .andExpect(status().isOk())
                .andExpect(view().name("event-history-compare"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(content().string(containsString("Observation 1 → 2")))
                .andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
                .andExpect(content().string(not(containsString("{\"events\""))));
    }

    @Test
    void rejectsAnInvalidHistoryQueryAndReportsMissingVersions() throws Exception {
        when(eventQueryService.resolveZone("Europe/Paris"))
                .thenReturn(ZoneId.of("Europe/Paris"));
        when(historyQueryService.compare(
                IDENTITY.value(), J6HistoryStream.EVENT_STATE, 1, 2))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/events/{id}/history", IDENTITY.value())
                        .param("stream", "NOT_A_STREAM"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("pagination demandée n’est pas valide")));

        mockMvc.perform(get("/events/{id}/history/compare", IDENTITY.value())
                        .param("stream", "EVENT_STATE")
                        .param("fromObservationId", "1")
                        .param("toObservationId", "2"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("n’existe pas dans ce flux")));
    }

    @Test
    void importsTheOfflineCorpusOnlyAfterConsumingTheLocalToken() throws Exception {
        when(eventQueryService.resolveZone("Europe/Paris"))
                .thenReturn(ZoneId.of("Europe/Paris"));
        when(offlineHistoryDemoService.importCorpus()).thenReturn(
                new J6OfflineHistoryDemoResult(IDENTITY.value(), 12, 12, 0));

        mockMvc.perform(post("/events/history/offline-demo")
                        .param("localFormToken", "one-use-token")
                        .param("zone", "Europe/Paris"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/events/" + IDENTITY.value() + "/history?zone=Europe%2FParis"));

        verify(formTokenService).consume(any(HttpSession.class),
                org.mockito.ArgumentMatchers.eq("one-use-token"));
        verify(offlineHistoryDemoService).importCorpus();
    }

    private static J6HistoryPage historyPage() {
        EventSourceTrace before = source("j6-before", 'a', 1);
        EventSourceTrace after = source("j6-after", 'b', 2);
        J6SemanticChange change = new J6SemanticChange(
                "status.description",
                Optional.of("Before"),
                Optional.of("<script>alert(1)</script>"),
                J6ChangeKind.CHANGED);
        J6HistoryVersion newest = new J6HistoryVersion(
                J6HistoryStream.EVENT_STATE,
                2,
                after,
                hash('d'),
                Optional.empty(),
                Optional.empty(),
                J6HistoryClassification.SYNTHETIC_CHANGE,
                Set.of(J6HistoryClassification.SYNTHETIC_CHANGE),
                OptionalLong.of(1),
                List.of(change),
                Optional.empty());
        J6HistoryVersion baseline = new J6HistoryVersion(
                J6HistoryStream.EVENT_STATE,
                1,
                before,
                hash('c'),
                Optional.empty(),
                Optional.empty(),
                J6HistoryClassification.BASELINE,
                Set.of(J6HistoryClassification.BASELINE),
                OptionalLong.empty(),
                List.of(),
                Optional.empty());
        return new J6HistoryPage(
                current(after),
                Optional.empty(),
                0,
                25,
                2,
                1,
                List.of(newest, baseline));
    }

    private static J6HistoryComparison comparison() {
        EventSourceTrace before = source("j6-before", 'a', 1);
        EventSourceTrace after = source("j6-after", 'b', 2);
        return new J6HistoryComparison(
                J6HistoryStream.EVENT_STATE,
                new J6ComparedVersion(
                        1, before, hash('c'), Optional.empty(), Optional.empty(), Optional.empty()),
                new J6ComparedVersion(
                        2, after, hash('d'), Optional.empty(), Optional.empty(), Optional.empty()),
                J6HistoryClassification.SYNTHETIC_CHANGE,
                List.of(new J6SemanticChange(
                        "status.description",
                        Optional.of("Before"),
                        Optional.of("<script>alert(1)</script>"),
                        J6ChangeKind.CHANGED)));
    }

    private static CanonicalEventObservationView current(EventSourceTrace source) {
        return new CanonicalEventObservationView(
                2,
                IDENTITY,
                Instant.parse("2026-08-12T10:00:00Z"),
                new ScheduledTeam(9101, "Home <script>alert(1)</script>"),
                new ScheduledTeam(9202, "Away"),
                new ScheduledEventStatus("finished", Optional.of("Finished")),
                Optional.empty(),
                source,
                hash('d'),
                2);
    }

    private static EventSourceTrace source(String fixtureId, char hash, long hour) {
        return EventSourceTrace.syntheticFixture(
                fixtureId,
                hash(hash),
                "scheduled-events-v1",
                Instant.parse("2026-08-18T10:00:00Z").plusSeconds(hour * 3_600));
    }

    private static String hash(char value) {
        return Character.toString(value).repeat(64);
    }
}
