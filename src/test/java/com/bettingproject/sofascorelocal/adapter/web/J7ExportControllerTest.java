package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.application.export.J7CanonicalExportService;
import com.bettingproject.sofascorelocal.application.export.J7ExportContract;
import com.bettingproject.sofascorelocal.application.export.J7ExportDownload;
import com.bettingproject.sofascorelocal.application.export.J7ExportHistory;
import com.bettingproject.sofascorelocal.application.export.J7ExportPreview;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservationView;
import com.bettingproject.sofascorelocal.domain.event.EventSourceTrace;
import com.bettingproject.sofascorelocal.domain.export.J7ExportError;
import com.bettingproject.sofascorelocal.domain.export.J7ExportException;
import com.bettingproject.sofascorelocal.domain.export.J7ExportManifest;
import com.bettingproject.sofascorelocal.domain.export.J7ExportStatus;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEventStatus;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTeam;
import com.bettingproject.sofascorelocal.security.LocalFormTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(J7ExportController.class)
@Import(LocalFormTokenService.class)
class J7ExportControllerTest {

    private static final CanonicalEventIdentity IDENTITY =
            CanonicalEventIdentity.sofascore(900_001L);
    private static final UUID EVENT_ID = IDENTITY.value();
    private static final UUID EXPORT_ID = UUID.fromString(
            "70000000-0000-0000-0000-000000000001");
    private static final String DATA_SHA = "a".repeat(64);
    private static final String SOURCE_SET_SHA = "b".repeat(64);
    private static final String CANDIDATE_SHA = "c".repeat(64);
    private static final String TERMINAL_SHA = "d".repeat(64);
    private static final String VALIDATION_CONFIRMATION =
            "VALIDER EXPORT J7 " + EXPORT_ID + " " + DATA_SHA;
    private static final String REJECTION_CONFIRMATION =
            "REJETER EXPORT J7 " + EXPORT_ID;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LocalFormTokenService formTokenService;

    @MockitoBean
    private J7CanonicalExportService exportService;

    @MockitoBean
    private CacheManager cacheManager;

    @Test
    void rendersLocalHistoryAndCandidateCreationWithStrictNoStoreHeaders()
            throws Exception {
        J7ExportHistory history = new J7ExportHistory(
                currentEvent(), List.of(candidate()));
        when(exportService.history(EVENT_ID)).thenReturn(history);

        mockMvc.perform(get("/events/{id}/exports", EVENT_ID))
                .andExpect(status().isOk())
                .andExpect(view().name("event-exports"))
                .andExpect(model().attribute("exportHistory", history))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store, no-cache, must-revalidate, max-age=0"))
                .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"))
                .andExpect(header().string(HttpHeaders.EXPIRES, "0"))
                .andExpect(header().string(
                        "X-Robots-Tag", "noindex, nofollow, noarchive"))
                .andExpect(content().string(containsString("Candidats et décisions humaines")))
                .andExpect(content().string(containsString("Synthetic Home FC")))
                .andExpect(content().string(containsString(EXPORT_ID.toString())))
                .andExpect(content().string(not(containsString("collecte fournisseur"))));
    }

