package com.bettingproject.sofascorelocal.domain.provider;

public enum J3ManualCallIntentState {
    AWAITING_CONFIRMATION,
    CONFIRMED_BLOCKED,
    CONFIRMED_READY,
    EXECUTING,
    COMPLETED,
    FAILED,
    EXPIRED,
    CANCELLED_BY_GLOBAL_STOP
}
