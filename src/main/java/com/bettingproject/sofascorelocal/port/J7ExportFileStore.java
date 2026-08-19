package com.bettingproject.sofascorelocal.port;

import com.bettingproject.sofascorelocal.domain.export.J7StoredFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface J7ExportFileStore {

    J7StoredFile writeNewOrVerify(String relativePath, byte[] content);

    Optional<byte[]> readExisting(String relativePath);

    List<String> findCandidatePaths(UUID canonicalEventId);

    byte[] readVerified(String relativePath, String expectedSha256, long expectedSizeBytes);

    void deleteVerified(String relativePath, String expectedSha256, long expectedSizeBytes);
}
