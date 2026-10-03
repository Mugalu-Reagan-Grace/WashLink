package com.example.washlink;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.washlink.data.BookingService;
import com.example.washlink.data.BookingServiceFacade;
import com.example.washlink.data.BookingPricing;
import com.example.washlink.data.AuthRepository;
import com.example.washlink.data.AuthGuard;
import com.example.washlink.models.Booking;
import com.example.washlink.models.OrderStatus;
import com.example.washlink.data.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

public class BookingRequestsActivity extends AppCompatActivity {

    private RecyclerView rv;
    private BookingRequestsAdapter adapter;
    private ListenerRegistration providerBookingsReg;
    private TextView stateView;
    private final List<Booking> allBookings = new ArrayList<>();
    private String filter = "pending";
    private String providerId;
    private boolean loaded;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_booking_requests);
        AuthGuard.requireRole(this, com.example.washlink.models.UserAccount.ROLE_PROVIDER,
                ProviderLoginActivity.class);

        View back = findViewById(R.id.btn_back);
        if (back != null) back.setOnClickListener(v -> finish());

        ProviderNavBarHelper.bind(this, ProviderNavBarHelper.TAB_BOOKINGS);

        rv = findViewById(R.id.rv_booking_requests);
        stateView = findViewById(R.id.tv_booking_requests_state);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new BookingRequestsAdapter(new ArrayList<>());
        rv.setAdapter(adapter);
        bindFilter(R.id.tab_pending, "pending");
        bindFilter(R.id.tab_accepted, "accepted");
        bindFilter(R.id.tab_history, "history");
        showState("Loading bookings...");

        providerId = AuthRepository.getInstance().getCurrentUserId();
        if (providerId == null) {
            showState("Sign in as a provider to view bookings.");
            return;
        }
        loadBookingsForProvider(providerId);
    }

    private void bindFilter(int viewId, String value) {
        View tab = findViewById(viewId);
        tab.setOnClickListener(v -> {
            filter = value;
            updateFilterStyles();
            updateList();
        });
    }

    private void updateFilterStyles() {
        int[] ids = {R.id.tab_pending, R.id.tab_accepted, R.id.tab_history};
        String[] values = {"pending", "accepted", "history"};
        for (int i = 0; i < ids.length; i++) {
            TextView tab = findViewById(ids[i]);
            boolean selected = values[i].equals(filter);
            tab.setBackgroundResource(selected
                    ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
            tab.setTextColor(androidx.core.content.ContextCompat.getColor(this,
                    selected ? R.color.white : R.color.chip_unselected_text));
        }
    }

    private void loadBookingsForProvider(String providerId) {
        showState("Loading bookings...");
        if (providerBookingsReg != null) {
            providerBookingsReg.remove();
            providerBookingsReg = null;
        }
        if (BookingServiceFacade.isUsingStub()) {
            showState("Demo booking data is not enabled.");
        } else {
            providerBookingsReg = BookingService.getInstance().addProviderBookingsListener(providerId, new BookingService.ProviderBookingsListener() {
                @Override
                public void onBookingsChanged(List<Booking> bookings) {
                    runOnUiThread(() -> {
                        allBookings.clear();
                        allBookings.addAll(bookings);
                        loaded = true;
                        updateList();
                    });
                }

                @Override
                public void onError(String message) {
                    runOnUiThread(() -> {
                        showState("Could not load bookings. Tap to retry.");
                        if (stateView != null) {
                            stateView.setClickable(true);
                            stateView.setOnClickListener(v -> {
                                loaded = false;
                                loadBookingsForProvider(providerId);
                            });
                        }
                    });
                }
            });
        }
    }

    private void updateList() {
        List<Booking> filtered = new ArrayList<>();
        for (Booking booking : allBookings) {
            OrderStatus status = OrderStatus.fromString(booking.getStatus());
            boolean matches = "pending".equals(filter)
                    ? status == OrderStatus.BOOKED
                    : "accepted".equals(filter)
                    ? status != OrderStatus.BOOKED && !status.isTerminal()
                    : status.isTerminal();
            if (matches) filtered.add(booking);
        }
        adapter.setItems(filtered);
        if (filtered.isEmpty()) showState(loaded ? "No bookings in this section yet." : "Loading bookings...");
        else showList();
    }

    private void showState(String message) {
        if (stateView != null) {
            stateView.setText(message);
            stateView.setVisibility(View.VISIBLE);
            stateView.setClickable(false);
            stateView.setOnClickListener(null);
        }
        if (rv != null) rv.setVisibility(View.GONE);
    }

    private void showList() {
        if (stateView != null) stateView.setVisibility(View.GONE);
        if (rv != null) rv.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (providerBookingsReg != null) providerBookingsReg.remove();
    }

    private class BookingRequestsAdapter extends RecyclerView.Adapter<BookingRequestsAdapter.VH> {
        private List<Booking> items;

        BookingRequestsAdapter(List<Booking> items) { this.items = items; }

        void setItems(List<Booking> newItems) { this.items = newItems; notifyDataSetChanged(); }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = getLayoutInflater().inflate(R.layout.item_order_history, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(VH holder, int position) {
            Booking b = items.get(position);
            holder.tvOrderId.setText(b.getId() != null ? b.getId() : "Order");
            holder.tvDate.setText(b.getScheduledDateTime() != null ? b.getScheduledDateTime() : "");
            holder.tvStatus.setText(OrderStatus.fromString(b.getStatus()).getDisplayName());
            holder.tvType.setText(b.getServiceName() != null ? b.getServiceName() : "");
            holder.tvPrice.setText(BookingPricing.format((int) b.getTotal()));

            holder.itemView.setOnClickListener(v -> {
                Intent i = new Intent(BookingRequestsActivity.this, UpdateOrderStatusActivity.class);
                i.putExtra("booking_id", b.getId());
                startActivity(i);
            });
        }

        @Override
        public int getItemCount() { return items != null ? items.size() : 0; }

        class VH extends RecyclerView.ViewHolder {
            TextView tvOrderId, tvDate, tvStatus, tvType, tvPrice;
            VH(View itemView) {
                super(itemView);
                tvOrderId = itemView.findViewById(R.id.tv_order_id);
                tvDate = itemView.findViewById(R.id.tv_order_date);
                tvStatus = itemView.findViewById(R.id.tv_order_status);
                tvType = itemView.findViewById(R.id.tv_order_type);
                tvPrice = itemView.findViewById(R.id.tv_order_price);
            }
        }
    }
}
