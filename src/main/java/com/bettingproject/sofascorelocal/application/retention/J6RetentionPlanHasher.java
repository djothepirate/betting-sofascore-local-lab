package com.bettingproject.sofascorelocal.application.retention;

import com.bettingproject.sofascorelocal.domain.retention.J6RetentionCandidate;
import com.bettingproject.sofascorelocal.security.Sha256;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class J6RetentionPlanHasher {

    private J6RetentionPlanHasher() {
    }

    public static String calculate(
            int retentionDays,
            Instant cutoffAt,
            List<J6RetentionCandidate> candidates) {
        Objects.requireNonNull(cutoffAt, "cutoffAt");
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeUTF("j6-raw-payload-retention-plan-v1");
                output.writeInt(retentionDays);
                output.writeLong(cutoffAt.getEpochSecond());
                output.writeInt(cutoffAt.getNano());
                output.writeInt(candidates.size());
                for (J6RetentionCandidate candidate : candidates) {
                    output.writeLong(candidate.snapshotId());
                    output.writeLong(candidate.receivedAt().getEpochSecond());
                    output.writeInt(candidate.receivedAt().getNano());
                    output.writeLong(candidate.payloadSizeBytes());
                    output.writeUTF(candidate.payloadSha256());
                }
            }
            return Sha256.hex(bytes.toByteArray());
        }
        catch (IOException exception) {
            throw new IllegalStateException("unable to hash the J6 retention plan", exception);
        }
    }
}
