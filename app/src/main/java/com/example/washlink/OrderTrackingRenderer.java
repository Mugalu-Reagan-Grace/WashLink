package com.example.washlink;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;

import com.example.washlink.models.Booking;
import com.example.washlink.models.OrderStatus;

public final class OrderTrackingRenderer {
    private OrderTrackingRenderer() {
    }

    public static void render(Context context, View root, Booking booking) {
        OrderStatus status = OrderStatus.fromString(booking.getStatus());
        boolean dropOff = Booking.SERVICE_TYPE_DROPOFF.equals(booking.getServiceType());
        String providerName = booking.getProviderName();
        String address = booking.getAddress();
        setText(root, R.id.tv_order_title,
                isMissing(booking.getServiceName()) ? "Laundry service" : booking.getServiceName());
        setText(root, R.id.tv_order_id, isMissing(booking.getId())
                ? "Order details unavailable" : "Order #" + booking.getId());
        setText(root, R.id.tv_order_status, status.getDisplayName());
        setText(root, R.id.tv_eta_value,
                isMissing(booking.getScheduledDateTime())
                        ? "Not scheduled" : booking.getScheduledDateTime());
        setText(root, R.id.tv_tracking_provider, "Provider: "
                + (isMissing(providerName) ? "Details unavailable" : providerName));
        setText(root, R.id.tv_tracking_address,
                (dropOff ? "Drop-off/collection: " : "Pickup/delivery: ")
                        + (isMissing(address) ? "Address unavailable" : address));

        if (status == OrderStatus.CANCELLED || status == OrderStatus.REJECTED
                || status == OrderStatus.UNKNOWN) {
            View timeline = root.findViewById(R.id.timeline);
            if (timeline != null) timeline.setVisibility(View.GONE);
            setSummaryProgressVisibility(root, false);
            return;
        }

        setSummaryProgressVisibility(root, true);
        View timeline = root.findViewById(R.id.timeline);
        if (timeline != null) timeline.setVisibility(View.VISIBLE);

        int current = currentStep(status);
        int completed = current;
        int[] dotIds = {
                R.id.timeline_scheduled_dot,
                R.id.timeline_pickup_dot,
                R.id.timeline_washing_dot,
                R.id.timeline_drying_dot,
                R.id.timeline_delivery_dot
        };
        int[] checkIds = {R.id.timeline_scheduled_check, R.id.timeline_pickup_check, 0, 0, 0};
        int[] titleIds = {
                R.id.timeline_scheduled_title,
                R.id.timeline_pickup_title,
                R.id.timeline_washing_title,
                R.id.timeline_drying_title,
                R.id.timeline_delivery_title
        };
        int[] descriptionIds = {
                R.id.timeline_scheduled_desc,
                R.id.timeline_pickup_desc,
                R.id.timeline_washing_desc,
                R.id.timeline_drying_desc,
                R.id.timeline_delivery_desc
        };
        String[] descriptions = stepDescriptions(status, booking, dropOff);
        setText(root, R.id.timeline_scheduled_title, status == OrderStatus.BOOKED
                ? "Booking received" : status == OrderStatus.ACCEPTED
                ? "Provider accepted" : "Booking scheduled");
        if (dropOff) {
            setText(root, R.id.tv_label_pickup, "Drop-off");
            setText(root, R.id.tv_label_delivery, "Collection");
            setText(root, R.id.timeline_scheduled_title, status == OrderStatus.BOOKED
                    ? "Drop-off booked" : status == OrderStatus.ACCEPTED
                    ? "Provider accepted" : "Drop-off scheduled");
            setText(root, R.id.timeline_pickup_title, "Received by provider");
            setText(root, R.id.timeline_delivery_title,
                    status == OrderStatus.READY ? "Ready for collection" : "Collection");
        }
        int activeColor = context.getColor(R.color.laundr_blue);
        int completeColor = context.getColor(R.color.text_primary);
        int pendingColor = context.getColor(R.color.text_secondary);

        for (int i = 0; i < dotIds.length; i++) {
            boolean isComplete = i < completed;
            boolean isCurrent = i == current;
            View dot = root.findViewById(dotIds[i]);
            if (dot instanceof FrameLayout) {
                dot.setBackgroundResource(isComplete ? R.drawable.dot_timeline_complete
                        : isCurrent ? R.drawable.dot_active_indicator : R.drawable.dot_timeline_pending);
            } else if (dot != null) {
                dot.setBackgroundResource(isComplete ? R.drawable.dot_timeline_complete
                        : isCurrent ? R.drawable.dot_active_indicator : R.drawable.dot_timeline_pending);
            }

            if (checkIds[i] != 0) {
                ImageView check = root.findViewById(checkIds[i]);
                if (check != null) check.setVisibility(isComplete ? View.VISIBLE : View.GONE);
            }
            TextView title = root.findViewById(titleIds[i]);
            if (title != null) title.setTextColor(isCurrent ? activeColor
                    : isComplete ? completeColor : pendingColor);
            setText(root, descriptionIds[i], descriptions[i]);
        }

        updateSummaryProgress(root, status);
        setProgress(root, status);
    }

