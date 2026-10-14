package com.example.washlink;

import android.content.Intent;
import android.os.Bundle;

/** Compatibility entry point for legacy drop-off intents. */
public class OrderSummaryDropOffActivity extends OrderSummaryActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Intent intent = getIntent();
        intent.putExtra("drop_off", true);
        setIntent(intent);
        super.onCreate(savedInstanceState);
    }
}