    @Test
    void refusesPreControllerRequestsWithStrictNoStoreHeaders() throws Exception {
        mockMvc.perform(get("/events/{id}/exports", EVENT_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotAcceptable())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store, no-cache, must-revalidate, max-age=0"))
                .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"))
                .andExpect(header().string(HttpHeaders.EXPIRES, "0"))
                .andExpect(header().string(
                        "X-Robots-Tag", "noindex, nofollow, noarchive"));

        mockMvc.perform(get("/events/{id}/exports/{exportId}", EVENT_ID, EXPORT_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotAcceptable());

        mockMvc.perform(post("/events/{id}/exports/candidates", EVENT_ID)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .accept(MediaType.APPLICATION_JSON)
                        .param("localFormToken", "not-consumed"))
                .andExpect(status().isNotAcceptable());

        mockMvc.perform(get("/events/not-a-uuid/exports"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store, no-cache, must-revalidate, max-age=0"))
                .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"))
                .andExpect(header().string(HttpHeaders.EXPIRES, "0"))
                .andExpect(header().string(
                        "X-Robots-Tag", "noindex, nofollow, noarchive"));

        verify(exportService, never()).history(EVENT_ID);
        verify(exportService, never()).preview(EVENT_ID, EXPORT_ID);
        verify(exportService, never()).createCandidate(EVENT_ID);
    }

    @Test
    void rendersEscapedCandidateJsonAndOnlyHumanDecisionControls() throws Exception {
        J7ExportPreview preview = preview(candidate());
        when(exportService.preview(EVENT_ID, EXPORT_ID)).thenReturn(preview);

        mockMvc.perform(get("/events/{id}/exports/{exportId}", EVENT_ID, EXPORT_ID))
                .andExpect(status().isOk())
                .andExpect(view().name("event-export-preview"))
                .andExpect(model().attribute("exportPreview", preview))
                .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"))
                .andExpect(header().string(HttpHeaders.EXPIRES, "0"))
                .andExpect(header().string(
                        "X-Robots-Tag", "noindex, nofollow, noarchive"))
                .andExpect(content().string(containsString(VALIDATION_CONFIRMATION)))
                .andExpect(content().string(containsString(REJECTION_CONFIRMATION)))
                .andExpect(content().string(containsString(
                        "&lt;script&gt;alert(1)&lt;/script&gt;")))
                .andExpect(content().string(not(containsString(
                        "<script>alert(1)</script>"))))
                .andExpect(content().string(containsString(
                        "/exports/" + EXPORT_ID + "/validate")))
                .andExpect(content().string(containsString(
                        "/exports/" + EXPORT_ID + "/reject")))
                .andExpect(content().string(not(containsString(
                        "/exports/" + EXPORT_ID + "/download"))));
    }

    @Test
    void exposesDownloadOnlyForAHumanValidatedExport() throws Exception {
        when(exportService.preview(EVENT_ID, EXPORT_ID))
                .thenReturn(preview(validated()));

        mockMvc.perform(get("/events/{id}/exports/{exportId}", EVENT_ID, EXPORT_ID))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Export validé humainement")))
                .andExpect(content().string(containsString(
                        "/exports/" + EXPORT_ID + "/download")))
                .andExpect(content().string(not(containsString(
                        "/exports/" + EXPORT_ID + "/validate"))))
                .andExpect(content().string(not(containsString(
                        "/exports/" + EXPORT_ID + "/reject"))));
    }

    @Test
    void rendersARejectedReasonEscapedAndNeverOffersADownload() throws Exception {
        when(exportService.preview(EVENT_ID, EXPORT_ID))
                .thenReturn(preview(rejected("<script>reject()</script>")));

        mockMvc.perform(get("/events/{id}/exports/{exportId}", EVENT_ID, EXPORT_ID))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "&lt;script&gt;reject()&lt;/script&gt;")))
                .andExpect(content().string(not(containsString(
                        "<script>reject()</script>"))))
                .andExpect(content().string(containsString(
                        "Export rejeté et conservé localement")))
                .andExpect(content().string(not(containsString(
                        "/exports/" + EXPORT_ID + "/download"))));
    }

    @Test
    void createsACandidateThroughOneTokenAndRedirectsToItsPreview() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String token = formTokenService.issue(session);
        when(exportService.createCandidate(EVENT_ID)).thenReturn(candidate());

        mockMvc.perform(post("/events/{id}/exports/candidates", EVENT_ID)
                        .session(session)
                        .param("localFormToken", token))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/events/" + EVENT_ID + "/exports/" + EXPORT_ID))
                .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"))
                .andExpect(header().string(
                        "X-Robots-Tag", "noindex, nofollow, noarchive"));

        mockMvc.perform(post("/events/{id}/exports/candidates", EVENT_ID)
                        .session(session)
                        .param("localFormToken", token))
                .andExpect(status().isBadRequest());

        verify(exportService, times(1)).createCandidate(EVENT_ID);
    }

    @Test
    void forwardsTheExactValidationAndRejectsAChangedSourceSetSafely()
            throws Exception {
        MockHttpSession successSession = new MockHttpSession();
        String successToken = formTokenService.issue(successSession);
        when(exportService.validate(
                EVENT_ID, EXPORT_ID, VALIDATION_CONFIRMATION))
                .thenReturn(validated());

        mockMvc.perform(post(
                        "/events/{id}/exports/{exportId}/validate",
                        EVENT_ID,
                        EXPORT_ID)
                        .session(successSession)
                        .param("localFormToken", successToken)
                        .param("confirmationText", VALIDATION_CONFIRMATION))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/events/" + EVENT_ID + "/exports/" + EXPORT_ID));

        String staleConfirmation = VALIDATION_CONFIRMATION + "-stale";
        MockHttpSession staleSession = new MockHttpSession();
        String staleToken = formTokenService.issue(staleSession);
        when(exportService.validate(EVENT_ID, EXPORT_ID, staleConfirmation))
                .thenThrow(new J7ExportException(J7ExportError.SOURCE_SET_CHANGED));

        mockMvc.perform(post(
                        "/events/{id}/exports/{exportId}/validate",
                        EVENT_ID,
                        EXPORT_ID)
                        .session(staleSession)
                        .param("localFormToken", staleToken)
                        .param("confirmationText", staleConfirmation))
                .andExpect(status().isConflict())
                .andExpect(view().name("event-export-error"))
                .andExpect(model().attribute(
                        "exportErrorCode", "SOURCE_SET_CHANGED"))
                .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"))
                .andExpect(content().string(containsString(
                        "Créez un nouveau candidat")));

        verify(exportService).validate(
                EVENT_ID, EXPORT_ID, VALIDATION_CONFIRMATION);
    }

