package com.bettingproject.sofascorelocal.adapter.file;

import com.bettingproject.sofascorelocal.application.export.J7ExportContract;
import com.bettingproject.sofascorelocal.domain.export.J7ExportError;
import com.bettingproject.sofascorelocal.domain.export.J7ExportException;
import com.bettingproject.sofascorelocal.security.Sha256;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalJ7ExportFileStoreTest {

    private static final UUID EVENT_ID = UUID.fromString(
            "9740bb59-0207-31a3-a6ae-5c8463255887");
    private static final UUID EXPORT_ID = UUID.fromString(
            "70000000-0000-4000-8000-000000000007");

    @TempDir
    Path temporaryDirectory;

    @Test
    void atomicallyPublishesANewGeneratedFileAndAcceptsOnlyAnExactRetry() throws Exception {
        LocalJ7ExportFileStore store = new LocalJ7ExportFileStore(temporaryDirectory);
        String name = candidateName();
        byte[] content = "{\"safe\":true}\n".getBytes(StandardCharsets.UTF_8);

        var first = store.writeNewOrVerify(name, content);
        var retry = store.writeNewOrVerify(name, content);

        assertThat(first).isEqualTo(retry);
        assertThat(first.sha256()).isEqualTo(Sha256.hex(content));
        assertThat(store.readExisting(name))
                .hasValueSatisfying(value -> assertThat(value).isEqualTo(content));
        assertThat(store.readVerified(name, first.sha256(), first.sizeBytes()))
                .isEqualTo(content);
        try (var children = Files.list(temporaryDirectory)) {
            assertThat(children.map(path -> path.getFileName().toString()).toList())
                    .containsExactly(name);
        }

        assertThatThrownBy(() -> store.writeNewOrVerify(
                name,
                "{\"safe\":false}\n".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOfSatisfying(J7ExportException.class, exception ->
                        assertThat(exception.error()).isEqualTo(J7ExportError.FILE_TAMPERED));
        assertThat(Files.readAllBytes(temporaryDirectory.resolve(name))).isEqualTo(content);
    }

    @Test
    void readsOnlyABoundedRegularExistingFileForOrphanRecovery() throws Exception {
        LocalJ7ExportFileStore store = new LocalJ7ExportFileStore(temporaryDirectory);
        assertThat(store.readExisting(candidateName())).isEmpty();

        byte[] content = "{\"safe\":true}\n".getBytes(StandardCharsets.UTF_8);
        Files.write(temporaryDirectory.resolve(candidateName()), content);

        assertThat(store.readExisting(candidateName()))
                .hasValueSatisfying(value -> assertThat(value).isEqualTo(content));
        assertStorageRefusal(() -> store.readExisting("../outside.json"));
    }

    @Test
    void enumeratesOnlyRegularCandidateFilesForTheRequestedCanonicalEvent() throws Exception {
        LocalJ7ExportFileStore store = new LocalJ7ExportFileStore(temporaryDirectory);
        UUID otherEventId = UUID.fromString(
                "fb71858d-018b-33ee-9a4a-c46c749494ff");
        String requestedCandidate = candidateName();
        String requestedValidated = "j7-" + EVENT_ID + "-" + EXPORT_ID
                + ".validated.json";
        String otherCandidate = "j7-" + otherEventId + "-" + EXPORT_ID
                + ".candidate.json";
        Files.writeString(temporaryDirectory.resolve(requestedCandidate), "{}\n");
        Files.writeString(temporaryDirectory.resolve(requestedValidated), "{}\n");
        Files.writeString(temporaryDirectory.resolve(otherCandidate), "{}\n");
        Files.writeString(temporaryDirectory.resolve("unrelated.txt"), "ignored\n");

        assertThat(store.findCandidatePaths(EVENT_ID))
                .containsExactly(requestedCandidate);
    }

    @Test
    void refusesAMatchingCandidateSymlinkDuringOrphanEnumeration() throws Exception {
        Path outside = temporaryDirectory.resolve("outside.json");
        Files.writeString(outside, "{}\n", StandardCharsets.UTF_8);
        Path link = temporaryDirectory.resolve(candidateName());
        try {
            Files.createSymbolicLink(link, outside);
        }
        catch (UnsupportedOperationException | java.io.IOException | SecurityException exception) {
            org.junit.jupiter.api.Assumptions.abort(
                    "Symbolic links are unavailable in this test environment");
        }
        LocalJ7ExportFileStore store = new LocalJ7ExportFileStore(temporaryDirectory);

        assertThatThrownBy(() -> store.findCandidatePaths(EVENT_ID))
                .isInstanceOfSatisfying(J7ExportException.class, exception ->
                        assertThat(exception.error()).isEqualTo(J7ExportError.FILE_TAMPERED));
    }

    @Test
    void createsANewConfiguredDirectoryWithoutLeavingItsResolvedRoot() {
        Path configuredRoot = temporaryDirectory.resolve("new-export-root");
        LocalJ7ExportFileStore store = new LocalJ7ExportFileStore(configuredRoot);
        byte[] content = "{\"safe\":true}\n".getBytes(StandardCharsets.UTF_8);

        var stored = store.writeNewOrVerify(candidateName(), content);

        assertThat(Files.isDirectory(configuredRoot)).isTrue();
        assertThat(configuredRoot.resolve(stored.relativePath()).normalize().getParent())
                .isEqualTo(configuredRoot.toAbsolutePath().normalize());
    }

    @Test
    void refusesTraversalAbsolutePathsAndNonGeneratedNames() {
        LocalJ7ExportFileStore store = new LocalJ7ExportFileStore(temporaryDirectory);
        byte[] content = "{}\n".getBytes(StandardCharsets.UTF_8);

        assertStorageRefusal(() -> store.writeNewOrVerify("../outside.json", content));
        assertStorageRefusal(() -> store.writeNewOrVerify(
                temporaryDirectory.resolve("absolute.json").toString(), content));
        assertStorageRefusal(() -> store.writeNewOrVerify("export.json", content));
        assertStorageRefusal(() -> store.readVerified(
                "j7-" + EVENT_ID + "-" + EXPORT_ID + ".candidate.json/child",
                "a".repeat(64),
                1));
    }

    @Test
    void detectsManualAlterationAndNeverReturnsTheChangedBytes() throws Exception {
        LocalJ7ExportFileStore store = new LocalJ7ExportFileStore(temporaryDirectory);
        String name = candidateName();
        byte[] content = "{\"safe\":true}\n".getBytes(StandardCharsets.UTF_8);
        var stored = store.writeNewOrVerify(name, content);
        Files.writeString(
                temporaryDirectory.resolve(name),
                "{\"tampered\":true}\n",
                StandardCharsets.UTF_8);

        assertThatThrownBy(() -> store.readVerified(
                name,
                stored.sha256(),
                stored.sizeBytes()))
                .isInstanceOfSatisfying(J7ExportException.class, exception ->
                        assertThat(exception.error()).isEqualTo(J7ExportError.FILE_TAMPERED));
    }

    @Test
    void refusesOversizedContentAndDeletesOnlyAnExactlyVerifiedCandidate() throws Exception {
        LocalJ7ExportFileStore store = new LocalJ7ExportFileStore(temporaryDirectory);
        assertThatThrownBy(() -> store.writeNewOrVerify(
                candidateName(),
                new byte[Math.toIntExact(J7ExportContract.MAXIMUM_BYTES + 1)]))
                .isInstanceOfSatisfying(J7ExportException.class, exception ->
                        assertThat(exception.error()).isEqualTo(J7ExportError.FILE_TOO_LARGE));

        byte[] content = "{\"safe\":true}\n".getBytes(StandardCharsets.UTF_8);
        var stored = store.writeNewOrVerify(candidateName(), content);
        assertThatThrownBy(() -> store.deleteVerified(
                candidateName(),
                "a".repeat(64),
                stored.sizeBytes()))
                .isInstanceOfSatisfying(J7ExportException.class, exception ->
                        assertThat(exception.error()).isEqualTo(J7ExportError.FILE_TAMPERED));
        assertThat(Files.exists(temporaryDirectory.resolve(candidateName()))).isTrue();

        store.deleteVerified(candidateName(), stored.sha256(), stored.sizeBytes());
        assertThat(Files.exists(temporaryDirectory.resolve(candidateName()))).isFalse();
        store.deleteVerified(candidateName(), stored.sha256(), stored.sizeBytes());
    }

    @Test
    void refusesASymbolicLinkInsteadOfFollowingIt() throws Exception {
        Path outside = temporaryDirectory.resolve("outside-target.json");
        Files.writeString(outside, "outside", StandardCharsets.UTF_8);
        Path link = temporaryDirectory.resolve(candidateName());
        try {
            Files.createSymbolicLink(link, outside);
        }
        catch (UnsupportedOperationException | java.io.IOException | SecurityException exception) {
            org.junit.jupiter.api.Assumptions.abort("Symbolic links are unavailable in this test environment");
        }
        LocalJ7ExportFileStore store = new LocalJ7ExportFileStore(temporaryDirectory);

        assertThatThrownBy(() -> store.readVerified(
                candidateName(),
                Sha256.hex("outside".getBytes(StandardCharsets.UTF_8)),
                7))
                .isInstanceOfSatisfying(J7ExportException.class, exception ->
                        assertThat(exception.error()).isEqualTo(J7ExportError.FILE_TAMPERED));
        assertThat(Files.readString(outside, StandardCharsets.UTF_8)).isEqualTo("outside");
        Files.deleteIfExists(link);
        Files.deleteIfExists(outside);
    }

    private static String candidateName() {
        return "j7-" + EVENT_ID + "-" + EXPORT_ID + ".candidate.json";
    }

    private static void assertStorageRefusal(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action)
                .isInstanceOfSatisfying(J7ExportException.class, exception ->
                        assertThat(exception.error()).isEqualTo(J7ExportError.STORAGE_UNAVAILABLE));
    }
}
