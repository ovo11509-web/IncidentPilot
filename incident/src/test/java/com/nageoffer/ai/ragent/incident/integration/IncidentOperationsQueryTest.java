package com.nageoffer.ai.ragent.incident.integration;

import static org.junit.jupiter.api.Assertions.*;
import cn.dev33.satoken.stp.StpUtil;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mockStatic;
import com.nageoffer.ai.ragent.incident.core.service.IncidentQueryService;

class IncidentOperationsQueryTest {
    @Test void realPostgresKeysetsFilterAndExactEpisodeMembership() throws Exception {
        try(var db=new IncidentDbFixture()) {
            String key="alert-incident-v1:"+"a".repeat(64);
            var now=OffsetDateTime.now().minusMinutes(5);
            db.insertAlert("10",key,now);db.aggregation.processOne("10");
            db.insertAlert("11",key,now.plusSeconds(1));db.aggregation.processOne("11");
            db.insertAlert("12",key,now.minusHours(1));db.aggregation.processOne("12");
            String first=db.alerts.findById("10").getIncidentId();String second=db.alerts.findById("12").getIncidentId();
            assertNotEquals(first,second);
            var rows=db.incidents.findOperationsPage("OPEN",null,null,1,null,null,null,null);assertEquals(1,rows.size());assertEquals(first,rows.get(0).getId());
            var next=db.incidents.findOperationsPage(null,rows.get(0).getEpisodeStartAt(),first,2,null,null,null,null);
            assertEquals(1,next.size());assertEquals(second,next.get(0).getId());
            assertEquals(0,db.incidents.findOperationsPage("CLOSED",null,null,100,null,null,null,null).size());
            var alerts=db.alerts.findOperationsPage(first,null,1);assertEquals("10",alerts.get(0).getId());
            assertEquals("11",db.alerts.findOperationsPage(first,"10",2).get(0).getId());
            assertEquals(0,db.alerts.findOperationsPage(first,"11",2).size());
            assertEquals("12",db.alerts.findOperationsPage(second,null,2).get(0).getId());
            db.sql("UPDATE t_incident_alert SET title='health password=secret-value',summary='secret-value',labels_json='{\"token\":\"secret-value\"}' WHERE id='10'");
            try(var stp=mockStatic(StpUtil.class)) {
                var service=new IncidentQueryService(db.incidents,db.alerts);
                var view=service.alerts(first,1,null);assertEquals(true,view.get("hasMore"));
                assertFalse(view.toString().contains("secret-value"));
                assertFalse(view.toString().contains("labels"));assertFalse(view.toString().contains("summary"));
            }
        }
    }
}
