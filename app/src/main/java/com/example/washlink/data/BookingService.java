package com.example.washlink.data;

import com.example.washlink.models.Booking;
import com.example.washlink.models.BookingNotification;
import com.example.washlink.models.OrderStatus;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Firestore-backed booking data and live customer notification service.
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

    public interface CustomerNotificationsListener {
        void onNotificationsChanged(List<BookingNotification> notifications);
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
                .whereEqualTo("customerId", customerId)
                .orderBy("createdAt", Query.Direction.DESCENDING);
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

    public com.example.washlink.data.ListenerRegistration addCustomerNotificationsListener(
            String customerId, CustomerNotificationsListener listener) {
        com.google.firebase.firestore.ListenerRegistration registration = firestore
                .collection("users").document(customerId).collection("notifications")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        listener.onError(error.getMessage());
                        return;
                    }
                    List<BookingNotification> notifications = new ArrayList<>();
                    if (snapshot != null) {
                        for (com.google.firebase.firestore.DocumentSnapshot document : snapshot.getDocuments()) {
                            BookingNotification notification = document.toObject(BookingNotification.class);
                            if (notification != null) {
                                notification.setId(document.getId());
                                notifications.add(notification);
                            }
                        }
                    }
                    listener.onNotificationsChanged(notifications);
                });
        return registration::remove;
    }

    private com.example.washlink.data.ListenerRegistration addBookingsQuery(
            String field, String value, ProviderBookingsListener listener) {
        com.google.firebase.firestore.ListenerRegistration registration = firestore.collection("bookings")
                .whereEqualTo(field, value)
                .orderBy("createdAt", Query.Direction.DESCENDING)
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
        if (bookingId == null || bookingId.trim().isEmpty() || newStatus == null) {
            cb.onError("A booking and next status are required.");
            return;
        }
        PaymentClient.updateBookingStatus(bookingId, newStatus.name(), cb::onSuccess, cb::onError);
    }

    public void cancelBooking(String bookingId, SimpleCallback cb) {
        if (bookingId == null || bookingId.trim().isEmpty()) {
            cb.onError("A booking is required.");
            return;
        }
        PaymentClient.cancelBooking(bookingId, cb::onSuccess, cb::onError);
    }

    public void submitBookingReview(String bookingId, int rating, String reviewText,
                                    SimpleCallback callback) {
        if (bookingId == null || bookingId.trim().isEmpty()
                || rating < 1 || rating > 5 || reviewText == null
                || reviewText.trim().length() > 1000) {
            callback.onError("Choose a rating from 1 to 5 and enter a review of at most 1,000 characters.");
            return;
        }
        PaymentClient.submitBookingReview(bookingId, rating, reviewText.trim(),
                callback::onSuccess, callback::onError);
    }

    public void assignBookingRider(String bookingId, String riderId, SimpleCallback callback) {
        if (bookingId == null || bookingId.trim().isEmpty()
                || riderId == null || riderId.trim().isEmpty()) {
            callback.onError("Select a booking and rider.");
            return;
        }
        PaymentClient.assignBookingRider(bookingId, riderId,
                callback::onSuccess, callback::onError);
    }

    public void createBooking(Booking booking, SimpleCallback cb) {
        PaymentClient.createBooking(booking, paymentMethodCode(booking.getPaymentMethod()),
                bookingId -> {
                    booking.setId(bookingId);
                    cb.onSuccess();
                }, cb::onError);
    }

    private String paymentMethodCode(String paymentMethod) {
        if ("Cash".equalsIgnoreCase(paymentMethod)) return "cash";
        if ("Airtel Money".equalsIgnoreCase(paymentMethod)) return "airtel";
        if ("MTN Mobile Money".equalsIgnoreCase(paymentMethod)) return "mtn";
        return "card";
    }

}
