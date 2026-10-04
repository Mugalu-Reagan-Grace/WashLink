package com.example.washlink.ui;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.WeakHashMap;

public final class SystemBarInsets {
    private static final WeakHashMap<View, int[]> BASE_PADDING = new WeakHashMap<>();

    private SystemBarInsets() { }

    public static void apply(Activity activity) {
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null) return;
        View root = content.getChildCount() > 0 ? content.getChildAt(0) : content;

        int[] basePadding = BASE_PADDING.get(root);
        if (basePadding == null) {
            basePadding = new int[] {
                    root.getPaddingLeft(),
                    root.getPaddingTop(),
                    root.getPaddingRight(),
                    root.getPaddingBottom()
            };
            BASE_PADDING.put(root, basePadding);
        }
        int[] initialPadding = basePadding;

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets safeInsets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(
                    Math.max(initialPadding[0], safeInsets.left),
                    Math.max(initialPadding[1], safeInsets.top),
                    Math.max(initialPadding[2], safeInsets.right),
                    Math.max(initialPadding[3], safeInsets.bottom));

            return new WindowInsetsCompat.Builder(windowInsets)
                    .setInsets(WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout(), Insets.NONE)
                    .build();
        });
        ViewCompat.requestApplyInsets(root);
    }
}
