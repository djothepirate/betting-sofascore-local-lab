package com.bettingproject.sofascorelocal.application.event;

import com.bettingproject.sofascorelocal.domain.eventdata.J5EventDataBundle;

import java.time.ZoneId;
import java.util.Objects;

public record J5EventDataPage(
        ZoneId zoneId,
        J4EventSearchItem current,
        J5EventDataBundle data) {

    public J5EventDataPage {
        zoneId = Objects.requireNonNull(zoneId, "zoneId");
        current = Objects.requireNonNull(current, "current");
        data = Objects.requireNonNull(data, "data");
    }
}
