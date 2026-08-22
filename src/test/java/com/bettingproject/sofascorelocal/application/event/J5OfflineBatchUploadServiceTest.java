package com.bettingproject.sofascorelocal.application.event;

import com.bettingproject.sofascorelocal.application.network.J5LocalUnavailableEvidence;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class J5OfflineBatchUploadServiceTest {

    private final J5OfflineBatchUploadService service = new J5OfflineBatchUploadService();

    @Test
    void acceptsAnUnorderedExactFileSetAndGroupsItByThePlanOrder() {
        J5OfflineBatchPlan plan = plan(1001L, 2002L);
        List<J5OfflineBatchUpload> uploads = new ArrayList<>();
        for (J5OfflineBatchPlanEvent event : plan.events().reversed()) {
            for (String name : event.expectedFileNames().reversed()) {
                uploads.add(service.capture(name, "{}".getBytes(StandardCharsets.UTF_8)));
            }
        }

        J5OfflineBatchUploadSet result = service.validate(plan, uploads);

        assertThat(result.events()).extracting(value -> value.event().providerEventId())
                .containsExactly(1001L, 2002L);
        assertThat(result.totalBytes()).isEqualTo(12L);
    }

    @Test
    void acceptsCanonical404DeclarationsInPlaceOfPhysicalFiles() {
        J5OfflineBatchPlan plan = plan(1001L);
        List<J5OfflineBatchUpload> uploads = List.of(
                service.declareUnavailable404("event-1001-statistics.json"),
                service.capture(
                        "event-1001-incidents.json",
                        "{\"incidents\":[]}".getBytes(StandardCharsets.UTF_8)),
                service.capture(
                        "event-1001-lineups.json",
                        "{\"confirmed\":false}".getBytes(StandardCharsets.UTF_8)));

        J5OfflineBatchUploadSet result = service.validate(plan, uploads);

        assertThat(result.events()).hasSize(1);
        assertThat(result.events().getFirst().statistics())
                .isEqualTo(J5LocalUnavailableEvidence.declared404());
        assertThat(new String(
                result.events().getFirst().statistics().bytes(),
                StandardCharsets.UTF_8))
                .contains(J5LocalUnavailableEvidence.OPERATOR_MARKER);
    }

    @Test
    void rejectsEmptySensitiveInvalidDuplicateAndIncompleteInputs() {
        J5OfflineBatchPlan plan = plan(1001L);
        assertError(
                () -> service.capture("event-1001-statistics.json", new byte[0]),
                J5OfflineBatchError.EMPTY_PAYLOAD);
        assertError(
                () -> service.capture(
                        "event-1001-statistics.json",
                        "Cookie: secret".getBytes(StandardCharsets.UTF_8)),
                J5OfflineBatchError.SENSITIVE_CONTENT);

        J5OfflineBatchUpload invalid = service.capture(
                "../event-1001-statistics.json", "{}".getBytes(StandardCharsets.UTF_8));
        assertError(
                () -> service.validate(plan, List.of(invalid)),
                J5OfflineBatchError.FILE_NAME_INVALID);

        J5OfflineBatchUpload duplicate = service.capture(
                "event-1001-statistics.json", "{}".getBytes(StandardCharsets.UTF_8));
        assertError(
                () -> service.validate(plan, List.of(duplicate, duplicate)),
                J5OfflineBatchError.DUPLICATE_FILE);

        assertError(
                () -> service.validate(plan, List.of(duplicate)),
                J5OfflineBatchError.FILE_SET_MISMATCH);
    }

    @Test
    void rejectsEveryNonCanonicalFileNameAndAnExtraCanonicalFile() {
        J5OfflineBatchPlan plan = plan(1001L);
        List<String> invalidNames = List.of(
                "../event-1001-statistics.json",
                "folder\\event-1001-statistics.json",
                "Event-1001-statistics.json",
                "event-01001-statistics.json",
                "event-0-statistics.json",
                "event--1-statistics.json",
                "event-1001-STATISTICS.json",
                "event-1001-statistics.JSON",
                "event-1001-details.json",
                "event-9223372036854775808-statistics.json");

        for (String invalidName : invalidNames) {
            J5OfflineBatchUpload invalid = service.capture(
                    invalidName, "{}".getBytes(StandardCharsets.UTF_8));
            assertError(
                    () -> service.validate(plan, List.of(invalid)),
                    J5OfflineBatchError.FILE_NAME_INVALID);
        }

        J5OfflineBatchUpload extra = service.capture(
                "event-2002-statistics.json", "{}".getBytes(StandardCharsets.UTF_8));
        assertError(
                () -> service.validate(plan, List.of(extra)),
                J5OfflineBatchError.FILE_SET_MISMATCH);
    }

    @Test
    void rejectsAFileAboveFiveMibAndABatchAboveTwentyFiveMib() {
        J5OfflineBatchUpload exactLimit = service.capture(
                "event-1001-statistics.json",
                new byte[RawPayloadEvidence.MAXIMUM_BYTES]);
        assertThat(exactLimit.payload().sizeBytes())
                .isEqualTo(RawPayloadEvidence.MAXIMUM_BYTES);

        assertError(
                () -> service.capture(
                        "event-1001-statistics.json",
                        new byte[RawPayloadEvidence.MAXIMUM_BYTES + 1]),
                J5OfflineBatchError.PAYLOAD_TOO_LARGE);

        J5OfflineBatchPlan plan = plan(1001L, 2002L);
        RawPayloadEvidence fiveMib = RawPayloadEvidence.capture(
                new byte[RawPayloadEvidence.MAXIMUM_BYTES]);
        List<J5OfflineBatchUpload> uploads = plan.events().stream()
                .flatMap(event -> event.expectedFileNames().stream())
                .map(name -> new J5OfflineBatchUpload(name, fiveMib))
                .toList();
        assertError(
                () -> service.validate(plan, uploads),
                J5OfflineBatchError.BATCH_TOO_LARGE);
    }

    private static J5OfflineBatchPlan plan(long... providerEventIds) {
        List<J5OfflineBatchPlanEvent> events = java.util.Arrays.stream(providerEventIds)
                .mapToObj(providerEventId -> new J5OfflineBatchPlanEvent(
                        CanonicalEventIdentity.sofascore(providerEventId).value(),
                        providerEventId,
                        providerEventId,
                        Instant.parse("2026-08-22T14:00:00Z"),
                        "Home " + providerEventId,
                        "Away " + providerEventId,
                        "b".repeat(64),
                        J5OfflineBatchPlanEvent.expectedFileNames(providerEventId)))
                .toList();
        return new J5OfflineBatchPlan(
                UUID.fromString("51000000-0000-0000-0000-000000000010"),
                LocalDate.parse("2026-08-22"),
                ZoneId.of("Europe/Paris"),
                Instant.parse("2026-08-21T22:00:00Z"),
                Instant.parse("2026-08-22T22:00:00Z"),
                Instant.parse("2026-08-22T08:00:00Z"),
                Instant.parse("2026-08-22T08:15:00Z"),
                events,
                "a".repeat(64),
                "IMPORTER " + events.size() + " MATCHS J5 HORS LIGNE " + "a".repeat(64));
    }

    private static void assertError(Runnable action, J5OfflineBatchError error) {
        assertThatThrownBy(action::run)
                .isInstanceOf(J5OfflineBatchException.class)
                .extracting(exception -> ((J5OfflineBatchException) exception).error())
                .isEqualTo(error);
    }
}
