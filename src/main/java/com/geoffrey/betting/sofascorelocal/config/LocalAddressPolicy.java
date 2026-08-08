package com.geoffrey.betting.sofascorelocal.config;

import java.net.InetAddress;
import java.net.UnknownHostException;

public final class LocalAddressPolicy {

    private LocalAddressPolicy() {
    }

    public static void requireLoopback(String configuredAddress) {
        String value = configuredAddress == null || configuredAddress.isBlank()
                ? "127.0.0.1"
                : configuredAddress.trim();
        try {
            InetAddress address = InetAddress.getByName(value);
            if (!address.isLoopbackAddress()) {
                throw new IllegalStateException(
                        "The SofaScore Local Lab must bind to a loopback address, not " + value);
            }
        }
        catch (UnknownHostException exception) {
            throw new IllegalStateException("Invalid server.address: " + value, exception);
        }
    }
}
