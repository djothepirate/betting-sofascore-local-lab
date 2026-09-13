package com.bettingproject.sofascorelocal.application.network.playwright;

/** Admission at the last transport boundary, coordinated with an individual event stop. */
public interface PlaywrightDispatchAdmission {
    PlaywrightDispatchAdmission UNRESTRICTED = new PlaywrightDispatchAdmission() {
        public void check() { }
        public Permit acquireDispatchPermit() { return () -> { }; }
    };
    void check();
    Permit acquireDispatchPermit();
    /** Called synchronously for authenticated, bounded evidence, before reading the body. */
    default void onTransportProgress(PlaywrightTransportDiagnostic diagnostic) { }
    interface Permit extends AutoCloseable { @Override void close(); }
}
