package com.nageoffer.ai.ragent.agent.confirm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import com.nageoffer.ai.ragent.incident.action.dao.ActionMapper;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IncidentActionConfirmationAdapterTest {
    @Test
    void cardContainsOnlyAuthoritativeIdAndState() {
        var actions = mock(ActionMapper.class);
        var row = new ActionMapper.ActionRow();
        row.setId("123");
        row.setState("PENDING_APPROVAL");
        when(actions.findById("123")).thenReturn(row);
        var adapter = new IncidentActionConfirmationAdapter(actions);
        var card = adapter.load("123");
        assertEquals(2, card.safeFields().size());
        assertEquals("123", card.safeFields().get("actionId"));
        row.setId("different");
        assertThrows(IllegalStateException.class, () -> adapter.load("123"));
        assertThrows(IllegalArgumentException.class, () -> adapter.load("message-1"));
    }
}
