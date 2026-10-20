package com.nageoffer.ai.ragent.incident.action.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nageoffer.ai.ragent.incident.action.worker.ActionExpiryWorker;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class ActionExpiryWorkerTest {
    @Test
    void pendingDeadlineExpiresOnceWithoutStartingExecution() throws Exception {
        try (var db = new ActionDbFixture()) {
            db.seedIncident("100");
            db.seedPendingAction("200", OffsetDateTime.now().minusMinutes(1));
            var worker = new ActionExpiryWorker(db.actions, db.approvals, db.guard,
                    db.actionTimeline, db.alerts);
            assertEquals(1, worker.expireDue());
            assertEquals(0, worker.expireDue());
            assertEquals("EXPIRED", db.actions.findById("200").getState());
            assertEquals("EXPIRED", db.approvals.findByActionId("200").getStatus());
            assertEquals(null, db.executions.findByActionId("200"));
            assertEquals(1, db.timeline.findByIncidentId("100").size());
        }
    }
}
