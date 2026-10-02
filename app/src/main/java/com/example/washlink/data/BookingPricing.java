package com.example.washlink.data;

import java.util.Locale;

public final class BookingPricing {
    public static final int PRICE_PER_KG = 5000;
    public static final int MINIMUM_KG = 5;
    public static final int PICKUP_FEE = 5000;
    public static final int DELIVERY_FEE = 5000;
    public static final double SERVICE_FEE_RATE = 0.03;

    private BookingPricing() {}

    public static int estimateWeightKg(int itemCount) {
        if (itemCount <= 0) return MINIMUM_KG;
        double estimated = itemCount * 0.45d;
        int rounded = (int) Math.round(estimated);
        return Math.max(MINIMUM_KG, rounded);
    }

    public static Quote quote(int kilograms, boolean pickupAndDelivery) {
        return quote(kilograms, pickupAndDelivery, PRICE_PER_KG);
    }

    public static Quote quote(int kilograms, boolean pickupAndDelivery, double pricePerKg) {
        if (!Double.isFinite(pricePerKg) || pricePerKg <= 0) {
            throw new IllegalArgumentException("Price per kilogram must be a positive number.");
        }
        int billableKg = Math.max(MINIMUM_KG, kilograms);
        int laundry = (int) Math.round(billableKg * pricePerKg);
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
