package com.example.washlink;

import com.example.washlink.models.BookingNotification;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BookingNotificationTest {
    @Test
    public void legacyNotificationWithoutReadFlagIsUnread() {
        BookingNotification notification = new BookingNotification();

        assertTrue(notification.isUnread());
    }

    @Test
    public void markingNotificationReadChangesUnreadState() {
        BookingNotification notification = new BookingNotification();

        notification.markRead();

        assertFalse(notification.isUnread());
    }
}
