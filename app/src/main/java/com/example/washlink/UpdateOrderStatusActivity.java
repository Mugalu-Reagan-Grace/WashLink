package com.example.washlink;

import android.os.Bundle;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.washlink.data.BookingService;
import com.example.washlink.data.BookingServiceFacade;
import com.example.washlink.data.BookingServiceStub;
import com.example.washlink.data.AuthGuard;
import com.example.washlink.models.UserAccount;
import com.example.washlink.models.Booking;
import com.example.washlink.models.OrderStatus;
import com.example.washlink.data.ListenerRegistration;

import java.util.List;

public class UpdateOrderStatusActivity extends AppCompatActivity {

    private ListenerRegistration bookingListener;
    private String bookingId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_update_order_status);
        AuthGuard.requireRole(this, UserAccount.ROLE_PROVIDER, ProviderLoginActivity.class);

        View back = findViewById(R.id.btn_back);
        if (back != null) back.setOnClickListener(v -> finish());

        TextView tvOrderId = findViewById(R.id.tv_order_id);
        TextView tvCurrentStatus = findViewById(R.id.tv_current_status_value);
        RadioGroup radioGroup = findViewById(R.id.radio_group_status);
        View btnUpdate = findViewById(R.id.btn_update_status);

        bookingId = getIntent().getStringExtra("booking_id");
        if (bookingId == null || bookingId.isEmpty()) {
            // Safe fallback: disable update and inform user
            btnUpdate.setEnabled(false);
            Toast.makeText(this, "No booking selected.", Toast.LENGTH_LONG).show();
            return;
        }

        // Attach realtime listener (use stub by default until Firebase configured)
        BookingService.BookingListener listener = new BookingService.BookingListener() {
            @Override
            public void onBookingLoaded(Booking booking) {
                runOnUiThread(() -> {
                    if (booking == null) return;
                    tvOrderId.setText(booking.getId() != null ? "Order #" + booking.getId() : "Order");
                    OrderStatus current = OrderStatus.fromString(booking.getStatus());
                    tvCurrentStatus.setText(current.getDisplayName());

                    // Populate radio options from remaining forward statuses
                    radioGroup.removeAllViews();
                    List<OrderStatus> remaining = OrderStatus.getRemainingForwardStatuses(current);
                    if (remaining.isEmpty()) {
                        btnUpdate.setEnabled(false);
                        Toast.makeText(UpdateOrderStatusActivity.this, "No further statuses available.", Toast.LENGTH_SHORT).show();
                    } else {
                        btnUpdate.setEnabled(true);
                        for (int i = 0; i < remaining.size(); i++) {
                            OrderStatus s = remaining.get(i);
                            RadioButton rb = new RadioButton(UpdateOrderStatusActivity.this);
                            rb.setId(View.generateViewId());
                            rb.setText(s.getDisplayName());
                            rb.setTag(s.name());
                            rb.setPadding(10, 10, 10, 10);
                            radioGroup.addView(rb);
                            if (i == 0) rb.setChecked(true);
                        }
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(UpdateOrderStatusActivity.this, "Error loading booking: " + message, Toast.LENGTH_LONG).show());
            }
        };

        if (BookingServiceFacade.isUsingStub()) {
            bookingListener = BookingServiceStub.getInstance().addBookingListener(bookingId, listener);
        } else {
            bookingListener = BookingService.getInstance().addBookingListener(bookingId, listener);
        }

        btnUpdate.setOnClickListener(v -> {
            int checkedId = radioGroup.getCheckedRadioButtonId();
            if (checkedId == -1) {
                Toast.makeText(this, "Select a status first.", Toast.LENGTH_SHORT).show();
                return;
            }
            RadioButton checked = findViewById(checkedId);
            String statusName = (String) checked.getTag();
            OrderStatus newStatus = OrderStatus.fromString(statusName);
            btnUpdate.setEnabled(false);
            if (BookingServiceFacade.isUsingStub()) {
                BookingServiceStub.getInstance().updateBookingStatus(bookingId, newStatus, new BookingService.SimpleCallback() {
                    @Override
                    public void onSuccess() {
                        runOnUiThread(() -> {
                            Toast.makeText(UpdateOrderStatusActivity.this, "Status updated.", Toast.LENGTH_SHORT).show();
                            finish();
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> {
                            Toast.makeText(UpdateOrderStatusActivity.this, "Failed to update: " + message, Toast.LENGTH_LONG).show();
                            btnUpdate.setEnabled(true);
                        });
                    }
                });
            } else {
                BookingService.getInstance().updateBookingStatus(bookingId, newStatus, new BookingService.SimpleCallback() {
                    @Override
                    public void onSuccess() {
                        runOnUiThread(() -> {
                            Toast.makeText(UpdateOrderStatusActivity.this, "Status updated.", Toast.LENGTH_SHORT).show();
                            finish();
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> {
                            Toast.makeText(UpdateOrderStatusActivity.this, "Failed to update: " + message, Toast.LENGTH_LONG).show();
                            btnUpdate.setEnabled(true);
                        });
                    }
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bookingListener != null) {
            if (BookingServiceFacade.isUsingStub()) BookingServiceStub.getInstance().removeListener(bookingListener);
            else BookingService.getInstance().removeListener(bookingListener);
        }
    }
}
