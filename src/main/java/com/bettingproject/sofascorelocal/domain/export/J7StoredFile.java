package com.bettingproject.sofascorelocal.domain.export;

import java.util.Objects;

public record J7StoredFile(String relativePath, String sha256, long sizeBytes) {

    public J7StoredFile {
        relativePath = Objects.requireNonNull(relativePath, "relativePath");
        sha256 = Objects.requireNonNull(sha256, "sha256");
        if (relativePath.isBlank() || sizeBytes < 1) {
            throw new IllegalArgumentException("stored file metadata is invalid");
        }
    }
}
