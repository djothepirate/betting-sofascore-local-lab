package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.application.network.J3RuntimeService;
import com.bettingproject.sofascorelocal.domain.scheduledevents.J3AutomationData.*;
import com.bettingproject.sofascorelocal.domain.scheduledevents.J3CollectionData.Trigger;
import com.bettingproject.sofascorelocal.port.J3AutomationStore;
import com.bettingproject.sofascorelocal.security.LocalFormTokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.*;
import java.util.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(J3AutomationController.class)
@Import(LocalFormTokenService.class)
class J3AutomationControllerTest {
    @Autowired MockMvc mvc;
    @Autowired LocalFormTokenService tokens;
    @MockitoBean J3RuntimeService runtime;
    @MockitoBean J3AutomationStore orders;
    @MockitoBean CacheManager cacheManager;
    static final LocalDate DATE=LocalDate.parse("2026-09-13");
    @org.junit.jupiter.api.BeforeEach void discardContextLifecycleNotifications() {clearInvocations(runtime);}

    @ParameterizedTest @ValueSource(strings={"localhost:8087","127.0.0.1:8087"})
    void oneDateAndOneClickSubmitProviderOrderAndDoubleClickCannotSubmitAgain(String host) throws Exception {
        var session=new MockHttpSession();String token=tokens.issue(session);UUID id=UUID.randomUUID();
        when(runtime.manual(id,DATE,null)).thenReturn(order(id));
        var request=multipart("/j3/collect").header("Host",host).header("Origin","http://"+host)
                .header("Sec-Fetch-Site","same-origin").header("Sec-Fetch-Dest","document").session(session)
                .param("localFormToken",token).param("orderId",id.toString()).param("date",DATE.toString());
        mvc.perform(request).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/j3/orders/"+id));
        mvc.perform(request).andExpect(status().isBadRequest());
        verify(runtime,times(1)).manual(id,DATE,null);verifyNoInteractions(orders);
    }
    @Test void jsonImportReordersContiguousFilesBeforeSubmittingOneAtomicBatch() throws Exception {
        var session=new MockHttpSession();UUID id=UUID.randomUUID();
        var first=new MockMultipartFile("pageFiles","page-1.json","application/json","{\"scheduled\":[],\"hasNextPage\":true}".getBytes());
        var last=new MockMultipartFile("pageFiles","page-2.json","application/json","{\"scheduled\":[],\"hasNextPage\":false}".getBytes());
        when(runtime.manual(eq(id),eq(DATE),anyList())).thenAnswer(i->{
            List<com.bettingproject.sofascorelocal.domain.provider.RawPayloadEvidence> pages=i.getArgument(2);
            org.assertj.core.api.Assertions.assertThat(pages).hasSize(2);
            org.assertj.core.api.Assertions.assertThat(pages.getFirst().bytes()).isEqualTo(first.getBytes());
            return order(id);
        });
        mvc.perform(multipart("/j3/import").file(last).file(first).header("Host","127.0.0.1:8087")
                .header("Origin","http://127.0.0.1:8087").header("Sec-Fetch-Site","same-origin")
                .header("Sec-Fetch-Dest","document").session(session)
                .param("localFormToken",tokens.issue(session)).param("orderId",id.toString()).param("date",DATE.toString()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/j3/orders/"+id));
    }
    @ParameterizedTest @ValueSource(strings={"page-2.json","page-36.json","../page-1.json","page-01.json"})
    void badImportNamesNeverCreateAnOrder(String name) throws Exception {
        var session=new MockHttpSession();
        mvc.perform(multipart("/j3/import").file(new MockMultipartFile("pageFiles",name,"application/json","{}".getBytes()))
                .header("Host","localhost:8087").session(session).param("localFormToken",tokens.issue(session))
                .param("orderId",UUID.randomUUID().toString()).param("date",DATE.toString()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/?j3Date="+DATE+"#manual-call-control"));
        verifyNoInteractions(runtime,orders);
    }
    @ParameterizedTest
    @ValueSource(strings={"https://foreign.invalid","null","http://127.0.0.1:8087"})
    void untrustedOriginAndMissingTokenCannotChangePreferences(String origin) throws Exception {
        var session=new MockHttpSession();String token=tokens.issue(session);
        mvc.perform(post("/j3/settings").header("Host","localhost:8087").header("Origin",origin).session(session)
                .param("localFormToken",token).param("revision","1").param("mode","STARTUP_OR_DAY_CHANGE"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/j3/settings").header("Host","localhost:8087").session(session)
                .param("localFormToken","invalid").param("revision","1").param("mode","STARTUP_OR_DAY_CHANGE"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(runtime,orders);
    }
    @ParameterizedTest @ValueSource(strings={"localhost:8087","127.0.0.1:8087"})
    void uncheckedAutomationBoxPersistsDisabledAndFixedTime(String host) throws Exception {
        var session=new MockHttpSession();
        mvc.perform(post("/j3/settings").header("Host",host).header("Origin","http://"+host)
                .header("Sec-Fetch-Site","same-origin").header("Sec-Fetch-Dest","document").session(session)
                .param("localFormToken",tokens.issue(session)).param("revision","3").param("mode","DAILY_AT").param("time","08:30"))
                .andExpect(status().is3xxRedirection());
        verify(runtime).configure(3,false,Mode.DAILY_AT,LocalTime.of(8,30));
    }
    @ParameterizedTest @ValueSource(strings={"localhost:8087","127.0.0.1:8087"})
    void uncheckedAutomationBoxAcceptsTheEmptyTimeSubmittedByTheStartupForm(String host) throws Exception {
        var session=new MockHttpSession();
        mvc.perform(post("/j3/settings").header("Host",host).header("Origin","http://"+host)
                .header("Sec-Fetch-Site","same-origin").header("Sec-Fetch-Dest","document").session(session)
                .param("localFormToken",tokens.issue(session)).param("revision","1")
                .param("mode","STARTUP_OR_DAY_CHANGE").param("time",""))
                .andExpect(redirectedUrl("/#j3-automation"));
        verify(runtime).configure(1,false,Mode.STARTUP_OR_DAY_CHANGE,null);
    }
    @ParameterizedTest @CsvSource({
        "localhost:8087,1", "localhost:8087,2", "127.0.0.1:8087,1", "127.0.0.1:8087,2"
    })
    void sameOriginBrowserCanCreateOrReviseAPlannedCollection(String host,int revision) throws Exception {
        var session=new MockHttpSession();UUID rule=UUID.randomUUID();
        mvc.perform(post("/j3/plans").header("Host",host).header("Origin","http://"+host)
                .header("Sec-Fetch-Site","same-origin").header("Sec-Fetch-Dest","document").session(session)
                .param("localFormToken",tokens.issue(session)).param("ruleId",rule.toString())
                .param("revision",Integer.toString(revision)).param("date",DATE.toString())
                .param("at","2026-09-14T18:30").param("offset",""))
                .andExpect(redirectedUrl("/#j3-automation"));
        verify(runtime).schedule(rule,revision,DATE,LocalDateTime.parse("2026-09-14T18:30"),null);
        verifyNoMoreInteractions(runtime);
    }

    @ParameterizedTest @CsvSource(delimiter='|',nullValues="ABSENT",textBlock="""
            ABSENT | 2026-09-14T18:30 | '' | Renseignez la date du calendrier à collecter.
            '' | 2026-09-14T18:30 | '' | Renseignez la date du calendrier à collecter.
            2026-02-30 | 2026-09-14T18:30 | '' | La date à collecter est incorrecte.
            14/09/2026 | 2026-09-14T18:30 | '' | La date à collecter est incorrecte.
            2026-09-14 | ABSENT | '' | Renseignez la date et l’heure de déclenchement à Paris.
            2026-09-14 | '' | '' | Renseignez la date et l’heure de déclenchement à Paris.
            2026-09-14 | 2026-09-14 | '' | La date ou l’heure de déclenchement est incorrecte.
            2026-09-14 | 2026-02-30T18:30 | '' | La date ou l’heure de déclenchement est incorrecte.
            2026-09-14 | 2026-09-14T25:00 | '' | La date ou l’heure de déclenchement est incorrecte.
            2026-09-14 | 2026-09-14T18:30 | invalid | Le décalage choisi ne correspond pas à cet horaire à Paris.
            """)
    void invalidPlanFieldsReturnAnExplanationWithoutCallingRuntime(String date,String at,String offset,String message) throws Exception {
        var session=new MockHttpSession();UUID rule=UUID.randomUUID();
        var request=post("/j3/plans").header("Host","localhost:8087").header("Origin","http://localhost:8087")
                .session(session).param("localFormToken",tokens.issue(session))
                .param("ruleId",rule.toString()).param("revision","1").param("offset",offset);
        if(date!=null)request.param("date",date);
        if(at!=null)request.param("at",at);
        mvc.perform(request).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/*#j3-automation"))
                .andExpect(flash().attribute("j3AutomationError",org.hamcrest.Matchers.startsWith(message)))
                .andExpect(flash().attribute("j3PlanInput",new J3AutomationController.PlanInput(rule,1,date,at,offset)));
        verifyNoInteractions(runtime,orders);
    }

    @ParameterizedTest @CsvSource(delimiter='|',textBlock="""
            J3_PLAN_MUST_BE_FUTURE | Choisissez un horaire futur à Paris.
            J3_PLAN_REVISION_CONFLICT | Les paramètres ont changé.
            J3_PLAN_IDENTITY_CONFLICT | Les paramètres ont changé.
            J3_PLAN_ALREADY_ADMITTED | Cet ordre est déjà pris en charge
            J3_PLAN_LIMIT | La file J3 est pleine.
            """)
    void translatedRepositoryPlanRejectionsKeepTheirUserExplanation(String code,String message) throws Exception {
        var session=new MockHttpSession();
        when(runtime.schedule(any(),anyInt(),any(),any(),any())).thenThrow(
                new InvalidDataAccessApiUsageException(code,new IllegalArgumentException(code)));
        mvc.perform(post("/j3/plans").header("Host","localhost:8087").session(session)
                .param("localFormToken",tokens.issue(session)).param("ruleId",UUID.randomUUID().toString())
                .param("revision","2").param("date",DATE.toString()).param("at","2026-09-14T18:30"))
                .andExpect(redirectedUrl("/?j3Date="+DATE+"#j3-automation"))
                .andExpect(flash().attribute("j3AutomationError",containsString(message)));
    }

    @ParameterizedTest @CsvSource(delimiter='|',textBlock="""
            J3_TIME_DOES_NOT_EXIST | Cette heure n’existe pas à Paris
            J3_TIME_OFFSET_REQUIRED | Cette heure existe deux fois
            J3_TIME_OFFSET_INVALID | Le décalage choisi ne correspond pas
            J3_TIME_MINUTE_REQUIRED | Saisissez l’heure de déclenchement en heures et minutes, sans secondes.
            """)
    void rejectedParisTimeReturnsItsExplanationAndPreservesTheForm(String code,String message) throws Exception {
        var session=new MockHttpSession();UUID rule=UUID.randomUUID();
        when(runtime.schedule(any(),anyInt(),any(),any(),any())).thenThrow(new IllegalArgumentException(code));
        mvc.perform(post("/j3/plans").header("Host","localhost:8087").session(session)
                .param("localFormToken",tokens.issue(session)).param("ruleId",rule.toString())
                .param("revision","2").param("date",DATE.toString()).param("at","2026-10-25T02:30").param("offset","+01:00"))
                .andExpect(redirectedUrl("/?j3Date="+DATE+"#j3-automation"))
                .andExpect(flash().attribute("j3AutomationError",containsString(message)))
                .andExpect(flash().attribute("j3PlanInput",new J3AutomationController.PlanInput(rule,2,DATE.toString(),"2026-10-25T02:30","+01:00")));
    }

    @Test void unrelatedPersistenceFailureIsNotMisreportedAsInvalidUserInput() {
        var session=new MockHttpSession();
        var failure=new InvalidDataAccessApiUsageException("unrelated",new IllegalStateException("unrelated"));
        when(runtime.schedule(any(),anyInt(),any(),any(),any())).thenThrow(failure);
        org.assertj.core.api.Assertions.assertThatThrownBy(()->mvc.perform(
                post("/j3/plans").header("Host","localhost:8087").session(session)
                        .param("localFormToken",tokens.issue(session)).param("ruleId",UUID.randomUUID().toString())
                        .param("revision","1").param("date",DATE.toString()).param("at","2026-09-14T18:30")))
                .hasCause(failure);
    }

    @Test void malformedDateStillRequiresTrustedOriginAndAValidFormToken() throws Exception {
        var session=new MockHttpSession();String token=tokens.issue(session);
        mvc.perform(post("/j3/plans").header("Host","localhost:8087").header("Origin","null").session(session)
                .param("localFormToken",token).param("ruleId",UUID.randomUUID().toString())
                .param("revision","1").param("date","invalid").param("at",""))
                .andExpect(status().isForbidden());
        mvc.perform(post("/j3/plans").header("Host","localhost:8087").header("Origin","http://localhost:8087").session(session)
                .param("localFormToken","invalid").param("ruleId",UUID.randomUUID().toString())
                .param("revision","1").param("date","invalid").param("at",""))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(runtime,orders);
    }
    @ParameterizedTest @ValueSource(strings={"localhost:8087","127.0.0.1:8087"})
    void sameOriginBrowserCanCancelAPlannedCollection(String host) throws Exception {
        var session=new MockHttpSession();UUID id=UUID.randomUUID();
        mvc.perform(post("/j3/plans/"+id+"/cancel").header("Host",host).header("Origin","http://"+host)
                .header("Sec-Fetch-Site","same-origin").header("Sec-Fetch-Dest","document").session(session)
                .param("localFormToken",tokens.issue(session)))
                .andExpect(redirectedUrl("/#j3-automation"));
        verify(runtime).cancel(id);
        verifyNoMoreInteractions(runtime);
    }
    @ParameterizedTest @ValueSource(strings={"https://foreign.invalid","null"})
    void untrustedOriginCannotCreateReviseOrCancelPlansEvenWithAValidToken(String origin) throws Exception {
        var session=new MockHttpSession();String token=tokens.issue(session);UUID id=UUID.randomUUID();
        for(int revision:List.of(1,2)) {
            mvc.perform(post("/j3/plans").header("Host","localhost:8087").header("Origin",origin).session(session)
                    .param("localFormToken",token).param("ruleId",id.toString()).param("revision",Integer.toString(revision))
                    .param("date",DATE.toString()).param("at","2026-09-14T18:30"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/j3/plans/"+id+"/cancel").header("Host","localhost:8087").header("Origin",origin)
                .session(session).param("localFormToken",token)).andExpect(status().isForbidden());
        verifyNoInteractions(runtime,orders);
    }
    @Test void orderProgressReadsLedgerWithoutRepeatingWork() throws Exception {
        UUID id=UUID.randomUUID();when(orders.find(id)).thenReturn(Optional.of(order(id)));
        mvc.perform(get("/j3/orders/"+id).header("Host","localhost:8087")).andExpect(status().isOk())
                .andExpect(content().string(containsString("2026-09-13"))).andExpect(header().string("Cache-Control",containsString("no-store")));
        verifyNoInteractions(runtime);
    }
    static Order order(UUID id) {
        Instant now=Instant.parse("2026-09-13T09:00:00Z");
        return new Order(id,"MANUAL|"+id,null,0,DATE,Trigger.MANUAL_PROVIDER,now,now,OrderState.QUEUED,now,now.plusSeconds(1200),null,null,null,null);
    }
}
