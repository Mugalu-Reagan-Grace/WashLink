package com.example.washlink.data;

import java.util.Calendar;

public final class BookingSlotAvailability {
    private BookingSlotAvailability() {}

    public static boolean isFutureSlot(Calendar dateRangeStart, int dayOffset,
                                       int hourOfDay, Calendar now) {
        Calendar slot = (Calendar) dateRangeStart.clone();
        slot.add(Calendar.DAY_OF_YEAR, dayOffset);
        slot.set(Calendar.HOUR_OF_DAY, hourOfDay);
        slot.set(Calendar.MINUTE, 0);
        slot.set(Calendar.SECOND, 0);
        slot.set(Calendar.MILLISECOND, 0);
        return slot.after(now);
    }
}
