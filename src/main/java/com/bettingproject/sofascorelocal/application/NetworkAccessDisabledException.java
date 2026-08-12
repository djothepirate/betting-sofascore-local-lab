package com.bettingproject.sofascorelocal.application;

public class NetworkAccessDisabledException extends IllegalStateException {

    public NetworkAccessDisabledException(String message) {
        super(message);
    }
}
