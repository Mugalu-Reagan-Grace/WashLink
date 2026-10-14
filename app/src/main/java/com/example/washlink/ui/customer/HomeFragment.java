package com.example.washlink.ui.customer;

import android.content.Intent;
import android.view.ContextThemeWrapper;
import android.os.Bundle;
import android.view.View;
import android.graphics.Outline;
import android.view.ViewOutlineProvider;
import android.graphics.Typeface;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.CalendarView;
import android.widget.LinearLayout;
import android.widget.Button;
import android.widget.Toast;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import android.content.res.ColorStateList;
import com.example.washlink.ui.ThemeManager;

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
        ThemeManager.apply(view, requireContext());
        int accent = ThemeManager.accent(requireContext(), ThemeManager.ROLE_CUSTOMER);
        View bookButton = view.findViewById(R.id.btn_book_service);
        bookButton.setElevation(6 * getResources().getDisplayMetrics().density);
        bookButton.setBackgroundTintList(ColorStateList.valueOf(
                Color.rgb(Math.max(0, Color.red(accent) - 35),
                        Math.max(0, Color.green(accent) - 35),
                        Math.max(0, Color.blue(accent) - 35))));
        ((TextView) bookButton).setTextColor(Color.WHITE);
        tintHomeAction(view.findViewById(R.id.iv_calendar), accent);
        bindNavigation(view, MainActivity.TAB_HOME);
        View avatar = view.findViewById(R.id.iv_avatar);
        if (avatar != null) {
            avatar.setClipToOutline(true);
            avatar.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View target, Outline outline) {
                    outline.setOval(0, 0, target.getWidth(), target.getHeight());
                }

            });
        }
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
        view.findViewById(R.id.iv_calendar).setOnClickListener(v -> showActivityCalendar());
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

    private void tintHomeAction(View container, int accent) {
        if (!(container instanceof android.view.ViewGroup)) return;
        container.setBackgroundTintList(ColorStateList.valueOf(
                Color.rgb(Math.min(255, Color.red(accent) + 215),
                        Math.min(255, Color.green(accent) + 215),
                        Math.min(255, Color.blue(accent) + 215))));
        ImageView icon = ((android.view.ViewGroup) container).getChildAt(0) instanceof ImageView
                ? (ImageView) ((android.view.ViewGroup) container).getChildAt(0) : null;
        if (icon != null) ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(accent));
    }

    private void showActivityCalendar() {
            if (FirebaseAuth.getInstance().getCurrentUser() == null) {
                Toast.makeText(requireContext(), "Sign in to view your activity calendar.",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            LinearLayout content = new LinearLayout(requireContext());
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(24, 0, 24, 0);
            int accent = ThemeManager.accent(requireContext(), ThemeManager.ROLE_CUSTOMER);
            int calendarStyle = R.style.Theme_WashLink_CalendarBlue;
            String accentName = ThemeManager.accentName(requireContext(),
                    ThemeManager.ROLE_CUSTOMER);
            if ("amber".equals(accentName)) calendarStyle = R.style.Theme_WashLink_CalendarAmber;
            else if ("emerald".equals(accentName)) calendarStyle = R.style.Theme_WashLink_CalendarEmerald;
            else if ("violet".equals(accentName)) calendarStyle = R.style.Theme_WashLink_CalendarViolet;
            CalendarView calendar = new CalendarView(
                    new ContextThemeWrapper(requireContext(), calendarStyle));
            calendar.setSelectedWeekBackgroundColor(accent);
            calendar.setFocusedMonthDateColor(accent);
            calendar.setUnfocusedMonthDateColor(ContextCompat.getColor(requireContext(),
                    R.color.text_secondary));
            calendar.setDate(System.currentTimeMillis(), false, true);
            TextView activityLabel = new TextView(requireContext());
            activityLabel.setPadding(8, 16, 8, 16);
            activityLabel.setText("Loading your activity...");
            content.addView(calendar);
            content.addView(activityLabel);
            AlertDialog dialog = new AlertDialog.Builder(requireContext())
                    .setTitle("Activity calendar")
                    .setView(content)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            Button okButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (okButton != null) {
                okButton.setTextColor(accent);
                okButton.setAllCaps(false);
            }
            final ListenerRegistration[] calendarRegistration = new ListenerRegistration[1];
            dialog.setOnDismissListener(unused -> {
                if (calendarRegistration[0] != null) calendarRegistration[0].remove();
            });

            List<Booking> bookings = new ArrayList<>();
            SimpleDateFormat dayFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            calendar.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
                Calendar selected = Calendar.getInstance();
                selected.set(year, month, dayOfMonth);
                String day = dayFormat.format(selected.getTime());
                StringBuilder message = new StringBuilder();
                for (Booking booking : bookings) {
                    if (booking.getCreatedAt() > 0
                            && day.equals(dayFormat.format(new Date(booking.getCreatedAt())))) {
                        if (message.length() > 0) message.append("\n");
                        message.append("Order ")
                                .append(booking.getId() == null ? "" : "#" + booking.getId())
                                .append(" - ")
                                .append(booking.getServiceName() == null
                                        ? "Laundry service" : booking.getServiceName());
                    }
                }
                activityLabel.setText(message.length() == 0
                        ? "No orders recorded on this day." : message.toString());
            });
            calendarRegistration[0] = BookingServiceFacade.getBookingService().addCustomerBookingsListener(
                    FirebaseAuth.getInstance().getCurrentUser().getUid(),
                    new BookingService.CustomerBookingsListener() {
                        @Override public void onBookingsChanged(List<Booking> updated) {
                            bookings.clear();
                            bookings.addAll(updated);
                            Calendar now = Calendar.getInstance();
                            calendar.setDate(now.getTimeInMillis(), false, true);
                            String today = dayFormat.format(now.getTime());
                            int count = 0;
                            for (Booking booking : bookings) {
                                if (booking.getCreatedAt() > 0
                                        && today.equals(dayFormat.format(
                                        new Date(booking.getCreatedAt())))) {
                                    count++;
                                }
                            }
                            activityLabel.setText(count == 0
                                    ? "No orders recorded today."
                                    : count + (count == 1
                                    ? " order recorded today." : " orders recorded today."));
                        }
                        @Override public void onError(String message) {
                            if (isAdded()) activityLabel.setText("Could not load activity history.");
                        }
                    });
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
