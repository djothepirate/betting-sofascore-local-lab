package com.bettingproject.sofascorelocal.application.export;

public final class J7ExportContract {

    public static final String SCHEMA_ID =
            "urn:betting-project:sofascore-local-lab:j7:canonical-event-export:v1";
    public static final String SCHEMA_VERSION = "1.0.0";
    public static final String EXPORT_KIND = "J7_CANONICAL_EVENT";
    public static final String SELECTION_MODE = "LATEST_AVAILABLE";
    public static final long MAXIMUM_BYTES = 5L * 1024L * 1024L;

    private J7ExportContract() {
    }
}
