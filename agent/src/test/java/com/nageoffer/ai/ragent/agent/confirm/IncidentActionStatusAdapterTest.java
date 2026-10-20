package com.nageoffer.ai.ragent.agent.confirm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nageoffer.ai.ragent.incident.action.dao.ActionMapper;
import org.junit.jupiter.api.Test;

class IncidentActionStatusAdapterTest {
    @Test
    void restoredStatusKeepsWorkerOwnershipAndUnknownEvidencePath() {
        var actions = mock(ActionMapper.class);
        var row = new ActionMapper.ActionRow();
        row.setId("200"); row.setIncidentId("100"); row.setState("APPROVED");
        when(actions.findById("200")).thenReturn(row);
        var adapter = new IncidentActionStatusAdapter(actions);
        assertEquals("AWAIT_DISPATCH", adapter.load("200").get("nextStep"));
        row.setState("UNKNOWN");
        assertEquals("RECONCILE", adapter.load("200").get("nextStep"));
        verify(actions, org.mockito.Mockito.times(2)).findById("200");
    }
}
