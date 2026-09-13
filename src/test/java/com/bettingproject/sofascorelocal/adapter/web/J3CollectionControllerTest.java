package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.domain.scheduledevents.J3CollectionData.*;
import com.bettingproject.sofascorelocal.port.J3CollectionStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.*;
import java.util.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(J3CollectionController.class)
class J3CollectionControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean J3CollectionStore store;
    @MockitoBean com.bettingproject.sofascorelocal.port.J3AutomationStore orders;
    @MockitoBean com.bettingproject.sofascorelocal.port.J3LivePauseStore pauses;
    @MockitoBean CacheManager cacheManager;
    static final LocalDate DATE=LocalDate.parse("2026-09-13");
    @Test void paginationLinksKeepTheExactCollectionAndEscapeProviderNames() throws Exception {
        UUID id=UUID.randomUUID();
        var entry=new Entry(1,"<script>alert(1)</script>","Europe",7L,"League",Map.of(7200,1),List.of(1L),null);
        when(store.page(id,DATE,2,25)).thenReturn(Optional.of(new CatalogPage(id,DATE,Instant.parse("2026-09-13T09:00:00Z"),2,25,60,List.of(entry))));
        when(store.dates(3660)).thenReturn(List.of(new DateSummary(DATE,id,Instant.parse("2026-09-13T09:00:00Z"),"AVAILABLE")));
        mvc.perform(get("/j3/collections/"+id).param("date",DATE.toString()).param("page","2"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("&lt;script&gt;")))
                .andExpect(content().string(not(containsString("<script>alert"))))
                .andExpect(content().string(containsString("/j3/collections/"+id+"?date=2026-09-13&amp;page=3&amp;size=25")));
        verify(store).page(id,DATE,2,25);verify(store).dates(3660);verifyNoMoreInteractions(store);
    }
    @Test void anOlderOpenPageKeepsItsIdentityAndLinksToTheNewerSuccess() throws Exception {
        UUID old=UUID.randomUUID(),latest=UUID.randomUUID();
        when(store.page(old,DATE,2,25)).thenReturn(Optional.of(new CatalogPage(old,DATE,
                Instant.parse("2026-09-13T08:00:00Z"),2,25,60,List.of())));
        when(store.dates(3660)).thenReturn(List.of(new DateSummary(DATE,latest,
                Instant.parse("2026-09-13T09:00:00Z"),"AVAILABLE")));
        mvc.perform(get("/j3/collections/"+old).param("date",DATE.toString()).param("page","2"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Un succès plus récent")))
                .andExpect(content().string(containsString("/j3/collections/"+latest+"?date=2026-09-13")))
                .andExpect(content().string(containsString("/j3/collections/"+old+"?date=2026-09-13&amp;page=3&amp;size=25")));
        verify(store).page(old,DATE,2,25);verify(store).dates(3660);verifyNoMoreInteractions(store);
        verifyNoInteractions(orders,pauses);
    }
    @Test void wrongDateCannotExposeAnotherCollection() throws Exception {
        UUID id=UUID.randomUUID();when(store.page(id,DATE,1,25)).thenReturn(Optional.empty());
        mvc.perform(get("/j3/collections/"+id).param("date",DATE.toString())).andExpect(status().isNotFound());
    }
    @Test void provenMissingHistoryIsShownWithoutFetchingAnything() throws Exception {
        when(store.latest(DATE)).thenReturn(Optional.empty());when(store.hasSuccess(DATE)).thenReturn(true);
        when(store.dates(3660)).thenReturn(List.of());
        mvc.perform(get("/j3/collections").param("date",DATE.toString())).andExpect(status().isOk())
                .andExpect(content().string(containsString("Un succès est attesté")));
        verify(store).latest(DATE);verify(store).hasSuccess(DATE);verify(store).dates(3660);verifyNoMoreInteractions(store);
    }
}
