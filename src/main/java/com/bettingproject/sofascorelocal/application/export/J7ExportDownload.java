package com.bettingproject.sofascorelocal.application.export;

import java.util.Objects;
import java.util.UUID;

public record J7ExportDownload(
        UUID exportId,
        String fileName,
        String sha256,
        byte[] content) {

    public J7ExportDownload {
        exportId = Objects.requireNonNull(exportId, "exportId");
        fileName = Objects.requireNonNull(fileName, "fileName");
        sha256 = Objects.requireNonNull(sha256, "sha256");
        content = Objects.requireNonNull(content, "content").clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }

    public long sizeBytes() {
        return content.length;
    }
}
