package com.example.washlink.data;

import androidx.annotation.NonNull;

import com.example.washlink.models.Booking;
import com.example.washlink.models.OrderStatus;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
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

    public interface CustomerBookingsListener {
        void onBookingsChanged(List<Booking> bookings);
        void onError(String message);
    }

    public interface SimpleCallback {
        void onSuccess();
        void onError(String message);
    }

    private BookingService() {}

    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();

    public static synchronized BookingService getInstance() {
        if (instance == null) instance = new BookingService();
        return instance;
    }

    public com.example.washlink.data.ListenerRegistration addBookingListener(String bookingId, BookingListener listener) {
        com.google.firebase.firestore.ListenerRegistration registration = firestore.collection("bookings").document(bookingId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        listener.onError(error.getMessage());
                    } else if (snapshot != null && snapshot.exists()) {
                        Booking booking = snapshot.toObject(Booking.class);
                        if (booking != null) {
                            booking.setId(snapshot.getId());
                            listener.onBookingLoaded(booking);
                        }
                    } else {
                        listener.onError("Booking not found");
                    }
                });
        return registration::remove;
    }

    public com.example.washlink.data.ListenerRegistration addProviderBookingsListener(String providerId, ProviderBookingsListener listener) {
        return addBookingsQuery("providerId", providerId, listener);
    }

    public com.example.washlink.data.ListenerRegistration addCustomerBookingsListener(
            String customerId, CustomerBookingsListener listener) {
        Query query = firestore.collection("bookings")
                .whereEqualTo("customerId", customerId);
        com.google.firebase.firestore.ListenerRegistration registration = query.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                listener.onError(error.getMessage());
                return;
            }
            List<Booking> bookings = new ArrayList<>();
            if (snapshot != null) {
                for (com.google.firebase.firestore.DocumentSnapshot document : snapshot.getDocuments()) {
                    Booking booking = document.toObject(Booking.class);
                    if (booking != null) {
                        booking.setId(document.getId());
                        bookings.add(booking);
                    }
                }
            }
            listener.onBookingsChanged(bookings);
        });
        return registration::remove;
    }

    private com.example.washlink.data.ListenerRegistration addBookingsQuery(
            String field, String value, ProviderBookingsListener listener) {
        com.google.firebase.firestore.        ListenerRegistration registration = firestore.collection("bookings")
                .whereEqualTo(field, value)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        listener.onError(error.getMessage());
                        return;
                    }
                    List<Booking> bookings = new ArrayList<>();
                    if (snapshot != null) {
                        for (com.google.firebase.firestore.DocumentSnapshot document : snapshot.getDocuments()) {
                            Booking booking = document.toObject(Booking.class);
                            if (booking != null) {
                                booking.setId(document.getId());
                                bookings.add(booking);
                            }
                        }
                    }
                    listener.onBookingsChanged(bookings);
                });
        return registration::remove;
    }

    public void removeListener(com.example.washlink.data.ListenerRegistration reg) {
        if (reg != null) reg.remove();
    }

    public void updateBookingStatus(String bookingId, OrderStatus newStatus, SimpleCallback cb) {
        firestore.collection("bookings").document(bookingId)
                .update("status", newStatus.name(), "updatedAt", System.currentTimeMillis())
                .addOnSuccessListener(unused -> cb.onSuccess())
                .addOnFailureListener(error -> cb.onError(error.getMessage()));
    }

    public void createBooking(Booking booking, SimpleCallback cb) {
        String id = firestore.collection("bookings").document().getId();
        booking.setId(id);
        firestore.collection("bookings").document(id).set(booking)
                .addOnSuccessListener(unused -> cb.onSuccess())
                .addOnFailureListener(error -> cb.onError(error.getMessage()));
    }
}
