package com.example.washlink.data;

import com.google.firebase.functions.FirebaseFunctions;
import com.example.washlink.models.Booking;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Thin Android client for the authenticated Firebase callable payment API.
 * Flutterwave credentials and transaction verification stay on the server.
 */
public final class FlutterwavePaymentClient {
    private FlutterwavePaymentClient() {}

    public static void createBooking(Booking booking, String paymentMethod,
                                     Consumer<String> onCreated, Consumer<String> onError) {
        Map<String, Object> data = new HashMap<>();
        data.put("providerId", booking.getProviderId());
        data.put("serviceType", booking.getServiceType());
        data.put("serviceName", booking.getServiceName());
        data.put("itemCount", booking.getItemCount());
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

    public static void initialize(String bookingId, String paymentMethod,
                                  Consumer<String> onCheckoutUrl, Consumer<String> onError) {
        Map<String, Object> data = new HashMap<>();
        data.put("bookingId", bookingId);
        data.put("paymentMethod", paymentMethod);
        FirebaseFunctions.getInstance("us-central1")
                .getHttpsCallable("initializeFlutterwaveCheckout")
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

    public static void registerCashOnDelivery(String bookingId,
                                              Runnable onReady, Consumer<String> onError) {
        Map<String, Object> data = new HashMap<>();
        data.put("bookingId", bookingId);
        data.put("paymentMethod", "cash");
        FirebaseFunctions.getInstance("us-central1")
                .getHttpsCallable("initializeFlutterwaveCheckout")
                .call(data)
                .addOnSuccessListener(result -> {
                    Object value = result.getData();
                    if (value instanceof Map<?, ?>
                            && Boolean.TRUE.equals(((Map<?, ?>) value).get("cashOnDelivery"))) {
                        onReady.run();
                    } else {
                        onError.accept("Could not confirm cash on delivery for this booking.");
                    }
                })
                .addOnFailureListener(error -> onError.accept(errorMessage(error)));
    }

    public static void verify(String bookingId, String transactionId,
                              Consumer<String> onResult, Consumer<String> onError) {
        Map<String, Object> data = new HashMap<>();
        data.put("bookingId", bookingId);
        data.put("transactionId", transactionId);
        FirebaseFunctions.getInstance("us-central1")
                .getHttpsCallable("verifyFlutterwavePayment")
                .call(data)
                .addOnSuccessListener(result -> {
                    Object value = result.getData();
                    if (value instanceof Map<?, ?>) {
                        Object status = ((Map<?, ?>) value).get("paymentStatus");
                        if (status instanceof String) {
                            onResult.accept((String) status);
                            return;
                        }
                    }
                    onError.accept("Payment verification returned an invalid response.");
                })
                .addOnFailureListener(error -> onError.accept(errorMessage(error)));
    }

    private static String errorMessage(Exception error) {
        String message = error.getLocalizedMessage();
        return message == null || message.trim().isEmpty()
                ? "Could not connect to the payment service." : message;
    }
}
