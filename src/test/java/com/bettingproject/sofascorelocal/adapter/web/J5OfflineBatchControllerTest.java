package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.application.event.J4EventQueryService;
import com.bettingproject.sofascorelocal.application.event.J4EventSearchItem;
import com.bettingproject.sofascorelocal.application.event.J4EventSearchResult;
import com.bettingproject.sofascorelocal.application.event.J5OfflineBatchControlService;
import com.bettingproject.sofascorelocal.application.event.J5OfflineBatchControlSnapshot;
import com.bettingproject.sofascorelocal.application.event.J5OfflineBatchError;
import com.bettingproject.sofascorelocal.application.event.J5OfflineBatchEventResult;
import com.bettingproject.sofascorelocal.application.event.J5OfflineBatchException;
import com.bettingproject.sofascorelocal.application.event.J5OfflineBatchImportService;
import com.bettingproject.sofascorelocal.application.event.J5OfflineBatchPlan;
import com.bettingproject.sofascorelocal.application.event.J5OfflineBatchPlanEvent;
import com.bettingproject.sofascorelocal.application.event.J5OfflineBatchResult;
import com.bettingproject.sofascorelocal.application.event.J5OfflineBatchState;
import com.bettingproject.sofascorelocal.application.event.J5OfflineBatchUpload;
import com.bettingproject.sofascorelocal.application.event.J5OfflineBatchUploadService;
import com.bettingproject.sofascorelocal.application.network.J5RealEndpointResult;
import com.bettingproject.sofascorelocal.application.network.J5LocalUnavailableEvidence;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservationView;
import com.bettingproject.sofascorelocal.domain.event.EventSourceTrace;
import com.bettingproject.sofascorelocal.domain.eventdata.J5CompletenessStatus;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEventStatus;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTeam;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTournament;
import com.bettingproject.sofascorelocal.security.LocalFormTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockPart;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(J5OfflineBatchController.class)
@Import({LocalFormTokenService.class, J5OfflineBatchUploadService.class})
class J5OfflineBatchControllerTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 22);
    private static final ZoneId ZONE = ZoneId.of("Europe/Paris");
    private static final Instant FROM = Instant.parse("2026-08-21T22:00:00Z");
    private static final Instant TO = Instant.parse("2026-08-22T22:00:00Z");
    private static final Instant STARTS_AT = Instant.parse("2026-08-22T18:00:00Z");
    private static final Instant PREPARED_AT = Instant.parse("2026-08-22T08:00:00Z");
    private static final CanonicalEventIdentity IDENTITY =
            CanonicalEventIdentity.sofascore(900_001L);
    private static final UUID EVENT_ID = IDENTITY.value();
    private static final UUID REQUEST_ID = UUID.fromString(
            "85000000-0000-0000-0000-000000000001");
    private static final String PLAN_SHA = "a".repeat(64);
    private static final String NORMALIZED_SHA = "b".repeat(64);
    private static final String CONFIRMATION =
            "IMPORTER 1 MATCHS J5 HORS LIGNE " + PLAN_SHA;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LocalFormTokenService formTokenService;

    @MockitoBean
    private J4EventQueryService eventQueryService;

    @MockitoBean
    private J5OfflineBatchControlService controlService;

    @MockitoBean
    private J5OfflineBatchImportService importService;

    @MockitoBean
    private CacheManager cacheManager;

    @BeforeEach
    void allowTheCurrentPlanToReachMultipartValidation() {
        when(controlService.requireReadyForUpload(REQUEST_ID)).thenReturn(plan());
    }

    @Test
    void rendersTheLocalSelectionAndPendingPlanWithStrictNoStoreHeaders()
            throws Exception {
        J4EventSearchResult search = search();
        J5OfflineBatchControlSnapshot control = pendingControl();
        when(eventQueryService.search(DATE, ZONE.getId())).thenReturn(search);
        when(controlService.snapshot()).thenReturn(control);

        mockMvc.perform(get("/j5-import-batches")
                        .param("date", DATE.toString())
                        .param("zone", ZONE.getId())
                        .flashAttr("batchMessage", "Message local")
                        .flashAttr("batchMessageKind", "safe"))
                .andExpect(status().isOk())
                .andExpect(view().name("j5-offline-batches"))
                .andExpect(model().attribute("search", search))
                .andExpect(model().attribute("control", control))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store, no-cache, must-revalidate, max-age=0"))
                .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"))
                .andExpect(header().string(HttpHeaders.EXPIRES, "0"))
                .andExpect(header().string(
                        "X-Robots-Tag", "noindex, nofollow, noarchive"))
                .andExpect(header().string(
                        "Content-Security-Policy",
                        containsString("script-src 'self'")))
                .andExpect(content().string(containsString("ZÉRO APPEL FOURNISSEUR")))
                .andExpect(content().string(containsString("name=\"eventIds\"")))
                .andExpect(content().string(containsString(EVENT_ID.toString())))
                .andExpect(content().string(containsString(CONFIRMATION)))
                .andExpect(content().string(containsString("aria-live=\"polite\"")))
                .andExpect(content().string(containsString(
                        "event-900001-statistics.json")))
                .andExpect(content().string(containsString(
                        "event-900001-incidents.json")))
                .andExpect(content().string(containsString(
                        "event-900001-lineups.json")))
                .andExpect(content().string(containsString("name=\"unavailable404\"")))
                .andExpect(content().string(containsString(
                        "404 observés sans fichier téléchargeable")))
                .andExpect(content().string(containsString(
                        "/js/j5-offline-batch-manifest.js")))
                .andExpect(content().string(containsString(
                        "data-expected-count=\"3\"")))
                .andExpect(content().string(containsString(
                        "data-batch-manifest-summary=\"true\"")))
                .andExpect(content().string(containsString(
                        "data-batch-evidence-row=\"true\"")))
                .andExpect(content().string(containsString(
                        "data-batch-submit=\"true\"")))
                .andExpect(content().string(not(containsString(
                        "https://www.sofascore.com/api"))));
    }

    @Test
    void preparesOnlyTheCheckedCanonicalEventsAndConsumesTheTokenOnce()
            throws Exception {
        MockHttpSession session = new MockHttpSession();
        String token = formTokenService.issue(session);
        when(controlService.prepare(DATE, ZONE.getId(), List.of(EVENT_ID)))
                .thenReturn(pendingControl());

        mockMvc.perform(post("/j5-import-batches/prepare")
                        .session(session)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("localFormToken", token)
                        .param("date", DATE.toString())
                        .param("zone", ZONE.getId())
                        .param("eventIds", EVENT_ID.toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/j5-import-batches?date=2026-08-22&zone=Europe%2FParis"))
                .andExpect(flash().attribute("batchMessageKind", "safe"));

        mockMvc.perform(post("/j5-import-batches/prepare")
                        .session(session)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("localFormToken", token)
                        .param("date", DATE.toString())
                        .param("zone", ZONE.getId())
                        .param("eventIds", EVENT_ID.toString()))
                .andExpect(status().isBadRequest());

        verify(controlService, times(1))
                .prepare(DATE, ZONE.getId(), List.of(EVENT_ID));
    }

    @Test
    void capturesOneMultipartSelectionAndForwardsOnlyMinimizedUploads()
            throws Exception {
        MockHttpSession session = new MockHttpSession();
        String token = formTokenService.issue(session);
        J5OfflineBatchResult result = mock(J5OfflineBatchResult.class);
        when(result.completed()).thenReturn(true);
        when(result.eventCount()).thenReturn(1);
        when(result.localJsonImports()).thenReturn(3);
        when(importService.execute(
                eq(REQUEST_ID),
                eq(CONFIRMATION),
                eq(true),
                anyList())).thenReturn(result);

        mockMvc.perform(executionRequest(
                        session,
                        token,
                        json("event-900001-statistics.json"),
                        json("event-900001-incidents.json"),
                        json("event-900001-lineups.json")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/j5-import-batches?date=2026-08-22&zone=Europe%2FParis"))
                .andExpect(flash().attribute("batchResult", result))
                .andExpect(flash().attribute("batchMessageKind", "safe"));

        @SuppressWarnings("unchecked")
        var uploads = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(importService).execute(
                eq(REQUEST_ID),
                eq(CONFIRMATION),
                eq(true),
                uploads.capture());
        List<J5OfflineBatchUpload> captured = uploads.getValue();
        org.assertj.core.api.Assertions.assertThat(captured)
                .extracting(J5OfflineBatchUpload::fileName)
                .containsExactly(
                        "event-900001-statistics.json",
                        "event-900001-incidents.json",
                        "event-900001-lineups.json");
        org.assertj.core.api.Assertions.assertThat(captured)
                .allSatisfy(upload ->
                        org.assertj.core.api.Assertions.assertThat(
                                upload.payload().bytes()).isEqualTo(
                                        "{}".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void combinesFilesAndAnExplicit404DeclarationIntoTheExactManifest()
            throws Exception {
        MockHttpSession session = new MockHttpSession();
        String token = formTokenService.issue(session);
        J5OfflineBatchResult result = mock(J5OfflineBatchResult.class);
        when(result.completed()).thenReturn(true);
        when(result.eventCount()).thenReturn(1);
        when(result.localJsonImports()).thenReturn(3);
        when(importService.execute(
                eq(REQUEST_ID),
                eq(CONFIRMATION),
                eq(true),
                anyList())).thenReturn(result);

        mockMvc.perform(executionRequest(
                        session,
                        token,
                        json("event-900001-incidents.json"),
                        json("event-900001-lineups.json"),
                        unavailable404("event-900001-statistics.json")))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("batchResult", result))
                .andExpect(flash().attribute(
                        "batchMessage", containsString("1 déclaration(s) 404")));

        @SuppressWarnings("unchecked")
        var uploads = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(importService).execute(
                eq(REQUEST_ID), eq(CONFIRMATION), eq(true), uploads.capture());
        List<J5OfflineBatchUpload> captured = uploads.getValue();
        org.assertj.core.api.Assertions.assertThat(captured)
                .extracting(J5OfflineBatchUpload::fileName)
                .containsExactly(
                        "event-900001-incidents.json",
                        "event-900001-lineups.json",
                        "event-900001-statistics.json");
        org.assertj.core.api.Assertions.assertThat(captured.getLast().payload())
                .isEqualTo(J5LocalUnavailableEvidence.declared404());
    }

    @Test
    void acceptsOnly404DeclarationsWithTheBrowserEmptyFileSentinel() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String token = formTokenService.issue(session);
        J5OfflineBatchResult result = mock(J5OfflineBatchResult.class);
        when(result.completed()).thenReturn(true);
        when(result.eventCount()).thenReturn(1);
        when(result.localJsonImports()).thenReturn(3);
        when(importService.execute(
                eq(REQUEST_ID), eq(CONFIRMATION), eq(true), anyList())).thenReturn(result);

        mockMvc.perform(executionRequest(
                        session,
                        token,
                        emptyFileSentinel(),
                        unavailable404("event-900001-statistics.json"),
                        unavailable404("event-900001-incidents.json"),
                        unavailable404("event-900001-lineups.json")))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("batchResult", result))
                .andExpect(flash().attribute(
                        "batchMessage", containsString("3 déclaration(s) 404")));

        verify(importService).execute(
                eq(REQUEST_ID), eq(CONFIRMATION), eq(true), anyList());
    }

    @Test
    void rejectsAFileAndA404DeclarationForTheSameBatchSlot() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String token = formTokenService.issue(session);

        mockMvc.perform(executionRequest(
                        session,
                        token,
                        json("event-900001-statistics.json"),
                        json("event-900001-incidents.json"),
                        json("event-900001-lineups.json"),
                        unavailable404("event-900001-statistics.json")))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("batchErrorCode", "DUPLICATE_FILE"))
                .andExpect(flash().attribute(
                        "batchMessage", containsString("combine fichier et déclaration 404")));

        verify(importService, never()).execute(
                eq(REQUEST_ID), eq(CONFIRMATION), eq(true), anyList());
    }

    @Test
    void rejectsA404DeclarationThatCarriesAClientFilename() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String token = formTokenService.issue(session);
        MockPart disguisedDeclaration = new MockPart(
                "unavailable404",
                "declaration.txt",
                "event-900001-statistics.json".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(executionRequest(
                        session,
                        token,
                        json("event-900001-incidents.json"),
                        json("event-900001-lineups.json"),
                        disguisedDeclaration))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("batchErrorCode", "FILE_SET_MISMATCH"));

        verify(importService, never()).execute(
                eq(REQUEST_ID), eq(CONFIRMATION), eq(true), anyList());
    }

    @Test
    void rejectsNonUtf8BytesInA404Declaration() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String token = formTokenService.issue(session);
        MockPart invalidUtf8 = new MockPart(
                "unavailable404", new byte[] {(byte) 0xc3, 0x28});

        mockMvc.perform(executionRequest(
                        session,
                        token,
                        json("event-900001-incidents.json"),
                        json("event-900001-lineups.json"),
                        invalidUtf8))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("batchErrorCode", "FILE_NAME_INVALID"));

        verify(importService, never()).execute(
                eq(REQUEST_ID), eq(CONFIRMATION), eq(true), anyList());
    }

    @Test
    void rejectsAMissingBatchSlotWithoutA404Declaration() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String token = formTokenService.issue(session);

        mockMvc.perform(executionRequest(
                        session,
                        token,
                        json("event-900001-incidents.json"),
                        json("event-900001-lineups.json")))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("batchErrorCode", "FILE_SET_MISMATCH"))
                .andExpect(flash().attribute(
                        "batchMessage", containsString("exactement une preuve par famille")));

        verify(importService, never()).execute(
                eq(REQUEST_ID), eq(CONFIRMATION), eq(true), anyList());
    }

    @Test
    void rejectsAnEmptyMultipartBeforeTheImportServiceWithASafeError()
            throws Exception {
        MockHttpSession session = new MockHttpSession();
        String token = formTokenService.issue(session);
        MockPart empty = new MockPart(
                "batchFiles",
                "event-900001-statistics.json",
                new byte[0]);
        empty.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        mockMvc.perform(executionRequest(session, token, empty))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("batchErrorCode", "EMPTY_PAYLOAD"))
                .andExpect(flash().attribute(
                        "batchMessage", "Un fichier JSON sélectionné est vide."));

        verify(importService, never()).execute(
                eq(REQUEST_ID),
                eq(CONFIRMATION),
                eq(true),
                anyList());
    }

    @Test
    void exposesOnlyTheSafeCodeWhenTheImportRejectsSensitiveContent()
            throws Exception {
        MockHttpSession session = new MockHttpSession();
        String token = formTokenService.issue(session);
        when(importService.execute(
                eq(REQUEST_ID),
                eq(CONFIRMATION),
                eq(true),
                anyList())).thenThrow(new J5OfflineBatchException(
                        J5OfflineBatchError.SENSITIVE_CONTENT,
                        new IllegalArgumentException("Bearer super-secret-value")));

        mockMvc.perform(executionRequest(
                        session,
                        token,
                        json("event-900001-statistics.json"),
                        json("event-900001-incidents.json"),
                        json("event-900001-lineups.json")))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("batchErrorCode", "SENSITIVE_CONTENT"))
                .andExpect(flash().attribute(
                        "batchMessage",
                        containsString("contenu sensible interdit")))
                .andExpect(flash().attribute(
                        "batchMessage",
                        not(containsString("super-secret-value"))));
    }

    @Test
    void handlesAMultipartTransportLimitThroughTheRealMvcAdviceAndKeepsPlanCoordinates()
            throws Exception {
        MockHttpSession session = new MockHttpSession();
        String token = formTokenService.issue(session);
        J5OfflineBatchControlSnapshot snapshot = mock(J5OfflineBatchControlSnapshot.class);
        J5OfflineBatchPlan currentPlan = mock(J5OfflineBatchPlan.class);
        when(controlService.snapshot()).thenReturn(snapshot);
        when(snapshot.plan()).thenReturn(currentPlan);
        when(currentPlan.date()).thenReturn(LocalDate.of(2026, 10, 25));
        when(currentPlan.zoneId()).thenReturn(ZoneId.of("America/New_York"));
        when(importService.execute(
                eq(REQUEST_ID),
                eq(CONFIRMATION),
                eq(true),
                anyList())).thenThrow(new MaxUploadSizeExceededException(32L * 1024 * 1024));

        mockMvc.perform(executionRequest(
                        session,
                        token,
                        json("event-900001-statistics.json"),
                        json("event-900001-incidents.json"),
                        json("event-900001-lineups.json")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/j5-import-batches?date=2026-10-25&zone=America%2FNew_York"))
                .andExpect(flash().attribute(
                        "batchErrorCode", "MULTIPART_LIMIT_EXCEEDED"))
                .andExpect(flash().attribute(
                        "batchMessage",
                        "Le dépôt dépasse une limite multipart. Vérifiez 5 Mio maximum "
                                + "par fichier et 25 Mio maximum pour le lot."));
    }

    @Test
    void rejectsAnExpiredPlanBeforeReadingMultipartBytes() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String token = formTokenService.issue(session);
        MockPart first = spy(json("event-900001-statistics.json"));
        when(controlService.requireReadyForUpload(REQUEST_ID))
                .thenThrow(new J5OfflineBatchException(
                        J5OfflineBatchError.CONFIRMATION_EXPIRED));

        mockMvc.perform(executionRequest(
                        session,
                        token,
                        first,
                        json("event-900001-incidents.json"),
                        json("event-900001-lineups.json")))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute(
                        "batchErrorCode", "CONFIRMATION_EXPIRED"));

        verify(first, times(1)).getInputStream();
        verify(importService, never()).execute(
                eq(REQUEST_ID), eq(CONFIRMATION), eq(true), anyList());
    }

    @Test
    void rejectsAnInvalidManifestBeforeReadingMultipartBytes() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String token = formTokenService.issue(session);
        MockPart invalid = spy(json("../event-900001-statistics.json"));

        mockMvc.perform(executionRequest(
                        session,
                        token,
                        invalid,
                        json("event-900001-incidents.json"),
                        json("event-900001-lineups.json")))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("batchErrorCode", "FILE_NAME_INVALID"));

        verify(invalid, times(1)).getInputStream();
        verify(importService, never()).execute(
                eq(REQUEST_ID), eq(CONFIRMATION), eq(true), anyList());
    }

    @Test
    void rejectsAnRfc5987EncodedFilenameBeforeReadingMultipartBytes() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String token = formTokenService.issue(session);
        MockPart encoded = spy(json("event-900001-statistics.json"));
        encoded.getHeaders().set(
                HttpHeaders.CONTENT_DISPOSITION,
                "form-data; name=\"batchFiles\"; "
                        + "filename*=UTF-8''event-%39%30%30%30%30%31-statistics.json");

        mockMvc.perform(executionRequest(
                        session,
                        token,
                        encoded,
                        json("event-900001-incidents.json"),
                        json("event-900001-lineups.json")))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("batchErrorCode", "FILE_NAME_INVALID"));

        verify(encoded, times(1)).getInputStream();
        verify(importService, never()).execute(
                eq(REQUEST_ID), eq(CONFIRMATION), eq(true), anyList());
    }

    @Test
    void rejectsAnUnknownMultipartFileOutsideTheAuthoritativeManifest() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String token = formTokenService.issue(session);
        MockPart ignored = new MockPart(
                "ignored",
                "ignored.json",
                "Cookie: secret".getBytes(StandardCharsets.UTF_8));
        ignored.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        mockMvc.perform(executionRequest(
                        session,
                        token,
                        json("event-900001-statistics.json"),
                        json("event-900001-incidents.json"),
                        json("event-900001-lineups.json"),
                        ignored))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("batchErrorCode", "FILE_SET_MISMATCH"))
                .andExpect(flash().attribute(
                        "batchMessage",
                        not(containsString("Cookie: secret"))));

        verify(importService, never()).execute(
                eq(REQUEST_ID), eq(CONFIRMATION), eq(true), anyList());
    }

    @Test
    void stopsOnlyThePendingPlanThroughTheOneUseToken() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String token = formTokenService.issue(session);
        J5OfflineBatchControlSnapshot stopped = new J5OfflineBatchControlSnapshot(
                J5OfflineBatchState.STOPPED_LOCKED,
                PREPARED_AT.plusSeconds(10),
                plan(),
                J5OfflineBatchError.OPERATOR_STOP.name(),
                J5OfflineBatchResult.failed(
                        plan(), J5OfflineBatchError.OPERATOR_STOP, 0),
                true,
                List.of());
        when(controlService.stop(REQUEST_ID)).thenReturn(stopped);

        mockMvc.perform(post("/j5-import-batches/stop")
                        .session(session)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("localFormToken", token)
                        .param("requestId", REQUEST_ID.toString())
                        .param("date", DATE.toString())
                        .param("zone", ZONE.getId()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("batchMessageKind", "safe"));

        verify(controlService).stop(REQUEST_ID);
    }

    @Test
    void rendersExecutingAsAttentionWithAReadOnlyPlan() throws Exception {
        when(eventQueryService.search(DATE, ZONE.getId())).thenReturn(search());
        when(controlService.snapshot()).thenReturn(new J5OfflineBatchControlSnapshot(
                J5OfflineBatchState.EXECUTING,
                PREPARED_AT.plusSeconds(5),
                plan(),
                null,
                null,
                true,
                List.of()));

        mockMvc.perform(get("/j5-import-batches")
                        .param("date", DATE.toString())
                        .param("zone", ZONE.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("EXECUTING")))
                .andExpect(content().string(containsString(
                        "Le plan reste visible en lecture seule.")))
                .andExpect(content().string(containsString(
                        "event-900001-statistics.json")))
                .andExpect(content().string(not(containsString(
                        "name=\"confirmationText\""))));
    }

    @Test
    void rendersTheCompletedMinimizedResultWithoutAnyPayload() throws Exception {
        J5OfflineBatchResult result = completedResult();
        when(eventQueryService.search(DATE, ZONE.getId())).thenReturn(search());
        when(controlService.snapshot()).thenReturn(new J5OfflineBatchControlSnapshot(
                J5OfflineBatchState.COMPLETED_LOCKED,
                PREPARED_AT.plusSeconds(10),
                plan(),
                "COMPLETED",
                result,
                true,
                List.of()));

        mockMvc.perform(get("/j5-import-batches")
                        .param("date", DATE.toString())
                        .param("zone", ZONE.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "Preuve terminale minimisée")))
                .andExpect(content().string(containsString("EVENT_STATISTICS")))
                .andExpect(content().string(containsString("NOUVELLE_VERSION")))
                .andExpect(content().string(containsString("Opérations cache fournisseur")))
                .andExpect(content().string(containsString("Acquisitions coordinateur")))
                .andExpect(content().string(containsString("0f".repeat(32))))
                .andExpect(content().string(not(containsString("{\"eventId\""))));
    }

    private static MockMultipartHttpServletRequestBuilder executionRequest(
            MockHttpSession session,
            String localFormToken,
            MockPart... files) {
        MockMultipartHttpServletRequestBuilder request =
                multipart("/j5-import-batches/execute");
        request.session(session);
        request.part(
                controlPart("localFormToken", localFormToken),
                controlPart("requestId", REQUEST_ID.toString()),
                controlPart("confirmationText", CONFIRMATION),
                controlPart("acknowledged", "true"),
                controlPart("date", DATE.toString()),
                controlPart("zone", ZONE.getId()));
        request.part(files);
        return request;
    }

    private static MockPart controlPart(String name, String value) {
        return new MockPart(name, value.getBytes(StandardCharsets.UTF_8));
    }

    private static MockPart json(String fileName) {
        MockPart part = new MockPart(
                "batchFiles",
                fileName,
                "{}".getBytes(StandardCharsets.UTF_8));
        part.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return part;
    }

    private static MockPart unavailable404(String fileName) {
        return controlPart("unavailable404", fileName);
    }

    private static MockPart emptyFileSentinel() {
        return new MockPart("batchFiles", "", new byte[0]);
    }

    private static J5OfflineBatchControlSnapshot pendingControl() {
        return new J5OfflineBatchControlSnapshot(
                J5OfflineBatchState.AWAITING_CONFIRMATION,
                PREPARED_AT,
                plan(),
                null,
                null,
                true,
                List.of());
    }

    private static J5OfflineBatchResult completedResult() {
        return new J5OfflineBatchResult(
                REQUEST_ID,
                true,
                "COMPLETED",
                1,
                3,
                6,
                List.of(new J5OfflineBatchEventResult(
                        EVENT_ID,
                        IDENTITY.providerEventId(),
                        "Synthetic Home FC",
                        "Synthetic Away FC",
                        List.of(
                                endpoint(SofascoreEndpointType.EVENT_STATISTICS, 71L),
                                endpoint(SofascoreEndpointType.EVENT_INCIDENTS, 72L),
                                endpoint(SofascoreEndpointType.EVENT_LINEUPS, 73L)))));
    }

    private static J5RealEndpointResult endpoint(
            SofascoreEndpointType endpointType,
            long snapshotId) {
        return new J5RealEndpointResult(
                endpointType,
                snapshotId,
                "0f".repeat(32),
                2,
                snapshotId + 10,
                true,
                J5CompletenessStatus.COMPLETE,
                100,
                0);
    }

    private static J5OfflineBatchPlan plan() {
        return new J5OfflineBatchPlan(
                REQUEST_ID,
                DATE,
                ZONE,
                FROM,
                TO,
                PREPARED_AT,
                PREPARED_AT.plusSeconds(15 * 60),
                List.of(new J5OfflineBatchPlanEvent(
                        EVENT_ID,
                        IDENTITY.providerEventId(),
                        41,
                        STARTS_AT,
                        "Synthetic Home FC",
                        "Synthetic Away FC",
                        NORMALIZED_SHA,
                        J5OfflineBatchPlanEvent.expectedFileNames(
                                IDENTITY.providerEventId()))),
                PLAN_SHA,
                CONFIRMATION);
    }

    private static J4EventSearchResult search() {
        CanonicalEventObservationView event = new CanonicalEventObservationView(
                41,
                IDENTITY,
                STARTS_AT,
                new ScheduledTeam(9_101, "Synthetic Home FC"),
                new ScheduledTeam(9_202, "Synthetic Away FC"),
                new ScheduledEventStatus("notstarted", Optional.of("Not started")),
                Optional.of(new ScheduledTournament(77, "Synthetic League")),
                EventSourceTrace.syntheticFixture(
                        "j5-offline-batch-web-test",
                        "c".repeat(64),
                        "scheduled-events-v1",
                        PREPARED_AT.minusSeconds(60)),
                NORMALIZED_SHA,
                1);
        return new J4EventSearchResult(
                DATE,
                ZONE,
                FROM,
                TO,
                List.of(new J4EventSearchItem(
                        event,
                        STARTS_AT.atZone(ZONE))));
    }
}
