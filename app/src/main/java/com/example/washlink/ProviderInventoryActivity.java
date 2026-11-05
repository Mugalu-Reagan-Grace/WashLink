package com.example.washlink;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.example.washlink.data.AuthGuard;
import com.example.washlink.data.AuthRepository;
import com.example.washlink.models.UserAccount;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ProviderInventoryActivity extends WashLinkActivity {
    private final List<DocumentSnapshot> items = new ArrayList<>();
    private FirebaseFirestore firestore;
    private String providerId;
    private LinearLayout rows;
    private TextView state;
    private View addButton;
    private boolean approved;
    private ListenerRegistration profileRegistration;
    private ListenerRegistration inventoryRegistration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AuthGuard.requireRole(this, UserAccount.ROLE_PROVIDER, ProviderLoginActivity.class);
        setContentView(R.layout.activity_provider_inventory);
        firestore = FirebaseFirestore.getInstance();
        providerId = AuthRepository.getInstance().getCurrentUserId();
        ProviderNavBarHelper.bind(this, -1);
        rows = findViewById(R.id.provider_inventory_rows);
        state = findViewById(R.id.tv_provider_inventory_state);
        addButton = findViewById(R.id.btn_provider_inventory_add);
        findViewById(R.id.btn_provider_inventory_back).setOnClickListener(v -> finish());
        addButton.setOnClickListener(v -> showItemDialog(null));
        if (providerId == null) {
            showState("Sign in as a provider to manage inventory.");
            addButton.setEnabled(false);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (providerId != null) listenForInventory();
    }

    private void listenForInventory() {
        removeListeners();
        showState("Loading inventory…");
        profileRegistration = firestore.collection("providers").document(providerId)
                .addSnapshotListener((document, error) -> {
                    if (error != null || document == null || !document.exists()) {
                        approved = false;
                        addButton.setEnabled(false);
                        showState("Could not verify provider approval: "
                                + (error == null ? "profile unavailable" : error.getMessage()));
                        renderItems();
                        return;
                    }
                    approved = Boolean.TRUE.equals(document.getBoolean("isApproved"));
                    addButton.setEnabled(approved);
                    if (!approved) {
                        showState("Inventory changes are disabled until your provider account is approved.");
                    } else if (items.isEmpty()) {
                        showState("No inventory items yet. Add supplies or products above.");
                    } else {
                        showState("");
                    }
                    renderItems();
                });
        inventoryRegistration = firestore.collection("providers").document(providerId)
                .collection("inventory").addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        showState("Could not load inventory: " + error.getMessage());
                        return;
                    }
                    items.clear();
                    if (snapshot != null) items.addAll(snapshot.getDocuments());
                    items.sort((left, right) -> safe(left.getString("name"))
                            .compareToIgnoreCase(safe(right.getString("name"))));
                    if (approved) showState(items.isEmpty()
                            ? "No inventory items yet. Add supplies or products above." : "");
                    renderItems();
                });
    }

    private void renderItems() {
        rows.removeAllViews();
        for (DocumentSnapshot item : items) {
            String name = safe(item.getString("name"));
            int quantity = integer(item, "quantity");
            int threshold = integer(item, "lowStockThreshold");
            String unit = safe(item.getString("unit"));
            boolean low = quantity <= threshold;

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(16), dp(14), dp(16), dp(12));
            card.setBackgroundResource(R.drawable.bg_card_bordered);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cardParams.bottomMargin = dp(10);
            rows.addView(card, cardParams);

            TextView title = new TextView(this);
            title.setText(name);
            title.setTextColor(getColor(R.color.text_primary));
            title.setTextSize(16);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            card.addView(title);

            String detail = (safe(item.getString("sku")).isEmpty() ? "" : "SKU "
                    + item.getString("sku") + " · ")
                    + (unit.isEmpty() ? "units" : unit)
                    + " · " + money(number(item, "unitCost")) + " per " + (unit.isEmpty() ? "unit" : unit);
            TextView details = new TextView(this);
            if (item.get("unitCost") == null) {
                detail = (safe(item.getString("sku")).isEmpty() ? "" : "SKU "
                        + item.getString("sku") + " · ") + (unit.isEmpty() ? "units" : unit);
            }
            details.setText(detail);
            details.setTextColor(getColor(R.color.text_secondary));
            details.setTextSize(12);
            details.setPadding(0, dp(3), 0, dp(8));
            card.addView(details);

            TextView stock = new TextView(this);
            stock.setText(quantity + " " + (unit.isEmpty() ? "units" : unit)
                    + "  ·  Low at " + threshold + "  ·  " + (low ? "LOW STOCK" : "IN STOCK"));
            stock.setTextColor(getColor(low ? R.color.pending_text : R.color.text_primary));
            stock.setTextSize(14);
            stock.setTypeface(null, android.graphics.Typeface.BOLD);
            card.addView(stock);

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setPadding(0, dp(10), 0, 0);
            card.addView(actions);
            addAction(actions, "Adjust stock", v -> showAdjustmentDialog(item));
            addAction(actions, "Edit", v -> showItemDialog(item));
            addAction(actions, "Delete", v -> confirmDelete(item));
            for (int index = 0; index < actions.getChildCount(); index++) {
                actions.getChildAt(index).setEnabled(approved);
            }
        }
        if (items.isEmpty() && state.getText().length() == 0) {
            showState("No inventory items yet. Add supplies or products above.");
        }
    }

    private void addAction(LinearLayout parent, String text, View.OnClickListener listener) {
        TextView button = new TextView(this);
        button.setText(text);
        button.setTextColor(getColor(R.color.laundr_blue));
        button.setTextSize(12);
        button.setGravity(android.view.Gravity.CENTER);
        button.setPadding(dp(9), dp(10), dp(9), dp(10));
        button.setBackgroundResource(R.drawable.bg_card_selected);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        params.setMargins(dp(2), 0, dp(2), 0);
        parent.addView(button, params);
        button.setOnClickListener(listener);
    }

    private void showItemDialog(DocumentSnapshot existing) {
        if (!approved) {
            showToast("Provider approval is required to change inventory.");
            return;
        }
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.setPadding(dp(20), dp(4), dp(20), 0);
        EditText name = input(fields, "Item name", InputType.TYPE_CLASS_TEXT);
        EditText sku = input(fields, "SKU (optional)", InputType.TYPE_CLASS_TEXT);
        EditText unit = input(fields, "Unit (optional)", InputType.TYPE_CLASS_TEXT);
        EditText quantity = input(fields, "Current stock (whole number)", InputType.TYPE_CLASS_NUMBER);
        EditText threshold = input(fields, "Low-stock threshold (whole number)", InputType.TYPE_CLASS_NUMBER);
        EditText cost = input(fields, "Unit cost in UGX (optional)", InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if (existing != null) {
            name.setText(safe(existing.getString("name")));
            sku.setText(safe(existing.getString("sku")));
            unit.setText(safe(existing.getString("unit")));
            quantity.setText(String.valueOf(integer(existing, "quantity")));
            threshold.setText(String.valueOf(integer(existing, "lowStockThreshold")));
            if (existing.get("unitCost") != null) cost.setText(String.valueOf(number(existing, "unitCost")));
        } else {
            quantity.setText("0");
            threshold.setText("0");
        }
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "Add inventory item" : "Edit inventory item")
                .setView(fields)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(existing == null ? "Add" : "Save", null)
                .create();
        dialog.setOnShowListener(unused -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> saveItem(existing, name, sku, unit, quantity, threshold, cost, dialog)));
        dialog.show();
    }

    private void saveItem(DocumentSnapshot existing, EditText nameInput, EditText skuInput,
                          EditText unitInput, EditText quantityInput, EditText thresholdInput,
                          EditText costInput, AlertDialog dialog) {
        String name = nameInput.getText().toString().trim();
        String sku = skuInput.getText().toString().trim();
        String unit = unitInput.getText().toString().trim();
        int quantity;
        int threshold;
        Double cost = null;
        try {
            quantity = Integer.parseInt(quantityInput.getText().toString().trim());
            threshold = Integer.parseInt(thresholdInput.getText().toString().trim());
            String costText = costInput.getText().toString().trim();
            if (!costText.isEmpty()) cost = Double.parseDouble(costText);
        } catch (NumberFormatException error) {
            showToast("Enter valid stock, threshold and optional unit cost values.");
            return;
        }
        if (name.isEmpty() || name.length() > 100 || sku.length() > 60 || unit.length() > 30
                || quantity < 0 || quantity > 1000000 || threshold < 0 || threshold > 1000000
                || (cost != null && (!Double.isFinite(cost) || cost < 0 || cost > 1000000000))) {
            showToast("Name must be 1–100 characters, stock and threshold 0–1,000,000, and cost nonnegative.");
            return;
        }
        Map<String, Object> record = new HashMap<>();
        record.put("name", name);
        record.put("sku", sku);
        record.put("unit", unit);
        record.put("quantity", quantity);
        record.put("lowStockThreshold", threshold);
        if (cost != null) record.put("unitCost", cost);
        record.put("updatedAt", FieldValue.serverTimestamp());
        if (existing == null) {
            record.put("createdAt", FieldValue.serverTimestamp());
            firestore.collection("providers").document(providerId).collection("inventory")
                    .add(record)
                    .addOnSuccessListener(reference -> {
                        dialog.dismiss();
                        showToast("Inventory item added.");
                    })
                    .addOnFailureListener(error -> showToast("Could not add item: " + error.getMessage()));
        } else {
            if (cost == null) record.put("unitCost", FieldValue.delete());
            else record.put("unitCost", cost);
            firestore.collection("providers").document(providerId).collection("inventory")
                    .document(existing.getId()).update(record)
                    .addOnSuccessListener(unused -> {
                        dialog.dismiss();
                        showToast("Inventory item updated.");
                    })
                    .addOnFailureListener(error -> showToast("Could not update item: " + error.getMessage()));
        }
    }

    private void showAdjustmentDialog(DocumentSnapshot item) {
        if (!approved) {
            showToast("Provider approval is required to adjust stock.");
            return;
        }
        EditText amountInput = new EditText(this);
        amountInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        amountInput.setHint("Whole-number quantity");
        amountInput.setText("1");
        int padding = dp(20);
        LinearLayout container = new LinearLayout(this);
        container.setPadding(padding, 0, padding, 0);
        container.addView(amountInput);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Adjust " + safe(item.getString("name")))
                .setMessage("Current stock: " + integer(item, "quantity") + " "
                        + (safe(item.getString("unit")).isEmpty() ? "units" : item.getString("unit")))
                .setView(container)
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton("Remove", (d, which) -> adjust(item, amountInput, -1))
                .setPositiveButton("Add", (d, which) -> adjust(item, amountInput, 1))
                .create();
        dialog.show();
    }

    private void adjust(DocumentSnapshot item, EditText amountInput, int direction) {
        int amount;
        try {
            amount = Integer.parseInt(amountInput.getText().toString().trim());
        } catch (NumberFormatException error) {
            showToast("Enter a whole-number adjustment.");
            return;
        }
        if (amount < 1 || amount > 1000000) {
            showToast("Enter an adjustment between 1 and 1,000,000.");
            return;
        }
        DocumentReference reference = firestore.collection("providers").document(providerId)
                .collection("inventory").document(item.getId());
        firestore.runTransaction(transaction -> {
            DocumentSnapshot current = transaction.get(reference);
            if (!current.exists()) throw new IllegalStateException("Inventory item no longer exists.");
            Object stored = current.get("quantity");
            if (!(stored instanceof Number)) throw new IllegalStateException("Stock quantity is invalid.");
            int currentQuantity = ((Number) stored).intValue();
            long next = (long) currentQuantity + (long) direction * amount;
            if (next < 0) throw new IllegalStateException("Stock cannot be reduced below zero.");
            if (next > 1000000) throw new IllegalStateException("Stock quantity exceeds 1,000,000.");
            Map<String, Object> updates = new HashMap<>();
            updates.put("quantity", (int) next);
            updates.put("updatedAt", FieldValue.serverTimestamp());
            transaction.update(reference, updates);
            return null;
        }).addOnSuccessListener(unused -> showToast(direction > 0
                ? "Stock increased." : "Stock decreased."))
                .addOnFailureListener(error -> showToast("Could not adjust stock: " + error.getMessage()));
    }

    private void confirmDelete(DocumentSnapshot item) {
        if (!approved) {
            showToast("Provider approval is required to delete inventory items.");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Delete inventory item?")
                .setMessage("Remove " + safe(item.getString("name"))
                        + "? This does not affect laundry services or POS invoices.")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Delete", (dialog, which) -> firestore.collection("providers")
                        .document(providerId).collection("inventory").document(item.getId())
                        .delete()
                        .addOnSuccessListener(unused -> showToast("Inventory item deleted."))
                        .addOnFailureListener(error ->
                                showToast("Could not delete item: " + error.getMessage())))
                .show();
    }

    private EditText input(LinearLayout parent, String hint, int inputType) {
        EditText edit = new EditText(this);
        edit.setSingleLine(true);
        edit.setHint(hint);
        edit.setInputType(inputType);
        parent.addView(edit, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(50)));
        return edit;
    }

    private int integer(DocumentSnapshot snapshot, String key) {
        Object value = snapshot.get(key);
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private double number(DocumentSnapshot snapshot, String key) {
        Object value = snapshot.get(key);
        return value instanceof Number ? ((Number) value).doubleValue() : 0;
    }

    private String money(double value) {
        return String.format(Locale.getDefault(), "UGX %,.0f", value);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private void showState(String message) {
        state.setText(message);
        state.setVisibility(message.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + .5f);
    }

    private void removeListeners() {
        if (profileRegistration != null) profileRegistration.remove();
        if (inventoryRegistration != null) inventoryRegistration.remove();
        profileRegistration = null;
        inventoryRegistration = null;
    }

    @Override
    protected void onStop() {
        removeListeners();
        super.onStop();
    }
}
