package com.bettingproject.sofascorelocal.security;

/** Safe, value-free failures from the one-time J7 delivery confirmation boundary. */
public enum J7DeliveryConfirmationError {
    INVALID_SESSION_KEY,
    INVALID_DELIVERY_IDENTITY,
    INVALID_ACTION_CONTEXT,
    INVALID_OR_EXPIRED_CONFIRMATION,
    CONFIRMATION_CAPACITY_EXCEEDED
}
