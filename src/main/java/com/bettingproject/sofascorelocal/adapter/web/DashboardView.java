package com.bettingproject.sofascorelocal.adapter.web;

import java.util.List;

public record DashboardView(
        String generatedAt,
        String projectStatus,
        String connectorMode,
        boolean configurationEnabled,
        boolean qualificationTransportAvailable,
        String binding,
        String baseUrlState,
        int maximumConcurrency,
        String minimumDelay,
        String databaseState,
        String flywayVersion,
        long snapshotCount,
        long incidentCount,
        FixtureCorpusView fixtureCorpus,
        LastCallView lastCall,
        List<EndpointRowView> endpoints) {

    public record FixtureCorpusView(
            String availability,
            String family,
            String origin,
            boolean providerSchemaValidated,
            String parserVersion,
            int declaredCount,
            int availableCount,
            int parsedCount,
            int schemaIncompatibleCount,
            int unexpectedContentCount,
            int loadingFailureCount) {
    }

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
