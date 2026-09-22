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
import com.example.washlink.data.BookingServiceStub;
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
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new BookingRequestsAdapter(new ArrayList<>());
        rv.setAdapter(adapter);

        // Use current signed-in user as providerId (AuthRepository.getCurrentUserId())
        String providerId = AuthRepository.getInstance().getCurrentUserId();
        if (providerId == null) providerId = "prov-1"; // fallback for dev
        loadBookingsForProvider(providerId);
    }

    private void loadBookingsForProvider(String providerId) {
        if (BookingServiceFacade.isUsingStub()) {
            List<Booking> bookings = BookingServiceStub.getInstance().getAllBookingsForProvider(providerId);
            adapter.setItems(bookings);
        } else {
            // Attach Firestore listener for provider bookings
            providerBookingsReg = BookingService.getInstance().addProviderBookingsListener(providerId, new BookingService.ProviderBookingsListener() {
                @Override
                public void onBookingsChanged(List<Booking> bookings) {
                    runOnUiThread(() -> adapter.setItems(bookings));
                }

                @Override
                public void onError(String message) {
                    runOnUiThread(() -> adapter.setItems(new ArrayList<>()));
                }
            });
        }
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
            holder.tvPrice.setText(String.format("$%.2f", b.getTotal()));

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
