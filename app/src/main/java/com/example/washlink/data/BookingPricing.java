package com.example.washlink.data;

import java.util.Locale;

public final class BookingPricing {
    public static final int PRICE_PER_KG = 5000;
    public static final int MINIMUM_KG = 5;
    public static final int PICKUP_FEE = 5000;
    public static final int DELIVERY_FEE = 5000;
    public static final double SERVICE_FEE_RATE = 0.03;

    private BookingPricing() {}

    public static Quote quote(int kilograms, boolean pickupAndDelivery) {
        int billableKg = Math.max(MINIMUM_KG, kilograms);
        int laundry = billableKg * PRICE_PER_KG;
        int pickup = pickupAndDelivery ? PICKUP_FEE : 0;
        int delivery = pickupAndDelivery ? DELIVERY_FEE : 0;
        int serviceFee = (int) Math.round((laundry + pickup + delivery) * SERVICE_FEE_RATE);
        return new Quote(billableKg, laundry, pickup, delivery, serviceFee,
                laundry + pickup + delivery + serviceFee);
    }

    public static String format(int amount) {
        return String.format(Locale.US, "UGX %,d", amount);
    }

    public static final class Quote {
        public final int kilograms;
        public final int laundry;
        public final int pickup;
        public final int delivery;
        public final int serviceFee;
        public final int total;

        private Quote(int kilograms, int laundry, int pickup, int delivery,
                      int serviceFee, int total) {
            this.kilograms = kilograms;
            this.laundry = laundry;
            this.pickup = pickup;
            this.delivery = delivery;
            this.serviceFee = serviceFee;
            this.total = total;
        }
    }
}
