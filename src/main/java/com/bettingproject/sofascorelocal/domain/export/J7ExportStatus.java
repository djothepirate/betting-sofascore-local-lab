package com.bettingproject.sofascorelocal.domain.export;

public enum J7ExportStatus {
    COHERENCE_CHECKED,
    HUMAN_VALIDATED,
    REJECTED;

    public boolean isTerminal() {
        return this != COHERENCE_CHECKED;
    }
}
