package com.nageoffer.ai.ragent.incident.action.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nageoffer.ai.ragent.incident.action.dao.ExecutionMapper;
import com.nageoffer.ai.ragent.incident.action.dao.TicketMapper;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class ActionStateMachineTest {
    @Test
    void mapperRejectsTerminalReentryUnknownRestartAndStaleVersion() throws Exception {
        try (var db = new ActionDbFixture()) {
            db.seedIncident("100");
            db.seedPendingAction("200", OffsetDateTime.now().plusMinutes(10));
            assertEquals(0, db.actions.transition("200", "PENDING_APPROVAL", 99,
                    "APPROVED", OffsetDateTime.now().plusMinutes(5), null));
            assertEquals(1, db.actions.transition("200", "PENDING_APPROVAL", 0,
                    "APPROVED", OffsetDateTime.now().plusMinutes(5), null));
            assertEquals(0, db.actions.transition("200", "APPROVED", 1,
                    "SUCCEEDED", null, null));
            assertEquals(1, db.actions.transition("200", "APPROVED", 1,
                    "DISPATCHING", null, null));
            var action = db.actions.findById("200");
            var execution = new ExecutionMapper.NewExecution();
            execution.setId("300"); execution.setActionId("200");
            execution.setExecutionKey(action.getExecutionKey());
            execution.setDispatchRequestId("dispatch-300");
            execution.setToolName(action.getToolName()); execution.setToolVersion(action.getToolVersion());
            execution.setTargetType(action.getTargetType()); execution.setTargetId(action.getTargetId());
            execution.setBindingDigest(action.getBindingDigest());
            execution.setMaxExecutionDurationMs(120000); execution.setReceiptGracePeriodMs(180000);
            db.executions.insert(execution);
            var ticket = new TicketMapper.NewTicket();
            ticket.setId("400"); ticket.setExecutionId("300"); ticket.setSecretDigest("b".repeat(64));
            ticket.setIssuedAt(OffsetDateTime.now()); ticket.setExpiresAt(OffsetDateTime.now().plusMinutes(2));
            db.tickets.insertIssued(ticket);
            assertEquals(1, db.tickets.consume("400", OffsetDateTime.now()));
            assertEquals(0, db.tickets.consume("400", OffsetDateTime.now()));
            assertEquals(0, db.tickets.expire("400", "LATE", null));
            assertEquals(1, db.actions.transition("200", "DISPATCHING", 2,
                    "EXECUTING", null, null));
            assertEquals(1, db.actions.transition("200", "EXECUTING", 3,
                    "UNKNOWN", null, "RECEIPT_DEADLINE"));
            assertEquals(0, db.actions.transition("200", "UNKNOWN", 4,
                    "EXECUTING", null, null));
            assertEquals(1, db.actions.transition("200", "UNKNOWN", 4,
                    "FAILED", null, "PROVEN_NOT_APPLIED"));
            assertEquals(0, db.actions.transition("200", "FAILED", 5,
                    "EXECUTING", null, null));
            assertEquals(0, db.approvals.decide("200", 0, "PENDING", "42", "admin",
                    OffsetDateTime.now(), null, null, null));
            assertEquals(1, db.approvals.decide("200", 0, "APPROVED", "42", "admin",
                    OffsetDateTime.now(), OffsetDateTime.now().plusMinutes(5), null, null));
            assertEquals(0, db.approvals.decide("200", 1, "REJECTED", "42", "admin",
                    OffsetDateTime.now(), null, null, null));
        }
    }
}
