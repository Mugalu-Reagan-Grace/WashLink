package com.example.washlink.ui.customer;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.graphics.Typeface;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;

import com.example.washlink.NearbyProvidersActivity;
import com.example.washlink.ChatInboxActivity;
import com.example.washlink.R;
import com.example.washlink.data.AuthRepository;
import com.example.washlink.data.BookingService;
import com.example.washlink.data.BookingServiceFacade;
import com.example.washlink.data.ListenerRegistration;
import com.example.washlink.data.ProfilePhotoManager;
import com.example.washlink.data.OrderProgressStage;
import com.example.washlink.models.Booking;
import com.example.washlink.models.OrderStatus;
import com.example.washlink.models.UserAccount;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

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
        loadCustomerHeader(view);

        View activeCard = view.findViewById(R.id.active_order_card);
        TextView orderTitle = view.findViewById(R.id.tv_order_title);
        TextView orderId = view.findViewById(R.id.tv_order_id);
        TextView orderStatus = view.findViewById(R.id.tv_order_status);
        TextView etaValue = view.findViewById(R.id.tv_eta_value);

        if (orderTitle != null) orderTitle.setText("Your latest order");
        if (orderId != null) orderId.setText("Loading orders...");
        if (orderStatus != null) orderStatus.setText("Loading");
        if (etaValue != null) etaValue.setText("—");
        setOrderProgressVisibility(view, false);
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

    private void loadCustomerHeader(View view) {
        TextView name = view.findViewById(R.id.tv_user_name);
        TextView address = view.findViewById(R.id.tv_user_address);
        ImageView avatar = view.findViewById(R.id.iv_avatar);
        if (name == null || address == null || avatar == null) return;

        name.setText(R.string.home_welcome);
        address.setText(R.string.home_add_pickup_address);
        avatar.setOnClickListener(v ->
                ((MainActivity) requireActivity()).showTab(MainActivity.TAB_PROFILE));
        address.setOnClickListener(v ->
                ((MainActivity) requireActivity()).showTab(MainActivity.TAB_PROFILE));
        AuthRepository.getInstance().getCurrentUserAccount(new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(UserAccount account) {
                if (!isAdded() || getView() != view) return;
                String fullName = account.getName() == null ? "" : account.getName().trim();
                if (!fullName.isEmpty()) {
                    name.setText(getString(R.string.home_greeting, fullName.split("\\s+", 2)[0]));
                }
                String savedAddress = account.getAddress() == null ? "" : account.getAddress().trim();
                address.setText(savedAddress.isEmpty()
                        ? getString(R.string.home_add_pickup_address) : savedAddress);
                FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
                String authPhotoUrl = firebaseUser == null || firebaseUser.getPhotoUrl() == null
                        ? "" : firebaseUser.getPhotoUrl().toString();
                ProfilePhotoManager.display(avatar, account.getPhotoUri(), authPhotoUrl,
                        account.getEmail());
            }

            @Override
            public void onError(String message) {
                if (!isAdded() || getView() != view) return;
                name.setText(R.string.home_welcome);
                address.setText(R.string.home_address_unavailable_retry);
                address.setOnClickListener(v -> loadCustomerHeader(view));
            }
        });
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
                        updateOrderProgress(active);
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
                        View currentView = getView();
                        if (currentView != null) setOrderProgressVisibility(currentView, false);
                    }
                });
    }

    private void showNoActiveOrder(TextView orderTitle, TextView orderId,
                                   TextView orderStatus, TextView etaValue, TextView details) {
        activeBookingId = null;
        View currentView = getView();
        if (currentView != null) setOrderProgressVisibility(currentView, false);
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

    private void updateOrderProgress(Booking booking) {
        View root = getView();
        if (root == null) return;
        OrderStatus status = OrderStatus.fromString(booking.getStatus());
        int stage = OrderProgressStage.fromStatus(status);
        setOrderProgressVisibility(root, stage >= 0);
        View pickupDot = root.findViewById(R.id.dot_pickup);
        View cleaningDot = root.findViewById(R.id.dot_cleaning);
        View deliveryDot = root.findViewById(R.id.dot_delivery);
        View progressFill = root.findViewById(R.id.progress_track_fill);
        TextView pickupLabel = root.findViewById(R.id.tv_label_pickup);
        TextView cleaningLabel = root.findViewById(R.id.tv_label_cleaning);
        TextView deliveryLabel = root.findViewById(R.id.tv_label_delivery);
        int activeColor = ContextCompat.getColor(requireContext(), R.color.laundr_blue);
        int inactiveColor = ContextCompat.getColor(requireContext(), R.color.divider_gray);
        setProgressStep(pickupDot, pickupLabel, stage >= 0, activeColor, inactiveColor);
        setProgressStep(cleaningDot, cleaningLabel, stage >= 1, activeColor, inactiveColor);
        setProgressStep(deliveryDot, deliveryLabel, stage >= 2, activeColor, inactiveColor);
        if (progressFill != null) {
            ConstraintLayout.LayoutParams params =
                    (ConstraintLayout.LayoutParams) progressFill.getLayoutParams();
            params.matchConstraintPercentWidth = stage < 0 ? 0f : stage == 0 ? 0.02f
                    : stage == 1 ? 0.5f : 1f;
            progressFill.setLayoutParams(params);
            progressFill.setVisibility(stage < 0 ? View.INVISIBLE : View.VISIBLE);
        }
        if (pickupLabel != null) {
            pickupLabel.setText(Booking.SERVICE_TYPE_DROPOFF.equals(booking.getServiceType())
                    ? R.string.label_dropoff : R.string.label_pickup);
        }
    }

    private void setProgressStep(View dot, TextView label, boolean complete,
                                 int activeColor, int inactiveColor) {
        if (dot != null) {
            dot.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    complete ? activeColor : inactiveColor));
        }
        if (label != null) {
            label.setTextColor(complete ? activeColor : inactiveColor);
            label.setTypeface(null, complete ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    private void setOrderProgressVisibility(View root, boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        int[] progressViews = {
                R.id.progress_track_bg, R.id.progress_track_fill,
                R.id.dot_pickup, R.id.dot_cleaning, R.id.dot_delivery,
                R.id.tv_label_pickup, R.id.tv_label_cleaning, R.id.tv_label_delivery
        };
        for (int id : progressViews) {
            View progressView = root.findViewById(id);
            if (progressView != null) progressView.setVisibility(visibility);
        }
    }

    @Override
    public void onDestroyView() {
        if (bookingsRegistration != null) bookingsRegistration.remove();
        bookingsRegistration = null;
        activeBookingId = null;
        super.onDestroyView();
    }
}
