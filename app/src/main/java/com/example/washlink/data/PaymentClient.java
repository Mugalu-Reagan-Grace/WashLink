package com.example.washlink.data;

import android.os.Handler;
import android.os.Looper;

import com.example.washlink.BuildConfig;
import com.example.washlink.models.Booking;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Sends authenticated booking and Pesapal requests to the independently hosted WashLink API.
 */
public final class PaymentClient {
    private static final ExecutorService NETWORK = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private PaymentClient() {}

    public static void createBooking(Booking booking, String paymentMethod,
                                     Consumer<String> onCreated, Consumer<String> onError) {
        JSONObject data = new JSONObject();
        try {
            data.put("providerId", booking.getProviderId());
            data.put("serviceType", booking.getServiceType());
            data.put("serviceName", booking.getServiceName());
            data.put("serviceNames", new JSONArray(booking.getServiceNames() == null
                    || booking.getServiceNames().isEmpty()
                    ? Collections.singletonList(booking.getServiceName())
                    : booking.getServiceNames()));
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
        } catch (Exception error) {
            onMain(() -> onError.accept("Could not prepare the booking request."));
            return;
        }
        request("/v1/bookings", data, result -> {
            String bookingId = result.optString("bookingId", "");
            if (bookingId.isEmpty()) {
                onError.accept("Booking creation returned an invalid response.");
            } else {
                onCreated.accept(bookingId);
            }
        }, onError);
    }

    public static void initializePesapal(String bookingId, String paymentMethod,
                                         Consumer<String> onCheckoutUrl, Consumer<String> onError) {
        JSONObject data = new JSONObject();
        try {
            data.put("bookingId", bookingId);
            data.put("paymentMethod", paymentMethod);
        } catch (Exception error) {
            onMain(() -> onError.accept("Could not prepare the payment request."));
            return;
        }
        request("/v1/payments/pesapal/initialize", data, result -> {
            String url = result.optString("checkoutUrl", "");
            if (url.startsWith("https://")) {
                onCheckoutUrl.accept(url);
            } else {
                onError.accept("The payment provider returned an invalid checkout link.");
            }
        }, onError);
    }

    public static void verifyPesapal(String bookingId, String trackingId,
                                     Consumer<String> onResult, Consumer<String> onError) {
        JSONObject data = new JSONObject();
        try {
            data.put("bookingId", bookingId);
            data.put("orderTrackingId", trackingId);
        } catch (Exception error) {
            onMain(() -> onError.accept("Could not prepare the payment verification request."));
            return;
        }
        request("/v1/payments/pesapal/verify", data, result -> {
            String status = result.optString("paymentStatus", "");
            if (!status.isEmpty()) {
                onResult.accept(status);
            } else {
                onError.accept("Payment verification returned an invalid response.");
            }
        }, onError);
    }

    public static void updateBookingStatus(String bookingId, String status,
                                           Runnable onSuccess, Consumer<String> onError) {
        Map<String, Object> values = new HashMap<>();
        values.put("bookingId", bookingId);
        values.put("status", status);
        request("/v1/bookings/status", toJson(values), ignored -> onSuccess.run(), onError);
    }

    public static void cancelBooking(String bookingId,
                                     Runnable onSuccess, Consumer<String> onError) {
        Map<String, Object> values = new HashMap<>();
        values.put("bookingId", bookingId);
        request("/v1/bookings/cancel", toJson(values), ignored -> onSuccess.run(), onError);
    }

    public static void submitBookingReview(String bookingId, int rating, String reviewText,
                                           Runnable onSuccess, Consumer<String> onError) {
        Map<String, Object> values = new HashMap<>();
        values.put("bookingId", bookingId);
        values.put("rating", rating);
        values.put("reviewText", reviewText);
        request("/v1/bookings/review", toJson(values), ignored -> onSuccess.run(), onError);
    }

    public static void assignBookingRider(String bookingId, String riderId,
                                          Runnable onSuccess, Consumer<String> onError) {
        Map<String, Object> values = new HashMap<>();
        values.put("bookingId", bookingId);
        values.put("riderId", riderId);
        request("/v1/bookings/assign-rider", toJson(values), ignored -> onSuccess.run(), onError);
    }

    private static void request(String path, JSONObject body, Consumer<JSONObject> onSuccess,
                                Consumer<String> onError) {
        if (BuildConfig.WASHLINK_API_BASE_URL.isEmpty()) {
            onMain(() -> onError.accept("The WashLink payment server is not configured in this app."));
            return;
        }
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            onMain(() -> onError.accept("Sign in again before creating a booking or payment."));
            return;
        }
        user.getIdToken(false).addOnSuccessListener(tokenResult -> {
            String token = tokenResult.getToken();
            if (token == null || token.isEmpty()) {
                onError.accept("Could not authenticate with the WashLink payment server.");
                return;
            }
            NETWORK.execute(() -> {
                HttpURLConnection connection = null;
                try {
                    URL url = new URL(BuildConfig.WASHLINK_API_BASE_URL + path);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("POST");
                    connection.setConnectTimeout(15000);
                    connection.setReadTimeout(30000);
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Authorization", "Bearer " + token);
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    connection.setRequestProperty("Accept", "application/json");
                    byte[] requestBody = body.toString().getBytes(StandardCharsets.UTF_8);
                    try (OutputStream output = connection.getOutputStream()) {
                        output.write(requestBody);
                    }
                    int status = connection.getResponseCode();
                    InputStream stream = status >= 200 && status < 300
                            ? connection.getInputStream() : connection.getErrorStream();
                    String responseBody = readBody(stream);
                    JSONObject result = responseBody.isEmpty()
                            ? new JSONObject() : new JSONObject(responseBody);
                    if (status < 200 || status >= 300) {
                        String message = result.optString("message",
                                "The WashLink payment server could not complete the request.");
                        onMain(() -> onError.accept(message));
                    } else {
                        onMain(() -> onSuccess.accept(result));
                    }
                } catch (Exception error) {
                    String message = error.getLocalizedMessage();
                    onMain(() -> onError.accept(message == null || message.trim().isEmpty()
                            ? "Could not connect to the WashLink payment server." : message));
                } finally {
                    if (connection != null) connection.disconnect();
                }
            });
        }).addOnFailureListener(error -> {
            String message = error.getLocalizedMessage();
            onError.accept(message == null || message.trim().isEmpty()
                    ? "Could not authenticate with the WashLink payment server." : message);
        });
    }

    private static JSONObject toJson(Map<String, Object> values) {
        return new JSONObject(values);
    }

    private static String readBody(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) body.append(line);
        }
        return body.toString();
    }

    private static void onMain(Runnable action) {
        MAIN.post(action);
    }
}
