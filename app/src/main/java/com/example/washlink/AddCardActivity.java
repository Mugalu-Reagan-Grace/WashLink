package com.example.washlink;

import android.content.Intent;
import android.os.Bundle;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.TextView;
import android.text.Editable;
import android.text.TextWatcher;
import com.google.android.material.textfield.TextInputLayout;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

public class AddCardActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_add_card);

        ImageView backButton = findViewById(R.id.btn_back);
        FrameLayout bellLayout = findViewById(R.id.iv_bell);
        MaterialButton addCardButton = findViewById(R.id.btn_add_card);
        TextInputLayout cardNumberLayout = findViewById(R.id.til_card_number);
        TextInputLayout holderLayout = findViewById(R.id.til_cardholder);
        TextInputLayout expiryLayout = findViewById(R.id.til_expiry);
        TextInputLayout cvvLayout = findViewById(R.id.til_cvv);
        TextView cardNumberPreview = findViewById(R.id.tv_card_number_preview);
        TextView holderPreview = findViewById(R.id.tv_cardholder_preview);
        TextView expiryPreview = findViewById(R.id.tv_expiry_preview);
        TextView cvvPreview = findViewById(R.id.tv_cvv_preview);

        backButton.setOnClickListener(v -> finish());
        bellLayout.setOnClickListener(v -> {
                Intent intent = new Intent(AddCardActivity.this, NotificationsActivity.class);
                startActivity(intent);
            });
        addCardButton.setOnClickListener(v -> {
            Toast.makeText(this, "Card added successfully", Toast.LENGTH_SHORT).show();
            finish();
        });

        addWatcher(cardNumberLayout, text -> cardNumberPreview.setText(
                text.isEmpty() ? "1234 5678 9012 3456" : text));
        addWatcher(holderLayout, text -> holderPreview.setText(
                text.isEmpty() ? "CARDHOLDER NAME" : text.toUpperCase()));
        addWatcher(expiryLayout, text -> expiryPreview.setText(
                text.isEmpty() ? "MM/YY" : text));
        addWatcher(cvvLayout, text -> cvvPreview.setText(
                text.isEmpty() ? "123" : text));
    }

    private void addWatcher(TextInputLayout layout, TextWatcherCallback callback) {
        if (layout == null || layout.getEditText() == null) return;
        layout.getEditText().addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                callback.onChanged(s.toString().trim());
            }
            @Override public void afterTextChanged(Editable s) { }
        });
    }

    private interface TextWatcherCallback {
        void onChanged(String text);
    }
}
