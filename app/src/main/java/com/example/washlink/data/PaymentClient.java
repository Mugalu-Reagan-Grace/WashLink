package com.example.washlink.data;

import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.FirebaseFunctionsException;
import com.example.washlink.models.Booking;

import java.util.HashMap;
import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Thin Android client for the authenticated Firebase callable payment API.
 * Payment credentials and transaction verification stay on the server.
 */
public final class PaymentClient {
    private PaymentClient() {}

    public static void createBooking(Booking booking, String paymentMethod,
                                     Consumer<String> onCreated, Consumer<String> onError) {
        Map<String, Object> data = new HashMap<>();
        data.put("providerId", booking.getProviderId());
        data.put("serviceType", booking.getServiceType());
        data.put("serviceName", booking.getServiceName());
        data.put("serviceNames", booking.getServiceNames() == null
                || booking.getServiceNames().isEmpty()
                ? Collections.singletonList(booking.getServiceName())
                : booking.getServiceNames());
        data.put("itemCount", booking.getItemCount());
        if (booking.getWeightKg() > 0) data.put("weightKg", booking.getWeightKg());
        data.put("subtotal", booking.getSubtotal());
        data.put("pickupFee", booking.getPickupFee());
        data.put("deliveryFee", booking.getDeliveryFee());
        data.put("serviceFee", booking.getServiceFee());
        data.put("tax", booking.getTax());
        data.put("total", booking.getTotal());
        data.put("address", booking.getAddress());
        data.put("scheduledDateTime", booking.getScheduledDateTime());
        data.put("specialInstructions", booking.getSpecialInstructions());
        data.put("paymentMethod", paymentMethod);
        FirebaseFunctions.getInstance("us-central1")
                .getHttpsCallable("createBooking")
                .call(data)
                .addOnSuccessListener(result -> {
                    Object value = result.getData();
                    if (value instanceof Map<?, ?>) {
                        Object bookingId = ((Map<?, ?>) value).get("bookingId");
                        if (bookingId instanceof String && !((String) bookingId).isEmpty()) {
                            onCreated.accept((String) bookingId);
                            return;
                        }
                    }
                    onError.accept("Booking creation returned an invalid response.");
                })
                .addOnFailureListener(error -> onError.accept(errorMessage(error)));
    }

    public static void initializePesapal(String bookingId, String paymentMethod,
                                         Consumer<String> onCheckoutUrl, Consumer<String> onError) {
        Map<String, Object> data = new HashMap<>();
        data.put("bookingId", bookingId);
        data.put("paymentMethod", paymentMethod);
        FirebaseFunctions.getInstance("us-central1")
                .getHttpsCallable("initializePesapalCheckout")
                .call(data)
                .addOnSuccessListener(result -> {
                    Object value = result.getData();
                    if (value instanceof Map<?, ?>) {
                        Object url = ((Map<?, ?>) value).get("checkoutUrl");
                        if (url instanceof String && ((String) url).startsWith("https://")) {
                            onCheckoutUrl.accept((String) url);
                            return;
                        }
                    }
                    onError.accept("The payment provider returned an invalid checkout link.");
                })
                .addOnFailureListener(error -> onError.accept(errorMessage(error)));
    }

    public static void verifyPesapal(String bookingId, String trackingId,
                                     Consumer<String> onResult, Consumer<String> onError) {
        Map<String, Object> data = new HashMap<>();
        data.put("bookingId", bookingId);
        data.put("orderTrackingId", trackingId);
        FirebaseFunctions.getInstance("us-central1")
                .getHttpsCallable("verifyPesapalPayment")
                .call(data)
                .addOnSuccessListener(result -> handlePaymentStatus(
                        result.getData(), onResult, onError))
                .addOnFailureListener(error -> onError.accept(errorMessage(error)));
    }

    public static void verifyFlutterwave(String bookingId, String transactionId,
                                         Consumer<String> onResult, Consumer<String> onError) {
        Map<String, Object> data = new HashMap<>();
        data.put("bookingId", bookingId);
        data.put("transactionId", transactionId);
        FirebaseFunctions.getInstance("us-central1")
                .getHttpsCallable("verifyFlutterwavePayment")
                .call(data)
                .addOnSuccessListener(result -> handlePaymentStatus(
                        result.getData(), onResult, onError))
                .addOnFailureListener(error -> onError.accept(errorMessage(error)));
    }

    private static void handlePaymentStatus(Object value,
                                            Consumer<String> onResult,
                                            Consumer<String> onError) {
        if (value instanceof Map<?, ?>) {
            Object status = ((Map<?, ?>) value).get("paymentStatus");
            if (status instanceof String) {
                onResult.accept((String) status);
                return;
            }
        }
        onError.accept("Payment verification returned an invalid response.");
    }

    private static String errorMessage(Exception error) {
        if (error instanceof FirebaseFunctionsException
                && ((FirebaseFunctionsException) error).getCode()
                == FirebaseFunctionsException.Code.NOT_FOUND) {
            return "Bookings are unavailable because the Firebase booking service is not deployed. "
                    + "Enable billing and deploy Functions, or connect this demo app to the local Firebase emulators.";
        }
        String message = error.getLocalizedMessage();
        return message == null || message.trim().isEmpty()
                ? "Could not connect to the payment service." : message;
    }
}
