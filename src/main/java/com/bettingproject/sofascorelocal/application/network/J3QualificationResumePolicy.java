package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsParseStatus;
import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsV1Parser;
import com.bettingproject.sofascorelocal.domain.provider.J3ProviderQualificationSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.J3StoredQualificationPage;
import com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsProviderPageRequest;
import com.bettingproject.sofascorelocal.port.J3QualificationCheckpointStore;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Authorizes only the explicitly approved continuation of the consumed J3 sequence.
 * The historical page-one payload is reparsed locally; this policy never performs transport.
 */
@Component
public class J3QualificationResumePolicy {

    public static final int RESUME_FIRST_PAGE = 2;

    private final J3ProviderQualificationPolicy providerPolicy;
    private final J3QualificationCheckpointStore checkpointStore;
    private final ScheduledEventsV1Parser parser;

    public J3QualificationResumePolicy(
            J3ProviderQualificationPolicy providerPolicy,
            J3QualificationCheckpointStore checkpointStore) {
        this(providerPolicy, checkpointStore, new ScheduledEventsV1Parser());
    }

    J3QualificationResumePolicy(
            J3ProviderQualificationPolicy providerPolicy,
            J3QualificationCheckpointStore checkpointStore,
            ScheduledEventsV1Parser parser) {
        this.providerPolicy = Objects.requireNonNull(providerPolicy, "providerPolicy");
        this.checkpointStore = Objects.requireNonNull(checkpointStore, "checkpointStore");
        this.parser = Objects.requireNonNull(parser, "parser");
    }

    public J3ProviderQualificationSnapshot snapshot() {
        J3ProviderQualificationSnapshot provider = providerPolicy.snapshot();
        if (!provider.available()) {
            return provider;
        }

        List<J3StoredQualificationPage> pages;
        try {
            pages = checkpointStore.findStoredPages(
                    ScheduledEventsProviderPageRequest.QUALIFICATION_DATE);
        }
        catch (RuntimeException exception) {
            return blocked("J3_CHECKPOINT_READ_UNAVAILABLE");
        }

        List<J3StoredQualificationPage> pageOne = pages.stream()
                .filter(page -> page.page() == ScheduledEventsProviderPageRequest.FIRST_PAGE)
                .toList();
        if (pageOne.isEmpty()) {
            return blocked("J3_PAGE_1_CHECKPOINT_MISSING");
        }
        if (pageOne.size() != 1) {
            return blocked("J3_PAGE_1_CHECKPOINT_AMBIGUOUS");
        }
        if (pages.stream().anyMatch(page -> page.page() >= RESUME_FIRST_PAGE)) {
            return blocked("J3_RESUME_ALREADY_ATTEMPTED");
        }

        J3StoredQualificationPage checkpoint = pageOne.getFirst();
        if (checkpoint.httpStatus() < 200 || checkpoint.httpStatus() >= 300) {
            return blocked("J3_PAGE_1_CHECKPOINT_HTTP_INVALID");
        }
        var parsing = parser.parseTransportResponse(checkpoint.toTransportResponse());
        if (parsing.status() != ScheduledEventsParseStatus.PARSED) {
            return blocked("J3_PAGE_1_CHECKPOINT_NOT_PARSEABLE");
        }
        if (!parsing.page().orElseThrow().hasNextPage()) {
            return blocked("J3_PAGE_1_CHECKPOINT_HAS_NO_NEXT_PAGE");
        }

        return J3ProviderQualificationSnapshot.available(
                provider.providerOrigin(),
                RESUME_FIRST_PAGE);
    }

    private static J3ProviderQualificationSnapshot blocked(String blocker) {
        return J3ProviderQualificationSnapshot.blocked(List.of(blocker));
    }
}
