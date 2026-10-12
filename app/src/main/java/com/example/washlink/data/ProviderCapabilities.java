package com.example.washlink.data;

public final class ProviderCapabilities {
    private ProviderCapabilities() { }

    public static boolean supportsBookingMode(String bookingMode, Object supportsDropoff,
                                              Object supportsPickupDelivery) {
        return "Drop Off".equalsIgnoreCase(bookingMode)
                ? !Boolean.FALSE.equals(supportsDropoff)
                : !Boolean.FALSE.equals(supportsPickupDelivery);
    }

    public static String preferredBookingMode(Object supportsDropoff,
                                              Object supportsPickupDelivery) {
        return supportsBookingMode("Pickup & Delivery", supportsDropoff, supportsPickupDelivery)
                ? "Pickup & Delivery" : "Drop Off";
    }
}
