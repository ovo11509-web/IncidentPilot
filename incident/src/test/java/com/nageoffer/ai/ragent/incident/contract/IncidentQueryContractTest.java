package com.nageoffer.ai.ragent.incident.contract;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.exception.NotRoleException;
import com.nageoffer.ai.ragent.incident.alert.dao.AlertMapper;
import com.nageoffer.ai.ragent.incident.core.dao.IncidentMapper;
import com.nageoffer.ai.ragent.incident.core.service.*;
import com.nageoffer.ai.ragent.incident.core.web.IncidentController;
import com.nageoffer.ai.ragent.incident.alert.web.IncidentErrorAdvice;
import com.nageoffer.ai.ragent.incident.timeline.dao.TimelineMapper;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class IncidentQueryContractTest {
    @Test void boundedQueriesAndPersonnelAuthorization() throws Exception {
        var incidents=mock(IncidentMapper.class); var alerts=mock(AlertMapper.class);
        var row=new IncidentMapper.IncidentRow(); row.setId("100"); row.setLifecycleState("OPEN");
        row.setEpisodeStartAt(OffsetDateTime.parse("2026-10-04T00:00:00Z"));
        row.setFirstAlertReceivedAt(row.getEpisodeStartAt()); row.setLastAlertReceivedAt(row.getEpisodeStartAt());
        when(incidents.findById("100")).thenReturn(row);
        when(incidents.findOperationsPage(null,null,null,2,null,null,null,null)).thenReturn(List.of(row,row));
        var alert=new AlertMapper.AlertRow();alert.setId("10");alert.setTitle("health password=secret-value <script>alert(1)</script>");
        alert.setOccurredAt(row.getEpisodeStartAt());alert.setReceivedAt(row.getEpisodeStartAt());
        when(alerts.findOperationsPage("100",null,2)).thenReturn(List.of(alert));
        var controller=new IncidentController(incidents,alerts,mock(TimelineMapper.class),mock(IncidentTransitionService.class),new EpisodePartitioner());
        controller.setQueryService(new IncidentQueryService(incidents,alerts));
        var mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new IncidentErrorAdvice()).build();
        try(var stp=mockStatic(StpUtil.class)) {
            mvc.perform(get("/incident/v1/incidents").param("limit","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.hasMore").value(true))
                .andExpect(jsonPath("$.data.items[0].incidentId").value("100"));
            for(var q:List.of("limit=101","limit=0","state=UNKNOWN","afterId=1","afterEpisodeStartAt=bad&afterId=1"))
                mvc.perform(get("/incident/v1/incidents?"+q)).andExpect(status().isUnprocessableEntity());
            var response=mvc.perform(get("/incident/v1/incidents/100/alerts").param("limit","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].alertId").value("10"))
                .andReturn().getResponse().getContentAsString();
            assertFalse(response.contains("secret-value")); assertFalse(response.contains("payload")); assertFalse(response.contains("labels"));
            mvc.perform(get("/incident/v1/incidents/101/alerts")).andExpect(status().isNotFound());
            mvc.perform(get("/incident/v1/incidents/100/alerts?afterId=x")).andExpect(status().isUnprocessableEntity());
            stp.when(()->StpUtil.checkRole("admin")).thenThrow(new NotRoleException("admin"));
            mvc.perform(get("/incident/v1/incidents")).andExpect(status().isForbidden());
            mvc.perform(get("/incident/v1/incidents/100/alerts")).andExpect(status().isForbidden());
        }
        verify(alerts).findOperationsPage("100",null,2); verify(alerts,never()).findAllByAggregationKey(any());
    }
}
