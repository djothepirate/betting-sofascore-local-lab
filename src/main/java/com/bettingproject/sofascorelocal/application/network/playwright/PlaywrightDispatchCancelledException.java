package com.bettingproject.sofascorelocal.application.network.playwright;

/** A proven cancellation before GET, distinct from a transport of unknown outcome. */
public final class PlaywrightDispatchCancelledException extends RuntimeException {
    public PlaywrightDispatchCancelledException() { super("LIVE_DISPATCH_CANCELLED"); }
}
