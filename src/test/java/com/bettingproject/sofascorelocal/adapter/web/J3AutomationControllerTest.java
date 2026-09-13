package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.application.network.J3RuntimeService;
import com.bettingproject.sofascorelocal.domain.scheduledevents.J3AutomationData.*;
import com.bettingproject.sofascorelocal.domain.scheduledevents.J3CollectionData.Trigger;
import com.bettingproject.sofascorelocal.port.J3AutomationStore;
import com.bettingproject.sofascorelocal.security.LocalFormTokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

    @Test void oneDateAndOneClickSubmitProviderOrderAndDoubleClickCannotSubmitAgain() throws Exception {
        var session=new MockHttpSession();String token=tokens.issue(session);UUID id=UUID.randomUUID();
        when(runtime.manual(id,DATE,null)).thenReturn(order(id));
        var request=post("/j3/collect").header("Host","localhost:8087").session(session)
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
        mvc.perform(multipart("/j3/import").file(last).file(first).header("Host","127.0.0.1:8087").session(session)
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
    @Test void foreignOriginAndMissingTokenCannotChangePreferences() throws Exception {
        var session=new MockHttpSession();String token=tokens.issue(session);
        mvc.perform(post("/j3/settings").header("Host","localhost:8087").header("Origin","https://foreign.invalid").session(session)
                .param("localFormToken",token).param("revision","1").param("mode","STARTUP_OR_DAY_CHANGE"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/j3/settings").header("Host","localhost:8087").session(session)
                .param("localFormToken","invalid").param("revision","1").param("mode","STARTUP_OR_DAY_CHANGE"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(runtime,orders);
    }
    @Test void uncheckedAutomationBoxPersistsDisabledAndFixedTime() throws Exception {
        var session=new MockHttpSession();
        mvc.perform(post("/j3/settings").header("Host","localhost:8087").session(session)
                .param("localFormToken",tokens.issue(session)).param("revision","3").param("mode","DAILY_AT").param("time","08:30"))
                .andExpect(status().is3xxRedirection());
        verify(runtime).configure(3,false,Mode.DAILY_AT,LocalTime.of(8,30));
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
