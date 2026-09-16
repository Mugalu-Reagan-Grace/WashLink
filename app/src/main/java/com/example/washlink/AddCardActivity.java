package com.example.washlink;

import android.content.Intent;
import android.os.Bundle;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

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

        backButton.setOnClickListener(v -> finish());
        bellLayout.setOnClickListener(v -> {
                Intent intent = new Intent(AddCardActivity.this, NotificationsActivity.class);
                startActivity(intent);
            });
        addCardButton.setOnClickListener(v -> {
            Toast.makeText(this, "Card added successfully", Toast.LENGTH_SHORT).show();
            finish();
        });
    }
}
