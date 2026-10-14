package com.example.washlink;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

public class AddCardActivity extends WashLinkActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.payment_methods_title)
                .setMessage(R.string.payment_methods_checkout_info)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> finish())
                .setOnCancelListener(dialog -> finish())
                .show();
    }
}
