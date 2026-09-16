package com.example.washlink;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

public class SuccessActivity extends AppCompatActivity {

    private MaterialButton bookWashButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_success);

        bookWashButton = findViewById(R.id.btn_book_wash);
        bookWashButton.setOnClickListener(v -> bookWashClicked());
    }

    private void bookWashClicked() {
        Intent intent = new Intent(SuccessActivity.this, SelectServiceActivity.class);
        startActivity(intent);
        finish();
    }
}
