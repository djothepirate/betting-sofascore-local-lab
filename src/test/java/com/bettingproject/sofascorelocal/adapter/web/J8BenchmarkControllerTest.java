package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.application.benchmark.J8BenchmarkReport;
import com.bettingproject.sofascorelocal.application.benchmark.J8BenchmarkReportTestFixture;
import com.bettingproject.sofascorelocal.application.benchmark.J8BenchmarkService;
import com.bettingproject.sofascorelocal.application.benchmark.J8BenchmarkWindow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.CannotCreateTransactionException;

import java.time.Instant;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(J8BenchmarkController.class)
class J8BenchmarkControllerTest {

    private static final String STRICT_CACHE_CONTROL =
            "no-store, no-cache, must-revalidate, max-age=0";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private J8BenchmarkService benchmarkService;

    @MockitoBean
    private CacheManager cacheManager;

    @Test
    void rendersTheReadOnlyHtmlReportWithStrictHeadersAndEscapedEvidence()
            throws Exception {
        J8BenchmarkReport report = J8BenchmarkReportTestFixture.measured(
                "Competition <script>alert(1)</script>");
        when(benchmarkService.load(J8BenchmarkWindow.allAvailable())).thenReturn(report);

        mockMvc.perform(get("/benchmark").accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(view().name("benchmark"))
                .andExpect(model().attribute("benchmarkReport", report))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, STRICT_CACHE_CONTROL))
                .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"))
                .andExpect(header().string(HttpHeaders.EXPIRES, "0"))
                .andExpect(header().string(
                        "X-Robots-Tag", "noindex, nofollow, noarchive"))
                .andExpect(header().string(
                        "Content-Security-Policy", containsString("script-src 'none'")))
                .andExpect(content().string(containsString(
                        "agrégation locale — aucun appel fournisseur")))
                .andExpect(content().string(containsString("FULL_ATTEMPT_LEDGER")))
                .andExpect(content().string(containsString(
                        "2026-08-29T10:15:30Z")))
                .andExpect(content().string(containsString(
                        "Ratio marginal = (tentatives J4 phase 2 + J5)")))
                .andExpect(content().string(containsString(
                        "Erreurs opérationnelles hors 404")))
                .andExpect(content().string(containsString(
                        "Taux de 404 — synthèse toutes tentatives")))
                .andExpect(content().string(containsString(
                        "Taux 404 / tentatives endpoint")))
                .andExpect(content().string(containsString(
                        "Latence n / min / P50 / P95 / max")))
                .andExpect(content().string(containsString("INCOMPLETE_ATTEMPT")))
                .andExpect(content().string(containsString(
                        "Couverture pondérée")))
                .andExpect(content().string(containsString(
                        "Première récupération après dernière rupture")))
                .andExpect(content().string(containsString(
                        "Competition &lt;script&gt;alert(1)&lt;/script&gt;")))
                .andExpect(content().string(not(containsString("<script>alert(1)</script>"))));

