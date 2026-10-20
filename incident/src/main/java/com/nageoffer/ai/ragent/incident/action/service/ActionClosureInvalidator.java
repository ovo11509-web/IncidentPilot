package com.nageoffer.ai.ragent.incident.action.service;

import com.nageoffer.ai.ragent.incident.action.dao.ActionMapper;
import com.nageoffer.ai.ragent.incident.action.dao.ApprovalMapper;
import com.nageoffer.ai.ragent.incident.action.dao.ExecutionMapper;
import com.nageoffer.ai.ragent.incident.action.dao.TicketMapper;
import com.nageoffer.ai.ragent.incident.action.timeline.ActionTimelineWriter;
import com.nageoffer.ai.ragent.incident.core.dao.IncidentMapper;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;

/** Runs inside the Incident close transaction after the Incident row has been locked. */
@Service
public final class ActionClosureInvalidator {
    private final ActionMapper actions;
    private final ApprovalMapper approvals;
    private final ExecutionMapper executions;
    private final TicketMapper tickets;
    private final IncidentMapper incidents;
    private final ActionTimelineWriter timeline;

    public ActionClosureInvalidator(ActionMapper actions, ApprovalMapper approvals,
            ExecutionMapper executions, TicketMapper tickets, IncidentMapper incidents,
            ActionTimelineWriter timeline) {
        this.actions = actions;
        this.approvals = approvals;
        this.executions = executions;
        this.tickets = tickets;
        this.incidents = incidents;
        this.timeline = timeline;
    }

    public void invalidate(String incidentId, String correlationId, OffsetDateTime at) {
        for (var action : actions.lockUnstartedByIncident(incidentId)) {
            if ("DISPATCHING".equals(action.getState())) {
                var execution = executions.findByActionId(action.getId());
                if (execution != null) {
                    var ticket = tickets.lockIssued(execution.getId());
                    if (ticket != null) {
                        if (tickets.expire(ticket.getId(), "INCIDENT_CLOSED", null) != 1) {
                            throw new IllegalStateException("ticket invalidation conflict");
                        }
                        timeline.ticketExpired(incidentId, reserve(incidentId), execution.getId(),
                                ticket.getId(), correlationId, at);
                    }
                }
            }
            if (actions.transition(action.getId(), action.getState(), action.getVersion(),
                    "EXPIRED", null, "INCIDENT_CLOSED") != 1) {
                throw new IllegalStateException("Action invalidation conflict");
            }
            if ("PENDING_APPROVAL".equals(action.getState())) {
                var approval = approvals.lockByActionId(action.getId());
                if (approval == null || approvals.decide(action.getId(), approval.getVersion(),
                        "EXPIRED", null, null, at, null, "INCIDENT_CLOSED", null) != 1) {
                    throw new IllegalStateException("approval invalidation conflict");
                }
            }
            timeline.statusChanged(incidentId, reserve(incidentId), action.getId(),
                    "EXPIRED", "INCIDENT_CLOSED", correlationId, at);
        }
    }

    private long reserve(String incidentId) {
        var incident = incidents.findById(incidentId);
        if (incident == null || incidents.reserveTimeline(incidentId, incident.getTimelineSeq(),
                incident.getVersion(), 1) != 1) {
            throw new IllegalStateException("Action close timeline conflict");
        }
        return incident.getTimelineSeq() + 1;
    }
}
