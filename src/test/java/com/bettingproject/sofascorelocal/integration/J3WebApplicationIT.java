package com.bettingproject.sofascorelocal.integration;

import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderCampaignFactory;
import com.bettingproject.sofascorelocal.domain.scheduledevents.J3AutomationData.*;
import com.bettingproject.sofascorelocal.port.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.*;
import org.springframework.test.context.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Complete local Web context, forms, serial runtime and PostgreSQL; provider is disabled and mocked. */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class J3WebApplicationIT {
    @Container static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:18.4-alpine")
            .withDatabaseName("j3_web_qualification").withUsername("sofascore_lab").withPassword("integration-test-only");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username",POSTGRES::getUsername);
        r.add("spring.datasource.password",POSTGRES::getPassword);
        r.add("spring.config.import",()->"");
        r.add("sofascore.j3.runtime-enabled",()->true);
        r.add("sofascore.enabled",()->false);
        r.add("sofascore.j3-qualification-enabled",()->false);
        r.add("sofascore.playwright.enabled",()->false);
        r.add("sofascore.live.enabled",()->false);
    }
    @Autowired MockMvc mvc;
    @Autowired J3CollectionStore collections;
    @Autowired J3AutomationStore orders;
    @MockitoBean PlaywrightProviderCampaignFactory provider;

    @Test void firstWebStartupAcceptsOneClickImportAndReadsItsDurableDateWithoutAnyNetwork() throws Exception {
        var session=new MockHttpSession();
        var dashboard=mvc.perform(get("/").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Collecter et consulter une date"))).andReturn();
        var model=dashboard.getModelAndView().getModel();
        String token=(String)model.get("localFormToken");
        UUID id=(UUID)model.get("j3OrderId");
        assertThat(orders.settings().enabled()).isTrue();
        assertThat(model.get("j3Settings")).isNotNull();
        String body="""
                {"scheduled":[{"tournament":{"id":601,"name":"J3 Web synthetic",
                "category":{"id":1,"name":"Local"},"uniqueTournament":{"id":7,"name":"Synthetic League"}},
                "timezoneEventCount":{"7200":1}}],"hasNextPage":false}
                """;
        var page=new MockMultipartFile("pageFiles","page-1.json","application/json",body.getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/j3/import").file(page).session(session).header("Host","127.0.0.1:8087")
                .param("localFormToken",token).param("orderId",id.toString()).param("date","2026-09-13"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/j3/orders/"+id));
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
        while(orders.find(id).map(o->!o.terminal()).orElse(true) && System.nanoTime()<end) Thread.sleep(25);
        assertThat(orders.find(id).orElseThrow().state()).isEqualTo(OrderState.COMPLETED);
        assertThat(collections.latest(LocalDate.of(2026,9,13)).orElseThrow().id()).isEqualTo(id);
        mvc.perform(get("/").param("j3Date","2026-09-13").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("J3 Web synthetic")));
        mvc.perform(get("/j3/collections/"+id).param("date","2026-09-13"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("J3 Web synthetic")));
        mvc.perform(get("/j3/collections/"+id+"/evidence").param("date","2026-09-13"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("trigger=MANUAL_IMPORT")))
                .andExpect(content().string(containsString("localImportPages=1")));
        assertThat(orders.recent(200)).hasSize(1);
        verifyNoInteractions(provider);
    }
}
