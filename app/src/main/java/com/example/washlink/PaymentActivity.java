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
import com.example.washlink.data.BookingService;
import com.example.washlink.data.BookingServiceFacade;
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
            selectedPaymentMethod = "Google Pay";
            Toast.makeText(this, "Google Pay selected", Toast.LENGTH_SHORT).show();
            visaRadio.setChecked(false);
            mcRadio.setChecked(false);
            airtelRadio.setChecked(false);
            mtnRadio.setChecked(false);
            cashRadio.setChecked(false);
            visaCard.setBackgroundResource(R.drawable.bg_card_bordered);
            mastercardCard.setBackgroundResource(R.drawable.bg_card_bordered);
            airtelCard.setBackgroundResource(R.drawable.bg_card_bordered);
            mtnCard.setBackgroundResource(R.drawable.bg_card_bordered);
            cashCard.setBackgroundResource(R.drawable.bg_card_bordered);
        });

        addNewCardButton.setOnClickListener(v -> {
            Intent intent = new Intent(PaymentActivity.this, AddCardActivity.class);
            startActivity(intent);
        });

        int weightKg = getIntent() != null ? getIntent().getIntExtra("weight_kg", 10) : 10;
        BookingPricing.Quote quote = BookingPricing.quote(weightKg, true);
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
            payButton.setEnabled(false);
            String address = getIntent().getStringExtra("selected_address");
            if (address == null) address = getIntent().getStringExtra("address");
            if (address == null) address = "Address to be confirmed";
            Booking booking = new Booking(
                    user.getUid(),
                    user.getDisplayName() == null ? "Customer" : user.getDisplayName(),
                    getIntent().getStringExtra("provider_id"),
                    getIntent().getStringExtra("provider_name"),
                    Booking.SERVICE_TYPE_PICKUP,
                    getIntent().getStringExtra("selected_service"),
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
            booking.setPaymentMethod(selectedPaymentMethod);
            booking.setPaymentStatus("cash".equals(selectedPaymentMethod) ? "PENDING" : "PAID");

            BookingServiceFacade.getBookingService().createBooking(booking, new BookingService.SimpleCallback() {
                @Override
                public void onSuccess() {
                    Intent intent = new Intent(PaymentActivity.this, OrderConfirmedActivity.class);
                    if (getIntent() != null) intent.putExtras(getIntent());
                    intent.putExtra("booking_id", booking.getId());
                    intent.putExtra("selected_payment_method", selectedPaymentMethod);
                    intent.putExtra("quote_total", quote.total);
                    startActivity(intent);
                    finish();
                }

                @Override
                public void onError(String message) {
                    payButton.setEnabled(true);
                    Toast.makeText(PaymentActivity.this,
                            "Could not place order: " + message, Toast.LENGTH_LONG).show();
                }
            });
        });

        selectPaymentMethod("visa");
    }

    private void selectPaymentMethod(String method) {
        if ("visa".equals(method)) {
            selectedPaymentMethod = "Visa •••• 4242";
        } else if ("mastercard".equals(method)) {
            selectedPaymentMethod = "Mastercard •••• 4242";
        } else if ("airtel".equals(method)) {
            selectedPaymentMethod = getString(R.string.payment_airtel_money);
        } else if ("mtn".equals(method)) {
            selectedPaymentMethod = getString(R.string.payment_mtn_mobile_money);
        } else if ("cash".equals(method)) {
            selectedPaymentMethod = getString(R.string.payment_cash_on_delivery);
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
