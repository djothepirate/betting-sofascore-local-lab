package com.bettingproject.sofascorelocal.application.export;

import com.bettingproject.sofascorelocal.application.delivery.J7DeliveryPayloadClass;
import tools.jackson.databind.JsonNode;

/**
 * Classifies only the persisted source evidence of a verified J7 canonical v1 export.
 */
final class J7DeliveryPayloadClassifier {

    private static final int J7_CANONICAL_V1_SOURCE_COUNT = 5;

    private J7DeliveryPayloadClassifier() {
    }

    static J7DeliveryPayloadClass classify(JsonNode persistedSources) {
        if (persistedSources == null
                || !persistedSources.isArray()
                || persistedSources.size() != J7_CANONICAL_V1_SOURCE_COUNT) {
            return J7DeliveryPayloadClass.MIXED_OR_UNKNOWN;
        }

        boolean providerDerived = false;
        boolean synthetic = false;
        for (int index = 0; index < persistedSources.size(); index++) {
            JsonNode source = persistedSources.get(index);
            if (source == null || !source.isObject()) {
                return J7DeliveryPayloadClass.MIXED_OR_UNKNOWN;
            }
            JsonNode availabilityNode = source.get("availability");
            JsonNode sourceKindNode = source.get("sourceKind");
            if (availabilityNode == null || !availabilityNode.isString()) {
                return J7DeliveryPayloadClass.MIXED_OR_UNKNOWN;
            }

            String availability = availabilityNode.stringValue();
            if ("MISSING".equals(availability)) {
                if (sourceKindNode == null || !sourceKindNode.isNull()) {
                    return J7DeliveryPayloadClass.MIXED_OR_UNKNOWN;
                }
                continue;
            }
            if (!isKnownConcreteAvailability(availability)
                    || sourceKindNode == null
                    || !sourceKindNode.isString()) {
                return J7DeliveryPayloadClass.MIXED_OR_UNKNOWN;
            }

            switch (sourceKindNode.stringValue()) {
                case "PROVIDER_SNAPSHOT" -> providerDerived = true;
                case "SYNTHETIC_FIXTURE" -> synthetic = true;
                default -> {
                    return J7DeliveryPayloadClass.MIXED_OR_UNKNOWN;
                }
            }
            if (providerDerived && synthetic) {
                return J7DeliveryPayloadClass.MIXED_OR_UNKNOWN;
            }
        }

        if (providerDerived) {
            return J7DeliveryPayloadClass.PROVIDER_DERIVED;
        }
        if (synthetic) {
            return J7DeliveryPayloadClass.SYNTHETIC_ONLY;
        }
        return J7DeliveryPayloadClass.MIXED_OR_UNKNOWN;
    }

    private static boolean isKnownConcreteAvailability(String availability) {
        return "PRESENT".equals(availability)
                || "EMPTY_VALID".equals(availability)
                || "UNAVAILABLE".equals(availability);
    }
}
