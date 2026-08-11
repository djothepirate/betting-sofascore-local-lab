package com.bettingproject.sofascorelocal.adapter.web;

import java.util.List;

public record DashboardView(
        String generatedAt,
        String projectStatus,
        String connectorMode,
        boolean configurationEnabled,
        String binding,
        String baseUrlState,
        int maximumConcurrency,
        String minimumDelay,
        String databaseState,
        String flywayVersion,
        long snapshotCount,
        long incidentCount,
        LastCallView lastCall,
        List<EndpointRowView> endpoints) {

    public record LastCallView(
            String receivedAt,
            String logicalEndpoint,
            Integer httpStatus,
            Long latencyMs,
            String payloadHash) {
    }

    public record EndpointRowView(
            String type,
            String cacheTtl,
            boolean manualOnly,
            boolean callable,
            boolean uriConfigured,
            String purpose) {
    }
}
