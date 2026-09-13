package com.bettingproject.sofascorelocal.adapter.web;

import com.bettingproject.sofascorelocal.application.network.J3ManualCollectionEvidenceService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ManualCallController.class)
class ManualCallControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean J3ManualCollectionEvidenceService evidence;
    @MockitoBean CacheManager cacheManager;
    @ParameterizedTest
    @ValueSource(strings={"rearm","activate","prepare","confirm","execute","import-json-pages","stop"})
    void legacyMutationsAreRetiredWithoutTouchingAnyJ3Evidence(String action)throws Exception {
        mvc.perform(post("/manual-call/"+action).header("Host","localhost:8087"))
                .andExpect(status().isGone()).andExpect(header().string("Cache-Control","no-store"));
        verifyNoInteractions(evidence);
    }
}