    @Test
    void forwardsTheExactRejectionReasonAndMapsInvalidInputToBadRequest()
            throws Exception {
        String reason = "Observation humaine locale";
        MockHttpSession successSession = new MockHttpSession();
        String successToken = formTokenService.issue(successSession);
        when(exportService.reject(
                EVENT_ID, EXPORT_ID, REJECTION_CONFIRMATION, reason))
                .thenReturn(rejected(reason));

        mockMvc.perform(post(
                        "/events/{id}/exports/{exportId}/reject",
                        EVENT_ID,
                        EXPORT_ID)
                        .session(successSession)
                        .param("localFormToken", successToken)
                        .param("confirmationText", REJECTION_CONFIRMATION)
                        .param("reason", reason))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/events/" + EVENT_ID + "/exports/" + EXPORT_ID));

        MockHttpSession invalidSession = new MockHttpSession();
        String invalidToken = formTokenService.issue(invalidSession);
        when(exportService.reject(
                EVENT_ID, EXPORT_ID, REJECTION_CONFIRMATION, " "))
                .thenThrow(new J7ExportException(
                        J7ExportError.INVALID_REJECTION_REASON));

        mockMvc.perform(post(
                        "/events/{id}/exports/{exportId}/reject",
                        EVENT_ID,
                        EXPORT_ID)
                        .session(invalidSession)
                        .param("localFormToken", invalidToken)
                        .param("confirmationText", REJECTION_CONFIRMATION)
                        .param("reason", " "))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("event-export-error"))
                .andExpect(model().attribute(
                        "exportErrorCode", "INVALID_REJECTION_REASON"))
                .andExpect(content().string(containsString(
                        "entre 1 et 500 caractères")));

