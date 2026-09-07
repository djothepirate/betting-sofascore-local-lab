package com.bettingproject.sofascorelocal.application.network.playwright;

/** Admission at the last transport boundary, coordinated with an individual event stop. */
public interface PlaywrightDispatchAdmission {
    PlaywrightDispatchAdmission UNRESTRICTED = new PlaywrightDispatchAdmission() {
        public void check() { }
        public Permit acquireDispatchPermit() { return () -> { }; }
    };
    void check();
    Permit acquireDispatchPermit();
    interface Permit extends AutoCloseable { @Override void close(); }
}
