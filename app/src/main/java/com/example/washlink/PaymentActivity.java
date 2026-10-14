package com.example.washlink;

import android.content.Intent;
import android.os.Bundle;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

import java.util.Locale;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.google.android.material.button.MaterialButton;
import com.example.washlink.data.BookingPricing;
import com.example.washlink.data.FlutterwavePaymentClient;
import com.example.washlink.models.Booking;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.Collections;

public class PaymentActivity extends WashLinkActivity {
    private boolean dropOff;

    private ImageView backButton;
    private FrameLayout bellLayout;
    private ConstraintLayout cardPaymentCard;
    private ConstraintLayout airtelCard;
    private ConstraintLayout mtnCard;
    private ConstraintLayout cashCard;
    private RadioButton cardPaymentRadio;
    private RadioButton airtelRadio;
    private RadioButton mtnRadio;
    private RadioButton cashRadio;
    private MaterialButton payButton;
    private String selectedPaymentMethod = "Flutterwave card";
    private String selectedPaymentCode = "card";
    private Booking pendingBooking;
    private BookingPricing.Quote pendingQuote;
    private String pendingPaymentCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        dropOff = getIntent().getBooleanExtra("drop_off", false);
        setContentView(R.layout.activity_customer_payment);
        com.example.washlink.data.AuthGuard.requireRole(this,
                com.example.washlink.models.UserAccount.ROLE_CUSTOMER, SignInActivity.class);

        backButton = findViewById(R.id.btn_back);
        bellLayout = findViewById(R.id.iv_bell);
        cardPaymentCard = findViewById(R.id.card_payment);
        airtelCard = findViewById(R.id.card_airtel);
        mtnCard = findViewById(R.id.card_mtn);
        cashCard = findViewById(R.id.card_cash);
        cardPaymentRadio = findViewById(R.id.radio_card_payment);
        airtelRadio = findViewById(R.id.radio_airtel);
        mtnRadio = findViewById(R.id.radio_mtn);
        cashRadio = findViewById(R.id.radio_cash);
        payButton = findViewById(R.id.btn_pay);
        int accent = com.example.washlink.ui.ThemeManager.accent(this,
                com.example.washlink.ui.ThemeManager.ROLE_CUSTOMER);
        ColorStateList radioTint = ColorStateList.valueOf(accent);
        cardPaymentRadio.setButtonTintList(radioTint);
        airtelRadio.setButtonTintList(radioTint);
        mtnRadio.setButtonTintList(radioTint);
        cashRadio.setButtonTintList(radioTint);

        backButton.setOnClickListener(v -> finish());
        bellLayout.setOnClickListener(v -> {
                Intent intent = new Intent(PaymentActivity.this, NotificationsActivity.class);
                startActivity(intent);
            });

        cardPaymentCard.setOnClickListener(v -> selectPaymentMethod("card"));
        airtelCard.setOnClickListener(v -> selectPaymentMethod("airtel"));
        mtnCard.setOnClickListener(v -> selectPaymentMethod("mtn"));
        cashCard.setOnClickListener(v -> selectPaymentMethod("cash"));

        cardPaymentRadio.setOnClickListener(v -> selectPaymentMethod("card"));
        airtelRadio.setOnClickListener(v -> selectPaymentMethod("airtel"));
        mtnRadio.setOnClickListener(v -> selectPaymentMethod("mtn"));
        cashRadio.setOnClickListener(v -> selectPaymentMethod("cash"));

