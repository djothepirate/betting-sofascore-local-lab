package com.bettingproject.sofascorelocal.domain.provider;

import java.util.Objects;

public record J3ManualCallExecutionResult(
        boolean completed,
        int completedPages,
        Integer failedPage,
        String terminalCode,
        int providerRequests,
        int cacheHits,
        int localJsonImports) {

    public J3ManualCallExecutionResult {
        if (completedPages < 0
                || completedPages > ScheduledEventsProviderPageRequest.MAXIMUM_COLLECTION_PAGE) {
            throw new IllegalArgumentException("completedPages must be between 0 and 25");
        }
        if (providerRequests < 0 || cacheHits < 0 || localJsonImports < 0
                || cacheHits > completedPages) {
            throw new IllegalArgumentException("resolution counters must be non-negative");
        }
        if (localJsonImports > 0 && (providerRequests > 0 || cacheHits > 0)) {
            throw new IllegalArgumentException(
                    "a collection cannot mix local imports with provider or cache resolutions");
        }
        int resolvedPages = providerRequests + cacheHits + localJsonImports;
        if (resolvedPages < completedPages || resolvedPages > completedPages + 1) {
            throw new IllegalArgumentException(
                    "resolution counters must cover completed pages and at most one failed page");
        }
        if (completed) {
            if (completedPages < 1 || failedPage != null || terminalCode != null
                    || resolvedPages != completedPages) {
                throw new IllegalArgumentException(
                        "a completed result requires at least one page and no failure");
            }
        }
        else {
            if (failedPage == null || failedPage < 1
                    || failedPage
                            > ScheduledEventsProviderPageRequest.MAXIMUM_COLLECTION_PAGE + 1) {
                throw new IllegalArgumentException("a failed result requires a failed page");
            }
            terminalCode = Objects.requireNonNull(terminalCode, "terminalCode").trim();
            if (terminalCode.isEmpty()) {
                throw new IllegalArgumentException("terminalCode must not be blank");
            }
        }
    }

    public static J3ManualCallExecutionResult successful(int completedPages) {
        return successful(completedPages, completedPages, 0);
    }

    public static J3ManualCallExecutionResult successful(
            int completedPages,
            int providerRequests,
            int cacheHits) {
        return new J3ManualCallExecutionResult(
                true, completedPages, null, null, providerRequests, cacheHits, 0);
    }

    public static J3ManualCallExecutionResult successfulLocalImport(int completedPages) {
        return new J3ManualCallExecutionResult(
                true, completedPages, null, null, 0, 0, completedPages);
    }

    public static J3ManualCallExecutionResult failed(
            int completedPages,
            int failedPage,
            String terminalCode) {
        return failed(completedPages, failedPage, terminalCode, completedPages, 0);
    }

    public static J3ManualCallExecutionResult failed(
            int completedPages,
            int failedPage,
            String terminalCode,
            int providerRequests,
            int cacheHits) {
        return new J3ManualCallExecutionResult(
                false,
                completedPages,
                failedPage,
                terminalCode,
                providerRequests,
                cacheHits,
                0);
    }

    public static J3ManualCallExecutionResult failedLocalImport(
            int completedPages,
            int failedPage,
            String terminalCode,
            int localJsonImports) {
        return new J3ManualCallExecutionResult(
                false,
                completedPages,
                failedPage,
                terminalCode,
                0,
                0,
                localJsonImports);
    }
}
