package com.example.washlink.ui.customer;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.washlink.NearbyProvidersActivity;
import com.example.washlink.ChatInboxActivity;
import com.example.washlink.R;
import com.example.washlink.data.BookingService;
import com.example.washlink.data.BookingServiceFacade;
import com.example.washlink.data.ListenerRegistration;
import com.example.washlink.models.Booking;
import com.example.washlink.models.OrderStatus;
import com.google.firebase.auth.FirebaseAuth;

import java.util.List;

public class HomeFragment extends CustomerTabFragment {
    private ListenerRegistration bookingsRegistration;
    private String activeBookingId;
    private boolean loadFailed;

    @Override
    public View onCreateView(@NonNull android.view.LayoutInflater inflater,
                             @Nullable android.view.ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_customer_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        bindNavigation(view, MainActivity.TAB_HOME);

        View activeCard = view.findViewById(R.id.active_order_card);
        TextView orderTitle = view.findViewById(R.id.tv_order_title);
        TextView orderId = view.findViewById(R.id.tv_order_id);
        TextView orderStatus = view.findViewById(R.id.tv_order_status);
        TextView etaValue = view.findViewById(R.id.tv_eta_value);

        if (orderTitle != null) orderTitle.setText("Your latest order");
        if (orderId != null) orderId.setText("Loading orders...");
        if (orderStatus != null) orderStatus.setText("Loading");
        if (etaValue != null) etaValue.setText("—");
        if (activeCard != null) {
            activeCard.setOnClickListener(v -> openActiveOrder());
        }

        view.findViewById(R.id.btn_book_service).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), NearbyProvidersActivity.class)));
        view.findViewById(R.id.iv_bell).setOnClickListener(v -> openNotifications());
        view.findViewById(R.id.iv_chat_inbox).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), ChatInboxActivity.class)));
        view.findViewById(R.id.row_order_history).setOnClickListener(v ->
                ((MainActivity) requireActivity()).showTab(MainActivity.TAB_HISTORY));
        view.findViewById(R.id.row_profile_settings).setOnClickListener(v ->
                ((MainActivity) requireActivity()).showTab(MainActivity.TAB_PROFILE));
        TextView details = view.findViewById(R.id.tv_view_details);
        if (details != null) {
            details.setText("View details");
            details.setOnClickListener(v -> openActiveOrder());
        }

        View.OnClickListener openOrRetry = v -> {
            if (loadFailed) loadLatestBooking(orderTitle, orderId, orderStatus, etaValue, details);
            else openActiveOrder();
        };
        if (activeCard != null) activeCard.setOnClickListener(openOrRetry);
        if (details != null) details.setOnClickListener(openOrRetry);
        loadLatestBooking(orderTitle, orderId, orderStatus, etaValue, details);
    }

    private void loadLatestBooking(TextView orderTitle, TextView orderId,
                                   TextView orderStatus, TextView etaValue, TextView details) {
        if (bookingsRegistration != null) bookingsRegistration.remove();
        bookingsRegistration = null;
        loadFailed = false;
        if (orderId != null) orderId.setText("Loading orders...");
        if (orderStatus != null) orderStatus.setText("Loading");
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            showNoActiveOrder(orderTitle, orderId, orderStatus, etaValue, details);
            return;
        }
        String customerId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        bookingsRegistration = BookingServiceFacade.getBookingService().addCustomerBookingsListener(
                customerId, new BookingService.CustomerBookingsListener() {
                    @Override
                    public void onBookingsChanged(List<Booking> bookings) {
                        if (!isAdded()) return;
                        loadFailed = false;
                        Booking active = null;
                        for (Booking booking : bookings) {
                            if (!OrderStatus.fromString(booking.getStatus()).isTerminal()) {
                                active = booking;
                                break;
                            }
                        }
                        if (active == null) {
                            showNoActiveOrder(orderTitle, orderId, orderStatus, etaValue, details);
                            return;
                        }
                        activeBookingId = active.getId();
                        if (orderTitle != null) orderTitle.setText(active.getServiceName() == null
                                ? "Laundry order" : active.getServiceName());
                        if (orderId != null) orderId.setText("Order #" + active.getId());
                        if (orderStatus != null) orderStatus.setText(
                                OrderStatus.fromString(active.getStatus()).getDisplayName());
                        if (etaValue != null) etaValue.setText(active.getScheduledDateTime() == null
                                ? "Schedule pending" : active.getScheduledDateTime());
                        if (details != null) details.setText("View details");
                    }

                    @Override
                    public void onError(String message) {
                        if (!isAdded()) return;
                        loadFailed = true;
                        activeBookingId = null;
                        if (orderTitle != null) orderTitle.setText("Orders unavailable");
                        if (orderId != null) orderId.setText("Could not load your orders");
                        if (orderStatus != null) orderStatus.setText("Tap Retry to try again");
                        if (etaValue != null) etaValue.setText(" ");
                        if (details != null) details.setText("Retry");
                    }
                });
    }

    private void showNoActiveOrder(TextView orderTitle, TextView orderId,
                                   TextView orderStatus, TextView etaValue, TextView details) {
        activeBookingId = null;
        if (orderTitle != null) orderTitle.setText("No active order");
        if (orderId != null) orderId.setText("Book a wash to get started");
        if (orderStatus != null) orderStatus.setText("Ready");
        if (etaValue != null) etaValue.setText("Start booking");
        if (details != null) details.setText("Book now");
    }

    private void openActiveOrder() {
        if (activeBookingId == null) {
            startActivity(new Intent(requireContext(), NearbyProvidersActivity.class));
            return;
        }
        ((MainActivity) requireActivity()).showTab(MainActivity.TAB_TRACKING, activeBookingId);
    }

    @Override
    public void onDestroyView() {
        if (bookingsRegistration != null) bookingsRegistration.remove();
        bookingsRegistration = null;
        activeBookingId = null;
        super.onDestroyView();
    }
}
