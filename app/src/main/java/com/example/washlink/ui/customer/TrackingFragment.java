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
    private View trackingContent;
    private View trackingStatePanel;
    private View trackingProgress;
    private TextView trackingState;
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
        trackingContent = view.findViewById(R.id.tracking_content);
        trackingStatePanel = view.findViewById(R.id.tracking_state_panel);
        trackingProgress = view.findViewById(R.id.tracking_state_progress);
        trackingState = view.findViewById(R.id.tv_tracking_state);
        TextView orderId = view.findViewById(R.id.tv_order_id);
        TextView status = view.findViewById(R.id.tv_order_status);
        View timeline = view.findViewById(R.id.timeline);
        if (timeline != null) timeline.setVisibility(View.GONE);
        View call = view.findViewById(R.id.btn_call_provider);
        View cancel = view.findViewById(R.id.btn_cancel_booking);
        if (cancel != null) cancel.setVisibility(View.GONE);
        if (call != null) {
            call.setEnabled(false);
            call.setOnClickListener(v -> {
                if (providerPhone == null || providerPhone.trim().isEmpty()) {
                    Toast.makeText(requireContext(), R.string.tracking_contact_unavailable,
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
        trackingContent = root.findViewById(R.id.tracking_content);
        trackingStatePanel = root.findViewById(R.id.tracking_state_panel);
        trackingProgress = root.findViewById(R.id.tracking_state_progress);
        trackingState = root.findViewById(R.id.tv_tracking_state);
        showTrackingState(R.string.tracking_loading, false);
        TextView orderId = root.findViewById(R.id.tv_order_id);
        TextView status = root.findViewById(R.id.tv_order_status);
        View timeline = root.findViewById(R.id.timeline);
        View call = root.findViewById(R.id.btn_call_provider);
        View cancel = root.findViewById(R.id.btn_cancel_booking);
        providerPhone = null;
        if (status != null) {
            status.setText(R.string.tracking_loading);
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
                            if (booking == null) {
                                showTrackingState(R.string.tracking_order_unavailable, true);
                                return;
                            }
                            showTrackingContent();
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
                                    showTrackingContent();
                                    OrderTrackingRenderer.render(requireContext(), getView(), booking);
                                    if (cancel != null) BookingCancellationHelper.bind(
                                            requireContext(), cancel, booking);
                                    loadProviderContact(booking, call);
                                    return;
                                }
                            }
                            showTrackingState(R.string.tracking_no_active_order, false);
                            if (orderId != null) orderId.setText(R.string.tracking_no_active_order);
                            if (status != null) status.setText(R.string.tracking_status_empty);
                            if (timeline != null) timeline.setVisibility(View.GONE);
                            if (call != null) call.setVisibility(View.GONE);
                            if (cancel != null) cancel.setVisibility(View.GONE);
                        }

                        @Override public void onError(String message) {
                            showRetryError(orderId, status, timeline, call, cancel);
                        }
                    });
        } else {
            showTrackingState(R.string.tracking_sign_in_required, false);
            if (orderId != null) orderId.setText(R.string.tracking_sign_in_required);
            if (status != null) status.setText(R.string.tracking_status_empty);
            if (timeline != null) timeline.setVisibility(View.GONE);
            if (call != null) call.setVisibility(View.GONE);
            if (cancel != null) cancel.setVisibility(View.GONE);
        }
    }

    private void showRetryError(TextView orderId, TextView status, View timeline,
                                View call, View cancel) {
        if (!isAdded()) return;
        if (orderId != null) orderId.setText(R.string.tracking_load_error);
        if (status != null) status.setText(R.string.tracking_status_error);
        showTrackingState(R.string.tracking_load_error_retry, true);
        if (timeline != null) timeline.setVisibility(View.GONE);
        if (call != null) call.setVisibility(View.GONE);
        if (cancel != null) cancel.setVisibility(View.GONE);
    }

    private void showTrackingState(int message, boolean retry) {
        if (trackingContent == null || trackingState == null) return;
        trackingContent.setVisibility(View.GONE);
        trackingStatePanel.setVisibility(View.VISIBLE);
        trackingState.setText(message);
        trackingProgress.setVisibility(message == R.string.tracking_loading
                ? View.VISIBLE : View.GONE);
        trackingState.setClickable(retry);
        trackingState.setFocusable(retry);
        trackingState.setOnClickListener(retry ? v -> {
            String bookingId = getArguments() == null
                    ? null : getArguments().getString("booking_id");
            loadBookingData(bookingId);
        } : null);
    }

    private void showTrackingContent() {
        if (trackingContent != null) trackingContent.setVisibility(View.VISIBLE);
        if (trackingStatePanel != null) {
            trackingStatePanel.setVisibility(View.GONE);
        }
        if (trackingState != null) {
            trackingState.setClickable(false);
            trackingState.setFocusable(false);
            trackingState.setOnClickListener(null);
        }
    }

    private void loadProviderContact(Booking booking, View call) {
        if (booking.getProviderId() == null || booking.getProviderId().trim().isEmpty()) {
            showContactState(call, booking, R.string.tracking_provider_details_unavailable, false);
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
                        showContactState(call, booking,
                                R.string.tracking_provider_contact_unavailable, false);
                    }
                })
                .addOnFailureListener(error -> showContactState(
                        call, booking, R.string.tracking_contact_retry, true));
    }

    private void showContactState(View call, Booking booking, int message, boolean retry) {
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
