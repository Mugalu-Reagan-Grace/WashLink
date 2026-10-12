package com.example.washlink;

import com.example.washlink.data.OrderProgressStage;
import com.example.washlink.models.OrderStatus;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class OrderProgressStageTest {
    @Test
    public void mapsActiveLifecycleStatusesToHomeProgressStages() {
        assertEquals(0, OrderProgressStage.fromStatus(OrderStatus.BOOKED));
        assertEquals(0, OrderProgressStage.fromStatus(OrderStatus.ACCEPTED));
        assertEquals(1, OrderProgressStage.fromStatus(OrderStatus.PICKED_UP));
        assertEquals(1, OrderProgressStage.fromStatus(OrderStatus.WASHING));
        assertEquals(1, OrderProgressStage.fromStatus(OrderStatus.DRYING));
        assertEquals(1, OrderProgressStage.fromStatus(OrderStatus.READY));
        assertEquals(2, OrderProgressStage.fromStatus(OrderStatus.OUT_FOR_DELIVERY));
    }

    @Test
    public void doesNotShowProgressForUnavailableOrTerminalStatuses() {
        assertEquals(-1, OrderProgressStage.fromStatus(OrderStatus.UNKNOWN));
        assertEquals(-1, OrderProgressStage.fromStatus(OrderStatus.CANCELLED));
        assertEquals(-1, OrderProgressStage.fromStatus(OrderStatus.REJECTED));
        assertEquals(-1, OrderProgressStage.fromStatus(OrderStatus.DELIVERED));
    }
}
