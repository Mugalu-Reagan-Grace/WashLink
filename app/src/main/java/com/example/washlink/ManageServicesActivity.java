package com.example.washlink;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.content.res.ColorStateList;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.washlink.data.AuthGuard;
import com.example.washlink.data.AuthRepository;
import com.example.washlink.models.UserAccount;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.example.washlink.ui.ThemeManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ManageServicesActivity extends WashLinkActivity {
    private final List<Map<String, Object>> services = new ArrayList<>();
    private RecyclerView recyclerView;
    private TextView stateView;
    private ServiceAdapter adapter;
    private String providerId;
    private MaterialCheckBox pickupOption;
    private MaterialCheckBox dropoffOption;
    private TextView pickupLabel;
    private TextView dropoffLabel;
    private View saveBookingOptionsButton;
    private ListenerRegistration profileRegistration;
    private boolean bookingOptionsDirty;
    private boolean applyingProfileSnapshot;
    private boolean savingBookingOptions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_provider_manage_services);
        ThemeManager.apply(findViewById(android.R.id.content), this);
        AuthGuard.requireRole(this, UserAccount.ROLE_PROVIDER, ProviderLoginActivity.class);

        View back = findViewById(R.id.btn_back);
        if (back != null) back.setOnClickListener(v -> finish());

        ProviderNavBarHelper.bind(this, ProviderNavBarHelper.TAB_SERVICES);
        providerId = AuthRepository.getInstance().getCurrentUserId();
        recyclerView = findViewById(R.id.rv_services);
        stateView = findViewById(R.id.tv_services_state);
        pickupOption = findViewById(R.id.cb_supports_pickup);
        dropoffOption = findViewById(R.id.cb_supports_dropoff);
        pickupLabel = findViewById(R.id.tv_supports_pickup);
        dropoffLabel = findViewById(R.id.tv_supports_dropoff);
        saveBookingOptionsButton = findViewById(R.id.btn_save_booking_options);
        int accent = ThemeManager.accent(this, ThemeManager.ROLE_PROVIDER);
        ColorStateList optionTint = new ColorStateList(new int[][] {
                new int[] {android.R.attr.state_enabled, android.R.attr.state_checked},
                new int[] {android.R.attr.state_enabled},
                new int[] {android.R.attr.state_checked},
                new int[] {}
        }, new int[] {accent, accent, accent, accent});
        pickupOption.setButtonTintList(optionTint);
        dropoffOption.setButtonTintList(optionTint);
        applyBookingOptionTextColor();
        findViewById(R.id.row_supports_pickup).setOnClickListener(v ->
                pickupOption.setChecked(!pickupOption.isChecked()));
        findViewById(R.id.row_supports_dropoff).setOnClickListener(v ->
                dropoffOption.setChecked(!dropoffOption.isChecked()));
        setBookingOptionsEnabled(false);
        saveBookingOptionsButton.setOnClickListener(v -> saveBookingOptions());
        pickupOption.setOnCheckedChangeListener((button, checked) -> {
            if (!applyingProfileSnapshot) bookingOptionsDirty = true;
        });
        dropoffOption.setOnCheckedChangeListener((button, checked) -> {
            if (!applyingProfileSnapshot) bookingOptionsDirty = true;
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ServiceAdapter();
        recyclerView.setAdapter(adapter);
        findViewById(R.id.btn_add_service).setOnClickListener(v -> showServiceDialog(-1));

        if (providerId == null) {
            showState("Sign in as a provider to manage services.");
            return;
        }
        loadServices();
    }

    private void loadServices() {
        if (profileRegistration != null) profileRegistration.remove();
        showState("Loading services...");
        profileRegistration = FirebaseFirestore.getInstance().collection("providers")
                .document(providerId).addSnapshotListener((document, error) -> {
                    if (error != null || document == null || !document.exists()) {
                        showState("Could not load services. Tap to retry.");
                        stateView.setClickable(true);
                        stateView.setOnClickListener(v -> loadServices());
                        return;
                    }
                    services.clear();
                    if (!bookingOptionsDirty) {
                        applyingProfileSnapshot = true;
                        pickupOption.setChecked(!Boolean.FALSE.equals(
                                document.get("supportsPickupDelivery")));
                        dropoffOption.setChecked(!Boolean.FALSE.equals(
                                document.get("supportsDropoff")));
                        applyingProfileSnapshot = false;
                    }
                    setBookingOptionsEnabled(!savingBookingOptions);
                    Object value = document.get("services");
                    if (value instanceof List<?>) {
                        for (Object item : (List<?>) value) {
                            if (item instanceof Map<?, ?>) {
                                Map<String, Object> service = new HashMap<>();
                                for (Map.Entry<?, ?> entry : ((Map<?, ?>) item).entrySet()) {
                                    if (entry.getKey() instanceof String) {
                                        service.put((String) entry.getKey(), entry.getValue());
                                    }
                                }
                                services.add(service);
                            }
                        }
                    }
                    adapter.notifyDataSetChanged();
                    if (services.isEmpty()) showState("No services yet. Add your first service.");
                    else showList();
                });
    }

    private void saveBookingOptions() {
        if (providerId == null) return;
        if (!pickupOption.isChecked() && !dropoffOption.isChecked()) {
            Toast.makeText(this, R.string.provider_select_booking_option,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        savingBookingOptions = true;
        setBookingOptionsEnabled(false);
        Map<String, Object> options = new HashMap<>();
        options.put("supportsPickupDelivery", pickupOption.isChecked());
        options.put("supportsDropoff", dropoffOption.isChecked());
        FirebaseFirestore.getInstance().collection("providers").document(providerId)
                .update(options)
                .addOnSuccessListener(unused -> {
                    savingBookingOptions = false;
                    bookingOptionsDirty = false;
                    setBookingOptionsEnabled(true);
                    Toast.makeText(this, R.string.provider_booking_options_saved,
                            Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(error -> Toast.makeText(this,
                        getString(R.string.provider_booking_options_save_error,
                                error.getMessage() == null ? "" : error.getMessage()),
                        Toast.LENGTH_LONG).show())
                .addOnCompleteListener(task -> {
                    savingBookingOptions = false;
                    setBookingOptionsEnabled(true);
                });
    }

    private void setBookingOptionsEnabled(boolean enabled) {
        pickupOption.setEnabled(enabled);
        dropoffOption.setEnabled(enabled);
        pickupLabel.setEnabled(enabled);
        dropoffLabel.setEnabled(enabled);
        saveBookingOptionsButton.setEnabled(enabled);
    }

    private void applyBookingOptionTextColor() {
        int color = ThemeManager.themeMode(this, ThemeManager.ROLE_PROVIDER)
                .equals("cream") ? getColor(R.color.cream_text_primary)
                : getColor(R.color.text_primary);
        pickupLabel.setTextColor(color);
        dropoffLabel.setTextColor(color);
    }

    private void showServiceDialog(int position) {
        Map<String, Object> existing = position < 0 ? null : services.get(position);
        Spinner serviceName = new Spinner(this);
        String[] knownServices = {"Wash & Fold", "Dry Cleaning", "Ironing & Pressing",
                "Bedding & Blankets", "Shoe Cleaning", "Stain Removal", "Carpet Cleaning",
                "Curtain Cleaning", "Express Laundry", "Custom service"};
        serviceName.setAdapter(new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_dropdown_item, knownServices));
        String existingName = existing != null && existing.get("name") instanceof String
                ? (String) existing.get("name") : knownServices[0];
        int selected = 0;
        for (int i = 0; i < knownServices.length; i++) {
            if (knownServices[i].equals(existingName)) selected = i;
        }
        serviceName.setSelection(selected);
        EditText customName = new EditText(this);
        customName.setHint("Custom service name");
        customName.setSingleLine(true);
        customName.setVisibility(selected == knownServices.length - 1 ? View.VISIBLE : View.GONE);
        customName.setText(selected == knownServices.length - 1 ? existingName : "");
        serviceName.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view,
                                                 int position, long id) {
                customName.setVisibility(position == knownServices.length - 1
                        ? View.VISIBLE : View.GONE);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });

        EditText price = new EditText(this);
        price.setHint("Price per kg (UGX)");
        price.setSingleLine(true);
        price.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if (existing != null && existing.get("pricePerKg") instanceof Number) {
            price.setText(String.valueOf(((Number) existing.get("pricePerKg")).doubleValue()));
        }

        android.widget.LinearLayout fields = new android.widget.LinearLayout(this);
        fields.setOrientation(android.widget.LinearLayout.VERTICAL);
        fields.setPadding(32, 0, 32, 0);
        fields.addView(serviceName);
        fields.addView(customName);
        fields.addView(price);

        new AlertDialog.Builder(this)
                .setTitle(existing == null ? getString(R.string.add_service) : "Edit service")
                .setView(fields)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    String selectedName = serviceName.getSelectedItemPosition()
                            == knownServices.length - 1
                            ? customName.getText().toString().trim()
                            : String.valueOf(serviceName.getSelectedItem());
                    double servicePrice;
                    try {
                        servicePrice = Double.parseDouble(price.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, "Enter a valid price.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (selectedName.isEmpty() || !Double.isFinite(servicePrice)
                            || servicePrice <= 0) {
                        Toast.makeText(this, "Enter a service name and a price above zero.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Map<String, Object> service = new HashMap<>();
                    service.put("name", selectedName);
                    service.put("pricePerKg", servicePrice);
                    service.put("isAvailable", existing == null
                            || !Boolean.FALSE.equals(existing.get("isAvailable")));
                    for (int i = 0; i < services.size(); i++) {
                        if (i == position) continue;
                        Object otherName = services.get(i).get("name");
                        if (otherName instanceof String
                                && ((String) otherName).equalsIgnoreCase(selectedName)) {
                            Toast.makeText(this, "A service with that name already exists.",
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }
                    if (position < 0) services.add(service);
                    else services.set(position, service);
                    persistServices();
                })
                .show();
    }

    private void removeService(int position) {
        new AlertDialog.Builder(this)
                .setTitle("Remove service?")
                .setMessage("This service will no longer appear in your service list.")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Remove", (dialog, which) -> {
                    services.remove(position);
                    persistServices();
                })
                .show();
    }

    private void toggleAvailability(int position) {
        if (position < 0 || position >= services.size()) return;
        Map<String, Object> service = new HashMap<>(services.get(position));
        boolean available = !Boolean.FALSE.equals(service.get("isAvailable"));
        service.put("isAvailable", !available);
        services.set(position, service);
        persistServices();
    }

    private void persistServices() {
        if (providerId == null) return;
        showState("Saving services...");
        FirebaseFirestore.getInstance().collection("providers").document(providerId)
                .update("services", new ArrayList<>(services))
                .addOnSuccessListener(unused -> {
                    adapter.notifyDataSetChanged();
                    if (services.isEmpty()) showState("No services yet. Add your first service.");
                    else showList();
                })
                .addOnFailureListener(error -> {
                    Toast.makeText(this, "Could not save services: " + error.getMessage(),
                            Toast.LENGTH_LONG).show();
                    loadServices();
                });
    }

    private void showState(String message) {
        stateView.setText(message);
        stateView.setVisibility(View.VISIBLE);
        stateView.setClickable(false);
        stateView.setOnClickListener(null);
        recyclerView.setVisibility(View.GONE);
    }

    private void showList() {
        stateView.setVisibility(View.GONE);
        stateView.setClickable(false);
        stateView.setOnClickListener(null);
        recyclerView.setVisibility(View.VISIBLE);
    }

    private class ServiceAdapter extends RecyclerView.Adapter<ServiceAdapter.Holder> {
        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new Holder(getLayoutInflater().inflate(R.layout.activity_provider_service_item, parent, false));
        }

        @Override
        public void onBindViewHolder(Holder holder, int position) {
            Map<String, Object> service = services.get(position);
            holder.name.setText(service.get("name") instanceof String
                    ? (String) service.get("name") : "Laundry service");
            Object amount = service.get("pricePerKg");
            String priceText = amount instanceof Number
                    ? String.format(Locale.getDefault(), "UGX %,.0f / kg", ((Number) amount).doubleValue())
                    : "Price not set";
            holder.price.setText(priceText);
            boolean available = !Boolean.FALSE.equals(service.get("isAvailable"));
            holder.availability.setText(available ? R.string.provider_service_available
                    : R.string.provider_service_unavailable);
            holder.availability.setTextColor(getColor(available
                    ? R.color.success_green : R.color.text_secondary));
            holder.availability.setOnClickListener(v -> {
                int index = holder.getBindingAdapterPosition();
                if (index != RecyclerView.NO_POSITION) toggleAvailability(index);
            });
            holder.edit.setOnClickListener(v -> {
                int index = holder.getBindingAdapterPosition();
                if (index != RecyclerView.NO_POSITION) showServiceDialog(index);
            });
            holder.remove.setOnClickListener(v -> {
                int index = holder.getBindingAdapterPosition();
                if (index != RecyclerView.NO_POSITION) removeService(index);
            });
        }

        @Override
        public int getItemCount() {
            return services.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final TextView name;
            final TextView price;
            final TextView edit;
            final TextView remove;
            final TextView availability;

            Holder(View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.tv_service_name);
                price = itemView.findViewById(R.id.tv_service_price);
                edit = itemView.findViewById(R.id.btn_service_edit);
                remove = itemView.findViewById(R.id.btn_service_remove);
                availability = itemView.findViewById(R.id.btn_service_availability);
            }
        }

    }

    @Override
    protected void onDestroy() {
        if (profileRegistration != null) profileRegistration.remove();
        super.onDestroy();
    }
}
