package com.example.washlink;

import com.example.washlink.data.BookingSlotAvailability;

import org.junit.Test;

import java.util.Calendar;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BookingSlotAvailabilityTest {
    @Test
    public void elapsedAndCurrentHourSlotsAreUnavailable() {
        Calendar date = dateAt(2026, Calendar.OCTOBER, 6, 0, 0);
        Calendar now = dateAt(2026, Calendar.OCTOBER, 6, 10, 1);

        assertFalse(BookingSlotAvailability.isFutureSlot(date, 0, 9, now));
        assertFalse(BookingSlotAvailability.isFutureSlot(date, 0, 10, now));
        assertTrue(BookingSlotAvailability.isFutureSlot(date, 0, 11, now));
    }

    @Test
    public void nextDaySlotsRemainAvailableAfterTodayHasClosed() {
        Calendar date = dateAt(2026, Calendar.OCTOBER, 6, 0, 0);
        Calendar now = dateAt(2026, Calendar.OCTOBER, 6, 23, 30);

        assertFalse(BookingSlotAvailability.isFutureSlot(date, 0, 17, now));
        assertTrue(BookingSlotAvailability.isFutureSlot(date, 1, 9, now));
    }

    private Calendar dateAt(int year, int month, int day, int hour, int minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month, day, hour, minute);
        return calendar;
    }
}
