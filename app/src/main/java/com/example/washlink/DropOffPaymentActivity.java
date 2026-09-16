package com.example.washlink;

import android.content.Intent;
import android.os.Bundle;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.Toast;
import android.widget.RadioGroup;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.google.android.material.button.MaterialButton;

import java.util.Locale;

public class DropOffPaymentActivity extends AppCompatActivity {

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_payment_dropoff);

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
                Intent intent = new Intent(DropOffPaymentActivity.this, NotificationsActivity.class);
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
                selectPaymentMethod("google_pay");
                Toast.makeText(this, "Google Pay selected", Toast.LENGTH_SHORT).show();
            });

        addNewCardButton.setOnClickListener(v -> {
            Intent intent = new Intent(DropOffPaymentActivity.this, AddCardActivity.class);
            startActivity(intent);
        });

        payButton.setText(String.format(Locale.US, "Pay UGX %,d", 26000));
        selectPaymentMethod("visa");

        payButton.setOnClickListener(v -> {
            Intent intent = new Intent(DropOffPaymentActivity.this, OrderConfirmedDropOffActivity.class);
            if (getIntent() != null) {
                intent.putExtras(getIntent());
            }
            intent.putExtra("selected_payment_method", getSelectedPaymentLabel());
            startActivity(intent);
            finish();
        });
    }

    private String selectedPaymentMethod = "Visa •••• 4242";

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
        } else if ("google_pay".equals(method)) {
            selectedPaymentMethod = "Google Pay";
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
        if (card == null) return;
        card.setBackgroundResource(selected ? R.drawable.bg_card_selected_outline : R.drawable.bg_card_bordered);
    }

    private String getSelectedPaymentLabel() {
        return selectedPaymentMethod;
    }
}
