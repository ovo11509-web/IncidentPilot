package com.nageoffer.ai.ragent.incident.action.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.dev33.satoken.stp.StpUtil;
import com.nageoffer.ai.ragent.incident.action.service.ActionDecisionService;
import java.time.OffsetDateTime;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ActionAuditEvidenceTest {
    @Test
    void approvalFactsStayAppendOnlyOrderedAndShareOutboxIdentityWithoutSecrets() throws Exception {
        try (var db = new ActionDbFixture(); var stp = Mockito.mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("42");
            db.seedIncident("100");
            db.seedPendingAction("200", OffsetDateTime.now().plusMinutes(10));
            db.seedPendingAction("201", OffsetDateTime.now().plusMinutes(10));
            var decisions = new ActionDecisionService(db.actions, db.approvals, db.guard,
                    db.actionTimeline, db.actionOutbox, db.properties, db.alerts);
            decisions.decide("200", 0, "APPROVE", null, "request-approval-200");
            decisions.decide("201", 0, "REJECT", "POLICY_REJECTED", "request-approval-201");
            var facts = db.timeline.findByIncidentId("100");
            assertEquals(2, facts.size());
            assertTrue(facts.get(0).getSequenceNo() < facts.get(1).getSequenceNo());
            assertEquals(2, new HashSet<>(facts.stream().map(f -> f.getBusinessEventKey()).toList()).size());
            for (var fact : facts) {
                var event = db.outbox.findById(fact.getId());
                assertEquals(fact.getBusinessEventKey(), event.getBusinessEventKey());
                assertEquals("PENDING", event.getPublishStatus());
                assertFalse(fact.getSummaryJson().contains("secret_digest"));
                assertFalse(event.getPayloadJson().contains("ticket"));
                assertFalse(event.getPayloadJson().contains("parameters"));
            }
            assertThrows(Exception.class, () -> db.sql("UPDATE t_incident_timeline "
                    + "SET event_type='MUTATED' WHERE incident_id='100'"));
            assertEquals(2, db.timeline.findByIncidentId("100").size());
        }
    }
}
