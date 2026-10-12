package com.example.washlink;

import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
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
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class UpdateOrderStatusActivity extends AppCompatActivity {

    private ListenerRegistration bookingListener;
    private String bookingId;
    private Booking currentBooking;
    private MaterialButton assignRiderButton;
    private View updateStatusButton;
    private RadioGroup statusOptions;
    private TextView approvalNotice;
    private boolean approvalChecked;
    private boolean providerApproved;
    private com.google.firebase.firestore.ListenerRegistration approvalListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_update_order_status);
        AuthGuard.requireRole(this, UserAccount.ROLE_PROVIDER, ProviderLoginActivity.class);

        View back = findViewById(R.id.btn_back);
        if (back != null) back.setOnClickListener(v -> finish());

        TextView tvOrderId = findViewById(R.id.tv_order_id);
        TextView customerDetails = findViewById(R.id.tv_customer_name);
        TextView tvCurrentStatus = findViewById(R.id.tv_current_status_value);
        assignRiderButton = findViewById(R.id.btn_assign_rider);
        approvalNotice = findViewById(R.id.tv_provider_approval_notice);
        MaterialButton callCustomerButton = findViewById(R.id.btn_call_customer);
        assignRiderButton.setEnabled(false);
        callCustomerButton.setEnabled(false);
        statusOptions = findViewById(R.id.radio_group_status);
        updateStatusButton = findViewById(R.id.btn_update_status);
        updateStatusButton.setEnabled(false);
        if (approvalNotice != null) {
            approvalNotice.setText(R.string.provider_approval_checking);
            approvalNotice.setVisibility(View.VISIBLE);
        }
        loadProviderApproval();
        RadioGroup radioGroup = statusOptions;
        View btnUpdate = updateStatusButton;
        radioGroup.removeAllViews();
        tvCurrentStatus.setText("Loading booking...");

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
                    currentBooking = booking;
                    tvOrderId.setText(booking.getId() != null ? "Order #" + booking.getId() : "Order");
                    String phone = booking.getCustomerPhone();
                    StringBuilder details = new StringBuilder()
                            .append("Customer: ").append(value(booking.getCustomerName(), "Customer"))
                            .append("\nService: ").append(value(booking.getServiceName(), "Laundry service"))
                            .append("\nContact: ").append(value(phone, "Not provided"))
                            .append("\nSchedule: ").append(value(booking.getScheduledDateTime(), "Not scheduled"))
                            .append("\nAddress: ").append(value(booking.getAddress(), "Not provided"))
                            .append("\nItems: ").append(booking.getItemCount())
                            .append("\nFulfilment: ").append(getString(
                                    Booking.SERVICE_TYPE_PICKUP.equals(booking.getServiceType())
                                            ? R.string.provider_booking_pickup
                                            : Booking.SERVICE_TYPE_DROPOFF.equals(booking.getServiceType())
                                            ? R.string.provider_booking_dropoff
                                            : R.string.provider_booking_mode_unavailable))
                            .append("\n").append(getString(R.string.provider_booking_subtotal_label))
                            .append(": ").append(
                                    com.example.washlink.data.BookingPricing.format(
                                            (int) booking.getSubtotal()))
                            .append("\n").append(getString(R.string.service_fee)).append(": ").append(
                                    com.example.washlink.data.BookingPricing.format(
                                            (int) booking.getServiceFeeForDisplay()))
                            .append("\n").append(getString(R.string.total)).append(": ")
                            .append(com.example.washlink.data.BookingPricing
                                    .format((int) booking.getTotal()))
                            .append("\nPayment: ").append(value(booking.getPaymentStatus(), "Pending"))
                            .append(" · ").append(value(booking.getPaymentMethod(), "Not selected"));
                    if (booking.getSpecialInstructions() != null
                            && !booking.getSpecialInstructions().trim().isEmpty()) {
                        details.append("\nInstructions: ").append(booking.getSpecialInstructions());
                    }
                    if (booking.getAssignedRiderName() != null) {
                        details.append("\nRider: ").append(booking.getAssignedRiderName())
                                .append(" · ").append(value(booking.getAssignedRiderPhone(), "No phone"));
                    }
                    customerDetails.setText(details);
                    callCustomerButton.setEnabled(booking.getCustomerPhone() != null
                            && !booking.getCustomerPhone().trim().isEmpty());
                    assignRiderButton.setEnabled(approvalChecked && providerApproved
                            && !BookingServiceFacade.isUsingStub()
                            && isRiderAssignableStatus(booking.getStatus()));
                    OrderStatus current = OrderStatus.fromString(booking.getStatus());
                    tvCurrentStatus.setText(current.getDisplayName());

                    // Populate radio options from remaining forward statuses
                    radioGroup.removeAllViews();
                    List<OrderStatus> availableStatuses = new java.util.ArrayList<>(
                            OrderStatus.getRemainingForwardStatuses(current));
                    if (current == OrderStatus.BOOKED) availableStatuses.add(OrderStatus.REJECTED);
                    if (availableStatuses.isEmpty()) {
                        btnUpdate.setEnabled(false);
                        Toast.makeText(UpdateOrderStatusActivity.this, "No further statuses available.", Toast.LENGTH_SHORT).show();
                    } else {
                        for (int i = 0; i < availableStatuses.size(); i++) {
                            OrderStatus s = availableStatuses.get(i);
                            RadioButton rb = new RadioButton(UpdateOrderStatusActivity.this);
                            rb.setId(View.generateViewId());
                            rb.setText(s.getDisplayName());
                            rb.setTag(s.name());
                            rb.setPadding(10, 10, 10, 10);
                            radioGroup.addView(rb);
                            if (i == 0) rb.setChecked(true);
                        }
                        btnUpdate.setEnabled(approvalChecked && providerApproved);
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    btnUpdate.setEnabled(false);
                    radioGroup.removeAllViews();
                    tvCurrentStatus.setText("Could not load booking.");
                    Toast.makeText(UpdateOrderStatusActivity.this,
                            "Error loading booking: " + message, Toast.LENGTH_LONG).show();
                });
            }
        };

        if (BookingServiceFacade.isUsingStub()) {
            bookingListener = BookingServiceStub.getInstance().addBookingListener(bookingId, listener);
        } else {
            bookingListener = BookingService.getInstance().addBookingListener(bookingId, listener);
        }

        assignRiderButton.setOnClickListener(v -> showRiderPicker());
        callCustomerButton.setOnClickListener(v -> {
            if (currentBooking == null || currentBooking.getCustomerPhone() == null
                    || currentBooking.getCustomerPhone().trim().isEmpty()) {
                Toast.makeText(this, "Customer phone number is unavailable.", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent dial = new Intent(Intent.ACTION_DIAL,
                    Uri.fromParts("tel", currentBooking.getCustomerPhone(), null));
            if (dial.resolveActivity(getPackageManager()) != null) startActivity(dial);
        });

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

    private void loadProviderApproval() {
        String providerId = com.example.washlink.data.AuthRepository.getInstance().getCurrentUserId();
        if (BookingServiceFacade.isUsingStub()) {
            approvalChecked = true;
            providerApproved = true;
            updateApprovalControls();
            return;
        }
        if (providerId == null) {
            approvalChecked = true;
            providerApproved = false;
            approvalNotice.setText(R.string.provider_approval_unavailable);
            approvalNotice.setVisibility(View.VISIBLE);
            updateApprovalControls();
            return;
        }
        approvalListener = FirebaseFirestore.getInstance().collection("providers")
                .document(providerId).addSnapshotListener((document, error) -> {
                    if (error != null) {
                        approvalChecked = true;
                        providerApproved = false;
                        if (approvalNotice != null) {
                            approvalNotice.setText(R.string.provider_approval_unavailable);
                            approvalNotice.setVisibility(View.VISIBLE);
                        }
                        updateApprovalControls();
                        return;
                    }
                    approvalChecked = true;
                    providerApproved = document != null && document.exists()
                            && Boolean.TRUE.equals(document.get("isApproved"));
                    updateApprovalControls();
                });
    }

    private void updateApprovalControls() {
        boolean canManageOrders = approvalChecked && providerApproved;
        if (approvalNotice != null) {
            if (canManageOrders) {
                approvalNotice.setVisibility(View.GONE);
            } else if (approvalNotice.getVisibility() != View.VISIBLE
                    || !getString(R.string.provider_approval_unavailable)
                    .contentEquals(approvalNotice.getText())) {
                approvalNotice.setText(approvalChecked
                        ? R.string.provider_approval_pending
                        : R.string.provider_approval_checking);
                approvalNotice.setVisibility(View.VISIBLE);
            }
        }
        if (currentBooking != null && assignRiderButton != null) {
            assignRiderButton.setEnabled(canManageOrders
                    && !BookingServiceFacade.isUsingStub()
                    && isRiderAssignableStatus(currentBooking.getStatus()));
        }
        if (updateStatusButton != null && statusOptions != null) {
            updateStatusButton.setEnabled(canManageOrders
                    && statusOptions.getChildCount() > 0);
        }
    }

    private void showRiderPicker() {
        if (currentBooking == null || BookingServiceFacade.isUsingStub()) {
            Toast.makeText(UpdateOrderStatusActivity.this, "Rider assignment is unavailable.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        String providerId = com.example.washlink.data.AuthRepository.getInstance().getCurrentUserId();
        if (providerId == null) return;
        FirebaseFirestore.getInstance().collection("providers").document(providerId)
                .collection("riders").whereEqualTo("isActive", true).get()
                .addOnSuccessListener(snapshot -> {
                    List<DocumentSnapshot> riders = new ArrayList<>(snapshot.getDocuments());
                    if (riders.isEmpty()) {
                        Toast.makeText(UpdateOrderStatusActivity.this,
                                "Add an active rider from your provider profile first.",
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    String[] labels = new String[riders.size()];
                    for (int i = 0; i < riders.size(); i++) {
                        labels[i] = riders.get(i).getString("name") + " · "
                                + riders.get(i).getString("phone");
                    }
                    new androidx.appcompat.app.AlertDialog.Builder(UpdateOrderStatusActivity.this)
                            .setTitle("Assign rider")
                            .setItems(labels, (dialog, index) -> {
                                assignRiderButton.setEnabled(false);
                                BookingService.getInstance().assignBookingRider(bookingId,
                                        riders.get(index).getId(), new BookingService.SimpleCallback() {
                                            @Override public void onSuccess() {
                                                Toast.makeText(UpdateOrderStatusActivity.this,
                                                        "Rider assigned.", Toast.LENGTH_SHORT).show();
                                            }
                                            @Override public void onError(String message) {
                                                assignRiderButton.setEnabled(true);
                                                Toast.makeText(UpdateOrderStatusActivity.this,
                                                        "Could not assign rider: " + message,
                                                        Toast.LENGTH_LONG).show();
                                            }
                                        });
                            })
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                })
                .addOnFailureListener(error -> Toast.makeText(UpdateOrderStatusActivity.this,
                        "Could not load riders: " + error.getMessage(), Toast.LENGTH_LONG).show());
    }

    private static String value(String text, String fallback) {
        return text == null || text.trim().isEmpty() ? fallback : text;
    }

    private static boolean isRiderAssignableStatus(String status) {
        OrderStatus orderStatus = OrderStatus.fromString(status);
        return orderStatus == OrderStatus.ACCEPTED
                || orderStatus == OrderStatus.PICKED_UP
                || orderStatus == OrderStatus.WASHING
                || orderStatus == OrderStatus.DRYING
                || orderStatus == OrderStatus.READY
                || orderStatus == OrderStatus.OUT_FOR_DELIVERY;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (approvalListener != null) approvalListener.remove();
        if (bookingListener != null) {
            if (BookingServiceFacade.isUsingStub()) BookingServiceStub.getInstance().removeListener(bookingListener);
            else BookingService.getInstance().removeListener(bookingListener);
        }
    }
}
