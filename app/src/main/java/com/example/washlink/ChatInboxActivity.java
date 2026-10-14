package com.example.washlink;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.washlink.data.AuthRepository;
import com.example.washlink.models.UserAccount;
import com.example.washlink.ui.ThemeManager;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

public class ChatInboxActivity extends WashLinkActivity {
    private LinearLayout rows;
    private TextView state;
    private String uid;
    private String role;
    private ListenerRegistration registration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_provider_chat_inbox);
        rows = findViewById(R.id.chat_inbox_rows);
        state = findViewById(R.id.tv_chat_inbox_state);
        findViewById(R.id.provider_chat_nav).setVisibility(View.GONE);
        findViewById(R.id.btn_provider_menu).setVisibility(View.GONE);
        findViewById(R.id.btn_chat_inbox_back).setOnClickListener(v -> finish());
        uid = AuthRepository.getInstance().getCurrentUserId();
        if (uid == null) {
            finish();
            return;
        }
        showState(getString(R.string.chat_inbox_loading), false);
        AuthRepository.getInstance().getCurrentSession(new AuthRepository.RoleCallback() {
            @Override
            public void onResult(UserAccount account) {
                if (account == null || (!UserAccount.ROLE_CUSTOMER.equals(account.getRole())
                        && !UserAccount.ROLE_PROVIDER.equals(account.getRole()))) {
                    finish();
                    return;
                }
                role = account.getRole();
                ThemeManager.rememberActiveRole(ChatInboxActivity.this, role);
                if (UserAccount.ROLE_PROVIDER.equals(role)) {
                    findViewById(R.id.provider_chat_nav).setVisibility(View.VISIBLE);
                    findViewById(R.id.btn_provider_menu).setVisibility(View.VISIBLE);
                    ProviderNavBarHelper.bind(ChatInboxActivity.this,
                            ProviderNavBarHelper.TAB_CHAT);
                }
                listenForChats();
            }

            @Override
            public void onError(String message) {
                showState(message, false);
            }
        });
    }

    private void listenForChats() {
        if (registration != null) registration.remove();
        String participantField = UserAccount.ROLE_PROVIDER.equals(role)
                ? "providerId" : "customerId";
        registration = FirebaseFirestore.getInstance().collection("chats")
                .whereEqualTo(participantField, uid)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        showState(getString(R.string.chat_inbox_error), true);
                        return;
                    }
                    rows.removeAllViews();
                    if (snapshot == null || snapshot.isEmpty()) {
                        showState(getString(R.string.chat_inbox_empty), false);
                        return;
                    }
                    List<DocumentSnapshot> chats = new ArrayList<>(snapshot.getDocuments());
                    chats.sort(Comparator.comparingLong(ChatInboxActivity::lastMessageTime).reversed());
                    state.setVisibility(View.GONE);
                    for (DocumentSnapshot chat : chats) addChatRow(chat);
                });
    }

    private void addChatRow(DocumentSnapshot chat) {
        String customerId = chat.getString("customerId");
        String providerId = chat.getString("providerId");
        if (customerId == null || providerId == null) return;
        boolean customer = UserAccount.ROLE_CUSTOMER.equals(role);
        String partner = customer ? chat.getString("providerName") : chat.getString("customerName");
        if (partner == null || partner.trim().isEmpty()) {
            partner = getString(R.string.provider_business_name_fallback);
        }
        final String partnerName = partner;
        String lastMessage = chat.getString("lastMessage");
        View row = getLayoutInflater().inflate(R.layout.activity_customer_chat_conversation_item, rows, false);
        TextView initial = row.findViewById(R.id.tv_chat_initial);
        TextView title = row.findViewById(R.id.tv_chat_partner);
        TextView preview = row.findViewById(R.id.tv_chat_last_message);
        TextView timestamp = row.findViewById(R.id.tv_chat_timestamp);
        initial.setText(partnerName.substring(0, 1).toUpperCase(java.util.Locale.getDefault()));
        title.setText(partnerName);
        preview.setText(lastMessage == null || lastMessage.trim().isEmpty()
                ? getString(R.string.chat_start_message) : lastMessage);
        com.google.firebase.Timestamp lastMessageAt = chat.getTimestamp("lastMessageAt");
        timestamp.setText(lastMessageAt == null ? "" : formatTimestamp(
                lastMessageAt.toDate()));
        TextView unread = row.findViewById(R.id.tv_chat_unread);
        long unreadCount = chat.getLong(UserAccount.ROLE_CUSTOMER.equals(role)
                ? "unreadCustomer" : "unreadProvider") == null ? 0
                : chat.getLong(UserAccount.ROLE_CUSTOMER.equals(role)
                ? "unreadCustomer" : "unreadProvider");
        if (unreadCount > 0) {
            unread.setText(unreadCount > 99 ? "99+" : String.valueOf(unreadCount));
            unread.setVisibility(View.VISIBLE);
        }
        row.setOnClickListener(v -> {
            Intent intent = new Intent(this, ChatActivity.class);
            intent.putExtra(ChatActivity.EXTRA_CHAT_ID, chat.getId());
            intent.putExtra(ChatActivity.EXTRA_CUSTOMER_ID, customerId);
            intent.putExtra(ChatActivity.EXTRA_PROVIDER_ID, providerId);
            intent.putExtra(ChatActivity.EXTRA_PROVIDER_NAME, chat.getString("providerName"));
            intent.putExtra(ChatActivity.EXTRA_PARTNER_NAME, partnerName);
            startActivity(intent);
        });
        rows.addView(row);
    }

    private static String formatTimestamp(Date date) {
        return new java.text.SimpleDateFormat("EEE dd yyyy, h:mm a",
                Locale.getDefault()).format(date);
    }

    private static long lastMessageTime(DocumentSnapshot chat) {
        com.google.firebase.Timestamp timestamp = chat.getTimestamp("lastMessageAt");
        return timestamp == null ? 0 : timestamp.toDate().getTime();
    }

    private void showState(String message, boolean retry) {
        state.setText(message);
        state.setVisibility(View.VISIBLE);
        state.setClickable(retry);
        state.setOnClickListener(retry ? v -> listenForChats() : null);
    }

    @Override
    protected void onDestroy() {
        if (registration != null) registration.remove();
        super.onDestroy();
    }
}
