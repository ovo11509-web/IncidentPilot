package com.nageoffer.ai.ragent.incident.contract;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.exception.NotRoleException;
import cn.dev33.satoken.exception.NotLoginException;
import com.nageoffer.ai.ragent.incident.alert.web.IncidentErrorAdvice;
import com.nageoffer.ai.ragent.incident.core.dao.IncidentWorkbenchMapper;
import com.nageoffer.ai.ragent.incident.core.service.IncidentWorkbenchQueryService;
import com.nageoffer.ai.ragent.incident.core.web.IncidentWorkbenchController;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class IncidentWorkbenchContractTest {
    @Test void authorizationPrecedesReadsAndFailuresStaySafe() throws Exception {
        var mapper=mock(IncidentWorkbenchMapper.class);
        var tx=mock(PlatformTransactionManager.class);
        when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        var mvc=MockMvcBuilders.standaloneSetup(new IncidentWorkbenchController(
                new IncidentWorkbenchQueryService(mapper,tx)))
                .setControllerAdvice(new IncidentErrorAdvice()).build();
        try(var stp=mockStatic(StpUtil.class)) {
            stp.when(StpUtil::checkLogin).thenThrow(NotLoginException.newInstance("login","-1","not logged in",null));
            mvc.perform(get("/incident/v1/workbench")).andExpect(status().isUnauthorized());
            verifyNoInteractions(mapper,tx);
            stp.when(StpUtil::checkLogin).thenAnswer(i->null);
            stp.when(()->StpUtil.checkRole("admin")).thenThrow(new NotRoleException("admin"));
            mvc.perform(get("/incident/v1/workbench")).andExpect(status().isForbidden());
            verifyNoInteractions(mapper,tx);
            stp.when(()->StpUtil.checkRole("admin")).thenAnswer(i->null);
            when(mapper.observedAt()).thenThrow(new DataAccessResourceFailureException("password=private SQL"));
            mvc.perform(get("/incident/v1/workbench")).andExpect(status().isServiceUnavailable())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private SQL"))));
        }
    }
    @Test void emptyResponseUsesExistingEnvelopeAndAllowlistedFields() throws Exception {
        var mapper=mock(IncidentWorkbenchMapper.class);
        var tx=mock(PlatformTransactionManager.class);
        when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(mapper.observedAt()).thenReturn(OffsetDateTime.parse("2026-10-10T00:00:00Z"));
        when(mapper.counts()).thenReturn(new IncidentWorkbenchMapper.Counts());
        when(mapper.alertBuckets(any())).thenReturn(List.of());
        when(mapper.attentionIncidents()).thenReturn(List.of());
        when(mapper.pendingActions()).thenReturn(List.of());
        when(mapper.recentActivity()).thenReturn(List.of());
        var mvc=MockMvcBuilders.standaloneSetup(new IncidentWorkbenchController(new IncidentWorkbenchQueryService(mapper,tx)))
            .setControllerAdvice(new IncidentErrorAdvice()).build();
        try(var stp=mockStatic(StpUtil.class)) {
            mvc.perform(get("/incident/v1/workbench")).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0")).andExpect(jsonPath("$.requestId").isString())
                .andExpect(jsonPath("$.data.counts.activeIncidents").value(0))
                .andExpect(jsonPath("$.data.pendingActions").isEmpty())
                .andExpect(jsonPath("$.data.windowStart").value("2026-10-09T00:00:00Z"))
                .andExpect(jsonPath("$.data.summary").doesNotExist());
        }
    }
}
