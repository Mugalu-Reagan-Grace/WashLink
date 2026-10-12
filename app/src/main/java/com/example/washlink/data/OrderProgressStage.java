package com.example.washlink.data;

import com.example.washlink.models.OrderStatus;

public final class OrderProgressStage {
    private OrderProgressStage() {}

    public static int fromStatus(OrderStatus status) {
        if (status == OrderStatus.BOOKED || status == OrderStatus.ACCEPTED) return 0;
        if (status == OrderStatus.PICKED_UP || status == OrderStatus.WASHING
                || status == OrderStatus.DRYING || status == OrderStatus.READY) return 1;
        if (status == OrderStatus.OUT_FOR_DELIVERY) return 2;
        return -1;
    }
}