        int weightKg = getIntent() != null ? getIntent().getIntExtra("weight_kg", com.example.washlink.data.BookingPricing.estimateWeightKg(getIntent().getIntExtra("item_count", 1))) : com.example.washlink.data.BookingPricing.estimateWeightKg(getIntent() != null ? getIntent().getIntExtra("item_count", 1) : 1);
        double pricePerKg = getIntent().getDoubleExtra("price_per_kg", BookingPricing.PRICE_PER_KG);
        BookingPricing.Quote quote = BookingPricing.quote(weightKg, !dropOff, pricePerKg);
        ((TextView) findViewById(R.id.tv_payment_subtotal))
                .setText(BookingPricing.format(quote.laundry + quote.pickup + quote.delivery));
        ((TextView) findViewById(R.id.tv_payment_tax))
                .setText(BookingPricing.format(quote.serviceFee));
        ((TextView) findViewById(R.id.tv_payment_total))
                .setText(BookingPricing.format(quote.total));
        String totalText = String.format(Locale.US, "Pay UGX %,d", quote.total);
        payButton.setText(totalText);

        payButton.setOnClickListener(v -> {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) {
                Toast.makeText(this, "Please sign in before placing an order", Toast.LENGTH_SHORT).show();
                return;
            }
            if (pendingBooking != null) {
                if (!pendingPaymentCode.equals(selectedPaymentCode)) {
                    Toast.makeText(this, "Retry the current payment before changing methods.",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                payButton.setEnabled(false);
                continuePayment(pendingBooking, pendingQuote, pendingPaymentCode);
                return;
            }
            payButton.setEnabled(false);
            String address = dropOff
                    ? getIntent().getStringExtra("provider_address")
                    : getIntent().getStringExtra("selected_address");
            if (address == null) address = getIntent().getStringExtra("address");
            if (address == null) {
                address = dropOff ? "Provider drop-off location" : "Address to be confirmed";
            }
            final String requestedPaymentCode = selectedPaymentCode;
            final String requestedPaymentLabel = selectedPaymentMethod;
            Booking booking = new Booking(
                    user.getUid(),
                    user.getDisplayName() == null ? "Customer" : user.getDisplayName(),
                    getIntent().getStringExtra("provider_id"),
                    getIntent().getStringExtra("provider_name"),
                    dropOff ? Booking.SERVICE_TYPE_DROPOFF : Booking.SERVICE_TYPE_PICKUP,
                    getIntent().getStringExtra("selected_laundry_service") == null
                            ? (getIntent().getStringExtra("selected_service") == null
                            ? (dropOff ? "Drop Off" : "Laundry service")
                            : getIntent().getStringExtra("selected_service"))
                            : getIntent().getStringExtra("selected_laundry_service"),
                    address);
            booking.setScheduledDateTime(getIntent().getStringExtra("selected_date")
                    + " • " + getIntent().getStringExtra("selected_time"));
            booking.setItemCount(getIntent().getIntExtra("item_count", 1));
            booking.setWeightKg(weightKg);
            ArrayList<String> serviceNames = getIntent().getStringArrayListExtra(
                    "selected_laundry_services");
            if (serviceNames == null || serviceNames.isEmpty()) {
                serviceNames = new ArrayList<>(Collections.singletonList(
                        booking.getServiceName()));
            }
            booking.setServiceNames(serviceNames);
            booking.setEstimatedWeight(weightKg + " kg");
            booking.setSubtotal(dropOff ? quote.laundry : quote.laundry + quote.pickup + quote.delivery);
            booking.setPickupFee(quote.pickup);
            booking.setDeliveryFee(quote.delivery);
            booking.setServiceFee(quote.serviceFee);
            booking.setTax(quote.serviceFee);
            booking.setTotal(quote.total);
            booking.setPaymentMethod(requestedPaymentLabel);
            booking.setPaymentStatus("PENDING");

            FlutterwavePaymentClient.createBooking(booking, requestedPaymentCode,
                    bookingId -> {
                    booking.setId(bookingId);
                    pendingBooking = booking;
                    pendingQuote = quote;
                    pendingPaymentCode = requestedPaymentCode;
                    if (!pendingPaymentCode.equals(selectedPaymentCode)) {
                        selectPaymentMethod("card".equals(pendingPaymentCode)
                                ? "card" : pendingPaymentCode);
                    }
                    continuePayment(pendingBooking, pendingQuote, pendingPaymentCode);
            }, message -> {
                    payButton.setEnabled(true);
                    Toast.makeText(PaymentActivity.this,
                            "Could not create booking: " + message, Toast.LENGTH_LONG).show();
            });
        });

        selectPaymentMethod("card");
    }

