package com.example.washlink.ui.customer;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.washlink.data.BookingService;
import com.example.washlink.data.BookingServiceFacade;
import com.example.washlink.data.ListenerRegistration;
import com.example.washlink.models.Booking;
import com.example.washlink.OrderTrackingActivity;
import com.example.washlink.R;
import com.google.firebase.auth.FirebaseAuth;
import java.util.ArrayList;
import java.util.List;

public class HistoryFragment extends CustomerTabFragment {
    private final List<Booking> allBookings = new ArrayList<>();
    private HistoryAdapter adapter;
    private String filter = "active";
    private ListenerRegistration registration;

    @Override
    public View onCreateView(@NonNull android.view.LayoutInflater inflater,
                             @Nullable android.view.ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_customer_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        bindNavigation(view, MainActivity.TAB_HISTORY);
        view.findViewById(R.id.iv_bell).setOnClickListener(v -> openNotifications());
        RecyclerView recyclerView = view.findViewById(R.id.rv_order_history);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new HistoryAdapter();
        recyclerView.setAdapter(adapter);
        bindFilter(view, R.id.filter_active, "active");
        bindFilter(view, R.id.filter_completed, "completed");
        bindFilter(view, R.id.filter_all, "all");

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Toast.makeText(requireContext(), "Sign in to view your orders", Toast.LENGTH_SHORT).show();
            return;
        }
        registration = BookingServiceFacade.getBookingService().addCustomerBookingsListener(
                FirebaseAuth.getInstance().getCurrentUser().getUid(),
                new BookingService.CustomerBookingsListener() {
                    @Override public void onBookingsChanged(List<Booking> bookings) {
                        allBookings.clear();
                        allBookings.addAll(bookings);
                        adapter.replace(filteredBookings());
                    }
                    @Override public void onError(String message) {
                        if (isAdded()) Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void bindFilter(View view, int id, String value) {
        view.findViewById(id).setOnClickListener(v -> {
            filter = value;
            adapter.replace(filteredBookings());
        });
    }

    private List<Booking> filteredBookings() {
        List<Booking> result = new ArrayList<>();
        for (Booking booking : allBookings) {
            boolean completed = "DELIVERED".equals(booking.getStatus())
                    || "CANCELLED".equals(booking.getStatus())
                    || "REJECTED".equals(booking.getStatus());
            if ("all".equals(filter)
                    || ("completed".equals(filter) && completed)
                    || ("active".equals(filter) && !completed)) result.add(booking);
        }
        return result;
    }

    @Override
    public void onDestroyView() {
        if (registration != null) registration.remove();
        registration = null;
        super.onDestroyView();
    }

    private class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.Holder> {
        private final List<Booking> items = new ArrayList<>();
        void replace(List<Booking> bookings) {
            items.clear();
            items.addAll(bookings);
            notifyDataSetChanged();
        }
        @NonNull @Override public Holder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int type) {
            return new Holder(getLayoutInflater().inflate(R.layout.item_order_history, parent, false));
        }
        @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
            Booking booking = items.get(position);
            holder.id.setText("Order #" + booking.getId());
            holder.date.setText(booking.getScheduledDateTime() == null ? "Date pending" : booking.getScheduledDateTime());
            holder.type.setText(booking.getServiceName() == null ? "Laundry service" : booking.getServiceName());
            holder.status.setText(booking.getStatus() == null ? "BOOKED" : booking.getStatus().replace('_', ' '));
            holder.price.setText(com.example.washlink.data.BookingPricing.format((int) booking.getTotal()));
            holder.itemView.setOnClickListener(v -> {
                ((MainActivity) requireActivity()).showTab(MainActivity.TAB_TRACKING, booking.getId());
            });
        }
        @Override public int getItemCount() { return items.size(); }
        class Holder extends RecyclerView.ViewHolder {
            final TextView id = itemView.findViewById(R.id.tv_order_id);
            final TextView date = itemView.findViewById(R.id.tv_order_date);
            final TextView status = itemView.findViewById(R.id.tv_order_status);
            final TextView type = itemView.findViewById(R.id.tv_order_type);
            final TextView price = itemView.findViewById(R.id.tv_order_price);
            Holder(View itemView) { super(itemView); }
        }
    }
}
