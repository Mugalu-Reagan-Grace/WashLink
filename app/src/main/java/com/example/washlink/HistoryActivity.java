package com.example.washlink;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.washlink.data.BookingService;
import com.example.washlink.data.BookingServiceFacade;
import com.example.washlink.data.ListenerRegistration;
import com.example.washlink.models.Booking;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class HistoryActivity extends AppCompatActivity {
    private final List<Booking> allBookings = new ArrayList<>();
    private HistoryAdapter adapter;
    private String filter = "active";
    private ListenerRegistration registration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_history);
        BottomNavHelper.bind(this);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        FrameLayout bell = findViewById(R.id.iv_bell);
        bell.setOnClickListener(v -> startActivity(new Intent(this, NotificationsActivity.class)));

        RecyclerView recyclerView = findViewById(R.id.rv_order_history);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HistoryAdapter();
        recyclerView.setAdapter(adapter);
        bindFilter(R.id.filter_active, "active");
        bindFilter(R.id.filter_completed, "completed");
        bindFilter(R.id.filter_all, "all");

        String uid = FirebaseAuth.getInstance().getCurrentUser() == null
                ? null : FirebaseAuth.getInstance().getCurrentUser().getUid();
        if (uid == null) {
            Toast.makeText(this, "Sign in to view your orders", Toast.LENGTH_SHORT).show();
            return;
        }
        registration = BookingServiceFacade.getBookingService().addCustomerBookingsListener(uid,
                new BookingService.CustomerBookingsListener() {
                    @Override
                    public void onBookingsChanged(List<Booking> bookings) {
                        allBookings.clear();
                        allBookings.addAll(bookings);
                        adapter.replace(filteredBookings());
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(HistoryActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void bindFilter(int id, String value) {
        findViewById(id).setOnClickListener(v -> {
            filter = value;
            adapter.replace(filteredBookings());
        });
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
            holder.status.setText(booking.getStatus() == null
                    ? "BOOKED" : booking.getStatus().replace('_', ' '));
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
