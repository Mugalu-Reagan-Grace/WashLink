package com.example.washlink;

import android.content.Intent;
import android.os.Bundle;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.example.washlink.data.BookingPricing;

public class OrderSummaryActivity extends WashLinkActivity {
    private boolean dropOff;

    private ImageView backButton;
    private FrameLayout bellLayout;
    private MaterialButton proceedPaymentButton;
    private TextView serviceTypeValueText;
    private TextView dateTimeValueText;
    private TextView addressValueText;
    private TextView itemsValueText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        dropOff = getIntent().getBooleanExtra("drop_off", false);
        setContentView(dropOff ? R.layout.activity_customer_order_summary_dropoff
                : R.layout.activity_customer_order_summary);
        com.example.washlink.data.AuthGuard.requireRole(this,
                com.example.washlink.models.UserAccount.ROLE_CUSTOMER, SignInActivity.class);

        backButton = findViewById(R.id.btn_back);
        bellLayout = findViewById(R.id.iv_bell);
        proceedPaymentButton = findViewById(R.id.btn_proceed_payment);
        TextView changeServiceButton = findViewById(R.id.tv_change_service);
        TextView editLogisticsButton = findViewById(R.id.tv_edit_logistics);
        serviceTypeValueText = findViewById(R.id.tv_service_type_value);
        dateTimeValueText = findViewById(R.id.tv_datetime_value);
        addressValueText = findViewById(R.id.tv_address_value);
        itemsValueText = findViewById(R.id.tv_items_value_inline);

        String serviceName = getIntent() != null ? getIntent().getStringExtra("selected_service") : null;
        String address = getIntent() != null ? (dropOff
                ? getIntent().getStringExtra("provider_address")
                : getIntent().getStringExtra("selected_address")) : null;
        String date = getIntent() != null ? getIntent().getStringExtra("selected_date") : null;
        String time = getIntent() != null ? getIntent().getStringExtra("selected_time") : null;
        String contact = getIntent() != null ? getIntent().getStringExtra("selected_contact") : null;
        int itemCount = getIntent() != null ? getIntent().getIntExtra("item_count", 1) : 1;
        int weightKg = getIntent() != null ? getIntent().getIntExtra("weight_kg", com.example.washlink.data.BookingPricing.estimateWeightKg(itemCount)) : com.example.washlink.data.BookingPricing.estimateWeightKg(itemCount);

        if (serviceName == null || serviceName.trim().isEmpty()) {
            serviceName = "Pickup & Delivery";
        }
        if (address == null || address.trim().isEmpty()) {
            address = "Home address";
        }
        if (date == null || date.trim().isEmpty()) {
            date = "Today";
        }
        if (time == null || time.trim().isEmpty()) {
            time = "10:00 AM";
        }
        if (contact == null || contact.trim().isEmpty()) {
            contact = "Call";
        }

        final String selectedService = serviceName;
        final String selectedAddress = address;
        final String selectedDate = date;
        final String selectedTime = time;
        final String selectedContact = contact;
        final int selectedItemCount = itemCount;
        double pricePerKg = getIntent().getDoubleExtra("price_per_kg", BookingPricing.PRICE_PER_KG);
        final BookingPricing.Quote quote = BookingPricing.quote(weightKg, true, pricePerKg);

        String laundryService = getIntent().getStringExtra("selected_laundry_service");
        serviceTypeValueText.setText(laundryService == null ? selectedService : laundryService);
        dateTimeValueText.setText(selectedDate + " • " + selectedTime + " • " + selectedContact);
        if (addressValueText != null) addressValueText.setText(selectedAddress);
        itemsValueText.setText(String.valueOf(selectedItemCount));
        setText(R.id.tv_summary_laundry_amount, quote.laundry);
        setText(R.id.tv_summary_pickup_fee, quote.pickup + quote.delivery);
        setText(R.id.tv_summary_subtotal_amount, quote.laundry + quote.pickup + quote.delivery);
        setText(R.id.tv_summary_service_fee, quote.serviceFee);
        setText(R.id.tv_summary_total_value, quote.total);
        setText(R.id.tv_base_service_value, quote.laundry);
        setText(R.id.tv_subtotal_value, quote.laundry);
        setText(R.id.tv_tax_value, quote.serviceFee);
        setText(R.id.tv_total_value, quote.total);

        backButton.setOnClickListener(v -> finish());
        changeServiceButton.setOnClickListener(v -> returnToProviderServices());
        editLogisticsButton.setOnClickListener(v -> returnToLogistics());
        bellLayout.setOnClickListener(v -> {
                Intent intent = new Intent(OrderSummaryActivity.this, NotificationsActivity.class);
                startActivity(intent);
            });

        proceedPaymentButton.setOnClickListener(v -> {
            Intent intent = new Intent(OrderSummaryActivity.this, PaymentActivity.class);
            intent.putExtras(getIntent());
            intent.putExtra("selected_service", selectedService);
            intent.putExtra(dropOff ? "provider_address" : "selected_address", selectedAddress);
            intent.putExtra("drop_off", dropOff);
            intent.putExtra("selected_date", selectedDate);
            intent.putExtra("selected_time", selectedTime);
            intent.putExtra("selected_contact", selectedContact);
            intent.putExtra("item_count", selectedItemCount);
            intent.putExtra("weight_kg", weightKg);
            intent.putExtra("quote_total", quote.total);
            startActivity(intent);
        });
    }

    private void setText(int id, int amount) {
        TextView view = findViewById(id);
        if (view != null) view.setText(BookingPricing.format(amount));
    }

    private void returnToProviderServices() {
        String providerId = getIntent().getStringExtra("provider_id");
        if (providerId == null || providerId.trim().isEmpty()) {
            Toast.makeText(this, "Provider details are unavailable. Choose a provider again.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent(this, ProviderDetailsActivity.class);
        intent.putExtras(getIntent());
        intent.putExtra("selected_service",
                getIntent().getStringExtra("selected_service"));
        intent.putStringArrayListExtra("repeat_service_names",
                getIntent().getStringArrayListExtra("selected_laundry_services"));
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    private void returnToLogistics() {
        String providerId = getIntent().getStringExtra("provider_id");
        if (providerId == null || providerId.trim().isEmpty()) {
            Toast.makeText(this, "Booking details are unavailable. Choose a provider again.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent(this, PickupAddressActivity.class);
        intent.putExtras(getIntent());
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }
}
