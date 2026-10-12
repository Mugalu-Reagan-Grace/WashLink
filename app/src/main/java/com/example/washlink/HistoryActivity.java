package com.example.washlink;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.washlink.data.BookingService;
import com.example.washlink.data.BookingServiceFacade;
import com.example.washlink.data.ListenerRegistration;
import com.example.washlink.models.Booking;
import com.example.washlink.models.OrderStatus;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.List;

public class HistoryActivity extends AppCompatActivity {
    private final List<Booking> allBookings = new ArrayList<>();
    private HistoryAdapter adapter;
    private RecyclerView recyclerView;
    private TextView emptyState;
    private String filter = "active";
    private ListenerRegistration registration;
    private boolean loaded;
    private boolean loadFailed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_customer_history);
        com.example.washlink.data.AuthGuard.requireRole(this,
                com.example.washlink.models.UserAccount.ROLE_CUSTOMER, SignInActivity.class);
        BottomNavHelper.bind(this);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        FrameLayout bell = findViewById(R.id.iv_bell);
        bell.setOnClickListener(v -> startActivity(new Intent(this, NotificationsActivity.class)));

        recyclerView = findViewById(R.id.rv_order_history);
        emptyState = findViewById(R.id.tv_empty_history);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HistoryAdapter();
        recyclerView.setAdapter(adapter);
        bindFilter(R.id.filter_active, "active");
        bindFilter(R.id.filter_completed, "completed");
        bindFilter(R.id.filter_all, "all");
        renderFilterSelection();
        emptyState.setOnClickListener(v -> {
            if (loadFailed) loadBookings();
        });
        loadBookings();
    }

    private void loadBookings() {
        if (registration != null) registration.remove();
        registration = null;
        loaded = false;
        loadFailed = false;
        allBookings.clear();
        adapter.replace(filteredBookings());
        updateEmptyState();
        String uid = FirebaseAuth.getInstance().getCurrentUser() == null
                ? null : FirebaseAuth.getInstance().getCurrentUser().getUid();
        if (uid == null) {
            loaded = true;
            updateEmptyState();
            return;
        }
        registration = BookingServiceFacade.getBookingService().addCustomerBookingsListener(uid,
                new BookingService.CustomerBookingsListener() {
                    @Override
                    public void onBookingsChanged(List<Booking> bookings) {
                        loaded = true;
                        loadFailed = false;
                        allBookings.clear();
                        allBookings.addAll(bookings);
                        adapter.replace(filteredBookings());
                        updateEmptyState();
                    }

                    @Override
                    public void onError(String message) {
                        loaded = false;
                        loadFailed = true;
                        updateEmptyState();
                    }
                });
    }

    private void bindFilter(int id, String value) {
        findViewById(id).setOnClickListener(v -> {
            filter = value;
            renderFilterSelection();
            adapter.replace(filteredBookings());
            updateEmptyState();
        });
    }

    private void renderFilterSelection() {
        setFilterStyle(findViewById(R.id.filter_active), "active".equals(filter));
        setFilterStyle(findViewById(R.id.filter_completed), "completed".equals(filter));
        setFilterStyle(findViewById(R.id.filter_all), "all".equals(filter));
    }

    private void setFilterStyle(View view, boolean selected) {
        if (!(view instanceof MaterialButton)) return;
        MaterialButton button = (MaterialButton) view;
        int fill = selected ? R.color.laundr_blue : R.color.white;
        int text = selected ? R.color.white : R.color.chip_unselected_text;
        button.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, fill)));
        button.setTextColor(ContextCompat.getColor(this, text));
        button.setStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(
                this, selected ? R.color.laundr_blue : R.color.divider_gray)));
        button.setStrokeWidth(Math.round(getResources().getDisplayMetrics().density));
        button.setSelected(selected);
    }

    private List<Booking> filteredBookings() {
        if ("all".equals(filter)) return new ArrayList<>(allBookings);
        List<Booking> result = new ArrayList<>();
        for (Booking booking : allBookings) {
            boolean completed = "DELIVERED".equals(booking.getStatus())
                    || "CANCELLED".equals(booking.getStatus())
                    || "REJECTED".equals(booking.getStatus());
            if (("completed".equals(filter) && completed)
                    || ("active".equals(filter) && !completed)) {
                result.add(booking);
            }
        }
        return result;
    }

    private void updateEmptyState() {
        if (recyclerView == null || emptyState == null) return;
        boolean hasItems = adapter != null && adapter.getItemCount() > 0;
        recyclerView.setVisibility(hasItems ? View.VISIBLE : View.GONE);
        emptyState.setVisibility(hasItems ? View.GONE : View.VISIBLE);
        if (hasItems) return;
        if (loadFailed) {
            emptyState.setText(R.string.history_load_error_retry);
        } else if (!loaded) {
            emptyState.setText(R.string.history_loading);
        } else if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            emptyState.setText(R.string.history_sign_in_required);
        } else if ("completed".equals(filter) && !allBookings.isEmpty()) {
            emptyState.setText(R.string.history_no_completed_orders);
        } else if ("active".equals(filter) && !allBookings.isEmpty()) {
            emptyState.setText(R.string.history_no_active_orders);
        } else {
            emptyState.setText(R.string.history_no_orders);
        }
        emptyState.setClickable(loadFailed);
        emptyState.setFocusable(loadFailed);
    }

    @Override
    protected void onDestroy() {
        if (registration != null) registration.remove();
        super.onDestroy();
    }

    private class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.Holder> {
        private final List<Booking> items = new ArrayList<>();

        void replace(List<Booking> bookings) {
            items.clear();
            items.addAll(bookings);
            notifyDataSetChanged();
        }

        @Override
        public Holder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            return new Holder(getLayoutInflater().inflate(R.layout.item_order_history, parent, false));
        }

        @Override
        public void onBindViewHolder(Holder holder, int position) {
            Booking booking = items.get(position);
            holder.id.setText("Order #" + booking.getId());
            holder.date.setText(booking.getScheduledDateTime() == null
                    ? "Date pending" : booking.getScheduledDateTime());
            holder.type.setText(booking.getServiceName() == null
                    ? "Laundry service" : booking.getServiceName());
            holder.status.setText(OrderStatus.fromString(booking.getStatus()).getDisplayName());
            holder.price.setText(com.example.washlink.data.BookingPricing.format(
                    (int) booking.getTotal()));
            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(HistoryActivity.this, OrderTrackingActivity.class);
                intent.putExtra("booking_id", booking.getId());
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final TextView id;
            final TextView date;
            final TextView status;
            final TextView type;
            final TextView price;

            Holder(View itemView) {
                super(itemView);
                id = itemView.findViewById(R.id.tv_order_id);
                date = itemView.findViewById(R.id.tv_order_date);
                status = itemView.findViewById(R.id.tv_order_status);
                type = itemView.findViewById(R.id.tv_order_type);
                price = itemView.findViewById(R.id.tv_order_price);
            }
        }
    }
}
