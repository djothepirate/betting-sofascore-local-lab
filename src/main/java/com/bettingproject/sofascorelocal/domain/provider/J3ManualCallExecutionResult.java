package com.bettingproject.sofascorelocal.domain.provider;

import java.util.Objects;

public record J3ManualCallExecutionResult(
        boolean completed,
        int completedPages,
        Integer failedPage,
        String terminalCode) {

    public J3ManualCallExecutionResult {
        if (completedPages < 0
                || completedPages > ScheduledEventsProviderPageRequest.MAXIMUM_COLLECTION_PAGE) {
            throw new IllegalArgumentException("completedPages must be between 0 and 25");
        }
        if (completed) {
            if (completedPages < 1 || failedPage != null || terminalCode != null) {
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
        return new J3ManualCallExecutionResult(true, completedPages, null, null);
    }

    public static J3ManualCallExecutionResult failed(
            int completedPages,
            int failedPage,
            String terminalCode) {
        return new J3ManualCallExecutionResult(
                false, completedPages, failedPage, terminalCode);
    }
}
