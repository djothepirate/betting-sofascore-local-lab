package com.bettingproject.sofascorelocal.domain.provider;

import java.util.Objects;

public record J3ManualCallExecutionResult(
        boolean completed,
        int completedPages,
        Integer failedPage,
        String terminalCode) {

    public J3ManualCallExecutionResult {
        if (completedPages < 0 || completedPages > 5) {
            throw new IllegalArgumentException("completedPages must be between 0 and 5");
        }
        if (completed) {
            if (completedPages != 5 || failedPage != null || terminalCode != null) {
                throw new IllegalArgumentException(
                        "a completed result requires exactly five pages and no failure");
            }
        }
        else {
            if (failedPage == null || failedPage < 1 || failedPage > 5) {
                throw new IllegalArgumentException("a failed result requires a failed page");
            }
            terminalCode = Objects.requireNonNull(terminalCode, "terminalCode").trim();
            if (terminalCode.isEmpty()) {
                throw new IllegalArgumentException("terminalCode must not be blank");
            }
        }
    }

    public static J3ManualCallExecutionResult successful() {
        return new J3ManualCallExecutionResult(true, 5, null, null);
    }

    public static J3ManualCallExecutionResult failed(
            int completedPages,
            int failedPage,
            String terminalCode) {
        return new J3ManualCallExecutionResult(
                false, completedPages, failedPage, terminalCode);
    }
}
