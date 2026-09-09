package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.domain.eventdata.J5EventDataObservationView;

@FunctionalInterface
public interface LineupCountryOverlayResolver {

    LineupCountryOverlay resolve(J5EventDataObservationView observation);

    static LineupCountryOverlayResolver none() {
        return ignored -> LineupCountryOverlay.empty();
    }
}
