package com.nageoffer.ai.ragent.incident.action.integration;

import static org.junit.jupiter.api.Assertions.*;
import com.nageoffer.ai.ragent.incident.core.dao.IncidentWorkbenchMapper;
import com.nageoffer.ai.ragent.incident.core.service.IncidentWorkbenchQueryService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

class IncidentWorkbenchQueryIntegrationTest {
    private IncidentWorkbenchMapper mapper(ActionDbFixture db) {
        var config=new Configuration(new Environment("workbench",new SpringManagedTransactionFactory(),db.transactions.getDataSource()));
        config.addMapper(IncidentWorkbenchMapper.class);
        return new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(config)).getMapper(IncidentWorkbenchMapper.class);
    }
    @Test void emptyDatabaseHasTwentyFourRealZeroBuckets() throws Exception {
        try(var db=new ActionDbFixture()) {
            var result=new IncidentWorkbenchQueryService(mapper(db),db.transactions).snapshot();
            assertEquals(0L,((IncidentWorkbenchMapper.Counts)result.get("counts")).getActiveIncidents());
            var buckets=(List<?>)result.get("alertBuckets");
            assertEquals(24,buckets.size());
            for(var bucket:buckets) assertEquals(0L,((Map<?,?>)bucket).get("count"));
            assertTrue(((List<?>)result.get("attentionIncidents")).isEmpty());
        }
    }
    @Test void statesLimitsBoundariesAndAllowlistUsePersistedFacts() throws Exception {
        try(var db=new ActionDbFixture()) {
            db.seedIncident("100");
            var states=List.of("PENDING_APPROVAL","APPROVED","APPROVAL_REJECTED","DISPATCHING","EXECUTING","SUCCEEDED","FAILED","EXECUTION_REJECTED","EXPIRED","UNKNOWN");
            for(int i=0;i<states.size();i++) {
                var actionId=String.valueOf(200+i);
                db.seedPendingAction(actionId,OffsetDateTime.now().plusMinutes(i+1));
                db.sql("UPDATE t_incident_action SET state='"+states.get(i)+"',authorization_due_at=now()+interval '1 hour' WHERE id='"+actionId+"'");
            }
            for(int i=0;i<10;i++) db.seedPendingAction(String.valueOf(300+i),OffsetDateTime.now().plusMinutes(20+i));
            var mapper=mapper(db);
            var counts=mapper.counts();
            assertEquals(1,counts.getActiveIncidents());assertEquals(11,counts.getPendingApprovalActions());
            assertEquals(1,counts.getExecutingActions());assertEquals(1,counts.getUnknownActions());
            assertEquals(8,mapper.pendingActions().size());assertEquals("200",mapper.pendingActions().get(0).getActionId());
            var attention=mapper.attentionIncidents();assertEquals(1,attention.size());assertEquals(11,attention.get(0).getPendingApprovalActionCount());
            var at=OffsetDateTime.parse("2026-10-10T00:30:00Z");
            db.sql("UPDATE t_incident_alert SET received_at='2026-10-09T00:30:00Z',occurred_at='2020-01-01T00:00:00Z'");
            var buckets=mapper.alertBuckets(at);
            assertEquals(24,buckets.size());assertEquals(1,buckets.get(0).getCount());assertEquals(0,buckets.get(23).getCount());
            db.sql("UPDATE t_incident_alert SET received_at='2026-10-09T01:30:00Z'");
            assertEquals(1,mapper.alertBuckets(at).get(1).getCount());
            db.sql("UPDATE t_incident_alert SET received_at='2026-10-10T00:30:00Z'");
            assertEquals(0,mapper.alertBuckets(at).stream().mapToLong(IncidentWorkbenchMapper.Bucket::getCount).sum());
            db.sql("UPDATE t_incident_alert SET received_at='2026-10-09T00:29:59Z'");
            assertEquals(0,mapper.alertBuckets(at).stream().mapToLong(IncidentWorkbenchMapper.Bucket::getCount).sum());
        }
    }
    @Test void lifecycleAttentionActivityOrderingTimezoneAndIdentityAreIndependent() throws Exception {
        try(var db=new ActionDbFixture()) {
            db.seedIncident("100");
            var states=List.of("OPEN","ACKNOWLEDGED","MITIGATING","RESOLVED","CLOSED");
            for(int i=0;i<12;i++) {
                var state=states.get(i%5);var sealed=List.of("RESOLVED","CLOSED").contains(state);
                db.sql("INSERT INTO t_incident(id,aggregation_key,fingerprint,aggregation_rule_version,aggregation_window_seconds,episode_start_at,episode_anchor_alert_id,first_alert_received_at,last_alert_received_at,alert_count,lifecycle_state,lifecycle_sealed_at,aggregation_slot_active,born_sealed,created_event_id,timeline_seq,version,created_at,updated_at) SELECT '"+(400+i)+"','key-"+i+"',fingerprint,aggregation_rule_version,aggregation_window_seconds,episode_start_at,episode_anchor_alert_id,first_alert_received_at,last_alert_received_at,alert_count,'"+state+"',"+(sealed?"now()":"NULL")+",false,false,'"+(500+i)+"',1,0,created_at,'2026-10-10T00:00:00Z' FROM t_incident WHERE id='100'");
                db.sql("INSERT INTO t_incident_timeline(id,incident_id,sequence_no,business_event_key,event_type,actor_type,actor_id,occurred_at,correlation_id,summary_json,created_at) VALUES ('"+(600+i)+"','"+(400+i)+"',1,'activity-"+i+"','INCIDENT_CREATED','SYSTEM','private actor',now(),'99','{\"secret\":\"do not return\"}','2026-10-10T00:00:00Z')");
            }
            db.seedPendingAction("200",OffsetDateTime.now().plusMinutes(10));
            db.sql("UPDATE t_incident_action SET state='UNKNOWN',authorization_due_at=now()+interval '1 hour'");
            db.sql("UPDATE t_incident SET lifecycle_state='CLOSED',lifecycle_sealed_at=now(),aggregation_slot_active=false WHERE id='100'");
            var mapper=mapper(db);assertEquals(8,mapper.counts().getActiveIncidents());
            var attention=mapper.attentionIncidents();assertEquals(8,attention.size());assertEquals("100",attention.get(0).getIncidentId());
            assertEquals("411",attention.get(1).getIncidentId());
            var activity=mapper.recentActivity();assertEquals(8,activity.size());assertEquals("611",activity.get(0).getEventId());assertEquals(1,activity.get(0).getSequenceNo());
            var tx=new TransactionTemplate(db.transactions);
            tx.execute(status->{
                var template=new org.springframework.jdbc.core.JdbcTemplate(db.transactions.getDataSource());
                template.execute("SET LOCAL TIME ZONE 'Asia/Kathmandu'");
                var buckets=mapper.alertBuckets(OffsetDateTime.parse("2026-10-10T05:45:00+05:45"));
                assertEquals(java.time.Instant.parse("2026-10-09T00:00:00Z"),buckets.get(0).getStartAt().toInstant());
                assertEquals(24,buckets.size());return null;
            });
            assertThrows(java.sql.SQLException.class,()->db.sql("INSERT INTO t_incident_alert SELECT * FROM t_incident_alert WHERE id='11'"));
            assertEquals(1,mapper.alertBuckets(OffsetDateTime.now().plusSeconds(1)).stream().mapToLong(IncidentWorkbenchMapper.Bucket::getCount).sum());
            var response=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(new IncidentWorkbenchQueryService(mapper,db.transactions).snapshot());
            assertFalse(response.contains("private actor"));assertFalse(response.contains("do not return"));assertTrue(response.contains("\"incidentId\":\"100\""));
        }
    }
    @Test void repeatableReadKeepsAllReadsInOneSnapshotAcrossCommittedWriter() throws Exception {
        try(var db=new ActionDbFixture()) {
            db.seedIncident("100");
            var mapper=mapper(db);
            var intercepted=org.mockito.Mockito.mock(IncidentWorkbenchMapper.class,
                org.mockito.AdditionalAnswers.delegatesTo(mapper));
            var executor=Executors.newSingleThreadExecutor();
            try {
                org.mockito.Mockito.doAnswer(invocation->{
                    var at=mapper.observedAt();
                    executor.submit(()->db.seedPendingAction("200",OffsetDateTime.now().plusMinutes(10))).get(10,TimeUnit.SECONDS);
                    return at;
                }).when(intercepted).observedAt();
                var snapshot=new IncidentWorkbenchQueryService(intercepted,db.transactions).snapshot();
                assertEquals(0,((IncidentWorkbenchMapper.Counts)snapshot.get("counts")).getPendingApprovalActions());
                assertTrue(((List<?>)snapshot.get("pendingActions")).isEmpty());
                assertEquals(1,mapper.counts().getPendingApprovalActions());
            } finally {executor.shutdownNow();}
        }
    }
}
