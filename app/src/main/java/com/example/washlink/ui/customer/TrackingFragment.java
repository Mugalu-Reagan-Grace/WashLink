package com.example.washlink.ui.customer;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.washlink.R;
import com.example.washlink.OrderTrackingRenderer;
import com.example.washlink.BookingCancellationHelper;
import com.example.washlink.data.BookingService;
import com.example.washlink.data.BookingServiceFacade;
import com.example.washlink.data.ListenerRegistration;
import com.example.washlink.models.Booking;
import com.example.washlink.models.OrderStatus;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class TrackingFragment extends CustomerTabFragment {
    private ListenerRegistration registration;
    private String providerPhone;
    @Override
    public View onCreateView(@NonNull android.view.LayoutInflater inflater,
                             @Nullable android.view.ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_customer_tracking, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        bindNavigation(view, MainActivity.TAB_TRACKING);
        View bell = view.findViewById(R.id.iv_bell);
        if (bell != null) bell.setOnClickListener(v -> openNotifications());
        TextView orderId = view.findViewById(R.id.tv_order_id);
        TextView status = view.findViewById(R.id.tv_order_status);
        View timeline = view.findViewById(R.id.timeline);
        View call = view.findViewById(R.id.btn_call_provider);
        View cancel = view.findViewById(R.id.btn_cancel_booking);
        if (cancel != null) cancel.setVisibility(View.GONE);
        if (call != null) {
            call.setEnabled(false);
            call.setOnClickListener(v -> {
                if (providerPhone == null || providerPhone.trim().isEmpty()) {
                    Toast.makeText(requireContext(), "Provider contact is unavailable.",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent dial = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + providerPhone));
                if (dial.resolveActivity(requireContext().getPackageManager()) != null) {
                    startActivity(dial);
                }
            });
        }

        String bookingId = getArguments() == null ? null : getArguments().getString("booking_id");
        loadBookingData(bookingId);
    }

    private void loadBookingData(String requestedBookingId) {
        if (registration != null) registration.remove();
        registration = null;
        View root = getView();
        if (root == null) return;
        TextView orderId = root.findViewById(R.id.tv_order_id);
        TextView status = root.findViewById(R.id.tv_order_status);
        View timeline = root.findViewById(R.id.timeline);
        View call = root.findViewById(R.id.btn_call_provider);
        View cancel = root.findViewById(R.id.btn_cancel_booking);
        providerPhone = null;
        if (status != null) {
            status.setText("Loading order updates...");
            status.setOnClickListener(null);
        }
        if (call != null) {
            call.setEnabled(false);
            call.setVisibility(View.GONE);
        }
        if (cancel != null) cancel.setVisibility(View.GONE);

        if (requestedBookingId != null && !requestedBookingId.isEmpty()) {
            registration = BookingServiceFacade.getBookingService().addBookingListener(
                    requestedBookingId, new BookingService.BookingListener() {
                        @Override public void onBookingLoaded(Booking booking) {
                            if (!isAdded() || getView() == null) return;
                            OrderTrackingRenderer.render(requireContext(), getView(), booking);
                            if (cancel != null) BookingCancellationHelper.bind(
                                    requireContext(), cancel, booking);
                            loadProviderContact(booking, call);
                        }

                        @Override public void onError(String message) {
                            showRetryError(orderId, status, timeline, call, cancel);
                        }
                    });
        } else if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            String customerId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            registration = BookingServiceFacade.getBookingService().addCustomerBookingsListener(
                    customerId, new BookingService.CustomerBookingsListener() {
                        @Override public void onBookingsChanged(java.util.List<Booking> bookings) {
                            if (!isAdded() || getView() == null) return;
                            for (Booking booking : bookings) {
                                if (!OrderStatus.fromString(booking.getStatus()).isTerminal()) {
                                    OrderTrackingRenderer.render(requireContext(), getView(), booking);
                                    if (cancel != null) BookingCancellationHelper.bind(
                                            requireContext(), cancel, booking);
                                    loadProviderContact(booking, call);
                                    return;
                                }
                            }
                            if (orderId != null) orderId.setText("No active order");
                            if (status != null) {
                                status.setText("Book a service to track an order");
                                status.setOnClickListener(null);
                            }
                            if (timeline != null) timeline.setVisibility(View.GONE);
                            if (call != null) call.setVisibility(View.GONE);
                            if (cancel != null) cancel.setVisibility(View.GONE);
                        }

                        @Override public void onError(String message) {
                            showRetryError(orderId, status, timeline, call, cancel);
                        }
                    });
        } else {
            if (orderId != null) orderId.setText("Sign in to view orders");
            if (status != null) {
                status.setText("No active booking");
                status.setOnClickListener(null);
            }
            if (timeline != null) timeline.setVisibility(View.GONE);
            if (call != null) call.setVisibility(View.GONE);
            if (cancel != null) cancel.setVisibility(View.GONE);
        }
    }

    private void showRetryError(TextView orderId, TextView status, View timeline,
                                View call, View cancel) {
        if (!isAdded()) return;
        if (orderId != null) orderId.setText("Could not load this order");
        if (status != null) {
            status.setText("Tap to retry loading order updates");
            status.setOnClickListener(v -> {
                String bookingId = getArguments() == null
                        ? null : getArguments().getString("booking_id");
                loadBookingData(bookingId);
            });
        }
        if (timeline != null) timeline.setVisibility(View.GONE);
        if (call != null) call.setVisibility(View.GONE);
        if (cancel != null) cancel.setVisibility(View.GONE);
    }

    private void loadProviderContact(Booking booking, View call) {
        if (booking.getProviderId() == null || booking.getProviderId().trim().isEmpty()) {
            showContactState(call, booking, "Provider details unavailable", false);
            return;
        }
        FirebaseFirestore.getInstance().collection("providers").document(booking.getProviderId()).get()
                .addOnSuccessListener(document -> {
                    Object phone = document.get("phone");
                    if (phone instanceof String && !((String) phone).trim().isEmpty() && isAdded()) {
                        providerPhone = (String) phone;
                        if (call != null) {
                            if (call instanceof TextView) {
                                ((TextView) call).setText(R.string.call_provider);
                            }
                            call.setOnClickListener(v -> {
                                Intent dial = new Intent(Intent.ACTION_DIAL,
                                        Uri.parse("tel:" + providerPhone));
                                if (dial.resolveActivity(requireContext().getPackageManager()) != null) {
                                    startActivity(dial);
                                }
                            });
                            call.setVisibility(View.VISIBLE);
                            call.setEnabled(true);
                        }
                    } else {
                        showContactState(call, booking, "Provider contact unavailable", false);
                    }
                })
                .addOnFailureListener(error -> showContactState(
                        call, booking, "Contact unavailable. Tap to retry", true));
    }

    private void showContactState(View call, Booking booking, String message, boolean retry) {
        if (!isAdded() || call == null) return;
        if (call instanceof TextView) ((TextView) call).setText(message);
        call.setVisibility(View.VISIBLE);
        call.setEnabled(retry);
        call.setOnClickListener(retry ? v -> loadProviderContact(booking, call) : null);
    }

    @Override
    public void onDestroyView() {
        if (registration != null) registration.remove();
        registration = null;
        super.onDestroyView();
    }
}
