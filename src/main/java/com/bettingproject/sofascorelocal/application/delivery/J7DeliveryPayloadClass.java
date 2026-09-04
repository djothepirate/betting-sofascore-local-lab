package com.bettingproject.sofascorelocal.application.delivery;

/**
 * Fail-closed provenance classification for the exact bytes of a validated J7 export.
 */
public enum J7DeliveryPayloadClass {
    SYNTHETIC_ONLY,
    PROVIDER_DERIVED,
    MIXED_OR_UNKNOWN
}
