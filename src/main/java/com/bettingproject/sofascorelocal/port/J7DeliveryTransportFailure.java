package com.bettingproject.sofascorelocal.port;

public enum J7DeliveryTransportFailure {
    TIMEOUT,
    TLS_FAILURE,
    IO_FAILURE,
    INTERRUPTED,
    AUTOMATIC_REPLAY_BLOCKED,
    ACKNOWLEDGEMENT_TOO_LARGE,
    UNSUPPORTED_ACKNOWLEDGEMENT_ENCODING,
    INVALID_RESPONSE_METADATA
}
