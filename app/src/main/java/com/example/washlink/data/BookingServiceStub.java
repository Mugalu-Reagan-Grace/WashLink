package com.example.washlink.data;

import androidx.annotation.NonNull;

import com.example.washlink.models.Booking;
import com.example.washlink.models.OrderStatus;
import com.example.washlink.data.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory stub implementation of booking service used when Firestore is not
 * configured. Provides real-time-style listeners and status updates.
 */
public class BookingServiceStub {

    private static BookingServiceStub instance;
    private final Map<String, Booking> bookings = new HashMap<>();
    private final List<BookingListenerHolder> listeners = new ArrayList<>();

    private static class BookingListenerHolder {
        String bookingId;
        BookingService.BookingListener listener;
    }

    private BookingServiceStub() {
        // seed with sample bookings
        Booking b1 = new Booking("cust-1", "Alice", "prov-1", "Bob's Laundry",
                Booking.SERVICE_TYPE_PICKUP, "Wash & Fold", "123 Main St");
        b1.setId("BKG-1001");
        b1.setStatus(OrderStatus.PICKED_UP.name());

        Booking b2 = new Booking("cust-2", "Charles", "prov-1", "Bob's Laundry",
                Booking.SERVICE_TYPE_PICKUP, "Wash & Iron", "45 High St");
        b2.setId("BKG-1002");
        b2.setStatus(OrderStatus.WASHING.name());

        bookings.put(b1.getId(), b1);
        bookings.put(b2.getId(), b2);
    }

    public static synchronized BookingServiceStub getInstance() {
        if (instance == null) instance = new BookingServiceStub();
        return instance;
    }

    public ListenerRegistration addBookingListener(String bookingId, BookingService.BookingListener listener) {
        BookingListenerHolder h = new BookingListenerHolder();
        h.bookingId = bookingId;
        h.listener = listener;
        listeners.add(h);
        // Immediately notify with current state
        Booking b = bookings.get(bookingId);
        if (b != null) listener.onBookingLoaded(b);
        else listener.onError("Booking not found (stub)");
        // Return a dummy ListenerRegistration that removes this listener on remove()
        return new ListenerRegistration() {
            @Override
            public void remove() {
                listeners.remove(h);
            }
        };
    }

    public void removeListener(ListenerRegistration reg) {
        if (reg != null) reg.remove();
    }

    public void updateBookingStatus(String bookingId, OrderStatus newStatus, BookingService.SimpleCallback cb) {
        Booking b = bookings.get(bookingId);
        if (b == null) {
            cb.onError("Booking not found (stub)");
            return;
        }
        b.setStatus(newStatus.name());
        b.setUpdatedAt(System.currentTimeMillis());
        // notify listeners for this booking
        for (BookingListenerHolder h : new ArrayList<>(listeners)) {
            if (h.bookingId != null && h.bookingId.equals(bookingId)) {
                h.listener.onBookingLoaded(b);
            }
        }
        cb.onSuccess();
    }

    public List<Booking> getAllBookingsForProvider(String providerId) {
        List<Booking> out = new ArrayList<>();
        for (Booking b : bookings.values()) {
            if (providerId == null || providerId.isEmpty() || providerId.equals(b.getProviderId())) {
                out.add(b);
            }
        }
        return out;
    }
}