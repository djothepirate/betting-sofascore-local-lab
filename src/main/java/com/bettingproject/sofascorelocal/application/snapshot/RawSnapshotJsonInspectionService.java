package com.bettingproject.sofascorelocal.application.snapshot;

import com.bettingproject.sofascorelocal.domain.provider.RawSnapshotInspectionSource;
import com.bettingproject.sofascorelocal.port.RawSnapshotInspectionStore;
import com.bettingproject.sofascorelocal.security.SensitiveContentScanner;
import com.bettingproject.sofascorelocal.security.Sha256;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

@Service
public class RawSnapshotJsonInspectionService {

    static final int RECENT_SNAPSHOT_LIMIT = 50;

    private static final ObjectMapper JSON_MAPPER = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private final RawSnapshotInspectionStore store;
    private final Clock clock;

    @Autowired
    public RawSnapshotJsonInspectionService(RawSnapshotInspectionStore store) {
        this(store, Clock.systemUTC());
    }

    RawSnapshotJsonInspectionService(
            RawSnapshotInspectionStore store,
            Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public RawSnapshotInspectionCatalog loadCatalog() {
        try {
            return RawSnapshotInspectionCatalog.available(
                    store.findRecent(RECENT_SNAPSHOT_LIMIT));
        }
        catch (DataAccessException | IllegalArgumentException | IllegalStateException exception) {
            return RawSnapshotInspectionCatalog.unavailable();
        }
    }

    public RawSnapshotJsonInspection inspect(long snapshotId) {
        if (snapshotId < 1) {
            throw new RawSnapshotInspectionException(
                    RawSnapshotInspectionError.INVALID_SELECTION);
        }

        RawSnapshotInspectionSource source;
        try {
            source = store.findById(snapshotId).orElseThrow(() ->
                    new RawSnapshotInspectionException(
                            RawSnapshotInspectionError.SNAPSHOT_NOT_FOUND));
        }
        catch (RawSnapshotInspectionException exception) {
            throw exception;
        }
        catch (DataAccessException exception) {
            throw new RawSnapshotInspectionException(
                    RawSnapshotInspectionError.LOCAL_DATABASE_UNAVAILABLE,
                    exception);
        }
        catch (IllegalArgumentException | IllegalStateException exception) {
            throw new RawSnapshotInspectionException(
                    RawSnapshotInspectionError.PAYLOAD_INTEGRITY_FAILURE,
                    exception);
        }

        byte[] payload = source.payloadRaw();
        if (source.summary().payloadSizeBytes() != payload.length
                || !source.summary().payloadSha256().equals(Sha256.hex(payload))) {
            throw new RawSnapshotInspectionException(
                    RawSnapshotInspectionError.PAYLOAD_INTEGRITY_FAILURE);
        }

        List<String> sensitiveFindings = SensitiveContentScanner.findings(payload);
        if (!sensitiveFindings.isEmpty()) {
            throw new RawSnapshotInspectionException(
                    RawSnapshotInspectionError.SENSITIVE_CONTENT_BLOCKED);
        }

        try {
            JsonNode root = JSON_MAPPER.readTree(payload);
            if (root == null) {
                throw new RawSnapshotInspectionException(
                        RawSnapshotInspectionError.INVALID_JSON);
            }
            String formatted = JSON_MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(root);
            return new RawSnapshotJsonInspection(
                    source.summary(),
                    clock.instant(),
                    formatted);
        }
        catch (JacksonException exception) {
            throw new RawSnapshotInspectionException(
                    RawSnapshotInspectionError.INVALID_JSON,
                    exception);
        }
    }
}
