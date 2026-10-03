package com.example.washlink;

import com.example.washlink.models.OrderStatus;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OrderStatusTest {
    @Test
    public void providerCanAdvanceOnlyToTheNextLifecycleStatus() {
        assertEquals(Collections.singletonList(OrderStatus.ACCEPTED),
                OrderStatus.getRemainingForwardStatuses(OrderStatus.BOOKED));
        assertEquals(Collections.singletonList(OrderStatus.DRYING),
                OrderStatus.getRemainingForwardStatuses(OrderStatus.WASHING));
    }

    @Test
    public void terminalStatusesCannotAdvance() {
        assertTrue(OrderStatus.getRemainingForwardStatuses(OrderStatus.DELIVERED).isEmpty());
        assertTrue(OrderStatus.getRemainingForwardStatuses(OrderStatus.REJECTED).isEmpty());
    }

    @Test
    public void missingOrUnknownPersistedStatusesAreNotPresentedAsBooked() {
        assertEquals(OrderStatus.UNKNOWN, OrderStatus.fromString(null));
        assertEquals(OrderStatus.UNKNOWN, OrderStatus.fromString("NOT_A_STATUS"));
        assertEquals("Status unavailable", OrderStatus.fromString(null).getDisplayName());
        assertTrue(OrderStatus.fromString(null).isTerminal());
        assertFalse(OrderStatus.fromString("BOOKED").isTerminal());
    }
}
