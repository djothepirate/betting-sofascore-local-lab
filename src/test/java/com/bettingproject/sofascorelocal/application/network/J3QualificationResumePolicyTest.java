package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.config.SofascoreProperties;
import com.bettingproject.sofascorelocal.domain.provider.J3StoredQualificationPage;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsProviderPageRequest;
import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class J3QualificationResumePolicyTest {

    private static final LocalDate DATE = ScheduledEventsProviderPageRequest.QUALIFICATION_DATE;
    private static final Instant REQUESTED_AT = Instant.parse("2026-08-13T13:28:17Z");

    @Test
    void authorizesOnlyPageTwoAfterReparsingTheSingleLocalPageOne() throws Exception {
        byte[] payload = Files.readAllBytes(
                Path.of("fixtures/scheduled-events/qualified-provider-shape.json"));
        var policy = new J3QualificationResumePolicy(
                configuredProviderPolicy(),
                date -> List.of(storedPage(1, 1L, payload)));

        var snapshot = policy.snapshot();

        assertThat(snapshot.available()).isTrue();
        assertThat(snapshot.providerOrigin()).hasToString("https://www.sofascore.com");
        assertThat(snapshot.firstPage()).isEqualTo(2);
        assertThat(snapshot.blockers()).isEmpty();
    }

    @Test
    void blocksWhenPageOneIsMissingOrAlreadyFollowedByPageTwo() throws Exception {
        var missing = new J3QualificationResumePolicy(
                configuredProviderPolicy(),
                date -> List.of()).snapshot();

        assertThat(missing.available()).isFalse();
        assertThat(missing.blockers()).containsExactly("J3_PAGE_1_CHECKPOINT_MISSING");

        byte[] payload = Files.readAllBytes(
                Path.of("fixtures/scheduled-events/qualified-provider-shape.json"));
        var alreadyAttempted = new J3QualificationResumePolicy(
                configuredProviderPolicy(),
                date -> List.of(
                        storedPage(1, 1L, payload),
                        storedPage(2, 2L, payload))).snapshot();

        assertThat(alreadyAttempted.available()).isFalse();
        assertThat(alreadyAttempted.blockers()).containsExactly(
                "J3_RESUME_ALREADY_ATTEMPTED");
    }

    @Test
    void blocksAnAmbiguousOrUnsuccessfulPageOne() throws Exception {
        byte[] payload = Files.readAllBytes(
                Path.of("fixtures/scheduled-events/qualified-provider-shape.json"));
        var ambiguous = new J3QualificationResumePolicy(
                configuredProviderPolicy(),
                date -> List.of(
                        storedPage(1, 1L, payload),
                        storedPage(1, 2L, payload))).snapshot();

        assertThat(ambiguous.available()).isFalse();
        assertThat(ambiguous.blockers()).containsExactly(
                "J3_PAGE_1_CHECKPOINT_AMBIGUOUS");

        var unsuccessful = new J3QualificationResumePolicy(
                configuredProviderPolicy(),
                date -> List.of(storedPage(1, 1L, 503, payload))).snapshot();

        assertThat(unsuccessful.available()).isFalse();
        assertThat(unsuccessful.blockers()).containsExactly(
                "J3_PAGE_1_CHECKPOINT_HTTP_INVALID");
    }

    @Test
    void blocksAnUnparseablePageOneOrOneWithoutANextPage() {
        var unparseable = new J3QualificationResumePolicy(
                configuredProviderPolicy(),
                date -> List.of(storedPage(
                        1,
                        1L,
                        "{}".getBytes(StandardCharsets.UTF_8)))).snapshot();

        assertThat(unparseable.available()).isFalse();
        assertThat(unparseable.blockers()).containsExactly(
                "J3_PAGE_1_CHECKPOINT_NOT_PARSEABLE");

        byte[] terminalPage = """
                {
                  "scheduled": [],
                  "hasNextPage": false
                }
                """.getBytes(StandardCharsets.UTF_8);
        var noNextPage = new J3QualificationResumePolicy(
                configuredProviderPolicy(),
                date -> List.of(storedPage(1, 1L, terminalPage))).snapshot();

        assertThat(noNextPage.available()).isFalse();
        assertThat(noNextPage.blockers()).containsExactly(
                "J3_PAGE_1_CHECKPOINT_HAS_NO_NEXT_PAGE");
    }

    @Test
    void convertsCheckpointReadFailuresIntoASafeBlocker() {
        var snapshot = new J3QualificationResumePolicy(
                configuredProviderPolicy(),
                date -> {
                    throw new IllegalStateException("database diagnostic");
                }).snapshot();

        assertThat(snapshot.available()).isFalse();
        assertThat(snapshot.blockers()).containsExactly("J3_CHECKPOINT_READ_UNAVAILABLE");
    }

    private static J3ProviderQualificationPolicy configuredProviderPolicy() {
        SofascoreProperties properties = new SofascoreProperties();
        properties.setEnabled(true);
        properties.setJ3QualificationEnabled(true);
        properties.setBaseUrl("https://www.sofascore.com");
        properties.setAllowedEndpoints(Set.of(SofascoreEndpointType.SCHEDULED_EVENTS));
        return new J3ProviderQualificationPolicy(properties);
    }

    private static J3StoredQualificationPage storedPage(
            int page,
            long snapshotId,
            byte[] payload) {
        return storedPage(page, snapshotId, 200, payload);
    }

    private static J3StoredQualificationPage storedPage(
            int page,
            long snapshotId,
            int httpStatus,
            byte[] payload) {
        return new J3StoredQualificationPage(
                snapshotId,
                DATE,
                page,
                SofascoreEndpointType.SCHEDULED_EVENTS.name()
                        + "|date=" + DATE + "|page=" + page,
                REQUESTED_AT,
                REQUESTED_AT.plusMillis(640),
                httpStatus,
                "application/json",
                Duration.ofMillis(640),
                RawPayloadEvidence.capture(payload));
    }
}
