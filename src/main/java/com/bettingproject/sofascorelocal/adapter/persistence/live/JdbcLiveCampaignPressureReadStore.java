package com.bettingproject.sofascorelocal.adapter.persistence.live;

import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;
import com.bettingproject.sofascorelocal.port.LiveCampaignPressureReadStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Per-campaign pressure view sourced only from persistent worker REQUEST_SENT evidence.
 * This intentionally does not read reservations, the shared resilience ledger, or any provider data.
 */
@Repository
public class JdbcLiveCampaignPressureReadStore implements LiveCampaignPressureReadStore {
    private static final String OBSERVED_DEPARTURES_SQL = """
            select call.endpoint, diagnostic.requested_at
            from live_call call
            join live_attempt_transport_diagnostic diagnostic
              on diagnostic.attempt_id = call.attempt_id
             and diagnostic.campaign_id = call.campaign_id
             and diagnostic.endpoint_type = call.endpoint
            where diagnostic.campaign_id = ?
              and diagnostic.requested_at is not null
            order by diagnostic.requested_at asc, call.ordinal asc
            """;

    private final JdbcTemplate jdbc;

    public JdbcLiveCampaignPressureReadStore(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    @Transactional(readOnly = true)
    public Pressure read(UUID campaignId) {
        Objects.requireNonNull(campaignId);
        return summarize(jdbc.query(OBSERVED_DEPARTURES_SQL, (row, index) -> new ObservedDeparture(
                SofascoreEndpointType.valueOf(row.getString("endpoint")),
                Objects.requireNonNull(row.getTimestamp("requested_at")).toInstant()), campaignId));
    }

    static Pressure summarize(List<ObservedDeparture> observedDepartures) {
        Objects.requireNonNull(observedDepartures);
        if (observedDepartures.isEmpty()) return Pressure.noObservedDepartures();
        List<ObservedDeparture> sorted = observedDepartures.stream()
                .map(Objects::requireNonNull)
                .peek(departure -> {
                    if (!LIVE_ENDPOINTS.contains(departure.endpoint()))
                        throw new IllegalArgumentException("LIVE_PRESSURE_ENDPOINT_INVALID");
                })
                .sorted(Comparator.comparing(ObservedDeparture::at))
                .toList();
        Map<SofascoreEndpointType, Integer> counts = new EnumMap<>(SofascoreEndpointType.class);
        LIVE_ENDPOINTS.forEach(endpoint -> counts.put(endpoint, 0));
        sorted.forEach(departure -> counts.compute(departure.endpoint(), (endpoint, count) -> count + 1));
        return new Pressure(sorted.size(), sorted.getFirst().at(), sorted.getLast().at(),
                peak(sorted, Duration.ofMinutes(1)), peak(sorted, Duration.ofMinutes(5)), LIVE_ENDPOINTS.stream()
                        .map(endpoint -> new Family(endpoint, counts.get(endpoint))).toList());
    }

    private static Peak peak(List<ObservedDeparture> sorted, Duration window) {
        int left = 0;
        Peak peak = Peak.empty();
        for (int right = 0; right < sorted.size(); right++) {
            Instant lowerExclusive = sorted.get(right).at().minus(window);
            while (!sorted.get(left).at().isAfter(lowerExclusive)) left++;
            int count = right - left + 1;
            if (count > peak.observedDepartures()) peak = new Peak(count, sorted.get(right).at());
        }
        return peak;
    }

    static record ObservedDeparture(SofascoreEndpointType endpoint, Instant at) {
        ObservedDeparture {
            Objects.requireNonNull(endpoint);
            Objects.requireNonNull(at);
        }
    }
}
