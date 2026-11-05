package com.example.washlink;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Color;
import android.content.res.ColorStateList;
import android.util.TypedValue;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

import com.example.washlink.data.AuthRepository;
import com.example.washlink.models.UserAccount;
import com.example.washlink.ui.ThemeManager;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Comparator;

public class ChatActivity extends WashLinkActivity {
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
        setContentView(R.layout.activity_customer_provider_chat);
        firestore = FirebaseFirestore.getInstance();
        messages = findViewById(R.id.chat_messages);
        scroll = findViewById(R.id.chat_scroll);
        input = findViewById(R.id.et_chat_message);
        state = findViewById(R.id.tv_chat_state);
        sendButton = findViewById(R.id.btn_chat_send);
        findViewById(R.id.provider_chat_thread_nav).setVisibility(View.GONE);
        findViewById(R.id.btn_provider_menu).setVisibility(View.GONE);
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
                ThemeManager.rememberActiveRole(ChatActivity.this, role);
                ThemeManager.apply(findViewById(android.R.id.content), ChatActivity.this);
                if (UserAccount.ROLE_PROVIDER.equals(role)) {
                    findViewById(R.id.provider_chat_thread_nav).setVisibility(View.VISIBLE);
                    findViewById(R.id.btn_provider_menu).setVisibility(View.VISIBLE);
                    ProviderNavBarHelper.bind(ChatActivity.this, ProviderNavBarHelper.TAB_CHAT);
                }
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
                if (UserAccount.ROLE_CUSTOMER.equals(role)
                        && (providerId == null || providerId.trim().isEmpty())) {
                    showState(getString(R.string.chat_load_error), false);
                    return;
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
                applyChatTheme();
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
                markConversationRead(chatRef);
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
            chat.put("unreadCustomer", 0L);
            chat.put("unreadProvider", 0L);
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
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        showState(getString(R.string.chat_load_error), true);
                        return;
                    }
                    messages.removeAllViews();
                    if (snapshot != null) {
                        List<DocumentSnapshot> ordered = new ArrayList<>(snapshot.getDocuments());
                        ordered.sort(Comparator.comparingLong(ChatActivity::messageTime));
                        for (DocumentSnapshot message : ordered) {
                            addMessage(message);
                        }
                    }
                    state.setVisibility(View.GONE);
                    ready = true;
                    sendButton.setEnabled(true);
                    scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
                });
    }

    private static long messageTime(DocumentSnapshot document) {
        Date date = messageDate(document);
        return date == null ? 0 : date.getTime();
    }

    private void addMessage(DocumentSnapshot document) {
        String senderId = document.getString("senderId");
        String text = document.getString("text");
        if (text == null) return;
        boolean mine = uid.equals(senderId);
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.VERTICAL);
        line.setGravity(mine ? Gravity.END : Gravity.START);
        line.setPadding(0, dp(5), 0, dp(5));
        messages.addView(line, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout messageColumn = new LinearLayout(this);
        messageColumn.setOrientation(LinearLayout.VERTICAL);
        messageColumn.setGravity(mine ? Gravity.END : Gravity.START);
        messageColumn.setPadding(mine ? dp(12) : 0, 0, mine ? 0 : dp(12), 0);
        FrameLayout bubbleFrame = new FrameLayout(this);
        bubbleFrame.setMinimumWidth(getResources().getDimensionPixelSize(R.dimen.chat_bubble_min_width));
        bubbleFrame.setElevation(dp(1));
        float radius = dp(18);
        float corner = dp(5);
        GradientDrawable bubbleBackground = new GradientDrawable();
        bubbleBackground.setColor(messageBubbleColor(mine));
        bubbleBackground.setCornerRadii(mine
                ? new float[] {radius, radius, radius, radius, corner, corner, radius, radius}
                : new float[] {radius, radius, radius, radius, radius, radius, corner, corner});
        bubbleFrame.setBackground(bubbleBackground);

        TextView bubble = new TextView(this);
        bubble.setText(text);
        bubble.setTextSize(16);
        bubble.setLineSpacing(1, 1.12f);
        bubble.setGravity(Gravity.START | Gravity.TOP);
        bubble.setTextColor(messageBubbleTextColor());
        bubble.setPadding(dp(12), dp(8), dp(12), dp(24));
        bubble.setMaxWidth(getResources().getDimensionPixelSize(R.dimen.chat_bubble_max_width)
                - dp(24));
        bubbleFrame.addView(bubble, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        TextView timestamp = new TextView(this);
        Date createdAt = messageDate(document);
        timestamp.setText(createdAt == null ? "Sending..." : formatTimestamp(createdAt));
        timestamp.setTextSize(11);
        timestamp.setTextColor(messageBubbleTimestampColor());
        FrameLayout.LayoutParams timestampParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.END);
        timestampParams.setMargins(0, 0, dp(10), dp(5));
        bubbleFrame.addView(timestamp, timestampParams);
        messageColumn.addView(bubbleFrame, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        line.addView(messageColumn, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private int messageBubbleColor(boolean mine) {
        if (ThemeManager.isDarkMode(this, role)) {
            return mine ? Color.rgb(0, 92, 75) : Color.rgb(32, 44, 51);
        }
        if ("cream".equals(ThemeManager.themeMode(this, role))) {
            return mine ? Color.rgb(220, 239, 216) : Color.rgb(255, 253, 248);
        }
        return mine ? Color.rgb(217, 253, 211) : Color.WHITE;
    }

    private int messageBubbleTextColor() {
        return ThemeManager.isDarkMode(this, role)
                ? Color.rgb(233, 237, 239) : Color.rgb(17, 27, 33);
    }

    private int messageBubbleTimestampColor() {
        return ThemeManager.isDarkMode(this, role)
                ? Color.rgb(134, 150, 160) : Color.rgb(102, 120, 129);
    }

    private String formatTimestamp(Date date) {
        return new java.text.SimpleDateFormat("h:mm a", Locale.getDefault()).format(date);
    }

    private void markConversationRead(com.google.firebase.firestore.DocumentReference chatRef) {
        String field = UserAccount.ROLE_CUSTOMER.equals(role)
                ? "unreadCustomer" : "unreadProvider";
        Map<String, Object> update = new HashMap<>();
        update.put(field, 0L);
        chatRef.update(update);
    }

    private static Date messageDate(DocumentSnapshot document) {
        Object value = document.get("createdAt");
        if (value instanceof com.google.firebase.Timestamp) {
            return ((com.google.firebase.Timestamp) value).toDate();
        }
        if (value instanceof Date) {
            return (Date) value;
        }
        if (value instanceof Number) {
            return new Date(((Number) value).longValue());
        }
        value = document.get("timestamp");
        if (value instanceof com.google.firebase.Timestamp) {
            return ((com.google.firebase.Timestamp) value).toDate();
        }
        if (value instanceof Date) {
            return (Date) value;
        }
        if (value instanceof Number) {
            return new Date(((Number) value).longValue());
        }
        return null;
    }

    private void applyChatTheme() {
        int background = chatSurfaceColor();
        int primary = messageTextColor();
        int secondary = timestampTextColor();
        findViewById(android.R.id.content).setBackgroundColor(background);
        ViewGroup content = findViewById(android.R.id.content);
        if (content.getChildCount() > 0) {
            content.getChildAt(0).setBackgroundColor(background);
        }
        findViewById(R.id.chat_scroll).setBackgroundColor(background);
        findViewById(R.id.chat_input_bar).setBackgroundColor(background);
        ((TextView) findViewById(R.id.tv_chat_title)).setTextColor(primary);
        state.setTextColor(secondary);
        input.setTextColor(primary);
        input.setHintTextColor(secondary);
        GradientDrawable inputBackground = new GradientDrawable();
        inputBackground.setColor(messageSurfaceColor());
        inputBackground.setCornerRadius(10 * getResources().getDisplayMetrics().density);
        input.setBackground(inputBackground);
        ViewCompat.setBackgroundTintList(sendButton,
                ColorStateList.valueOf(ThemeManager.accent(this, role)));
    }

    private int chatSurfaceColor() {
        if ("cream".equals(ThemeManager.themeMode(this, role))) {
            return getColor(R.color.cream_surface);
        }
        return ThemeManager.isDarkMode(this, role)
                ? Color.rgb(18, 25, 35) : Color.WHITE;
    }

    private int messageSurfaceColor() {
        if ("cream".equals(ThemeManager.themeMode(this, role))) {
            return getColor(R.color.cream_raised);
        }
        return ThemeManager.isDarkMode(this, role)
                ? Color.rgb(32, 44, 59) : Color.rgb(244, 245, 247);
    }

    private int messageTextColor() {
        if ("cream".equals(ThemeManager.themeMode(this, role))) {
            return getColor(R.color.cream_text_primary);
        }
        return ThemeManager.isDarkMode(this, role)
                ? Color.rgb(241, 245, 249) : Color.rgb(26, 26, 26);
    }

    private int timestampTextColor() {
        if ("cream".equals(ThemeManager.themeMode(this, role))) {
            return getColor(R.color.cream_text_secondary);
        }
        return ThemeManager.isDarkMode(this, role)
                ? Color.rgb(183, 195, 208) : Color.rgb(95, 99, 104);
    }

    private int resolveThemeColor(int attribute) {
        TypedValue value = new TypedValue();
        getTheme().resolveAttribute(attribute, value, true);
        return value.resourceId != 0
                ? getColor(value.resourceId) : value.data;
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
        update.put(UserAccount.ROLE_CUSTOMER.equals(role)
                ? "unreadProvider" : "unreadCustomer", FieldValue.increment(1));
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
