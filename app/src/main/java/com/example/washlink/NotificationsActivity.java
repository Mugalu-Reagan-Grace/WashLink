package com.example.washlink;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.graphics.Typeface;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.washlink.data.BookingService;
import com.example.washlink.data.BookingServiceFacade;
import com.example.washlink.data.ListenerRegistration;
import com.example.washlink.data.AuthRepository;
import com.example.washlink.models.BookingNotification;
import com.example.washlink.models.UserAccount;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class NotificationsActivity extends AppCompatActivity {
    private final List<BookingNotification> notifications = new ArrayList<>();
    private ArrayAdapter<BookingNotification> adapter;
    private ListView listView;
    private TextView stateView;
    private ListenerRegistration registration;
    private com.google.firebase.firestore.ListenerRegistration providerRegistration;
    private boolean isProvider;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_notifications);
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
                BookingNotification notification = notifications.get(position);
                message.setText(formatNotification(notification));
                message.setTypeface(null, notification.isUnread()
                        ? Typeface.BOLD : Typeface.NORMAL);
                message.setContentDescription((notification.isUnread()
                        ? "Unread notification. " : "Read notification. ")
                        + formatNotification(notification));
                return row;
            }
        };
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            BookingNotification notification = notifications.get(position);
            markRead(notification);
            String bookingId = notification.getBookingId();
            if (bookingId == null || bookingId.trim().isEmpty()) return;
            Intent intent = new Intent(this,
                    isProvider ? UpdateOrderStatusActivity.class : OrderTrackingActivity.class);
            intent.putExtra("booking_id", bookingId);
            startActivity(intent);
        });

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            showState("Sign in to view order updates.");
            return;
        }
        AuthRepository.getInstance().getCurrentSession(new AuthRepository.RoleCallback() {
            @Override public void onResult(UserAccount account) {
                if (account == null || (!UserAccount.ROLE_CUSTOMER.equals(account.getRole())
                        && !UserAccount.ROLE_PROVIDER.equals(account.getRole()))) {
                    startActivity(new Intent(NotificationsActivity.this, SignInActivity.class));
                    finish();
                    return;
                }
                isProvider = UserAccount.ROLE_PROVIDER.equals(account.getRole());
                String title = isProvider ? "Provider notifications" : "Notifications";
                ((TextView) findViewById(R.id.tv_notifications_title)).setText(title);
                if (isProvider) listenForProviderNotifications(account.getUid());
                else {
                    BottomNavHelper.bind(NotificationsActivity.this);
                    loadNotifications(account.getUid());
                }
            }
            @Override public void onError(String message) {
                showState(message == null ? "Could not verify your account." : message);
            }
        });
    }

    private void listenForProviderNotifications(String uid) {
        if (providerRegistration != null) providerRegistration.remove();
        showState("Loading notifications...");
        providerRegistration = FirebaseFirestore.getInstance().collection("users")
                .document(uid).collection("notifications")
                .limit(100)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        showState("Could not load notifications. Tap to retry.");
                        if (stateView != null) {
                            stateView.setClickable(true);
                            stateView.setOnClickListener(v -> listenForProviderNotifications(uid));
                        }
                        return;
                    }
                    notifications.clear();
                    if (snapshot != null) {
                        for (DocumentSnapshot document : snapshot.getDocuments()) {
                            BookingNotification notification = document.toObject(BookingNotification.class);
                            if (notification != null) {
                                notification.setId(document.getId());
                                notifications.add(notification);
                            }
                        }
                    }
                    notifications.sort(Comparator.comparingLong(
                            BookingNotification::getCreatedAt).reversed());
                    adapter.notifyDataSetChanged();
                    if (notifications.isEmpty()) showState("No notifications yet.");
                    else showList();
                });
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

    private void markRead(BookingNotification notification) {
        if (!notification.isUnread()) return;
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null || notification.getId() == null
                || notification.getId().trim().isEmpty()) return;
        notification.markRead();
        adapter.notifyDataSetChanged();
        FirebaseFirestore.getInstance().collection("users").document(uid)
                .collection("notifications").document(notification.getId())
                .update("read", true)
                .addOnFailureListener(error -> {
                    notification.setRead(false);
                    adapter.notifyDataSetChanged();
                    Toast.makeText(this, "Could not update notification: "
                            + (error.getLocalizedMessage() == null
                            ? "Please try again." : error.getLocalizedMessage()),
                            Toast.LENGTH_LONG).show();
                });
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
        if (providerRegistration != null) providerRegistration.remove();
        super.onDestroy();
    }
}
