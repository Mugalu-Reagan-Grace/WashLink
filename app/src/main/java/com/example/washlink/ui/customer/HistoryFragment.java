package com.example.washlink.ui.customer;

import android.os.Bundle;
import android.text.InputFilter;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.washlink.data.BookingService;
import com.example.washlink.data.BookingServiceFacade;
import com.example.washlink.data.ListenerRegistration;
import com.example.washlink.models.Booking;
import com.example.washlink.R;
import com.google.firebase.auth.FirebaseAuth;
import java.util.ArrayList;
import java.util.List;

public class HistoryFragment extends CustomerTabFragment {
    private final List<Booking> allBookings = new ArrayList<>();
    private HistoryAdapter adapter;
    private RecyclerView recyclerView;
    private TextView emptyState;
    private String filter = "active";
    private ListenerRegistration registration;
    private boolean loaded;
    private boolean loadFailed;

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
        recyclerView = view.findViewById(R.id.rv_order_history);
        emptyState = view.findViewById(R.id.tv_empty_history);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new HistoryAdapter();
        recyclerView.setAdapter(adapter);
        bindFilter(view, R.id.filter_active, "active");
        bindFilter(view, R.id.filter_completed, "completed");
        bindFilter(view, R.id.filter_all, "all");
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
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            loaded = true;
            updateEmptyState();
            return;
        }
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        registration = BookingServiceFacade.getBookingService().addCustomerBookingsListener(
                uid, new BookingService.CustomerBookingsListener() {
                    @Override public void onBookingsChanged(List<Booking> bookings) {
                        if (!isAdded()) return;
                        loaded = true;
                        loadFailed = false;
                        allBookings.clear();
                        allBookings.addAll(bookings);
                        adapter.replace(filteredBookings());
                        updateEmptyState();
                    }
                    @Override public void onError(String message) {
                        if (!isAdded()) return;
                        loaded = false;
                        loadFailed = true;
                        updateEmptyState();
                    }
                });
    }

    private void bindFilter(View view, int id, String value) {
        view.findViewById(id).setOnClickListener(v -> {
            filter = value;
            adapter.replace(filteredBookings());
            updateEmptyState();
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

    private void updateEmptyState() {
        if (recyclerView == null || emptyState == null || adapter == null) return;
        boolean hasItems = adapter.getItemCount() > 0;
        recyclerView.setVisibility(hasItems ? View.VISIBLE : View.GONE);
        emptyState.setVisibility(hasItems ? View.GONE : View.VISIBLE);
        if (hasItems) return;
        if (loadFailed) {
            emptyState.setText("Could not load orders. Tap to retry.");
        } else if (!loaded) {
            emptyState.setText("Loading orders...");
        } else if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            emptyState.setText("Sign in to view your orders.");
        } else if ("completed".equals(filter) && !allBookings.isEmpty()) {
            emptyState.setText("No completed orders yet.");
        } else if ("active".equals(filter) && !allBookings.isEmpty()) {
            emptyState.setText("No active orders.");
        } else {
            emptyState.setText("No orders yet.");
        }
        emptyState.setClickable(loadFailed);
        emptyState.setFocusable(loadFailed);
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
            boolean reviewAvailable = "DELIVERED".equals(booking.getStatus())
                    && !booking.isReviewSubmitted();
            holder.review.setVisibility(reviewAvailable ? View.VISIBLE : View.GONE);
            holder.review.setOnClickListener(v -> showReviewDialog(booking));
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
            final View review = itemView.findViewById(R.id.btn_review_booking);
            Holder(View itemView) { super(itemView); }
        }
    }

    private void showReviewDialog(Booking booking) {
        if (!isAdded()) return;
        LinearLayout fields = new LinearLayout(requireContext());
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.setPadding(32, 0, 32, 0);
        RatingBar rating = new RatingBar(requireContext());
        rating.setNumStars(5);
        rating.setStepSize(1f);
        rating.setRating(5f);
        fields.addView(rating);
        EditText reviewText = new EditText(requireContext());
        reviewText.setHint("Share your experience (optional)");
        reviewText.setMinLines(3);
        reviewText.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        reviewText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1000)});
        fields.addView(reviewText);
        new AlertDialog.Builder(requireContext())
                .setTitle("Rate " + (booking.getProviderName() == null
                        ? "your provider" : booking.getProviderName()))
                .setView(fields)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Submit", (dialog, which) -> {
                    BookingServiceFacade.getBookingService().submitBookingReview(
                            booking.getId(), Math.round(rating.getRating()),
                            reviewText.getText().toString(),
                            new BookingService.SimpleCallback() {
                                @Override public void onSuccess() {
                                    if (!isAdded()) return;
                                    Toast.makeText(requireContext(), "Thanks for your review.",
                                            Toast.LENGTH_SHORT).show();
                                    loadBookings();
                                }
                                @Override public void onError(String message) {
                                    if (!isAdded()) return;
                                    Toast.makeText(requireContext(),
                                            "Could not submit review: " + message,
                                            Toast.LENGTH_LONG).show();
                                }
                            });
                })
                .show();
    }
}
