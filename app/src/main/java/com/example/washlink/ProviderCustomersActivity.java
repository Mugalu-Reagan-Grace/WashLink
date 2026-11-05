package com.example.washlink;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.example.washlink.data.AuthGuard;
import com.example.washlink.data.AuthRepository;
import com.example.washlink.data.BookingPricing;
import com.example.washlink.data.BookingService;
import com.example.washlink.data.ListenerRegistration;
import com.example.washlink.models.Booking;
import com.example.washlink.models.OrderStatus;
import com.example.washlink.models.UserAccount;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ProviderCustomersActivity extends WashLinkActivity {
    private final Map<String, CustomerRecord> customers = new LinkedHashMap<>();
    private String providerId;
    private LinearLayout rows;
    private TextView state;
    private TextView customerCount;
    private EditText search;
    private ListenerRegistration registration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AuthGuard.requireRole(this, UserAccount.ROLE_PROVIDER, ProviderLoginActivity.class);
        setContentView(R.layout.activity_provider_customers);
        ProviderNavBarHelper.bind(this, -1);

        rows = findViewById(R.id.provider_customer_items);
        state = findViewById(R.id.tv_provider_customer_state);
        customerCount = findViewById(R.id.tv_provider_customer_count);
        search = findViewById(R.id.et_provider_customer_search);
        findViewById(R.id.btn_provider_customer_back).setOnClickListener(v -> finish());
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderCustomers();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        providerId = AuthRepository.getInstance().getCurrentUserId();
        if (providerId == null) {
            showState("Provider account is unavailable.");
            return;
        }
        listenForCustomers();
    }

    private void listenForCustomers() {
        if (registration != null) registration.remove();
        showState("Loading customers from your bookings...");
        registration = BookingService.getInstance().addProviderBookingsListener(providerId,
                new BookingService.ProviderBookingsListener() {
                    @Override
                    public void onBookingsChanged(List<Booking> bookings) {
                        customers.clear();
                        for (Booking booking : bookings) {
                            String key = customerKey(booking);
                            CustomerRecord record = customers.get(key);
                            if (record == null) {
                                record = new CustomerRecord(key);
                                customers.put(key, record);
                            }
                            record.add(booking);
                        }
                        renderCustomers();
                    }

                    @Override
                    public void onError(String message) {
                        showState("Could not load customer bookings: " + message);
                    }
                });
    }

    private String customerKey(Booking booking) {
        String id = booking.getCustomerId();
        if (id != null && !id.trim().isEmpty()) return "id:" + id.trim();
        String phone = booking.getCustomerPhone();
        if (phone != null && !phone.trim().isEmpty()) {
            return "phone:" + phone.replaceAll("[^0-9]", "");
        }
        String name = booking.getCustomerName();
        if (name != null && !name.trim().isEmpty()) {
            return "name:" + name.trim().toLowerCase(Locale.ROOT);
        }
        return "booking:" + booking.getId();
    }

    private void renderCustomers() {
        rows.removeAllViews();
        String query = search.getText() == null ? "" : search.getText().toString()
                .trim().toLowerCase(Locale.ROOT);
        List<CustomerRecord> visible = new ArrayList<>();
        int bookingCount = 0;
        for (CustomerRecord customer : customers.values()) {
            bookingCount += customer.bookings.size();
            if (customer.name.toLowerCase(Locale.ROOT).contains(query)
                    || customer.phone.toLowerCase(Locale.ROOT).contains(query)) {
                visible.add(customer);
            }
        }
        customerCount.setText(customers.size() + " customers · " + bookingCount + " bookings");
        if (customers.isEmpty()) {
            showState("Customer records appear after customers book with your business.");
            return;
        }
        if (visible.isEmpty()) {
            showState("No customers match your search.");
            return;
        }
        state.setVisibility(View.GONE);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (CustomerRecord customer : visible) {
            View row = inflater.inflate(R.layout.activity_provider_customer_item, rows, false);
            TextView initial = row.findViewById(R.id.tv_provider_customer_initial);
            TextView name = row.findViewById(R.id.tv_provider_customer_name);
            TextView phone = row.findViewById(R.id.tv_provider_customer_phone);
            TextView summary = row.findViewById(R.id.tv_provider_customer_bookings);
            TextView value = row.findViewById(R.id.tv_provider_customer_value);
            name.setText(customer.name);
            initial.setText(customer.name.substring(0, 1).toUpperCase(Locale.getDefault()));
            phone.setText(customer.phone.isEmpty() ? "Phone not recorded" : customer.phone);
            summary.setText(customer.bookings.size() + " booking"
                    + (customer.bookings.size() == 1 ? "" : "s"));
            value.setText("Booking value: " + BookingPricing.format((int) customer.bookingValue));
            row.setOnClickListener(v -> showCustomerHistory(customer));
            row.findViewById(R.id.btn_provider_customer_history)
                    .setOnClickListener(v -> showCustomerHistory(customer));
            rows.addView(row);
        }
    }

    private void showCustomerHistory(CustomerRecord customer) {
        LinearLayout history = new LinearLayout(this);
        history.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(18);
        history.setPadding(padding, 0, padding, 0);
        TextView summary = new TextView(this);
        summary.setText(customer.phone.isEmpty()
                ? customer.name : customer.name + "\n" + customer.phone);
        summary.setTextSize(14);
        summary.setTextColor(getColor(R.color.text_primary));
        summary.setPadding(0, 0, 0, dp(10));
        history.addView(summary);

        for (Booking booking : customer.bookings) {
            TextView entry = new TextView(this);
            OrderStatus status = OrderStatus.fromString(booking.getStatus());
            String service = booking.getServiceName() == null
                    ? "Laundry service" : booking.getServiceName();
            String date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                    .format(new java.util.Date(booking.getCreatedAt()));
            entry.setText(service + " · " + status.getDisplayName() + "\n"
                    + date + " · " + BookingPricing.format((int) booking.getTotal()));
            entry.setTextColor(getColor(R.color.text_primary));
            entry.setTextSize(13);
            entry.setPadding(0, dp(10), 0, dp(10));
            history.addView(entry);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.addView(history);
        new AlertDialog.Builder(this)
                .setTitle("Booking history")
                .setView(scroll)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + .5f);
    }

    private void showState(String message) {
        rows.removeAllViews();
        state.setText(message);
        state.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        if (registration != null) registration.remove();
        super.onDestroy();
    }

    private static final class CustomerRecord {
        final String key;
        final List<Booking> bookings = new ArrayList<>();
        String name = "Customer";
        String phone = "";
        double bookingValue;

        CustomerRecord(String key) {
            this.key = key;
        }

        void add(Booking booking) {
            bookings.add(booking);
            bookingValue += booking.getTotal();
            if (name.equals("Customer") && booking.getCustomerName() != null
                    && !booking.getCustomerName().trim().isEmpty()) {
                name = booking.getCustomerName().trim();
            }
            if (phone.isEmpty() && booking.getCustomerPhone() != null) {
                phone = booking.getCustomerPhone().trim();
            }
        }
    }
}
