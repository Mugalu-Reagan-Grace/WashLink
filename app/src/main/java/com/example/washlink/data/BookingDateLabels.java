package com.example.washlink.data;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public final class BookingDateLabels {
    private BookingDateLabels() {}

    public static String weekday(Calendar startDate, int dayOffset, Locale locale) {
        return format(startDate, dayOffset, "EEE", locale);
    }

    public static String chipDate(Calendar startDate, int dayOffset, Locale locale) {
        return format(startDate, dayOffset, "d", locale);
    }

    public static String selectedDate(Calendar startDate, int dayOffset, Locale locale) {
        return format(startDate, dayOffset, "EEE, MMM d", locale);
    }

    private static String format(Calendar startDate, int dayOffset, String pattern,
                                 Locale locale) {
        Calendar date = (Calendar) startDate.clone();
        date.add(Calendar.DAY_OF_YEAR, dayOffset);
        return new SimpleDateFormat(pattern, locale).format(date.getTime());
    }
}
