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
 * Per-campaign logical collection pressure sourced from persistent worker evidence.  A validated
 * 304 is still retained in its transport diagnostic, but is excluded here because the campaign
 * reused its accepted local projection and released the logical collection reservation.
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
              -- A malformed result must never hide an observed worker departure.  The positive
              -- proof below duplicates the publication boundary deliberately, so this read model
              -- remains fail-closed even if a row was inserted outside JdbcLiveCampaignStore.
              and not exists (
                  select 1
                  from live_call_result result
                  join live_call_dispatch dispatch on dispatch.attempt_id = result.attempt_id
                  join live_attempt_transport_diagnostic verified
                    on verified.attempt_id = result.attempt_id
                   and verified.campaign_id = call.campaign_id
                   and verified.endpoint_type = call.endpoint
                  join live_campaign campaign on campaign.campaign_id = call.campaign_id
                  where result.attempt_id = call.attempt_id
                    and campaign.policy_version in ('live-v9','live-v10')
                    and result.outcome = 'NOT_MODIFIED'
                    and result.scope = 'NONE'
                    and result.code = 'HTTP_304'
                    and result.successful = false
                    and result.parser_version is null
                    and result.projection_json is null
                    and result.projection_version is null
                    and result.projection_sha256 is null
                    and result.completeness_status is null
                    and result.completeness_score is null
                    and result.canonical_observation_id is null
                    and result.detail_observation_id is null
                    and result.j5_observation_id is null
                    and result.normalized_sha256 is null
                    and verified.transport_phase = 'COMPLETE'
                    and verified.response_complete = true
                    and verified.http_status = 304
                    and verified.requested_at is not null
                    and verified.headers_received_at is not null
                    and dispatch.authorized_at <= verified.requested_at
                    and not exists (
                        select 1 from live_call_receipt receipt where receipt.attempt_id = result.attempt_id
                    )
              )
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
