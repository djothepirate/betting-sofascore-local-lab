package com.bettingproject.sofascorelocal.application.event;

import com.bettingproject.sofascorelocal.config.SofascoreProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class J5OfflineBatchPolicy {

    private final SofascoreProperties properties;

    public J5OfflineBatchPolicy(SofascoreProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    public J5OfflineBatchPolicySnapshot snapshot() {
        List<String> blockers = new ArrayList<>();
        addIf(properties.isAutomaticRefreshEnabled(), blockers,
                "AUTOMATIC_REFRESH_MUST_BE_FALSE");
        addIf(properties.isLivePollingEnabled(), blockers,
                "LIVE_POLLING_MUST_BE_FALSE");
        addIf(!properties.isStoreRawPayloads(), blockers,
                "RAW_STORAGE_MUST_BE_TRUE");
        return new J5OfflineBatchPolicySnapshot(blockers.isEmpty(), blockers);
    }

    public void requireAvailable() {
        if (!snapshot().available()) {
            throw new J5OfflineBatchException(J5OfflineBatchError.OFFLINE_POLICY_UNAVAILABLE);
        }
    }

    private static void addIf(boolean condition, List<String> blockers, String blocker) {
        if (condition) {
            blockers.add(blocker);
        }
    }
}
