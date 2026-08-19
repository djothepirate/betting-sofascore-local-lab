package com.bettingproject.sofascorelocal.application.export;

import com.bettingproject.sofascorelocal.domain.export.J7ExportManifest;

import java.util.Objects;

public record J7ExportPreview(
        J7ExportManifest manifest,
        String prettyJson,
        String validationConfirmation,
        String rejectionConfirmation) {

    public J7ExportPreview {
        manifest = Objects.requireNonNull(manifest, "manifest");
        prettyJson = Objects.requireNonNull(prettyJson, "prettyJson");
        validationConfirmation = Objects.requireNonNull(
                validationConfirmation, "validationConfirmation");
        rejectionConfirmation = Objects.requireNonNull(
                rejectionConfirmation, "rejectionConfirmation");
    }
}
