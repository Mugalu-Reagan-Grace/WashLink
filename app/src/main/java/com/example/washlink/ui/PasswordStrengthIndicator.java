package com.example.washlink.ui;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.example.washlink.R;

import java.util.ArrayList;
import java.util.List;

public final class PasswordStrengthIndicator {
    private PasswordStrengthIndicator() {}

    public static void bind(EditText input, TextView feedback, ProgressBar meter) {
        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                update(s.toString(), feedback, meter);
            }

            @Override
            public void afterTextChanged(Editable editable) {}
        };
        input.addTextChangedListener(watcher);
        update(input.getText() == null ? "" : input.getText().toString(), feedback, meter);
    }

    private static void update(String password, TextView feedback, ProgressBar meter) {
        int score = PasswordStrength.score(password);
        meter.setProgress(score);
        if (password.isEmpty()) {
            feedback.setText(R.string.password_strength_start);
            feedback.setTextColor(feedback.getContext().getColor(R.color.text_secondary));
            meter.setProgressTintList(ColorStateList.valueOf(
                    feedback.getContext().getColor(R.color.divider_gray)));
            return;
        }

        int color;
        int label;
        if (score <= 2) {
            color = Color.rgb(198, 40, 40);
            label = R.string.password_strength_weak;
        } else if (score == 3 || password.length() < 8) {
            color = Color.rgb(239, 108, 0);
            label = R.string.password_strength_fair;
        } else if (score == 4) {
            color = Color.rgb(46, 125, 50);
            label = R.string.password_strength_good;
        } else {
            color = Color.rgb(27, 94, 32);
            label = R.string.password_strength_strong;
        }

        List<String> missing = new ArrayList<>();
        if (password.length() < 8) missing.add(feedback.getContext().getString(R.string.password_rule_length));
        if (!password.matches("(?s).*\\p{Lower}.*")) missing.add(feedback.getContext().getString(R.string.password_rule_lowercase));
        if (!password.matches("(?s).*\\p{Upper}.*")) missing.add(feedback.getContext().getString(R.string.password_rule_uppercase));
        if (!password.matches("(?s).*\\d.*")) missing.add(feedback.getContext().getString(R.string.password_rule_number));
        if (!password.matches("(?s).*[^\\p{Alnum}].*")) missing.add(feedback.getContext().getString(R.string.password_rule_symbol));

        String status = feedback.getContext().getString(R.string.password_strength_format,
                feedback.getContext().getString(label));
        if (!missing.isEmpty()) {
            status += " " + feedback.getContext().getString(R.string.password_strength_suggestions,
                    android.text.TextUtils.join(", ", missing));
        }
        feedback.setText(status);
        feedback.setTextColor(color);
        meter.setProgressTintList(ColorStateList.valueOf(color));
    }
}
