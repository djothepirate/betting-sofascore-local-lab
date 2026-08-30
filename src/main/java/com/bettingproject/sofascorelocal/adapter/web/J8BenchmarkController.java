package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.application.benchmark.J8BenchmarkService;
import com.bettingproject.sofascorelocal.application.benchmark.J8BenchmarkWindow;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.TransactionException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/benchmark")
public class J8BenchmarkController {

    private static final String CACHE_CONTROL_VALUE =
            "no-store, no-cache, must-revalidate, max-age=0";

    private final J8BenchmarkService benchmarkService;

    public J8BenchmarkController(J8BenchmarkService benchmarkService) {
        this.benchmarkService = benchmarkService;
    }

    @GetMapping(produces = MediaType.TEXT_HTML_VALUE)
    public String benchmark(
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            HttpServletResponse response,
            Model model) {
        applyNoStoreHeaders(response);
        try {
            J8BenchmarkWindow window = J8BenchmarkWindow.parse(from, to);
            var report = benchmarkService.load(window);
            model.addAttribute("benchmarkReport", report);
            model.addAttribute(
                    "selectedFrom",
                    report.effectiveWindow().fromInclusive()
                            .map(Object::toString).orElse(""));
            model.addAttribute(
                    "selectedTo",
                    report.effectiveWindow().toExclusive()
                            .map(Object::toString).orElse(""));
        }
        catch (IllegalArgumentException exception) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            model.addAttribute("benchmarkErrorCode", "INVALID_BENCHMARK_WINDOW");
        }
        catch (DataAccessException | TransactionException exception) {
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
            model.addAttribute("benchmarkErrorCode", "LOCAL_DATABASE_UNAVAILABLE");
        }
        catch (IllegalStateException exception) {
            response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
            model.addAttribute("benchmarkErrorCode", "INCOHERENT_LOCAL_EVIDENCE");
        }
        return "benchmark";
    }

    private static void applyNoStoreHeaders(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL_VALUE);
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        response.setHeader(HttpHeaders.EXPIRES, "0");
        response.setHeader("X-Robots-Tag", "noindex, nofollow, noarchive");
    }
}