        verify(benchmarkService).load(J8BenchmarkWindow.allAvailable());
    }

    @Test
    void passesAnExplicitStrictUtcHalfOpenWindowToTheService() throws Exception {
        J8BenchmarkWindow window = J8BenchmarkWindow.between(
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-29T00:00:00Z"));
        J8BenchmarkReport report = J8BenchmarkReportTestFixture.measured("Competition");
        when(benchmarkService.load(window)).thenReturn(report);

        mockMvc.perform(get("/benchmark")
                        .param("from", "2026-08-01T00:00:00Z")
                        .param("to", "2026-08-29T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedFrom", "2026-08-01T00:00:00Z"))
                .andExpect(model().attribute("selectedTo", "2026-08-29T00:00:00Z"));

        verify(benchmarkService).load(window);
    }

    @Test
    void acceptsAFutureRequestedEndAndRendersTheAsOfBoundedEffectiveWindow()
            throws Exception {
        J8BenchmarkWindow requestedWindow = J8BenchmarkWindow.between(
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-09-01T00:00:00Z"));
        J8BenchmarkWindow effectiveWindow = J8BenchmarkWindow.between(
                Instant.parse("2026-08-01T00:00:00Z"),
                J8BenchmarkReportTestFixture.AS_OF);
        J8BenchmarkReport report = J8BenchmarkReportTestFixture.withEffectiveWindow(
                J8BenchmarkReportTestFixture.measured("Competition"),
                effectiveWindow);
        when(benchmarkService.load(requestedWindow)).thenReturn(report);

        mockMvc.perform(get("/benchmark")
                        .param("from", "2026-08-01T00:00:00Z")
                        .param("to", "2026-09-01T00:00:00Z")
                        .accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(model().attribute(
                        "selectedFrom", "2026-08-01T00:00:00Z"))
                .andExpect(model().attribute(
                        "selectedTo", "2026-08-29T10:15:30Z"))
                .andExpect(content().string(containsString(
                        "<dt>Fin effective exclue</dt>"
                                + "<dd class=\"mono\">2026-08-29T10:15:30Z</dd>")))
                .andExpect(content().string(containsString("réponses reçues.")))
                .andExpect(content().string(not(containsString(
                        "réponses persistées."))));

        verify(benchmarkService).load(requestedWindow);
    }

    @Test
    void rejectsMissingEmptyOffsetAndReversedBoundsAsSafe400Errors()
            throws Exception {
        mockMvc.perform(get("/benchmark").param("from", "" ).param("to", ""))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("benchmark"))
                .andExpect(model().attribute(
                        "benchmarkErrorCode", "INVALID_BENCHMARK_WINDOW"))
                .andExpect(content().string(containsString("Rapport non produit")))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, STRICT_CACHE_CONTROL));

        mockMvc.perform(get("/benchmark")
                        .param("from", "2026-08-01T00:00:00Z"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/benchmark")
                        .param("from", "2026-08-01T02:00:00+02:00")
                        .param("to", "2026-08-02T02:00:00+02:00"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/benchmark")
                        .param("from", "2026-08-02T00:00:00Z")
                        .param("to", "2026-08-01T00:00:00Z"))
                .andExpect(status().isBadRequest());

        verify(benchmarkService, never()).load(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void returns503WithoutFallbackWhenTheLocalDatabaseIsUnavailable() throws Exception {
        when(benchmarkService.load(J8BenchmarkWindow.allAvailable())).thenThrow(
                new DataRetrievalFailureException("local database unavailable"));

        mockMvc.perform(get("/benchmark"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(model().attribute(
                        "benchmarkErrorCode", "LOCAL_DATABASE_UNAVAILABLE"))
                .andExpect(content().string(containsString(
                        "Aucune collecte de remplacement n’a été tentée")))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, STRICT_CACHE_CONTROL));
    }

    @Test
    void returns503WhenTheReadOnlyTransactionCannotBeCreated() throws Exception {
        when(benchmarkService.load(J8BenchmarkWindow.allAvailable())).thenThrow(
                new CannotCreateTransactionException(
                        "local transaction unavailable", new IllegalStateException("offline")));

        mockMvc.perform(get("/benchmark"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(model().attribute(
                        "benchmarkErrorCode", "LOCAL_DATABASE_UNAVAILABLE"))
                .andExpect(content().string(containsString(
                        "Aucune collecte de remplacement n’a été tentée")))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, STRICT_CACHE_CONTROL));
    }

    @Test
    void returns422WithoutInventedValuesForIncoherentEvidence() throws Exception {
        when(benchmarkService.load(J8BenchmarkWindow.allAvailable())).thenThrow(
                new IllegalStateException("incoherent evidence"));

        mockMvc.perform(get("/benchmark"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(model().attribute(
                        "benchmarkErrorCode", "INCOHERENT_LOCAL_EVIDENCE"))
                .andExpect(content().string(containsString(
                        "Aucune valeur n’a été inventée")))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, STRICT_CACHE_CONTROL));
    }

    @Test
    void rendersAnEmptyWindowAs200NotMeasured() throws Exception {
        when(benchmarkService.load(J8BenchmarkWindow.allAvailable())).thenReturn(
                J8BenchmarkReportTestFixture.notMeasured());

        mockMvc.perform(get("/benchmark"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("NOT_MEASURED")))
                .andExpect(content().string(containsString("NON MESURÉ")))
                .andExpect(content().string(not(containsString("0%"))))
                .andExpect(content().string(not(containsString("0 %"))))
                .andExpect(content().string(not(containsString("0.00%"))))
                .andExpect(content().string(not(containsString("0.00 %"))))
                .andExpect(content().string(not(containsString("0 ms"))))
                .andExpect(content().string(not(containsString("0/0"))))
                .andExpect(content().string(not(containsString("0 / 0"))))
                .andExpect(content().string(not(containsString("NaN"))))
                .andExpect(content().string(not(containsString("Infinity"))));
    }

    @Test
    void rendersAPartialReportWithMeasuredValuesAndAbsentDenominatorsExplicit()
            throws Exception {
        when(benchmarkService.load(J8BenchmarkWindow.allAvailable())).thenReturn(
                J8BenchmarkReportTestFixture.partial());

        mockMvc.perform(get("/benchmark").accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("PARTIAL")))
                .andExpect(content().string(containsString("SCHEDULED_EVENTS")))
                .andExpect(content().string(containsString(
                        "n=1 · min=100 · P50=100 · P95=100 · max=100 ms")))
                .andExpect(content().string(containsString(
                        "0 ciblés · 0 exploitables")))
                .andExpect(content().string(containsString(
                        "<dt>Événements ciblés</dt><dd>0</dd>")))
                .andExpect(content().string(containsString(
                        "<dt>Exploitables</dt><dd>0</dd>")))
                .andExpect(content().string(containsString(
                        "<dt>Surcoût découverte J3</dt><dd>1</dd>")))
                .andExpect(content().string(containsString(
                        "<dt>Appels marginaux J4 phase 2 + J5</dt><dd>0</dd>")))
                .andExpect(content().string(containsString(
                        "<dt>Tous appels directs de la fenêtre</dt><dd>1</dd>")))
                .andExpect(content().string(containsString(
                        "<dt>Appels marginaux / dossier exploitable</dt>"
                                + "<dd>NON MESURÉ</dd>")))
                .andExpect(content().string(containsString(
                        "<dt>Appels effectifs / dossier exploitable</dt>"
                                + "<dd>NON MESURÉ</dd>")))
                .andExpect(content().string(not(containsString("0 / 0"))));
    }

    @Test
    void keepsHistoricalDedupAndLatencyVisibleWithoutInventingParsingZeros()
            throws Exception {
        when(benchmarkService.load(J8BenchmarkWindow.allAvailable())).thenReturn(
                J8BenchmarkReportTestFixture.responseOnlyWithoutParsingProof());

        mockMvc.perform(get("/benchmark").accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("RESPONSE_ONLY")))
                .andExpect(content().string(containsString(
                        "data-metric=\"endpoint-response-evidence\">1</td>")))
                .andExpect(content().string(containsString(
                        "data-metric=\"endpoint-deduplicated\">1</td>")))
                .andExpect(content().string(containsString(
                        "data-metric=\"endpoint-parsing-state\">NOT_MEASURED</td>")))
                .andExpect(content().string(containsString(
                        "data-metric=\"endpoint-parsed\">NON MESURÉ</td>")))
                .andExpect(content().string(containsString(
                        "data-metric=\"endpoint-unavailable-rate\">NON MESURÉ</td>")))
                .andExpect(content().string(containsString(
                        "n=1 · min=180 · P50=180 · P95=180 · max=180 ms")))
                .andExpect(content().string(containsString(
                        "<dt>Réponses éligibles</dt><dd>NON MESURÉ</dd>")))
                .andExpect(content().string(containsString(
                        "<dt>Réponses parsées</dt><dd>NON MESURÉ</dd>")))
                .andExpect(content().string(containsString(
                        "<dt>Versions distinctes</dt><dd>NON MESURÉ</dd>")))
                .andExpect(content().string(containsString(
                        "<dt>Transitions de version</dt><dd>NON MESURÉ</dd>")))
                .andExpect(content().string(not(containsString(
                        "<dt>Réponses éligibles</dt><dd>0</dd>"))))
                .andExpect(content().string(not(containsString(
                        "<dt>Réponses parsées</dt><dd>0</dd>"))));
    }

    @Test
    void refusesJsonAndPostBeforeControllerWithStrictHeaders() throws Exception {
        mockMvc.perform(get("/benchmark").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotAcceptable())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, STRICT_CACHE_CONTROL))
                .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"))
                .andExpect(header().string(HttpHeaders.EXPIRES, "0"))
                .andExpect(header().string(
                        "X-Robots-Tag", "noindex, nofollow, noarchive"))
                .andExpect(header().string(
                        "Content-Security-Policy", containsString("script-src 'none'")));

        mockMvc.perform(post("/benchmark"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, STRICT_CACHE_CONTROL))
                .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"))
                .andExpect(header().string(HttpHeaders.EXPIRES, "0"))
                .andExpect(header().string(
                        "X-Robots-Tag", "noindex, nofollow, noarchive"));

        verify(benchmarkService, never()).load(org.mockito.ArgumentMatchers.any());
    }

    @ParameterizedTest(name = "strict benchmark headers: {0}")
    @MethodSource("strictHeaderScenarios")
    void appliesEveryStrictHeaderAndNeverLeaksExceptionDetails(
            String scenario,
            int expectedStatus) throws Exception {
        J8BenchmarkReport report = J8BenchmarkReportTestFixture.measured("Competition");
        if (scenario.equals("200") || scenario.equals("jsessionid")) {
            when(benchmarkService.load(J8BenchmarkWindow.allAvailable())).thenReturn(report);
        }
        else if (scenario.equals("422")) {
            when(benchmarkService.load(J8BenchmarkWindow.allAvailable())).thenThrow(
                    new IllegalStateException("SENSITIVE_EXCEPTION_MARKER"));
        }
        else if (scenario.equals("503")) {
            when(benchmarkService.load(J8BenchmarkWindow.allAvailable())).thenThrow(
                    new CannotCreateTransactionException(
                            "SENSITIVE_EXCEPTION_MARKER",
                            new IllegalStateException("SENSITIVE_EXCEPTION_MARKER")));
        }

        var request = switch (scenario) {
            case "200" -> get("/benchmark").accept(MediaType.TEXT_HTML);
            case "400" -> get("/benchmark").param("from", "").param("to", "");
            case "405" -> post("/benchmark");
            case "406" -> get("/benchmark").accept(MediaType.APPLICATION_JSON);
            case "422", "503" -> get("/benchmark");
            case "jsessionid" -> get(
                    "/benchmark;jsessionid=LOCAL_TEST_SESSION");
            default -> throw new IllegalArgumentException("unknown scenario");
        };

        mockMvc.perform(request)
                .andExpect(status().is(expectedStatus))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, STRICT_CACHE_CONTROL))
                .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"))
                .andExpect(header().string(HttpHeaders.EXPIRES, "0"))
                .andExpect(header().string(
                        "X-Robots-Tag", "noindex, nofollow, noarchive"))
                .andExpect(header().string(
                        "Content-Security-Policy", containsString("script-src 'none'")))
                .andExpect(content().string(not(containsString(
                        "SENSITIVE_EXCEPTION_MARKER"))));
    }

    private static Stream<Arguments> strictHeaderScenarios() {
        return Stream.of(
                Arguments.of("200", 200),
                Arguments.of("400", 400),
                Arguments.of("405", 405),
                Arguments.of("406", 406),
                Arguments.of("422", 422),
                Arguments.of("503", 503),
                Arguments.of("jsessionid", 200));
    }
}
