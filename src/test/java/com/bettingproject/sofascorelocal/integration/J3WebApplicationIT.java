package com.bettingproject.sofascorelocal.integration;

import com.bettingproject.sofascorelocal.application.network.playwright.PlaywrightProviderCampaignFactory;
import com.bettingproject.sofascorelocal.domain.scheduledevents.J3AutomationData.*;
import com.bettingproject.sofascorelocal.port.*;
import org.attoparser.config.ParseConfiguration;
import org.attoparser.dom.*;
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
        assertThat(orders.find(first.id()).orElseThrow().reason()).isEqualTo("PLAN_REVISED");
        assertThat(revised.state()).isEqualTo(OrderState.FUTURE);
        assertThat(revised.dueAt()).isEqualTo(at.plusHours(1).atZone(ZONE).toInstant());

        model=dashboardForm(host,session,"/");
        mvc.perform(browserPost("/j3/plans/"+revised.id()+"/cancel",host,session,model))
                .andExpect(redirectedUrl("/#j3-automation"));
        assertThat(orders.find(revised.id()).orElseThrow().state()).isEqualTo(OrderState.CANCELLED);
        assertThat(orders.find(revised.id()).orElseThrow().reason()).isEqualTo("OPERATOR_CANCELLED");
        mvc.perform(get("/").header("Host",host).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Remplacée par une nouvelle version de cet horaire.")))
                .andExpect(content().string(containsString("Annulée à votre demande.")));

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

    @ParameterizedTest @ValueSource(strings={"localhost:8087","127.0.0.1:8087"})
    void invalidPastPlanReturnsToDashboardWithoutChangingOrders(String host) throws Exception {
        var session=new MockHttpSession();
        var model=dashboardForm(host,session,"/");
        var before=orders.recent(200);
        var settings=orders.settings();
        LocalDate target=LocalDate.now(ZONE).minusDays(12);
        var latest=collections.latest(target);
        UUID rule=(UUID)model.get("j3RuleId");
        LocalDateTime past=LocalDateTime.now(ZONE).minusHours(1).withSecond(0).withNano(0);
        var rejected=mvc.perform(browserPost("/j3/plans",host,session,model)
                .param("ruleId",rule.toString()).param("revision","1")
                .param("date",target.toString()).param("at",past.toString()).param("offset",""))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/?j3Date="+target+"#j3-automation"))
                .andExpect(flash().attribute("j3AutomationError",
                        "La date et l’heure de déclenchement sont déjà passées. Choisissez un horaire futur à Paris."))
                .andReturn();
        assertThat(orders.recent(200)).isEqualTo(before);
        assertThat(orders.settings()).isEqualTo(settings);
        assertThat(collections.latest(target)).isEqualTo(latest);
        var retryPage=mvc.perform(get("/").header("Host",host).session(session).param("j3Date",target.toString())
                .flashAttrs(rejected.getFlashMap()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Choisissez un horaire futur à Paris.")))
                .andReturn();
        var html=html(retryPage);
        assertAutomationAlert(html);
        var dateField=element(html,"input","id","j3-plan-date");
        assertThat(dateField.getAttributeValue("value")).isEqualTo(target.toString());
        assertThat(element(html,"input","id","j3-plan-at").getAttributeValue("value")).isEqualTo(past.toString());
        assertThat(element(dateField.getParent(),"input","name","ruleId").getAttributeValue("value")).isEqualTo(rule.toString());
        var retry=retryPage.getModelAndView().getModel();
        assertThat(retry.get("localFormToken")).isNotEqualTo(model.get("localFormToken"));

        // Correct only the trigger: collecting a historical calendar remains allowed.
        LocalDateTime future=LocalDate.now(ZONE).plusDays(2).atTime(12,35);
        mvc.perform(browserPost("/j3/plans",host,session,retry)
                .param("ruleId",rule.toString()).param("revision","1").param("date",target.toString())
                .param("at",future.toString()).param("offset",""))
                .andExpect(redirectedUrl("/#j3-automation"));
        Order first=orders.recent(200).stream().filter(o->rule.equals(o.ruleId())).findFirst().orElseThrow();
        assertThat(first.date()).isEqualTo(target);
        assertThat(first.dueAt()).isEqualTo(future.atZone(ZONE).toInstant());
        assertThat(first.state()).isEqualTo(OrderState.FUTURE);

        // A rejected edit must not cancel/replace the existing valid plan.
        var beforeEdit=orders.recent(200);
        model=dashboardForm(host,session,"/");
        String offset=past.atZone(ZONE).getOffset().toString();
        var rejectedEdit=mvc.perform(browserPost("/j3/plans",host,session,model)
                .param("ruleId",rule.toString()).param("revision","2").param("date",target.plusDays(1).toString())
                .param("at",past.toString()).param("offset",offset))
                .andExpect(redirectedUrl("/?j3Date="+target.plusDays(1)+"#j3-automation"))
                .andExpect(flash().attributeExists("j3AutomationError")).andReturn();
        assertThat(orders.recent(200)).isEqualTo(beforeEdit);
        retryPage=mvc.perform(get("/").header("Host",host).session(session).param("j3Date",target.plusDays(1).toString())
                .flashAttrs(rejectedEdit.getFlashMap()))
                .andExpect(status().isOk())
                .andReturn();
        html=html(retryPage);
        var edit=elements(html).stream().filter(e->e.elementNameMatches("details") && e.hasAttribute("open")).toList();
        assertThat(edit).hasSize(1);
        assertThat(element(edit.getFirst(),"input","name","ruleId").getAttributeValue("value")).isEqualTo(rule.toString());
        assertThat(element(edit.getFirst(),"input","name","date").getAttributeValue("value")).isEqualTo(target.plusDays(1).toString());
        assertThat(element(edit.getFirst(),"input","name","at").getAttributeValue("value")).isEqualTo(past.toString());
        assertThat(element(edit.getFirst(),"option","value",offset).hasAttribute("selected")).isTrue();
        assertThat(element(html,"input","id","j3-plan-at").getAttributeValue("value")).isNullOrEmpty();
        retry=retryPage.getModelAndView().getModel();
        mvc.perform(browserPost("/j3/plans",host,session,retry)
                .param("ruleId",rule.toString()).param("revision","2").param("date",target.plusDays(1).toString())
                .param("at",future.plusHours(1).toString()).param("offset",""))
                .andExpect(redirectedUrl("/#j3-automation"));
        assertThat(orders.find(first.id()).orElseThrow().state()).isEqualTo(OrderState.CANCELLED);
        var revised=orders.recent(200).stream().filter(o->rule.equals(o.ruleId()) && o.ruleRevision()==2).findFirst().orElseThrow();
        assertThat(revised.state()).isEqualTo(OrderState.FUTURE);
        assertThat(revised.date()).isEqualTo(target.plusDays(1));
        assertThat(revised.dueAt()).isEqualTo(future.plusHours(1).atZone(ZONE).toInstant());
        model=dashboardForm(host,session,"/");
        mvc.perform(browserPost("/j3/plans/"+revised.id()+"/cancel",host,session,model))
                .andExpect(redirectedUrl("/#j3-automation"));
        assertThat(orders.find(revised.id()).orElseThrow().state()).isEqualTo(OrderState.CANCELLED);
        assertThat(orders.settings()).isEqualTo(settings);
        assertThat(collections.latest(target)).isEqualTo(latest);
        verifyNoInteractions(provider);
    }

    @Test void invalidCalendarOrTriggerRendersAnAlertWithoutAnyDatabaseWrite() throws Exception {
        String host="localhost:8087";
        var session=new MockHttpSession();
        var before=orders.recent(200);
        var settings=orders.settings();
        for(String[] fields:List.of(new String[]{"2026-09-14",""},new String[]{"2026-09-14","2026-02-30T12:35"},
                new String[]{"2026-09-14","2028-02-31T10:00"},
                new String[]{"","2026-09-14T12:35"},new String[]{"2026-02-30","2026-09-14T12:35"})) {
            var model=dashboardForm(host,session,"/");
            var rejected=mvc.perform(browserPost("/j3/plans",host,session,model)
                    .param("ruleId",model.get("j3RuleId").toString()).param("revision","1")
                    .param("date",fields[0]).param("at",fields[1]))
                    .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("j3AutomationError")).andReturn();
            var getDashboard=get("/").header("Host",host).session(session).flashAttrs(rejected.getFlashMap());
            if(fields[0].equals("2026-09-14"))getDashboard.param("j3Date",fields[0]);
            assertAutomationAlert(html(mvc.perform(getDashboard).andExpect(status().isOk()).andReturn()));
            assertThat(orders.recent(200)).isEqualTo(before);
            assertThat(orders.settings()).isEqualTo(settings);
        }
        verifyNoInteractions(provider);
    }

    @Test void rangeLimitsProtectNewPlansAndRevisionsBeforeAnyLedgerMutation() throws Exception {
        String host="localhost:8087";
        var session=new MockHttpSession();
        LocalDate today=LocalDate.now(ZONE),last=today.plusMonths(12);
        LocalDateTime future=today.plusDays(2).atTime(16,15);
        var settings=orders.settings();
        var model=dashboardForm(host,session,"/");
        UUID rule=(UUID)model.get("j3RuleId");
        var inputs=List.of(new String[]{"9999-01-31",future.toString(),"La date à collecter"},
                new String[]{"1999-12-31",future.toString(),"La date à collecter"},
                new String[]{last.plusDays(1).toString(),future.toString(),"La date à collecter"},
                new String[]{today.toString(),"9999-09-14T15:00","La programmation"},
                new String[]{today.toString(),last.plusDays(1).atStartOfDay().toString(),"La programmation"});
        for(int revision:List.of(1,2)) {
            var before=orders.recent(200);
            for(String[] input:inputs) {
                model=dashboardForm(host,session,"/");
                var rejected=mvc.perform(browserPost("/j3/plans",host,session,model)
                        .param("ruleId",rule.toString()).param("revision",Integer.toString(revision))
                        .param("date",input[0]).param("at",input[1]))
                        .andExpect(status().is3xxRedirection())
                        .andExpect(flash().attribute("j3AutomationError",org.hamcrest.Matchers.startsWith(input[2])))
                        .andReturn();
                var retry=html(mvc.perform(get("/").header("Host",host).session(session)
                        .flashAttrs(rejected.getFlashMap())).andExpect(status().isOk()).andReturn());
                assertAutomationAlert(retry);
                Element form=(Element)element(retry,"input","value",rule.toString()).getParent();
                assertThat(element(form,"input","name","date").getAttributeValue("value")).isEqualTo(input[0]);
                assertThat(element(form,"input","name","at").getAttributeValue("value")).isEqualTo(input[1]);
                assertThat(element(form,"input","name","date").getAttributeValue("min")).isEqualTo("2000-01-01");
                assertThat(element(form,"input","name","date").getAttributeValue("max")).isEqualTo(last.toString());
                assertThat(element(form,"input","name","at").getAttributeValue("min")).isEqualTo(today.atStartOfDay().toString());
                assertThat(element(form,"input","name","at").getAttributeValue("max")).isEqualTo(last.atTime(23,59).toString());
                if(revision==2)assertThat(((Element)form.getParent()).hasAttribute("open")).isTrue();
                assertThat(orders.recent(200)).isEqualTo(before);
                assertThat(orders.settings()).isEqualTo(settings);
            }
            if(revision==1) {
                model=dashboardForm(host,session,"/");
                // Both upper boundaries are inclusive; subsequent invalid edits must preserve this plan.
                mvc.perform(browserPost("/j3/plans",host,session,model)
                        .param("ruleId",rule.toString()).param("revision","1").param("date",last.toString())
                        .param("at",last.atTime(23,59).toString()))
                        .andExpect(redirectedUrl("/#j3-automation"));
            }
        }
        var planned=orders.recent(200).stream().filter(o->rule.equals(o.ruleId())).toList();
        assertThat(planned).hasSize(1);
        assertThat(planned.getFirst().state()).isEqualTo(OrderState.FUTURE);
        assertThat(planned.getFirst().date()).isEqualTo(last);
        assertThat(planned.getFirst().dueAt()).isEqualTo(last.atTime(23,59).atZone(ZONE).toInstant());
        model=dashboardForm(host,session,"/");
        mvc.perform(browserPost("/j3/plans/"+planned.getFirst().id()+"/cancel",host,session,model))
                .andExpect(redirectedUrl("/#j3-automation"));
        verifyNoInteractions(provider);
    }

    @Test void manualProviderAndImportRejectOutOfRangeDatesWhileStoredHistoryRemainsReadable() throws Exception {
        String host="localhost:8087";
        var session=new MockHttpSession();
        var before=orders.recent(200);
        var settings=orders.settings();
        LocalDate last=LocalDate.now(ZONE).plusMonths(12);
        for(String path:List.of("/j3/collect","/j3/import")) {
            for(String date:List.of("1999-12-31",last.plusDays(1).toString(),"9999-01-31")) {
                var page=mvc.perform(get("/").header("Host",host).session(session)).andExpect(status().isOk()).andReturn();
                var form=html(page);
                assertThat(element(form,"input","id","j3-date").getAttributeValue("min")).isEqualTo("2000-01-01");
                assertThat(element(form,"input","id","j3-date").getAttributeValue("max")).isEqualTo(last.toString());
                var model=page.getModelAndView().getModel();
                var request=multipart(path).file(new MockMultipartFile("pageFiles","page-1.json","application/json",
                        "{\"scheduled\":[],\"hasNextPage\":false}".getBytes(StandardCharsets.UTF_8)))
                        .header("Host",host).header("Origin","http://"+host).session(session)
                        .param("localFormToken",model.get("localFormToken").toString())
                        .param("orderId",model.get("j3OrderId").toString()).param("date",date);
                mvc.perform(request).andExpect(status().is3xxRedirection())
                        .andExpect(flash().attribute("j3Message",org.hamcrest.Matchers.startsWith("La date à collecter")));
                assertThat(orders.recent(200)).isEqualTo(before);
                assertThat(orders.settings()).isEqualTo(settings);
                // Consultation is not a request to collect or schedule; no date bound is imposed on a GET.
                mvc.perform(get("/").header("Host",host).session(session).param("j3Date",date))
                        .andExpect(status().isOk());
            }
        }
        verifyNoInteractions(provider);
    }

    private static Document html(org.springframework.test.web.servlet.MvcResult response) throws Exception {
        return new DOMMarkupParser(ParseConfiguration.htmlConfiguration())
                .parse(response.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
    private static List<Element> elements(INestableNode node) {
        var elements=new ArrayList<Element>();
        for(var child:node.getChildren())if(child instanceof Element element) {
            elements.add(element);elements.addAll(elements(element));
        }
        return elements;
    }
    private static Element element(INestableNode node,String tag,String attribute,String value) {
        return elements(node).stream().filter(e->e.elementNameMatches(tag) && value.equals(e.getAttributeValue(attribute)))
                .findFirst().orElseThrow(()->new AssertionError("Missing HTML element: "+tag+"["+attribute+"="+value+"]"));
    }
    private static void assertAutomationAlert(Document html) {
        var section=element(html,"section","id","j3-automation");
        assertThat(element(section,"p","role","alert").getAttributeValue("id")).isEqualTo("j3-automation-error");
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