        verify(exportService).reject(
                EVENT_ID, EXPORT_ID, REJECTION_CONFIRMATION, reason);
    }

    @Test
    void rendersSafeNotFoundUnprocessableAndUnavailableErrors() throws Exception {
        UUID missing = UUID.fromString(
                "70000000-0000-0000-0000-000000000099");
        when(exportService.preview(EVENT_ID, missing))
                .thenThrow(new J7ExportException(J7ExportError.EXPORT_NOT_FOUND));

        mockMvc.perform(get("/events/{id}/exports/{exportId}", EVENT_ID, missing))
                .andExpect(status().isNotFound())
                .andExpect(view().name("event-export-error"))
                .andExpect(content().string(containsString("EXPORT_NOT_FOUND")))
                .andExpect(header().string(HttpHeaders.EXPIRES, "0"));

        UUID tampered = UUID.fromString(
                "70000000-0000-0000-0000-000000000098");
        when(exportService.preview(EVENT_ID, tampered))
                .thenThrow(new J7ExportException(J7ExportError.FILE_TAMPERED));

        mockMvc.perform(get("/events/{id}/exports/{exportId}", EVENT_ID, tampered))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().string(containsString("FILE_TAMPERED")))
                .andExpect(content().string(not(containsString("java.lang"))));

        when(exportService.history(EVENT_ID))
                .thenThrow(new J7ExportException(J7ExportError.DATABASE_UNAVAILABLE));

        mockMvc.perform(get("/events/{id}/exports", EVENT_ID))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(containsString("DATABASE_UNAVAILABLE")))
                .andExpect(header().string(
                        "X-Robots-Tag", "noindex, nofollow, noarchive"));
    }

    @Test
    void downloadsOnlyVerifiedHumanValidatedJsonWithAllSecurityHeaders()
            throws Exception {
        byte[] payload = "{\"manifest\":{},\"data\":{}}\n"
                .getBytes(StandardCharsets.UTF_8);
        String filename = "j7-" + EVENT_ID + "-" + EXPORT_ID + ".validated.json";
        when(exportService.download(EVENT_ID, EXPORT_ID)).thenReturn(
                new J7ExportDownload(EXPORT_ID, filename, TERMINAL_SHA, payload));

        mockMvc.perform(get(
                        "/events/{id}/exports/{exportId}/download",
                        EVENT_ID,
                        EXPORT_ID))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().bytes(payload))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, payload.length))
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\""))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store, no-cache, must-revalidate, max-age=0"))
                .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"))
                .andExpect(header().string(HttpHeaders.EXPIRES, "0"))
                .andExpect(header().string(
                        "X-Robots-Tag", "noindex, nofollow, noarchive"));
    }

    @Test
    void returnsAnEmptyConflictForCandidateAndEmptyNotFoundForRejectionDownloads()
            throws Exception {
        when(exportService.download(EVENT_ID, EXPORT_ID))
                .thenThrow(new J7ExportException(J7ExportError.NOT_DOWNLOADABLE));
        when(exportService.history(EVENT_ID))
                .thenReturn(
                        new J7ExportHistory(currentEvent(), List.of(candidate())),
                        new J7ExportHistory(
                                currentEvent(),
                                List.of(rejected("Non retenu"))));

        mockMvc.perform(get(
                        "/events/{id}/exports/{exportId}/download",
                        EVENT_ID,
                        EXPORT_ID))
                .andExpect(status().isConflict())
                .andExpect(content().string(""))
                .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"));

        mockMvc.perform(get(
                        "/events/{id}/exports/{exportId}/download",
                        EVENT_ID,
                        EXPORT_ID))
                .andExpect(status().isNotFound())
                .andExpect(content().string(""))
                .andExpect(header().string(HttpHeaders.EXPIRES, "0"))
                .andExpect(header().string(
                        "X-Robots-Tag", "noindex, nofollow, noarchive"));
    }

    private static J7ExportPreview preview(J7ExportManifest manifest) {
        return new J7ExportPreview(
                manifest,
                "{\n  \"data\" : { \"name\" : \"<script>alert(1)</script>\" }\n}",
                VALIDATION_CONFIRMATION,
                REJECTION_CONFIRMATION);
    }

    private static J7ExportManifest candidate() {
        return manifest(
                J7ExportStatus.COHERENCE_CHECKED,
                CANDIDATE_SHA,
                ".candidate.json",
                Optional.empty(),
                Optional.empty());
    }

    private static J7ExportManifest validated() {
        return manifest(
                J7ExportStatus.HUMAN_VALIDATED,
                TERMINAL_SHA,
                ".validated.json",
                Optional.of(Instant.parse("2026-08-19T12:10:00Z")),
                Optional.empty());
    }

    private static J7ExportManifest rejected(String reason) {
        return manifest(
                J7ExportStatus.REJECTED,
                TERMINAL_SHA,
                ".rejected.json",
                Optional.of(Instant.parse("2026-08-19T12:10:00Z")),
                Optional.of(reason));
    }

    private static J7ExportManifest manifest(
            J7ExportStatus status,
            String currentSha,
            String suffix,
            Optional<Instant> decidedAt,
            Optional<String> reason) {
        return new J7ExportManifest(
                17,
                EXPORT_ID,
                EVENT_ID,
                J7ExportContract.SCHEMA_ID,
                J7ExportContract.SCHEMA_VERSION,
                Instant.parse("2026-08-19T12:00:00Z"),
                DATA_SHA,
                SOURCE_SET_SHA,
                CANDIDATE_SHA,
                currentSha,
                1_024,
                "j7-" + EVENT_ID + "-" + EXPORT_ID + suffix,
                "[{\"component\":\"EVENT_STATE\"}]",
                List.of(),
                "[]",
                status,
                decidedAt,
                reason);
    }

    private static CanonicalEventObservationView currentEvent() {
        EventSourceTrace source = EventSourceTrace.syntheticFixture(
                "j7-web-test",
                "e".repeat(64),
                "scheduled-events-v1",
                Instant.parse("2026-08-19T11:00:00Z"));
        return new CanonicalEventObservationView(
                23,
                IDENTITY,
                Instant.parse("2026-08-19T18:00:00Z"),
                new ScheduledTeam(9101, "Synthetic Home FC"),
                new ScheduledTeam(9202, "Synthetic Away FC"),
                new ScheduledEventStatus("finished", Optional.of("Finished")),
                Optional.empty(),
                source,
                "f".repeat(64),
                2);
    }
}
