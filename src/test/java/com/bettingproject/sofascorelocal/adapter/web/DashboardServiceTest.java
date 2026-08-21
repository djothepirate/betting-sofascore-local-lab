package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.adapter.sofascore.SofascoreEndpointCatalog;
import com.bettingproject.sofascorelocal.application.ConnectorGate;
import com.bettingproject.sofascorelocal.application.fixture.OfflineFixtureCorpusService;
import com.bettingproject.sofascorelocal.application.network.J3ProviderQualificationPolicy;
import com.bettingproject.sofascorelocal.config.SofascoreProperties;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardServiceTest {

    @Test
    void exposesTheVerifiedOfflineCorpusEvenWhenPostgresqlIsUnavailable() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject("select 1", Integer.class))
                .thenThrow(new DataAccessResourceFailureException("offline test database"));
        DashboardService service = new DashboardService(
                new SofascoreProperties(),
                new SofascoreEndpointCatalog(),
                new ConnectorGate(),
                jdbcTemplate,
                new OfflineFixtureCorpusService(),
                new J3ProviderQualificationPolicy(new SofascoreProperties()));

        DashboardView dashboard = service.load();

        assertThat(dashboard.connectorMode()).isEqualTo("LOCKED_OFFLINE_J3_POLICY");
        assertThat(dashboard.configurationEnabled()).isFalse();
        assertThat(dashboard.databaseState()).isEqualTo("UNAVAILABLE");
        assertThat(dashboard.fixtureCorpus())
                .isEqualTo(new DashboardView.FixtureCorpusView(
                        "AVAILABLE_OFFLINE",
                        "SCHEDULED_EVENTS",
                        "SYNTHETIC",
                        true,
                        "scheduled-events-v1",
                        12,
                        12,
                        7,
                        4,
                        1,
                        0));
        assertThat(dashboard.endpoints()).hasSize(8);
    }
}
