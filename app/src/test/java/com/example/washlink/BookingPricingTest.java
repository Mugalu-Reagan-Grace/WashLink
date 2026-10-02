package com.example.washlink;

import com.example.washlink.data.BookingPricing;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BookingPricingTest {
    @Test
    public void quoteUsesProviderRateForPickupAndDelivery() {
        BookingPricing.Quote quote = BookingPricing.quote(5, true, 12000);

        assertEquals(60000, quote.laundry);
        assertEquals(5000, quote.pickup);
        assertEquals(5000, quote.delivery);
        assertEquals(2100, quote.serviceFee);
        assertEquals(72100, quote.total);
    }

    @Test
    public void quoteUsesProviderRateForDropOff() {
        BookingPricing.Quote quote = BookingPricing.quote(5, false, 12000);

        assertEquals(60000, quote.laundry);
        assertEquals(0, quote.pickup);
        assertEquals(0, quote.delivery);
        assertEquals(1800, quote.serviceFee);
        assertEquals(61800, quote.total);
    }
}