    private static void updateSummaryProgress(View root, OrderStatus status) {
        int sequence = status.getSequence();
        setBackground(root, R.id.dot_pickup,
                sequence >= OrderStatus.PICKED_UP.getSequence()
                        ? R.drawable.dot_timeline_complete : R.drawable.dot_active_indicator);
        setBackground(root, R.id.dot_cleaning,
                sequence > OrderStatus.WASHING.getSequence()
                        ? R.drawable.dot_timeline_complete
                        : sequence == OrderStatus.WASHING.getSequence()
                        ? R.drawable.dot_active_indicator : R.drawable.dot_inactive_indicator);
        setBackground(root, R.id.dot_delivery,
                status == OrderStatus.DELIVERED ? R.drawable.dot_timeline_complete
                        : status == OrderStatus.OUT_FOR_DELIVERY
                        ? R.drawable.dot_active_indicator : R.drawable.dot_inactive_indicator);
        TextView cleaningLabel = root.findViewById(R.id.tv_label_cleaning);
        TextView deliveryLabel = root.findViewById(R.id.tv_label_delivery);
        if (cleaningLabel != null) {
            cleaningLabel.setTextColor(sequence >= OrderStatus.WASHING.getSequence()
                    ? root.getContext().getColor(R.color.text_primary)
                    : root.getContext().getColor(R.color.text_secondary));
        }
        if (deliveryLabel != null) {
            deliveryLabel.setTextColor(status == OrderStatus.OUT_FOR_DELIVERY
                    || status == OrderStatus.DELIVERED
                    ? root.getContext().getColor(R.color.text_primary)
                    : root.getContext().getColor(R.color.text_secondary));
        }
    }

    private static void setBackground(View root, int id, int drawableId) {
        View view = root.findViewById(id);
        if (view != null) view.setBackgroundResource(drawableId);
    }

    private static void setSummaryProgressVisibility(View root, boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        int[] ids = {
                R.id.progress_track_bg,
                R.id.progress_track_fill,
                R.id.dot_pickup,
                R.id.dot_cleaning,
                R.id.dot_delivery,
                R.id.tv_label_pickup,
                R.id.tv_label_cleaning,
                R.id.tv_label_delivery,
        };
        for (int id : ids) {
            View view = root.findViewById(id);
            if (view != null) view.setVisibility(visibility);
        }
    }

    private static int currentStep(OrderStatus status) {
        switch (status) {
            case BOOKED:
            case ACCEPTED:
                return 0;
            case PICKED_UP:
            case WASHING:
                return 2;
            case DRYING:
                return 3;
            case READY:
            case OUT_FOR_DELIVERY:
                return 4;
            case DELIVERED:
                return 5;
            default:
                return 0;
        }
    }

    private static String[] stepDescriptions(OrderStatus status, Booking booking, boolean dropOff) {
        String schedule = isMissing(booking.getScheduledDateTime())
                ? "Schedule not set" : "Scheduled for " + booking.getScheduledDateTime();
        String scheduled = status == OrderStatus.BOOKED
                ? "Waiting for provider confirmation. " + schedule
                : status == OrderStatus.ACCEPTED
                ? "Accepted by your provider. " + schedule : schedule;
        String pickup = status.getSequence() >= OrderStatus.PICKED_UP.getSequence()
                ? (dropOff ? "Items checked in at the provider" : "Items collected by your provider")
                : (dropOff ? "Waiting for drop-off" : "Waiting for collection");
        String washing = stepStatus(status, OrderStatus.WASHING, "Items are being washed");
        String drying = stepStatus(status, OrderStatus.DRYING, "Items are being dried and prepared");
        String delivery;
        if (status == OrderStatus.DELIVERED) delivery = dropOff
                ? "Order collected" : "Order delivered";
        else if (status == OrderStatus.OUT_FOR_DELIVERY) delivery = "Your order is on its way";
        else if (status == OrderStatus.READY) delivery = dropOff
                ? "Ready for customer collection" : "Ready and waiting for delivery";
        else delivery = dropOff ? "Collection is pending" : "Delivery is pending";
        return new String[]{scheduled, pickup, washing, drying, delivery};
    }

    private static String stepStatus(OrderStatus current, OrderStatus step, String inProgress) {
        if (current == step) return inProgress;
        if (current.getSequence() > step.getSequence()) return "Completed";
        return "Pending";
    }

    private static void setProgress(View root, OrderStatus status) {
        View fill = root.findViewById(R.id.progress_track_fill);
        if (fill == null) return;
        float fraction;
        if (status == OrderStatus.DELIVERED) fraction = 1f;
        else if (status == OrderStatus.OUT_FOR_DELIVERY || status == OrderStatus.READY) fraction = 0.8f;
        else if (status == OrderStatus.DRYING) fraction = 0.65f;
        else if (status == OrderStatus.WASHING || status == OrderStatus.PICKED_UP) fraction = 0.5f;
        else fraction = 0.2f;
        if (fill.getLayoutParams() instanceof ConstraintLayout.LayoutParams) {
            ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) fill.getLayoutParams();
            params.matchConstraintPercentWidth = fraction;
            fill.setLayoutParams(params);
        }
    }

    private static void setText(View root, int id, String value) {
        TextView text = root.findViewById(id);
        if (text != null) text.setText(value);
    }

    private static boolean isMissing(String value) {
        return value == null || value.trim().isEmpty();
    }
}
