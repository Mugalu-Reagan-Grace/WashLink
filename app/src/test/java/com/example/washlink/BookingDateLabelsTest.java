package com.example.washlink;

import com.example.washlink.data.BookingDateLabels;

import org.junit.Test;

import java.util.Calendar;
import java.util.Locale;

import static org.junit.Assert.assertEquals;

public class BookingDateLabelsTest {
    @Test
    public void labelsTodayAndNextDaysFromOneStartDate() {
        Calendar startDate = Calendar.getInstance();
        startDate.clear();
        startDate.set(2026, Calendar.OCTOBER, 6);

        assertEquals("Tue", BookingDateLabels.weekday(startDate, 0, Locale.US));
        assertEquals("6", BookingDateLabels.chipDate(startDate, 0, Locale.US));
        assertEquals("Wed", BookingDateLabels.weekday(startDate, 1, Locale.US));
        assertEquals("7", BookingDateLabels.chipDate(startDate, 1, Locale.US));
        assertEquals("Wed, Oct 7",
                BookingDateLabels.selectedDate(startDate, 1, Locale.US));
        assertEquals(Calendar.OCTOBER, startDate.get(Calendar.MONTH));
        assertEquals(6, startDate.get(Calendar.DAY_OF_MONTH));
    }

    @Test
    public void labelsRollForwardAcrossMonthBoundary() {
        Calendar startDate = Calendar.getInstance();
        startDate.clear();
        startDate.set(2026, Calendar.OCTOBER, 31);

        assertEquals("Sun", BookingDateLabels.weekday(startDate, 1, Locale.US));
        assertEquals("1", BookingDateLabels.chipDate(startDate, 1, Locale.US));
        assertEquals("Sun, Nov 1",
                BookingDateLabels.selectedDate(startDate, 1, Locale.US));
    }
}
