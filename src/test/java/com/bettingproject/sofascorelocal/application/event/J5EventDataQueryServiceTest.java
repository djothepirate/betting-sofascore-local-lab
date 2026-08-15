package com.bettingproject.sofascorelocal.application.event;

import com.bettingproject.sofascorelocal.domain.event.CanonicalEventIdentity;
import com.bettingproject.sofascorelocal.domain.event.CanonicalEventObservationView;
import com.bettingproject.sofascorelocal.domain.event.EventSourceTrace;
import com.bettingproject.sofascorelocal.domain.eventdata.J5EventDataBundle;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledEventStatus;
import com.bettingproject.sofascorelocal.domain.scheduledevents.ScheduledTeam;
import com.bettingproject.sofascorelocal.port.J5EventDataStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class J5EventDataQueryServiceTest {

    @Test
    void combinesTheExistingJ4IdentityWithTheLatestJ5Families() {
        J4EventQueryService eventQueryService = mock(J4EventQueryService.class);
        J5EventDataStore eventDataStore = mock(J5EventDataStore.class);
        J5EventDataQueryService service = new J5EventDataQueryService(
                eventQueryService,
                eventDataStore);
        var event = new CanonicalEventObservationView(
                1L,
                CanonicalEventIdentity.sofascore(900001L),
                Instant.parse("2026-08-12T14:00:00Z"),
                new ScheduledTeam(9101L, "Synthetic Home FC"),
                new ScheduledTeam(9202L, "Synthetic Away FC"),
                new ScheduledEventStatus("notstarted", Optional.empty()),
                Optional.empty(),
                EventSourceTrace.syntheticFixture(
                        "event-details-nominal",
                        "a".repeat(64),
                        "event-details-v1",
                        Instant.parse("2026-08-15T00:00:00Z")),
                "b".repeat(64),
                1L);
        var item = new J4EventSearchItem(
                event,
                event.startsAt().atZone(ZoneId.of("Europe/Paris")));
        var detail = new J4EventDetailResult(
                ZoneId.of("Europe/Paris"),
                item,
                List.of(item),
                Optional.empty());
        J5EventDataBundle bundle = J5EventDataBundle.empty();
        when(eventQueryService.findDetail(event.identity().value(), "Europe/Paris"))
                .thenReturn(Optional.of(detail));
        when(eventDataStore.findLatest(event.identity().value())).thenReturn(bundle);

        assertThat(service.find(event.identity().value(), "Europe/Paris"))
                .contains(new J5EventDataPage(
                        ZoneId.of("Europe/Paris"),
                        item,
                        bundle));
    }
}
