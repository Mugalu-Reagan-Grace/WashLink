package com.example.washlink;

import android.os.Bundle;
import android.widget.ImageView;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

public class NotificationsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_notifications);

        ImageView back = findViewById(R.id.btn_back);
        if (back != null) back.setOnClickListener(v -> finish());

        BottomNavHelper.bind(this);
    }
}
