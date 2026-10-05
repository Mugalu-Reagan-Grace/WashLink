package com.example.washlink;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.washlink.data.AuthRepository;
import com.example.washlink.models.UserAccount;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatActivity extends AppCompatActivity {
    public static final String EXTRA_CHAT_ID = "chat_id";
    public static final String EXTRA_CUSTOMER_ID = "customer_id";
    public static final String EXTRA_PROVIDER_ID = "provider_id";
    public static final String EXTRA_PROVIDER_NAME = "provider_name";
    public static final String EXTRA_PARTNER_NAME = "partner_name";

    private FirebaseFirestore firestore;
    private LinearLayout messages;
    private ScrollView scroll;
    private EditText input;
    private TextView state;
    private View sendButton;
    private ListenerRegistration messagesRegistration;
    private String uid;
    private String role;
    private String customerId;
    private String providerId;
    private String conversationId;
    private String customerName;
    private String providerName;
    private boolean ready;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        firestore = FirebaseFirestore.getInstance();
        messages = findViewById(R.id.chat_messages);
        scroll = findViewById(R.id.chat_scroll);
        input = findViewById(R.id.et_chat_message);
        state = findViewById(R.id.tv_chat_state);
        sendButton = findViewById(R.id.btn_chat_send);
        findViewById(R.id.btn_chat_back).setOnClickListener(v -> finish());
        sendButton.setEnabled(false);
        sendButton.setOnClickListener(v -> sendMessage());
        uid = AuthRepository.getInstance().getCurrentUserId();
        if (uid == null) {
            finish();
            return;
        }
        showState(getString(R.string.chat_loading), false);
        AuthRepository.getInstance().getCurrentSession(new AuthRepository.RoleCallback() {
            @Override
            public void onResult(UserAccount account) {
                if (account == null || (!UserAccount.ROLE_CUSTOMER.equals(account.getRole())
                        && !UserAccount.ROLE_PROVIDER.equals(account.getRole()))) {
                    finish();
                    return;
                }
                role = account.getRole();
                if (UserAccount.ROLE_CUSTOMER.equals(role)) {
                    customerId = uid;
                    customerName = account.getName();
                    providerId = getIntent().getStringExtra(EXTRA_PROVIDER_ID);
                    providerName = getIntent().getStringExtra(EXTRA_PROVIDER_NAME);
                } else {
                    providerId = uid;
                    providerName = getIntent().getStringExtra(EXTRA_PROVIDER_NAME);
                    customerId = getIntent().getStringExtra(EXTRA_CUSTOMER_ID);
                    customerName = getIntent().getStringExtra(EXTRA_PARTNER_NAME);
                }
                String suppliedChatId = getIntent().getStringExtra(EXTRA_CHAT_ID);
                conversationId = suppliedChatId == null
                        ? createConversationId(customerId, providerId) : suppliedChatId;
                if (customerId == null || providerId == null || conversationId == null
                        || !isParticipant()) {
                    showState(getString(R.string.chat_load_error), false);
                    return;
                }
                if (providerName == null || providerName.trim().isEmpty()) {
                    providerName = getString(R.string.provider_business_name_fallback);
                }
                if (customerName == null || customerName.trim().isEmpty()) {
                    customerName = getString(R.string.chat_customer_fallback);
                }
                String title = UserAccount.ROLE_CUSTOMER.equals(role)
                        ? providerName : customerName;
                ((TextView) findViewById(R.id.tv_chat_title)).setText(title);
                openConversation();
            }

            @Override
            public void onError(String message) {
                showState(message, false);
            }
        });
    }

    private boolean isParticipant() {
        return uid.equals(customerId) || uid.equals(providerId);
    }

    private String createConversationId(String customer, String provider) {
        if (customer == null || provider == null) return null;
        List<String> ids = new ArrayList<>();
        ids.add(customer);
        ids.add(provider);
        java.util.Collections.sort(ids);
        return ids.get(0) + "_" + ids.get(1);
    }

    private void openConversation() {
        com.google.firebase.firestore.DocumentReference chatRef =
                firestore.collection("chats").document(conversationId);
        chatRef.get().addOnSuccessListener(document -> {
            if (document.exists()) {
                if (!customerId.equals(document.getString("customerId"))
                        || !providerId.equals(document.getString("providerId"))) {
                    showState(getString(R.string.chat_load_error), false);
                    return;
                }
                listenForMessages(chatRef);
                return;
            }
            Map<String, Object> chat = new HashMap<>();
            chat.put("customerId", customerId);
            chat.put("providerId", providerId);
            chat.put("participantIds", java.util.Arrays.asList(customerId, providerId));
            chat.put("customerName", customerName);
            chat.put("providerName", providerName);
            chat.put("lastMessage", "");
            chat.put("createdAt", FieldValue.serverTimestamp());
            chat.put("lastMessageAt", FieldValue.serverTimestamp());
            chatRef.set(chat)
                    .addOnSuccessListener(unused -> listenForMessages(chatRef))
                    .addOnFailureListener(error ->
                            showState(getString(R.string.chat_load_error), true));
        }).addOnFailureListener(error -> showState(getString(R.string.chat_load_error), true));
    }

    private void listenForMessages(com.google.firebase.firestore.DocumentReference chatRef) {
        if (messagesRegistration != null) messagesRegistration.remove();
        messagesRegistration = chatRef.collection("messages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        showState(getString(R.string.chat_load_error), true);
                        return;
                    }
                    messages.removeAllViews();
                    if (snapshot != null) {
                        for (DocumentSnapshot message : snapshot.getDocuments()) {
                            addMessage(message);
                        }
                    }
                    state.setVisibility(View.GONE);
                    ready = true;
                    sendButton.setEnabled(true);
                    scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
                });
    }

    private void addMessage(DocumentSnapshot document) {
        String senderId = document.getString("senderId");
        String text = document.getString("text");
        if (text == null) return;
        boolean mine = uid.equals(senderId);
        LinearLayout line = new LinearLayout(this);
        line.setGravity(mine ? Gravity.END : Gravity.START);
        line.setPadding(0, 4, 0, 4);

        TextView bubble = new TextView(this);
        bubble.setText(text);
        bubble.setTextSize(14);
        bubble.setTextColor(ContextCompat.getColor(this,
                mine ? R.color.white : R.color.text_primary));
        bubble.setBackgroundResource(mine ? R.drawable.bg_chat_bubble_sent
                : R.drawable.bg_chat_bubble_received);
        bubble.setPadding(14, 10, 14, 10);
        bubble.setMaxWidth(getResources().getDimensionPixelSize(R.dimen.chat_bubble_max_width));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        line.addView(bubble, params);
        messages.addView(line);
    }

    private void sendMessage() {
        if (!ready) return;
        String text = input.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(this, R.string.chat_empty_message, Toast.LENGTH_SHORT).show();
            return;
        }
        if (text.length() > 2000) {
            Toast.makeText(this, R.string.chat_message_limit, Toast.LENGTH_SHORT).show();
            return;
        }
        ready = false;
        sendButton.setEnabled(false);
        com.google.firebase.firestore.DocumentReference chatRef =
                firestore.collection("chats").document(conversationId);
        com.google.firebase.firestore.WriteBatch batch = firestore.batch();
        Map<String, Object> message = new HashMap<>();
        message.put("senderId", uid);
        message.put("senderRole", role);
        message.put("text", text);
        message.put("createdAt", FieldValue.serverTimestamp());
        batch.set(chatRef.collection("messages").document(), message);
        Map<String, Object> update = new HashMap<>();
        update.put("lastMessage", text);
        update.put("lastMessageAt", FieldValue.serverTimestamp());
        update.put("lastSenderId", uid);
        batch.set(chatRef, update, SetOptions.merge());
        batch.commit().addOnSuccessListener(unused -> {
            input.setText("");
            ready = true;
            sendButton.setEnabled(true);
        }).addOnFailureListener(error -> {
            ready = true;
            sendButton.setEnabled(true);
            Toast.makeText(this, R.string.chat_send_error, Toast.LENGTH_SHORT).show();
        });
    }

    private void showState(String message, boolean retry) {
        state.setText(message);
        state.setVisibility(View.VISIBLE);
        state.setClickable(retry);
        state.setOnClickListener(retry ? v -> openConversation() : null);
    }

    @Override
    protected void onDestroy() {
        if (messagesRegistration != null) messagesRegistration.remove();
        super.onDestroy();
    }
}
