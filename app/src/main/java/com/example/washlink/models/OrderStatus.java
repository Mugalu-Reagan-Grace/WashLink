package com.example.washlink.models;

import java.util.ArrayList;
import java.util.List;

/**
 * Canonical order lifecycle used by both customer tracking and provider updates.
 */
public enum OrderStatus {
    BOOKED(0, "Booked"),
    ACCEPTED(1, "Accepted"),
    PICKED_UP(2, "Picked Up"),
    WASHING(3, "Washing"),
    DRYING(4, "Drying"),
    READY(5, "Ready"),
    OUT_FOR_DELIVERY(6, "Out for Delivery"),
    DELIVERED(7, "Delivered"),
    REJECTED(-1, "Rejected"),
    CANCELLED(-2, "Cancelled");

    private final int sequence;
    private final String displayName;

    OrderStatus(int sequence, String displayName) {
        this.sequence = sequence;
        this.displayName = displayName;
    }

    public int getSequence() {
        return sequence;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isTerminal() {
        return this == DELIVERED || this == REJECTED || this == CANCELLED;
    }

    public static List<OrderStatus> getRemainingForwardStatuses(OrderStatus current) {
        List<OrderStatus> remaining = new ArrayList<>();
        if (current == null) return remaining;
        if (current.sequence < 0) return remaining;
        for (OrderStatus s : values()) {
            if (s.sequence > current.sequence) remaining.add(s);
        }
        remaining.sort((a,b) -> Integer.compare(a.sequence,b.sequence));
        return remaining;
    }

    public static OrderStatus fromString(String value) {
        if (value == null) return BOOKED;
        try {
            return OrderStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            return BOOKED;
        }
    }
}