    private void continuePayment(Booking booking, BookingPricing.Quote quote, String methodCode) {
        if ("cash".equals(methodCode)) {
            FlutterwavePaymentClient.registerCashOnDelivery(booking.getId(),
                    () -> confirmBooking(booking, quote),
                    message -> {
                        payButton.setEnabled(true);
                        Toast.makeText(this, "Could not confirm cash on delivery: " + message,
                                Toast.LENGTH_LONG).show();
                    });
            return;
        }
        FlutterwavePaymentClient.initialize(booking.getId(), methodCode,
                checkoutUrl -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW,
                                android.net.Uri.parse(checkoutUrl)));
                        finish();
                    } catch (android.content.ActivityNotFoundException error) {
                        payButton.setEnabled(true);
                        Toast.makeText(this, "No browser is available to open checkout.",
                                Toast.LENGTH_LONG).show();
                    }
                },
                message -> {
                    payButton.setEnabled(true);
                    Toast.makeText(this, "Could not start payment: " + message,
                            Toast.LENGTH_LONG).show();
                });
    }

    private void confirmBooking(Booking booking, BookingPricing.Quote quote) {
        Intent intent = new Intent(PaymentActivity.this,
                dropOff ? OrderConfirmedDropOffActivity.class : OrderConfirmedActivity.class);
        if (getIntent() != null) intent.putExtras(getIntent());
        intent.putExtra("booking_id", booking.getId());
        intent.putExtra("selected_payment_method", booking.getPaymentMethod());
        intent.putExtra("payment_status", booking.getPaymentStatus());
        intent.putExtra("provider_name", booking.getProviderName());
        intent.putExtra("selected_address", booking.getAddress());
        intent.putExtra("provider_address", booking.getAddress());
        intent.putExtra("selected_date", getIntent().getStringExtra("selected_date"));
        intent.putExtra("selected_time", getIntent().getStringExtra("selected_time"));
        intent.putExtra("quote_total", quote.total);
        startActivity(intent);
        finish();
    }

    private void selectPaymentMethod(String method) {
        if ("card".equals(method)) {
            selectedPaymentMethod = "Flutterwave card";
            selectedPaymentCode = "card";
        } else if ("airtel".equals(method)) {
            selectedPaymentMethod = getString(R.string.payment_airtel_money);
            selectedPaymentCode = "airtel";
        } else if ("mtn".equals(method)) {
            selectedPaymentMethod = getString(R.string.payment_mtn_mobile_money);
            selectedPaymentCode = "mtn";
        } else if ("cash".equals(method)) {
            selectedPaymentMethod = getString(R.string.payment_cash_on_delivery);
            selectedPaymentCode = "cash";
        }

        cardPaymentRadio.setChecked("card".equals(method));
        airtelRadio.setChecked("airtel".equals(method));
        mtnRadio.setChecked("mtn".equals(method));
        cashRadio.setChecked("cash".equals(method));

        setCardSelectedState(cardPaymentCard, "card".equals(method));
        setCardSelectedState(airtelCard, "airtel".equals(method));
        setCardSelectedState(mtnCard, "mtn".equals(method));
        setCardSelectedState(cashCard, "cash".equals(method));
    }

    private void setCardSelectedState(ConstraintLayout card, boolean selected) {
        if (!selected) {
            card.setBackgroundResource(R.drawable.bg_card_bordered);
            return;
        }
        GradientDrawable outline = new GradientDrawable();
        outline.setColor(Color.TRANSPARENT);
        outline.setCornerRadius(14 * getResources().getDisplayMetrics().density);
        outline.setStroke((int) (1.5f * getResources().getDisplayMetrics().density),
                com.example.washlink.ui.ThemeManager.accent(this,
                        com.example.washlink.ui.ThemeManager.ROLE_CUSTOMER));
        card.setBackground(outline);
    }
}
