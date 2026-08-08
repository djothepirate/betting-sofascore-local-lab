package com.geoffrey.betting.sofascorelocal.application;

public class NetworkAccessDisabledException extends IllegalStateException {

    public NetworkAccessDisabledException(String message) {
        super(message);
    }
}
