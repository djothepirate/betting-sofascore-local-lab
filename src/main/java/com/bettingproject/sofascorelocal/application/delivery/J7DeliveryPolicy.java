package com.bettingproject.sofascorelocal.application.delivery;

import com.bettingproject.sofascorelocal.config.OptionalLocalPushProperties;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryError;
import com.bettingproject.sofascorelocal.domain.delivery.J7DeliveryException;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Runtime gate kept separate from every SofaScore acquisition property. */
public final class J7DeliveryPolicy {

    private final OptionalLocalPushProperties properties;

    public J7DeliveryPolicy(OptionalLocalPushProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    public void requireSyntheticLoopbackQualification() {
        if (properties.isEnabled() || properties.isRemoteDeliveryAuthorized()) {
            throw new J7DeliveryException(J7DeliveryError.REMOTE_DELIVERY_NOT_AUTHORIZED);
        }
        if (!properties.isLoopbackQualification()) {
            throw new J7DeliveryException(J7DeliveryError.LOOPBACK_QUALIFICATION_DISABLED);
        }
        if (!properties.isLoopbackQualificationSafe()) {
            throw new J7DeliveryException(J7DeliveryError.INVALID_LOOPBACK_ORIGIN);
        }
        if (properties.isAutomaticRetryEnabled()) {
            throw new J7DeliveryException(J7DeliveryError.REMOTE_DELIVERY_NOT_AUTHORIZED);
        }
    }

    public void requireRealDelivery() {
        if (!properties.isEnabled()) {
            throw new J7DeliveryException(J7DeliveryError.DELIVERY_DISABLED);
        }
        if (properties.getOfficialPermissionStatus()
                != OptionalLocalPushProperties.PermissionStatus.EVIDENCED_COMPATIBLE) {
            throw new J7DeliveryException(
                    J7DeliveryError.OFFICIAL_PERMISSION_NOT_EVIDENCED);
        }
        if (!properties.isRemoteDeliveryAuthorized()) {
            throw new J7DeliveryException(
                    J7DeliveryError.REMOTE_DELIVERY_NOT_AUTHORIZED);
        }
        throw new J7DeliveryException(J7DeliveryError.REMOTE_DELIVERY_NOT_AUTHORIZED);
    }

    public List<String> realDeliveryBlockers() {
        List<String> blockers = new ArrayList<>();
        if (!properties.isEnabled()) {
            blockers.add("DELIVERY_DISABLED");
        }
        if (properties.getOfficialPermissionStatus()
                != OptionalLocalPushProperties.PermissionStatus.EVIDENCED_COMPATIBLE) {
            blockers.add("OFFICIAL_PERMISSION_NOT_EVIDENCED");
        }
        if (!properties.isRemoteDeliveryAuthorized()) {
            blockers.add("REMOTE_DELIVERY_NOT_AUTHORIZED");
        }
        blockers.add("REAL_RECEIVER_WORK_ORDER_NOT_SATISFIED");
        return List.copyOf(blockers);
    }

    public URI syntheticLoopbackOrigin() {
        requireSyntheticLoopbackQualification();
        return URI.create(properties.getLoopbackOrigin());
    }
}
