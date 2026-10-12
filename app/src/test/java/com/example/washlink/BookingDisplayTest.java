package com.example.washlink;

import com.example.washlink.models.Booking;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BookingDisplayTest {
    @Test
    public void prefersExplicitServiceFee() {
        Booking booking = new Booking();
        booking.setServiceFee(900);
        booking.setTax(800);

        assertEquals(900, booking.getServiceFeeForDisplay(), 0);
    }

    @Test
    public void fallsBackToLegacyTaxFieldForServiceFee() {
        Booking booking = new Booking();
        booking.setTax(800);

        assertEquals(800, booking.getServiceFeeForDisplay(), 0);
    }
}
