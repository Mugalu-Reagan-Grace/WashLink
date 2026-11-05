package com.example.washlink;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.print.PrintManager;
import android.text.InputType;
import android.view.View;
import android.webkit.WebView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.example.washlink.data.AuthGuard;
import com.example.washlink.data.AuthRepository;
import com.example.washlink.models.UserAccount;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ProviderPosActivity extends WashLinkActivity {
    private final List<Service> services = new ArrayList<>();
    private final List<DocumentSnapshot> invoices = new ArrayList<>();
    private FirebaseFirestore firestore;
    private String providerId;
    private Spinner serviceSpinner;
    private EditText customerName;
    private EditText customerPhone;
    private EditText quantity;
    private Spinner paymentMethod;
    private TextView priceLabel;
    private TextView state;
    private LinearLayout invoiceRows;
    private View createButton;
    private boolean approved;
    private ListenerRegistration profileRegistration;
    private ListenerRegistration invoiceRegistration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AuthGuard.requireRole(this, UserAccount.ROLE_PROVIDER, ProviderLoginActivity.class);
        setContentView(R.layout.activity_provider_pos);
        firestore = FirebaseFirestore.getInstance();
        providerId = AuthRepository.getInstance().getCurrentUserId();
        ProviderNavBarHelper.bind(this, -1);
        findViewById(R.id.btn_provider_pos_back).setOnClickListener(v -> finish());
        serviceSpinner = findViewById(R.id.sp_pos_service);
        customerName = findViewById(R.id.et_pos_customer_name);
        customerPhone = findViewById(R.id.et_pos_customer_phone);
        quantity = findViewById(R.id.et_pos_quantity);
        paymentMethod = findViewById(R.id.sp_pos_payment);
        priceLabel = findViewById(R.id.tv_pos_price);
        state = findViewById(R.id.tv_pos_state);
        invoiceRows = findViewById(R.id.pos_invoice_rows);
        createButton = findViewById(R.id.btn_pos_create_invoice);
        paymentMethod.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Cash", "Mobile money", "Card", "Other"}));
        serviceSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view,
                                                 int position, long id) {
                updatePriceLabel();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        createButton.setOnClickListener(v -> createSale());
        if (providerId == null) {
            showState("Sign in as a provider to use in-person sales.");
            createButton.setEnabled(false);
        } else {
            listenForProviderAndInvoices();
        }
    }

    private void listenForProviderAndInvoices() {
        profileRegistration = firestore.collection("providers").document(providerId)
                .addSnapshotListener((document, error) -> {
                    if (error != null || document == null || !document.exists()) {
                        approved = false;
                        createButton.setEnabled(false);
                        showState("Could not verify provider approval: "
                                + (error == null ? "profile unavailable" : error.getMessage()));
                        return;
                    }
                    approved = Boolean.TRUE.equals(document.getBoolean("isApproved"));
                    services.clear();
                    Object catalogValue = document.get("services");
                    List<?> catalog = catalogValue instanceof List<?> ? (List<?>) catalogValue : null;
                    if (catalog != null) {
                        for (int index = 0; index < catalog.size(); index++) {
                            Object entry = catalog.get(index);
                            if (!(entry instanceof Map<?, ?>)) continue;
                            Map<?, ?> data = (Map<?, ?>) entry;
                            Object name = data.get("name");
                            Object price = data.get("pricePerKg");
                            if (name instanceof String && price instanceof Number
                                    && !Boolean.FALSE.equals(data.get("isAvailable"))) {
                                services.add(new Service(index, (String) name,
                                        ((Number) price).doubleValue()));
                            }
                        }
                    }
                    bindServices();
                    if (!approved) {
                        createButton.setEnabled(false);
                        showState("In-person sales are available after your provider account is approved.");
                    } else if (services.isEmpty()) {
                        createButton.setEnabled(false);
                        showState("Add an available service and price in Services before recording a sale.");
                    } else {
                        createButton.setEnabled(true);
                        showState("");
                    }
                });
        invoiceRegistration = firestore.collection("providers").document(providerId)
                .collection("invoices").addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        showState("Could not load invoices: " + error.getMessage());
                        return;
                    }
                    invoices.clear();
                    if (snapshot != null) invoices.addAll(snapshot.getDocuments());
                    Collections.sort(invoices, (left, right) ->
                            Long.compare(createdAt(right), createdAt(left)));
                    renderInvoices();
                });
    }

    private void bindServices() {
        List<String> labels = new ArrayList<>();
        for (Service service : services) {
            labels.add(service.name + " — " + money(service.price) + " / kg");
        }
        serviceSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, labels));
        updatePriceLabel();
    }

    private void updatePriceLabel() {
        if (services.isEmpty() || serviceSpinner.getSelectedItemPosition() < 0) {
            priceLabel.setText("No available services");
            return;
        }
        Service service = services.get(serviceSpinner.getSelectedItemPosition());
        priceLabel.setText("Catalog price: " + money(service.price) + " per kg");
    }

    private void createSale() {
        if (!approved || services.isEmpty() || serviceSpinner.getSelectedItemPosition() < 0) return;
        String name = customerName.getText().toString().trim();
        String phone = customerPhone.getText().toString().trim();
        double kg;
        try {
            kg = Double.parseDouble(quantity.getText().toString().trim());
        } catch (NumberFormatException e) {
            quantity.setError("Enter a valid quantity.");
            return;
        }
        if (name.length() < 2 || name.length() > 100) {
            customerName.setError("Enter the customer's name.");
            return;
        }
        if (phone.length() > 30 || (phone.length() > 0 && phone.length() < 7)) {
            customerPhone.setError("Enter a valid phone number.");
            return;
        }
        if (!Double.isFinite(kg) || kg <= 0 || kg > 1000) {
            quantity.setError("Enter a quantity greater than zero (up to 1,000 kg).");
            return;
        }

        Service service = services.get(serviceSpinner.getSelectedItemPosition());
        String method = new String[]{"cash", "mobile_money", "card", "other"}
                [paymentMethod.getSelectedItemPosition()];
        double total = service.price * kg;
        com.google.firebase.firestore.DocumentReference invoice =
                firestore.collection("providers").document(providerId)
                        .collection("invoices").document();
        com.google.firebase.firestore.DocumentReference sale =
                firestore.collection("providers").document(providerId)
                        .collection("sales").document(invoice.getId());
        String invoiceNumber = "WL-" + invoice.getId().substring(0, 8).toUpperCase(Locale.ROOT);
        Map<String, Object> record = new HashMap<>();
        record.put("providerId", providerId);
        record.put("invoiceNumber", invoiceNumber);
        record.put("customerName", name);
        record.put("customerPhone", phone);
        record.put("serviceName", service.name);
        record.put("serviceIndex", service.index);
        record.put("pricePerKg", service.price);
        record.put("quantityKg", kg);
        record.put("subtotal", total);
        record.put("total", total);
        record.put("currency", "UGX");
        record.put("paymentMethod", method);
        record.put("createdAt", FieldValue.serverTimestamp());

        createButton.setEnabled(false);
        WriteBatch batch = firestore.batch();
        batch.set(sale, record);
        batch.set(invoice, record);
        batch.commit().addOnSuccessListener(unused -> {
            Toast.makeText(this, "Sale recorded and invoice saved.", Toast.LENGTH_LONG).show();
            customerName.setText("");
            customerPhone.setText("");
            quantity.setText("");
            createButton.setEnabled(approved);
        }).addOnFailureListener(error -> {
            createButton.setEnabled(approved);
            Toast.makeText(this, "Could not record sale: " + error.getMessage(),
                    Toast.LENGTH_LONG).show();
        });
    }

    private void renderInvoices() {
        invoiceRows.removeAllViews();
        if (invoices.isEmpty()) return;
        for (DocumentSnapshot invoice : invoices) {
            String number = invoice.getString("invoiceNumber");
            String customer = invoice.getString("customerName");
            String service = invoice.getString("serviceName");
            Object storedTotal = invoice.get("total");
            Number total = storedTotal instanceof Number ? (Number) storedTotal : null;
            TextView row = new TextView(this);
            row.setText((number == null ? "Invoice" : number) + "  ·  "
                    + (customer == null ? "Customer" : customer) + "\n"
                    + (service == null ? "Service" : service) + "  ·  "
                    + money(total == null ? 0 : total.doubleValue()));
            row.setTextColor(getColor(R.color.text_primary));
            row.setTextSize(14);
            row.setPadding(dp(16), dp(14), dp(16), dp(14));
            row.setBackgroundResource(R.drawable.bg_card_bordered);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = dp(8);
            invoiceRows.addView(row, params);
            row.setOnClickListener(v -> showInvoice(invoice));
        }
    }

    private void showInvoice(DocumentSnapshot invoice) {
        String text = invoiceText(invoice);
        new AlertDialog.Builder(this)
                .setTitle(invoice.getString("invoiceNumber"))
                .setMessage(text)
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton("Print", (dialog, which) -> printInvoice(invoice))
                .setPositiveButton("Share", (dialog, which) -> shareInvoice(text))
                .show();
    }

    private String invoiceText(DocumentSnapshot invoice) {
        String created = invoice.getTimestamp("createdAt") == null ? ""
                : android.text.format.DateFormat.getDateFormat(this)
                .format(invoice.getTimestamp("createdAt").toDate());
        return "WASHLINK · IN-PERSON SALE\n"
                + "Invoice: " + safe(invoice.getString("invoiceNumber")) + "\n"
                + "Date: " + created + "\n\n"
                + "Customer: " + safe(invoice.getString("customerName")) + "\n"
                + "Phone: " + safe(invoice.getString("customerPhone")) + "\n"
                + "Service: " + safe(invoice.getString("serviceName")) + "\n"
                + "Price: " + money(number(invoice, "pricePerKg")) + " / kg\n"
                + "Quantity: " + number(invoice, "quantityKg") + " kg\n"
                + "Payment: " + safe(invoice.getString("paymentMethod")) + "\n\n"
                + "TOTAL: " + money(number(invoice, "total"));
    }

    private void shareInvoice(String text) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, "WashLink invoice");
        intent.putExtra(Intent.EXTRA_TEXT, text);
        try {
            startActivity(Intent.createChooser(intent, "Share invoice"));
        } catch (android.content.ActivityNotFoundException error) {
            Toast.makeText(this, "No app is available to share the invoice.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void printInvoice(DocumentSnapshot invoice) {
        PrintManager manager = (PrintManager) getSystemService(Context.PRINT_SERVICE);
        WebView webView = new WebView(this);
        String html = "<html><body style='font:16px sans-serif;padding:28px'><h1>WashLink</h1>"
                + "<h2>IN-PERSON SALE</h2><pre style='white-space:pre-wrap;font:16px sans-serif'>"
                + escapeHtml(invoiceText(invoice)) + "</pre></body></html>";
        webView.loadDataWithBaseURL(null, html, "text/HTML", "UTF-8", null);
        webView.setWebViewClient(new android.webkit.WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                String jobName = invoice.getString("invoiceNumber");
                manager.print(jobName == null ? "WashLink invoice" : jobName,
                        view.createPrintDocumentAdapter(jobName),
                        new android.print.PrintAttributes.Builder().build());
            }
        });
    }

    private String safe(String value) { return value == null ? "" : value; }
    private double number(DocumentSnapshot snapshot, String key) {
        Object value = snapshot.get(key);
        return value instanceof Number ? ((Number) value).doubleValue() : 0;
    }
    private String money(double value) {
        return String.format(Locale.getDefault(), "UGX %,.0f", value);
    }
    private String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
    private long createdAt(DocumentSnapshot snapshot) {
        return snapshot.getTimestamp("createdAt") == null ? 0
                : snapshot.getTimestamp("createdAt").toDate().getTime();
    }
    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + .5f);
    }
    private void showState(String message) {
        state.setText(message);
        state.setVisibility(message.isEmpty() ? View.GONE : View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        if (profileRegistration != null) profileRegistration.remove();
        if (invoiceRegistration != null) invoiceRegistration.remove();
        super.onDestroy();
    }

    private static final class Service {
        final int index;
        final String name;
        final double price;
        Service(int index, String name, double price) {
            this.index = index;
            this.name = name;
            this.price = price;
        }
    }
}
