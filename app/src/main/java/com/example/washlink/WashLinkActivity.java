package com.example.washlink;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.washlink.ui.ThemeManager;

/** Applies the signed-in role's saved appearance before each screen inflates its layout. */
public abstract class WashLinkActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ThemeManager.prepareActivity(this);
        super.onCreate(savedInstanceState);
    }
}
