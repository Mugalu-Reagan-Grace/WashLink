package com.example.washlink;

import android.content.Context;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.example.washlink.data.BookingService;
import com.example.washlink.models.Booking;
import com.example.washlink.models.OrderStatus;

public final class BookingCancellationHelper {
    private BookingCancellationHelper() {}

    public static void bind(Context context, View button, Booking booking) {
        boolean canCancel = OrderStatus.fromString(booking.getStatus()) == OrderStatus.BOOKED
                && !"PAID".equalsIgnoreCase(booking.getPaymentStatus())
                && (booking.getPaymentReference() == null
                || booking.getPaymentReference().trim().isEmpty());
        button.setVisibility(canCancel ? View.VISIBLE : View.GONE);
        if (!canCancel) return;

        button.setOnClickListener(view -> new AlertDialog.Builder(context)
                .setTitle(R.string.cancel_booking_confirm_title)
                .setMessage(R.string.cancel_booking_confirm_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.cancel_booking, (dialog, which) -> {
                    button.setEnabled(false);
                    BookingService.getInstance().cancelBooking(booking.getId(),
                            new BookingService.SimpleCallback() {
                                @Override
                                public void onSuccess() {
                                    Toast.makeText(context, R.string.booking_cancelled,
                                            Toast.LENGTH_SHORT).show();
                                }

                                @Override
                                public void onError(String message) {
                                    button.setEnabled(true);
                                    Toast.makeText(context,
                                            message == null || message.trim().isEmpty()
                                                    ? context.getString(R.string.cancel_booking_unavailable)
                                                    : message,
                                            Toast.LENGTH_LONG).show();
                                }
                            });
                })
                .show());
    }
}
