package com.example.washlink.ui.customer;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.washlink.R;

public class TrackingFragment extends CustomerTabFragment {
    private com.example.washlink.data.ListenerRegistration registration;
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
        String bookingId = getArguments() == null ? null : getArguments().getString("booking_id");
        if (bookingId != null && !bookingId.isEmpty()) {
            TextView id = view.findViewById(R.id.tv_order_id);
            TextView status = view.findViewById(R.id.tv_order_status);
            registration = com.example.washlink.data.BookingServiceFacade.getBookingService()
                    .addBookingListener(bookingId, new com.example.washlink.data.BookingService.BookingListener() {
                        @Override public void onBookingLoaded(com.example.washlink.models.Booking booking) {
                            id.setText("Order #" + booking.getId());
                            status.setText(booking.getStatus().replace('_', ' '));
                        }
                        @Override public void onError(String message) { status.setText("Unavailable"); }
                    });
        }
        View call = view.findViewById(R.id.btn_call_provider);
        if (call != null) call.setOnClickListener(v -> {
            Intent dial = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:+256700000000"));
            startActivity(dial);
        });
    }

    @Override
    public void onDestroyView() {
        if (registration != null) registration.remove();
        registration = null;
        super.onDestroyView();
    }
}
