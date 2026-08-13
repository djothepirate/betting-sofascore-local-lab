package com.bettingproject.sofascorelocal.application.network;

import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsParseStatus;
import com.bettingproject.sofascorelocal.adapter.sofascore.scheduledevents.ScheduledEventsV1Parser;
import com.bettingproject.sofascorelocal.domain.provider.J3ProviderQualificationSnapshot;
import com.bettingproject.sofascorelocal.domain.provider.J3StoredQualificationPage;
import com.bettingproject.sofascorelocal.domain.provider.ScheduledEventsProviderPageRequest;
import com.bettingproject.sofascorelocal.port.J3QualificationCheckpointStore;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Authorizes only the explicitly approved continuation of the consumed J3 sequence.
 * The historical page-one and page-two payloads are reparsed locally; this policy never performs
 * transport.
 */
@Component
public class J3QualificationResumePolicy {

    public static final int RESUME_FIRST_PAGE = 3;

    private final J3ProviderQualificationPolicy providerPolicy;
    private final J3QualificationCheckpointStore checkpointStore;
    private final ScheduledEventsV1Parser parser;

    @Autowired
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

        if (pages.stream().anyMatch(page -> page.page() >= RESUME_FIRST_PAGE)) {
            return blocked("J3_RESUME_ALREADY_ATTEMPTED");
        }

        for (int checkpointPage = ScheduledEventsProviderPageRequest.FIRST_PAGE;
                checkpointPage < RESUME_FIRST_PAGE;
                checkpointPage++) {
            J3ProviderQualificationSnapshot invalid = validateCheckpoint(
                    pages,
                    checkpointPage);
            if (invalid != null) {
                return invalid;
            }
        }

        return J3ProviderQualificationSnapshot.available(
                provider.providerOrigin(),
                RESUME_FIRST_PAGE);
    }

    private J3ProviderQualificationSnapshot validateCheckpoint(
            List<J3StoredQualificationPage> pages,
            int checkpointPage) {
        List<J3StoredQualificationPage> matches = pages.stream()
                .filter(page -> page.page() == checkpointPage)
                .toList();
        String blockerPrefix = "J3_PAGE_" + checkpointPage + "_CHECKPOINT_";
        if (matches.isEmpty()) {
            return blocked(blockerPrefix + "MISSING");
        }
        if (matches.size() != 1) {
            return blocked(blockerPrefix + "AMBIGUOUS");
        }

        J3StoredQualificationPage checkpoint = matches.getFirst();
        if (checkpoint.httpStatus() < 200 || checkpoint.httpStatus() >= 300) {
            return blocked(blockerPrefix + "HTTP_INVALID");
        }
        var parsing = parser.parseTransportResponse(checkpoint.toTransportResponse());
        if (parsing.status() != ScheduledEventsParseStatus.PARSED) {
            return blocked(blockerPrefix + "NOT_PARSEABLE");
        }
        if (!parsing.page().orElseThrow().hasNextPage()) {
            return blocked(blockerPrefix + "HAS_NO_NEXT_PAGE");
        }
        return null;
    }

    private static J3ProviderQualificationSnapshot blocked(String blocker) {
        return J3ProviderQualificationSnapshot.blocked(List.of(blocker));
    }
}
