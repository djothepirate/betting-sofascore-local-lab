package com.bettingproject.sofascorelocal.port;

import com.bettingproject.sofascorelocal.domain.provider.SofascoreEndpointType;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Read-only local evidence for a campaign's worker-observed REQUEST_SENT timestamps.
 * Reservations and traffic outside the Lab are deliberately out of scope.
 */
public interface LiveCampaignPressureReadStore {
    List<SofascoreEndpointType> LIVE_ENDPOINTS = List.of(
            SofascoreEndpointType.EVENT_DETAILS,
            SofascoreEndpointType.EVENT_STATISTICS,
            SofascoreEndpointType.EVENT_INCIDENTS,
            SofascoreEndpointType.EVENT_LINEUPS);

    Pressure read(UUID campaignId);

    static LiveCampaignPressureReadStore none() {
        return campaignId -> Pressure.noObservedDepartures();
    }

    record Pressure(int observedDepartures, Instant firstObservedDepartureAt, Instant lastObservedDepartureAt,
                    Peak oneMinutePeak, Peak fiveMinutePeak, List<Family> families) {
        public Pressure {
            if (observedDepartures < 0) throw new IllegalArgumentException("LIVE_PRESSURE_COUNT_INVALID");
            families = List.copyOf(Objects.requireNonNull(families));
            if (families.size() != LIVE_ENDPOINTS.size()
                    || !families.stream().map(Family::endpoint).toList().equals(LIVE_ENDPOINTS))
                throw new IllegalArgumentException("LIVE_PRESSURE_FAMILIES_INVALID");
            if (families.stream().mapToInt(Family::observedDepartures).sum() != observedDepartures)
                throw new IllegalArgumentException("LIVE_PRESSURE_FAMILY_TOTAL_INVALID");
            Objects.requireNonNull(oneMinutePeak);
            Objects.requireNonNull(fiveMinutePeak);
            if (oneMinutePeak.observedDepartures() > observedDepartures
                    || fiveMinutePeak.observedDepartures() > observedDepartures)
                throw new IllegalArgumentException("LIVE_PRESSURE_PEAK_INVALID");
            if (observedDepartures == 0) {
                if (firstObservedDepartureAt != null || lastObservedDepartureAt != null
                        || oneMinutePeak.observedDepartures() != 0 || fiveMinutePeak.observedDepartures() != 0)
                    throw new IllegalArgumentException("LIVE_PRESSURE_EMPTY_INVALID");
            } else if (firstObservedDepartureAt == null || lastObservedDepartureAt == null
                    || lastObservedDepartureAt.isBefore(firstObservedDepartureAt)) {
                throw new IllegalArgumentException("LIVE_PRESSURE_TIMES_INVALID");
            }
        }

        public static Pressure noObservedDepartures() {
            return new Pressure(0, null, null, Peak.empty(), Peak.empty(), LIVE_ENDPOINTS.stream()
                    .map(endpoint -> new Family(endpoint, 0)).toList());
        }
    }

    record Peak(int observedDepartures, Instant windowEndAt) {
        public Peak {
            if (observedDepartures < 0 || observedDepartures == 0 && windowEndAt != null
                    || observedDepartures > 0 && windowEndAt == null)
                throw new IllegalArgumentException("LIVE_PRESSURE_PEAK_INVALID");
        }

        public static Peak empty() {
            return new Peak(0, null);
        }
    }

    record Family(SofascoreEndpointType endpoint, int observedDepartures) {
        public Family {
            if (!LIVE_ENDPOINTS.contains(Objects.requireNonNull(endpoint)) || observedDepartures < 0)
                throw new IllegalArgumentException("LIVE_PRESSURE_FAMILY_INVALID");
        }
    }
}
