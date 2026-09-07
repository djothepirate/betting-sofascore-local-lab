package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.domain.eventdata.EventIncident;

/** Display labels shared by the live and J5 incident tables; normalized evidence remains unchanged. */
public final class IncidentPresentation {
    private IncidentPresentation() {}

    public static String motifLabel(EventIncident incident) {
        if ("card".equals(incident.incidentType()) && incident.description().isEmpty()
                && incident.reason().filter("Professional handball"::equals).isPresent()) {
            return "Main volontaire";
        }
        return incident.motifLabel();
    }
}
