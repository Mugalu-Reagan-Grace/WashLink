package com.example.washlink.data;

import androidx.annotation.NonNull;

import com.example.washlink.models.Booking;
import com.example.washlink.models.OrderStatus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lightweight BookingService shim that does NOT require Firestore at compile time.
 * By default the app uses BookingServiceStub via BookingServiceFacade, so these
 * methods only provide graceful error messages if called while Firebase is
 * disabled.
 */
public class BookingService {

    private static BookingService instance;

    public interface BookingListener {
        void onBookingLoaded(Booking booking);
        void onError(String message);
    }

    public interface ProviderBookingsListener {
        void onBookingsChanged(List<Booking> bookings);
        void onError(String message);
    }

    public interface SimpleCallback {
        void onSuccess();
        void onError(String message);
    }

    private BookingService() {}

    public static synchronized BookingService getInstance() {
        if (instance == null) instance = new BookingService();
        return instance;
    }

    // These methods are intentionally non-functional when Firebase is disabled.
    public com.example.washlink.data.ListenerRegistration addBookingListener(String bookingId, BookingListener listener) {
        listener.onError("Firestore disabled - enable it to use real bookings.");
        return null;
    }

    public com.example.washlink.data.ListenerRegistration addProviderBookingsListener(String providerId, ProviderBookingsListener listener) {
        listener.onError("Firestore disabled - enable it to use real bookings.");
        return null;
    }

    public void removeListener(com.example.washlink.data.ListenerRegistration reg) {
        if (reg != null) reg.remove();
    }

    public void updateBookingStatus(String bookingId, OrderStatus newStatus, SimpleCallback cb) {
        cb.onError("Firestore disabled - cannot update booking status.");
    }
}
