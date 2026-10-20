package com.nageoffer.ai.ragent.incident.action.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.dev33.satoken.stp.StpUtil;
import com.nageoffer.ai.ragent.incident.action.service.ActionProposalService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ActionProposalTest {
    @Test
    void proposalIsAtomicIdempotentAndRestrictedToRegisteredSimulation() throws Exception {
        try (var db = new ActionDbFixture(); var stp = Mockito.mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("42");
            db.seedIncident("100");
            var service = new ActionProposalService(db.actions, db.approvals, db.guard,
                    db.actionTimeline, db.alerts, db.properties);
            var request = new ActionProposalService.UserProposal("100", "request-1", null,
                    "simulation.set-state", "1", "demo", "{\"desiredState\":\"PAUSED\"}");
            var first = service.proposeUser(request);
            var retry = service.proposeUser(request);
            assertFalse(first.repeated());
            assertTrue(retry.repeated());
            assertEquals(first.action().getId(), retry.action().getId());
            assertEquals("PENDING", db.approvals.findByActionId(first.action().getId()).getStatus());
            assertEquals(1, db.timeline.findByIncidentId("100").size());
            assertThrows(ActionProposalService.SourceConflictException.class,
                    () -> service.proposeUser(new ActionProposalService.UserProposal("100", "request-1", null,
                            "simulation.set-state", "1", "demo", "{\"desiredState\":\"READY\"}")));
            assertThrows(IllegalArgumentException.class,
                    () -> service.proposeUser(new ActionProposalService.UserProposal("100", "request-2", null,
                            "simulation.set-state", "1", "production", "{\"desiredState\":\"PAUSED\"}")));
            assertEquals(1, db.actions.findByIncidentId("100").size());
        }
    }
}
