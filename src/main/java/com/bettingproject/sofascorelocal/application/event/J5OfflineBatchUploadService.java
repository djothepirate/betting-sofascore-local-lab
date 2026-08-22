package com.bettingproject.sofascorelocal.application.event;

import com.bettingproject.sofascorelocal.application.network.J5LocalUnavailableEvidence;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class J5OfflineBatchUploadService {

    public static final int MAXIMUM_TOTAL_BYTES = 25 * 1024 * 1024;
    public static final int MAXIMUM_FILES = J5OfflineBatchPlanService.MAXIMUM_EVENTS * 3;

    private static final Pattern FILE_NAME = Pattern.compile(
            "event-([1-9][0-9]{0,18})-(statistics|incidents|lineups)\\.json");

    public J5OfflineBatchUpload capture(String fileName, byte[] content) {
        Objects.requireNonNull(content, "content");
        if (content.length == 0) {
            throw rejected(J5OfflineBatchError.EMPTY_PAYLOAD);
        }
        if (content.length > RawPayloadEvidence.MAXIMUM_BYTES) {
            throw rejected(J5OfflineBatchError.PAYLOAD_TOO_LARGE);
        }
        try {
            return new J5OfflineBatchUpload(
                    Objects.requireNonNull(fileName, "fileName"),
                    RawPayloadEvidence.capture(content));
        }
        catch (IllegalArgumentException exception) {
            throw new J5OfflineBatchException(
                    J5OfflineBatchError.SENSITIVE_CONTENT, exception);
        }
    }

    public J5OfflineBatchUpload declareUnavailable404(String fileName) {
        return new J5OfflineBatchUpload(
                Objects.requireNonNull(fileName, "fileName"),
                J5LocalUnavailableEvidence.declared404());
    }

    public J5OfflineBatchUploadSet validate(
            J5OfflineBatchPlan plan,
            List<J5OfflineBatchUpload> uploads) {
        Objects.requireNonNull(plan, "plan");
        List<J5OfflineBatchUpload> files = List.copyOf(Objects.requireNonNull(
                uploads, "uploads"));
        validateManifest(plan, files.stream().map(J5OfflineBatchUpload::fileName).toList());

        Map<String, J5OfflineBatchUpload> byName = new HashMap<>();
        long totalBytes = 0;
        for (J5OfflineBatchUpload upload : files) {
            if (upload.payload().sizeBytes() == 0) {
                throw rejected(J5OfflineBatchError.EMPTY_PAYLOAD);
            }
            byName.put(upload.fileName(), upload);
            totalBytes += upload.payload().sizeBytes();
            if (totalBytes > MAXIMUM_TOTAL_BYTES) {
                throw rejected(J5OfflineBatchError.BATCH_TOO_LARGE);
            }
        }

        List<J5OfflineBatchEventPayloads> grouped = new ArrayList<>();
        for (J5OfflineBatchPlanEvent event : plan.events()) {
            List<String> names = event.expectedFileNames();
            grouped.add(new J5OfflineBatchEventPayloads(
                    event,
                    byName.get(names.get(0)).payload(),
                    byName.get(names.get(1)).payload(),
                    byName.get(names.get(2)).payload()));
        }
        return new J5OfflineBatchUploadSet(grouped, totalBytes);
    }

    public void validateManifest(J5OfflineBatchPlan plan, List<String> fileNames) {
        Objects.requireNonNull(plan, "plan");
        List<String> names;
        try {
            names = List.copyOf(Objects.requireNonNull(fileNames, "fileNames"));
        }
        catch (NullPointerException exception) {
            throw rejected(J5OfflineBatchError.FILE_NAME_INVALID);
        }
        if (names.isEmpty() || names.size() > MAXIMUM_FILES) {
            throw rejected(J5OfflineBatchError.FILE_SET_MISMATCH);
        }

        Set<String> expectedNames = new HashSet<>();
        Map<Long, J5OfflineBatchPlanEvent> plannedByProviderId = new HashMap<>();
        for (J5OfflineBatchPlanEvent event : plan.events()) {
            expectedNames.addAll(event.expectedFileNames());
            plannedByProviderId.put(event.providerEventId(), event);
        }

        Set<String> receivedNames = new HashSet<>();
        for (String fileName : names) {
            Matcher matcher = FILE_NAME.matcher(fileName);
            if (!matcher.matches()) {
                throw rejected(J5OfflineBatchError.FILE_NAME_INVALID);
            }
            long providerEventId;
            try {
                providerEventId = Long.parseLong(matcher.group(1));
            }
            catch (NumberFormatException exception) {
                throw rejected(J5OfflineBatchError.FILE_NAME_INVALID);
            }
            if (!plannedByProviderId.containsKey(providerEventId)
                    || !expectedNames.contains(fileName)) {
                throw rejected(J5OfflineBatchError.FILE_SET_MISMATCH);
            }
            if (!receivedNames.add(fileName)) {
                throw rejected(J5OfflineBatchError.DUPLICATE_FILE);
            }
        }
        if (!receivedNames.equals(expectedNames)) {
            throw rejected(J5OfflineBatchError.FILE_SET_MISMATCH);
        }
    }

    private static J5OfflineBatchException rejected(J5OfflineBatchError error) {
        return new J5OfflineBatchException(error);
    }
}
