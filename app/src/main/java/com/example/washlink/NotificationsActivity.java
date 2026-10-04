package com.example.washlink;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.washlink.data.BookingService;
import com.example.washlink.data.BookingServiceFacade;
import com.example.washlink.data.ListenerRegistration;
import com.example.washlink.models.BookingNotification;
import com.google.firebase.auth.FirebaseAuth;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.List;

public class NotificationsActivity extends AppCompatActivity {
    private final List<BookingNotification> notifications = new ArrayList<>();
    private ArrayAdapter<BookingNotification> adapter;
    private ListView listView;
    private TextView stateView;
    private ListenerRegistration registration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_notifications);
        com.example.washlink.data.AuthGuard.requireRole(this,
                com.example.washlink.models.UserAccount.ROLE_CUSTOMER, SignInActivity.class);

        ImageView back = findViewById(R.id.btn_back);
        if (back != null) back.setOnClickListener(v -> finish());

        listView = findViewById(R.id.lv_notifications);
        stateView = findViewById(R.id.tv_notifications_state);
        adapter = new ArrayAdapter<BookingNotification>(this, R.layout.notification_item,
                R.id.tv_notification_text, notifications) {
            @Override
            public android.view.View getView(int position, android.view.View convertView,
                                             android.view.ViewGroup parent) {
                android.view.View row = super.getView(position, convertView, parent);
                TextView message = row.findViewById(R.id.tv_notification_text);
                message.setText(formatNotification(notifications.get(position)));
                return row;
            }
        };
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            String bookingId = notifications.get(position).getBookingId();
            if (bookingId == null || bookingId.trim().isEmpty()) return;
            Intent intent = new Intent(this, OrderTrackingActivity.class);
            intent.putExtra("booking_id", bookingId);
            startActivity(intent);
        });

        String uid = FirebaseAuth.getInstance().getCurrentUser() == null
                ? null : FirebaseAuth.getInstance().getCurrentUser().getUid();
        if (uid == null) {
            showState("Sign in to view order updates.");
            return;
        }

        loadNotifications(uid);

        BottomNavHelper.bind(this);
    }

    private void loadNotifications(String uid) {
        if (registration != null) registration.remove();
        notifications.clear();
        adapter.notifyDataSetChanged();
        showState("Loading notifications...");
        registration = BookingServiceFacade.getBookingService().addCustomerNotificationsListener(uid,
                new BookingService.CustomerNotificationsListener() {
                    @Override
                    public void onNotificationsChanged(List<BookingNotification> incoming) {
                        notifications.clear();
                        notifications.addAll(incoming);
                        adapter.notifyDataSetChanged();
                        if (notifications.isEmpty()) showState("No notifications yet.");
                        else showList();
                    }

                    @Override
                    public void onError(String message) {
                        showState("Could not load notifications. Tap to retry.");
                        if (stateView != null) {
                            stateView.setClickable(true);
                            stateView.setFocusable(true);
                            stateView.setOnClickListener(v -> loadNotifications(uid));
                        }
                    }
                });
    }

    private String formatNotification(BookingNotification notification) {
        String title = notification.getTitle() == null ? "Order update" : notification.getTitle();
        String body = notification.getBody() == null ? "" : notification.getBody();
        if (notification.getCreatedAt() > 0) {
            body += "\n" + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                    .format(notification.getCreatedAt());
        }
        return body.isEmpty() ? title : title + "\n" + body;
    }

    private void showState(String message) {
        if (stateView != null) {
            stateView.setText(message);
            stateView.setVisibility(android.view.View.VISIBLE);
            stateView.setClickable(false);
            stateView.setFocusable(false);
            stateView.setOnClickListener(null);
        }
        if (listView != null) listView.setVisibility(android.view.View.GONE);
    }

    private void showList() {
        if (stateView != null) {
            stateView.setVisibility(android.view.View.GONE);
            stateView.setClickable(false);
            stateView.setFocusable(false);
            stateView.setOnClickListener(null);
        }
        if (listView != null) listView.setVisibility(android.view.View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        if (registration != null) registration.remove();
        super.onDestroy();
    }
}
