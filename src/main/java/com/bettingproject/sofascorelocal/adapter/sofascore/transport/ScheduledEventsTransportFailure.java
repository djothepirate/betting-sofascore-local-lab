package com.bettingproject.sofascorelocal.adapter.sofascore.transport;

public enum ScheduledEventsTransportFailure {
    TIMEOUT,
    IO_FAILURE,
    PAYLOAD_TOO_LARGE,
    SENSITIVE_CONTENT_REJECTED,
    UNEXPECTED_CONTENT,
    OPERATOR_STOP
}
