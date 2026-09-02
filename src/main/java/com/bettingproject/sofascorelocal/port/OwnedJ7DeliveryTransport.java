package com.bettingproject.sofascorelocal.port;

/** One manually scoped transport that must be closed after exactly one delivery action. */
public interface OwnedJ7DeliveryTransport extends J7DeliveryTransport, AutoCloseable {

    @Override
    void close();
}
