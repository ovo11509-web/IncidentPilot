package com.nageoffer.ai.ragent.rag.runbook;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.dev33.satoken.stp.StpUtil;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RunbookCatalogControllerTest {
    @Test void authorizationPrecedesCorpusReads() throws Exception {
        var corpus=mock(RunbookCorpus.class,withSettings().mockMaker(org.mockito.MockMakers.INLINE));
        var mvc=MockMvcBuilders.standaloneSetup(new RunbookCatalogController(corpus)).build();
        try(var auth=mockStatic(StpUtil.class,withSettings().mockMaker(org.mockito.MockMakers.INLINE))) {
            auth.when(StpUtil::checkLogin).thenThrow(NotLoginException.newInstance("login","-1","private",null));
            mvc.perform(get("/incident/v1/runbooks")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
            verifyNoInteractions(corpus);
            auth.when(StpUtil::checkLogin).thenAnswer(i->null);
            auth.when(()->StpUtil.checkRole("admin")).thenThrow(new NotRoleException("admin"));
            mvc.perform(get("/incident/v1/runbooks")).andExpect(status().isForbidden());
            verifyNoInteractions(corpus);
        }
    }
    @Test void actualPackagedCorpusHasSafeSerializedProjection() throws Exception {
        var corpus=new RunbookCorpus();
        var mvc=MockMvcBuilders.standaloneSetup(new RunbookCatalogController(corpus)).build();
        try(var auth=mockStatic(StpUtil.class,withSettings().mockMaker(org.mockito.MockMakers.INLINE))) {
            var result=mvc.perform(get("/incident/v1/runbooks")).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0")).andExpect(jsonPath("$.data.documents.length()").value(5))
                .andExpect(jsonPath("$.data.documents[0].sourceType").value("INCIDENTPILOT_SYNTHETIC"))
                .andExpect(jsonPath("$.data.documents[0].sourcePath").doesNotExist())
                .andReturn();
            var body=RunbookCorpus.JSON.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
            var document=body.path("data").path("documents").get(0);
            assertEquals(document.path("documentDigest").asText(),RunbookCorpus.hash(document.path("content").asText()));
            for(var section:document.path("sections"))assertEquals(section.path("chunkDigest").asText(),RunbookCorpus.hash(section.path("quote").asText()));
        }
    }
    @Test void unavailableCorpusIsAnExplicitSafeFailure() throws Exception {
        var corpus=mock(RunbookCorpus.class,withSettings().mockMaker(org.mockito.MockMakers.INLINE));
        when(corpus.available()).thenReturn(false);
        var mvc=MockMvcBuilders.standaloneSetup(new RunbookCatalogController(corpus)).build();
        try(var auth=mockStatic(StpUtil.class,withSettings().mockMaker(org.mockito.MockMakers.INLINE))) {
            mvc.perform(get("/incident/v1/runbooks")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("RUNBOOK_CORPUS_UNAVAILABLE")).andExpect(jsonPath("$.data").doesNotExist());
            verify(corpus,never()).documents();
        }
    }
}
