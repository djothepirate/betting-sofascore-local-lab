package com.bettingproject.sofascorelocal.integration;

import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderCampaignFactory;
import com.bettingproject.sofascorelocal.domain.scheduledevents.J3AutomationData.*;
import com.bettingproject.sofascorelocal.port.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import static com.bettingproject.sofascorelocal.domain.scheduledevents.J3AutomationData.ZONE;
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
        var existingOrderIds=orders.recent(200).stream().map(Order::id).toList();
        var dashboard=mvc.perform(get("/").session(session)).andExpect(status().isOk())
                .andExpect(header().string("Referrer-Policy","same-origin"))
                .andExpect(content().string(containsString("Collecter et consulter une date")))
                .andExpect(content().string(containsString(
                        ">B. Importer et valider J3 — ZÉRO APPEL</button>"))).andReturn();
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
                .header("Origin","http://127.0.0.1:8087").header("Sec-Fetch-Site","same-origin")
                .header("Sec-Fetch-Dest","document")
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
        assertThat(orders.recent(200)).extracting(Order::id).containsExactlyInAnyOrderElementsOf(
                java.util.stream.Stream.concat(existingOrderIds.stream(),java.util.stream.Stream.of(id)).toList());
        verifyNoInteractions(provider);
    }

    @ParameterizedTest @ValueSource(strings={"localhost:8087","127.0.0.1:8087"})
    void browserFormsSavePreferencesAndCreateReviseCancelPlansInPostgres(String host) throws Exception {
        var session=new MockHttpSession();
        var model=dashboardForm(host,session,"/");
        Settings initial=(Settings)model.get("j3Settings");
        mvc.perform(browserPost("/j3/settings",host,session,model)
                .param("revision",Long.toString(initial.revision())).param("mode","STARTUP_OR_DAY_CHANGE")
                .param("time",""))
                .andExpect(redirectedUrl("/#j3-automation"));
        assertThat(orders.settings().enabled()).isFalse();
        assertThat(orders.settings().revision()).isEqualTo(initial.revision()+1);
        model=dashboardForm(host,session,"/dashboard");
        assertThat(model.get("j3Settings")).isEqualTo(orders.settings());

        UUID rule=(UUID)model.get("j3RuleId");
        LocalDate target=LocalDate.now(ZONE).plusDays(2);
        LocalDateTime at=target.atTime(12,0);
        mvc.perform(browserPost("/j3/plans",host,session,model)
                .param("ruleId",rule.toString()).param("revision","1").param("date",target.toString())
                .param("at",at.toString()).param("offset",""))
                .andExpect(redirectedUrl("/#j3-automation"));
        Order first=orders.recent(200).stream().filter(o->rule.equals(o.ruleId())).findFirst().orElseThrow();
        assertThat(first.state()).isEqualTo(OrderState.FUTURE);
        assertThat(first.date()).isEqualTo(target);
        assertThat(first.dueAt()).isEqualTo(at.atZone(ZONE).toInstant());

        model=dashboardForm(host,session,"/");
        mvc.perform(browserPost("/j3/plans",host,session,model)
                .param("ruleId",rule.toString()).param("revision","2").param("date",target.toString())
                .param("at",at.plusHours(1).toString()).param("offset",""))
                .andExpect(redirectedUrl("/#j3-automation"));
        Order revised=orders.recent(200).stream().filter(o->rule.equals(o.ruleId()) && o.ruleRevision()==2)
                .findFirst().orElseThrow();
        assertThat(orders.find(first.id()).orElseThrow().state()).isEqualTo(OrderState.CANCELLED);
        assertThat(revised.state()).isEqualTo(OrderState.FUTURE);
        assertThat(revised.dueAt()).isEqualTo(at.plusHours(1).atZone(ZONE).toInstant());

        model=dashboardForm(host,session,"/");
        mvc.perform(browserPost("/j3/plans/"+revised.id()+"/cancel",host,session,model))
                .andExpect(redirectedUrl("/#j3-automation"));
        assertThat(orders.find(revised.id()).orElseThrow().state()).isEqualTo(OrderState.CANCELLED);

        model=dashboardForm(host,session,"/");
        mvc.perform(browserPost("/j3/settings",host,session,model)
                .param("revision",Long.toString(orders.settings().revision())).param("enabled","true")
                .param("mode","DAILY_AT").param("time","08:30"))
                .andExpect(redirectedUrl("/#j3-automation"));
        assertThat(orders.settings().enabled()).isTrue();
        assertThat(orders.settings().mode()).isEqualTo(Mode.DAILY_AT);
        assertThat(orders.settings().dailyTime()).isEqualTo(LocalTime.of(8,30));
        model=dashboardForm(host,session,"/");
        assertThat(model.get("j3Settings")).isEqualTo(orders.settings());
        mvc.perform(browserPost("/j3/settings",host,session,model)
                .param("revision",Long.toString(orders.settings().revision())).param("enabled","true")
                .param("mode","STARTUP_OR_DAY_CHANGE").param("time",""))
                .andExpect(redirectedUrl("/#j3-automation"));
        verifyNoInteractions(provider);
    }

    private Map<String,Object> dashboardForm(String host,MockHttpSession session,String path) throws Exception {
        return mvc.perform(get(path).header("Host",host).session(session))
                .andExpect(status().isOk()).andExpect(header().string("Referrer-Policy","same-origin"))
                .andReturn().getModelAndView().getModel();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder browserPost(
            String path,String host,MockHttpSession session,Map<String,Object> model) {
        return post(path).header("Host",host).header("Origin","http://"+host)
                .header("Sec-Fetch-Site","same-origin").header("Sec-Fetch-Dest","document")
                .session(session).param("localFormToken",(String)model.get("localFormToken"));
    }
}
