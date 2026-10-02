package com.example.washlink;

import android.content.Intent;
import android.os.Bundle;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

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

public class PaymentActivity extends AppCompatActivity {

    private ImageView backButton;
    private FrameLayout bellLayout;
    private ConstraintLayout visaCard;
    private ConstraintLayout mastercardCard;
    private ConstraintLayout airtelCard;
    private ConstraintLayout mtnCard;
    private ConstraintLayout cashCard;
    private RadioButton visaRadio;
    private RadioButton mcRadio;
    private RadioButton airtelRadio;
    private RadioButton mtnRadio;
    private RadioButton cashRadio;
    private MaterialButton googlePayButton;
    private MaterialButton addNewCardButton;
    private MaterialButton payButton;
    private String selectedPaymentMethod = "Visa •••• 4242";
    private String selectedPaymentCode = "card";
    private Booking pendingBooking;
    private BookingPricing.Quote pendingQuote;
    private String pendingPaymentCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_payment);

        backButton = findViewById(R.id.btn_back);
        bellLayout = findViewById(R.id.iv_bell);
        visaCard = findViewById(R.id.card_visa);
        mastercardCard = findViewById(R.id.card_mastercard);
        airtelCard = findViewById(R.id.card_airtel);
        mtnCard = findViewById(R.id.card_mtn);
        cashCard = findViewById(R.id.card_cash);
        visaRadio = findViewById(R.id.radio_visa);
        mcRadio = findViewById(R.id.radio_mc);
        airtelRadio = findViewById(R.id.radio_airtel);
        mtnRadio = findViewById(R.id.radio_mtn);
        cashRadio = findViewById(R.id.radio_cash);
        googlePayButton = findViewById(R.id.btn_google_pay);
        addNewCardButton = findViewById(R.id.btn_add_new_card);
        payButton = findViewById(R.id.btn_pay);

        backButton.setOnClickListener(v -> finish());
        bellLayout.setOnClickListener(v -> {
                Intent intent = new Intent(PaymentActivity.this, NotificationsActivity.class);
                startActivity(intent);
            });

        visaCard.setOnClickListener(v -> selectPaymentMethod("visa"));
        mastercardCard.setOnClickListener(v -> selectPaymentMethod("mastercard"));
        airtelCard.setOnClickListener(v -> selectPaymentMethod("airtel"));
        mtnCard.setOnClickListener(v -> selectPaymentMethod("mtn"));
        cashCard.setOnClickListener(v -> selectPaymentMethod("cash"));

        visaRadio.setOnClickListener(v -> selectPaymentMethod("visa"));
        mcRadio.setOnClickListener(v -> selectPaymentMethod("mastercard"));
        airtelRadio.setOnClickListener(v -> selectPaymentMethod("airtel"));
        mtnRadio.setOnClickListener(v -> selectPaymentMethod("mtn"));
        cashRadio.setOnClickListener(v -> selectPaymentMethod("cash"));

        googlePayButton.setOnClickListener(v -> {
            Toast.makeText(this, "Choose card or Uganda mobile money for hosted checkout.",
                    Toast.LENGTH_SHORT).show();
        });
        googlePayButton.setEnabled(false);

        addNewCardButton.setOnClickListener(v -> {
            Toast.makeText(this, "Card details are entered securely on Flutterwave checkout.",
                    Toast.LENGTH_SHORT).show();
        });

        int weightKg = getIntent() != null ? getIntent().getIntExtra("weight_kg", com.example.washlink.data.BookingPricing.estimateWeightKg(getIntent().getIntExtra("item_count", 15))) : com.example.washlink.data.BookingPricing.estimateWeightKg(getIntent() != null ? getIntent().getIntExtra("item_count", 15) : 15);
        double pricePerKg = getIntent().getDoubleExtra("price_per_kg", BookingPricing.PRICE_PER_KG);
        BookingPricing.Quote quote = BookingPricing.quote(weightKg, true, pricePerKg);
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
            String address = getIntent().getStringExtra("selected_address");
            if (address == null) address = getIntent().getStringExtra("address");
            if (address == null) address = "Address to be confirmed";
            final String requestedPaymentCode = selectedPaymentCode;
            final String requestedPaymentLabel = selectedPaymentMethod;
            Booking booking = new Booking(
                    user.getUid(),
                    user.getDisplayName() == null ? "Customer" : user.getDisplayName(),
                    getIntent().getStringExtra("provider_id"),
                    getIntent().getStringExtra("provider_name"),
                    Booking.SERVICE_TYPE_PICKUP,
                    getIntent().getStringExtra("selected_laundry_service") == null
                            ? getIntent().getStringExtra("selected_service")
                            : getIntent().getStringExtra("selected_laundry_service"),
                    address);
            booking.setScheduledDateTime(getIntent().getStringExtra("selected_date")
                    + " • " + getIntent().getStringExtra("selected_time"));
            booking.setItemCount(getIntent().getIntExtra("item_count", 0));
            booking.setEstimatedWeight(weightKg + " kg");
            booking.setSubtotal(quote.laundry + quote.pickup + quote.delivery);
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
                                ? "visa" : pendingPaymentCode);
                    }
                    continuePayment(pendingBooking, pendingQuote, pendingPaymentCode);
            }, message -> {
                    payButton.setEnabled(true);
                    Toast.makeText(PaymentActivity.this,
                            "Could not create booking: " + message, Toast.LENGTH_LONG).show();
            });
        });

        selectPaymentMethod("visa");
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
        Intent intent = new Intent(PaymentActivity.this, OrderConfirmedActivity.class);
        if (getIntent() != null) intent.putExtras(getIntent());
        intent.putExtra("booking_id", booking.getId());
        intent.putExtra("selected_payment_method", booking.getPaymentMethod());
        intent.putExtra("payment_status", booking.getPaymentStatus());
        intent.putExtra("provider_name", booking.getProviderName());
        intent.putExtra("selected_address", booking.getAddress());
        intent.putExtra("selected_date", getIntent().getStringExtra("selected_date"));
        intent.putExtra("selected_time", getIntent().getStringExtra("selected_time"));
        intent.putExtra("quote_total", quote.total);
        startActivity(intent);
        finish();
    }

    private void selectPaymentMethod(String method) {
        if ("visa".equals(method)) {
            selectedPaymentMethod = "Flutterwave card";
            selectedPaymentCode = "card";
        } else if ("mastercard".equals(method)) {
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

        visaRadio.setChecked("visa".equals(method));
        mcRadio.setChecked("mastercard".equals(method));
        airtelRadio.setChecked("airtel".equals(method));
        mtnRadio.setChecked("mtn".equals(method));
        cashRadio.setChecked("cash".equals(method));

        setCardSelectedState(visaCard, "visa".equals(method));
        setCardSelectedState(mastercardCard, "mastercard".equals(method));
        setCardSelectedState(airtelCard, "airtel".equals(method));
        setCardSelectedState(mtnCard, "mtn".equals(method));
        setCardSelectedState(cashCard, "cash".equals(method));
    }

    private void setCardSelectedState(ConstraintLayout card, boolean selected) {
        card.setBackgroundResource(selected ? R.drawable.bg_card_selected_outline : R.drawable.bg_card_bordered);
    }
}
