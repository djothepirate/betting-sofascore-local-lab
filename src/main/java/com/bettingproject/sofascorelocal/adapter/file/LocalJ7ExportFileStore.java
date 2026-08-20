package com.bettingproject.sofascorelocal.adapter.file;

import com.bettingproject.sofascorelocal.application.export.J7ExportContract;
import com.bettingproject.sofascorelocal.config.SofascoreProperties;
import com.bettingproject.sofascorelocal.domain.export.J7ExportError;
import com.bettingproject.sofascorelocal.domain.export.J7ExportException;
import com.bettingproject.sofascorelocal.domain.export.J7StoredFile;
import com.bettingproject.sofascorelocal.port.J7ExportFileStore;
import com.bettingproject.sofascorelocal.security.Sha256;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public final class LocalJ7ExportFileStore implements J7ExportFileStore {

    private static final Pattern GENERATED_NAME = Pattern.compile(
            "j7-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}-"
                    + "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
                    + "\\.(?:candidate|validated|rejected)\\.json");

    private final Path root;

    @Autowired
    public LocalJ7ExportFileStore(SofascoreProperties properties) {
        this(Objects.requireNonNull(properties, "properties")
                .getExportDirectory()
                .toAbsolutePath()
                .normalize());
    }

    LocalJ7ExportFileStore(Path root) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
    }

    @Override
    public synchronized J7StoredFile writeNewOrVerify(String relativePath, byte[] content) {
        Objects.requireNonNull(content, "content");
        if (content.length < 1 || content.length > J7ExportContract.MAXIMUM_BYTES) {
            throw new J7ExportException(J7ExportError.FILE_TOO_LARGE);
        }
        Path target = safeTarget(relativePath);
        String expectedSha256 = Sha256.hex(content);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            byte[] existing = readVerified(relativePath, expectedSha256, content.length);
            if (!Arrays.equals(existing, content)) {
                throw new J7ExportException(J7ExportError.FILE_TAMPERED);
            }
            return new J7StoredFile(relativePath, expectedSha256, content.length);
        }

        Path temporary = null;
        try {
            temporary = Files.createTempFile(root, ".j7-" + UUID.randomUUID(), ".tmp");
            if (Files.isSymbolicLink(temporary)
                    || !Files.isRegularFile(temporary, LinkOption.NOFOLLOW_LINKS)) {
                throw new J7ExportException(J7ExportError.STORAGE_UNAVAILABLE);
            }
            try (FileChannel channel = FileChannel.open(
                    temporary,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    LinkOption.NOFOLLOW_LINKS)) {
                ByteBuffer buffer = ByteBuffer.wrap(content);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            try {
                // The hard-link creation is an atomic create-new publication on the same
                // filesystem. Unlike ATOMIC_MOVE, it has defined no-replace semantics when
                // the destination already exists. Removing the temporary name afterwards
                // leaves the fully fsynced inode reachable only through the generated name.
                Files.createLink(target, temporary);
            }
            catch (FileAlreadyExistsException exception) {
                byte[] existing = readVerified(relativePath, expectedSha256, content.length);
                if (!Arrays.equals(existing, content)) {
                    throw new J7ExportException(J7ExportError.FILE_TAMPERED);
                }
            }
            catch (UnsupportedOperationException exception) {
                throw new J7ExportException(J7ExportError.STORAGE_UNAVAILABLE, exception);
            }
            byte[] published = readVerified(relativePath, expectedSha256, content.length);
            if (!Arrays.equals(published, content)) {
                throw new J7ExportException(J7ExportError.FILE_TAMPERED);
            }
            return new J7StoredFile(relativePath, expectedSha256, content.length);
        }
        catch (J7ExportException exception) {
            throw exception;
        }
        catch (IOException exception) {
            throw new J7ExportException(J7ExportError.STORAGE_UNAVAILABLE, exception);
        }
        finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                }
                catch (IOException ignored) {
                    // The generated temporary file is never exposed and can be retried safely.
                }
            }
        }
    }

    @Override
    public Optional<byte[]> readExisting(String relativePath) {
        Path target = safeTarget(relativePath);
        try {
            if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                return Optional.empty();
            }
            return Optional.of(readBoundedRegularFile(target, null));
        }
        catch (NoSuchFileException exception) {
            return Optional.empty();
        }
        catch (J7ExportException exception) {
            throw exception;
        }
        catch (IOException exception) {
            throw new J7ExportException(J7ExportError.STORAGE_UNAVAILABLE, exception);
        }
    }

    @Override
    public List<String> findCandidatePaths(UUID canonicalEventId) {
        Objects.requireNonNull(canonicalEventId, "canonicalEventId");
        Path directory = safeRoot();
        String prefix = "j7-" + canonicalEventId + "-";
        String suffix = ".candidate.json";
        List<String> candidates = new ArrayList<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
            for (Path entry : entries) {
                String name = entry.getFileName().toString();
                if (!name.startsWith(prefix)
                        || !name.endsWith(suffix)
                        || !GENERATED_NAME.matcher(name).matches()) {
                    continue;
                }
                Path target = safeTarget(name);
                BasicFileAttributes attributes = Files.readAttributes(
                        target,
                        BasicFileAttributes.class,
                        LinkOption.NOFOLLOW_LINKS);
                if (!attributes.isRegularFile() || attributes.isSymbolicLink()) {
                    throw new J7ExportException(J7ExportError.FILE_TAMPERED);
                }
                candidates.add(name);
            }
        }
        catch (J7ExportException exception) {
            throw exception;
        }
        catch (IOException | SecurityException exception) {
            throw new J7ExportException(J7ExportError.STORAGE_UNAVAILABLE, exception);
        }
        candidates.sort(Comparator.naturalOrder());
        return List.copyOf(candidates);
    }

    @Override
    public byte[] readVerified(
            String relativePath,
            String expectedSha256,
            long expectedSizeBytes) {
        Path target = safeTarget(relativePath);
        if (expectedSizeBytes < 1 || expectedSizeBytes > J7ExportContract.MAXIMUM_BYTES) {
            throw new J7ExportException(J7ExportError.FILE_TAMPERED);
        }
        try {
            byte[] bytes = readBoundedRegularFile(target, expectedSizeBytes);
            if (bytes.length != expectedSizeBytes
                    || !Sha256.hex(bytes).equals(expectedSha256)) {
                throw new J7ExportException(J7ExportError.FILE_TAMPERED);
            }
            return bytes;
        }
        catch (J7ExportException exception) {
            throw exception;
        }
        catch (IOException exception) {
            throw new J7ExportException(J7ExportError.STORAGE_UNAVAILABLE, exception);
        }
    }

    @Override
    public void deleteVerified(
            String relativePath,
            String expectedSha256,
            long expectedSizeBytes) {
        Path target = safeTarget(relativePath);
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        readVerified(relativePath, expectedSha256, expectedSizeBytes);
        try {
            Files.delete(target);
        }
        catch (IOException exception) {
            throw new J7ExportException(J7ExportError.STORAGE_UNAVAILABLE, exception);
        }
    }

    private Path safeTarget(String relativePath) {
        if (relativePath == null || !GENERATED_NAME.matcher(relativePath).matches()) {
            throw new J7ExportException(J7ExportError.STORAGE_UNAVAILABLE);
        }
        Path directory = safeRoot();
        Path target = directory.resolve(relativePath).normalize();
        if (!target.getParent().equals(directory) || !target.startsWith(directory)) {
            throw new J7ExportException(J7ExportError.STORAGE_UNAVAILABLE);
        }
        return target;
    }

    private Path safeRoot() {
        try {
            requireNoSymbolicLinkComponent(root);
            Files.createDirectories(root);
            requireNoSymbolicLinkComponent(root);
            if (!root.toRealPath().equals(root)) {
                throw new J7ExportException(J7ExportError.STORAGE_UNAVAILABLE);
            }
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(root)
                    || !Files.isWritable(root)) {
                throw new J7ExportException(J7ExportError.STORAGE_UNAVAILABLE);
            }
            return root;
        }
        catch (IOException | SecurityException exception) {
            throw new J7ExportException(J7ExportError.STORAGE_UNAVAILABLE, exception);
        }
    }

    private static void requireNoSymbolicLinkComponent(Path path) {
        Path current = path.getRoot();
        for (Path component : path) {
            current = current == null ? component : current.resolve(component);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)
                    && Files.isSymbolicLink(current)) {
                throw new J7ExportException(J7ExportError.STORAGE_UNAVAILABLE);
            }
        }
    }

    private static byte[] readBoundedRegularFile(Path target, Long expectedSizeBytes)
            throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(
                target,
                BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile() || attributes.isSymbolicLink()) {
            throw new J7ExportException(J7ExportError.FILE_TAMPERED);
        }
        try (FileChannel channel = FileChannel.open(
                target,
                StandardOpenOption.READ,
                LinkOption.NOFOLLOW_LINKS)) {
            long size = channel.size();
            if (size < 1
                    || size > J7ExportContract.MAXIMUM_BYTES
                    || expectedSizeBytes != null && size != expectedSizeBytes) {
                throw new J7ExportException(J7ExportError.FILE_TAMPERED);
            }
            ByteBuffer buffer = ByteBuffer.allocate(Math.toIntExact(size));
            while (buffer.hasRemaining()) {
                if (channel.read(buffer) < 0) {
                    throw new J7ExportException(J7ExportError.FILE_TAMPERED);
                }
            }
            if (channel.size() != size || channel.read(ByteBuffer.allocate(1)) >= 0) {
                throw new J7ExportException(J7ExportError.FILE_TAMPERED);
            }
            return buffer.array();
        }
    }
}
