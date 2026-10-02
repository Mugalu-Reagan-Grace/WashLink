package com.example.washlink;

import android.widget.TextView;

public final class PaymentStatusRenderer {
    private PaymentStatusRenderer() {}

    public static void render(TextView view, String status) {
        if (view == null) return;
        if ("PAID".equalsIgnoreCase(status)) {
            view.setText(R.string.payment_paid);
            view.setTextColor(view.getContext().getColor(R.color.payment_paid_text));
            view.setBackgroundResource(R.drawable.bg_payment_paid_pill);
        } else if ("FAILED".equalsIgnoreCase(status)) {
            view.setText(R.string.payment_failed);
            view.setTextColor(view.getContext().getColor(R.color.rejected_text));
            view.setBackgroundResource(R.drawable.bg_payment_failed_pill);
        } else {
            view.setText(R.string.payment_pending);
            view.setTextColor(view.getContext().getColor(R.color.payment_pending_text));
            view.setBackgroundResource(R.drawable.bg_payment_pending_pill);
        }
    }
}
