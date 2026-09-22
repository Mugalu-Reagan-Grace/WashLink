package com.example.washlink;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import android.widget.TextView;

public class SuccessActivity extends AppCompatActivity {

    public static final String EXTRA_USER_NAME = "extra_user_name";
    private MaterialButton bookWashButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_success);

        String userName = getIntent().getStringExtra(EXTRA_USER_NAME);
        if (userName != null && !userName.trim().isEmpty()) {
            TextView title = findViewById(R.id.tv_title);
            title.setText(getString(R.string.success_title, userName.trim()));
        }

        bookWashButton = findViewById(R.id.btn_book_wash);
        bookWashButton.setOnClickListener(v -> bookWashClicked());
    }

    private void bookWashClicked() {
        Intent intent = new Intent(SuccessActivity.this, SelectServiceActivity.class);
        startActivity(intent);
        finish();
    }
}
