package com.example.washlink;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.washlink.data.AuthGuard;
import com.example.washlink.data.AuthRepository;
import com.example.washlink.models.UserAccount;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ManageServicesActivity extends AppCompatActivity {
    private final List<Map<String, Object>> services = new ArrayList<>();
    private RecyclerView recyclerView;
    private TextView stateView;
    private ServiceAdapter adapter;
    private String providerId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_manage_services);
        AuthGuard.requireRole(this, UserAccount.ROLE_PROVIDER, ProviderLoginActivity.class);

        View back = findViewById(R.id.btn_back);
        if (back != null) back.setOnClickListener(v -> finish());

        ProviderNavBarHelper.bind(this, ProviderNavBarHelper.TAB_SERVICES);
        providerId = AuthRepository.getInstance().getCurrentUserId();
        recyclerView = findViewById(R.id.rv_services);
        stateView = findViewById(R.id.tv_services_state);
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
        showState("Loading services...");
        FirebaseFirestore.getInstance().collection("providers").document(providerId).get()
                .addOnSuccessListener(document -> {
                    services.clear();
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
                })
                .addOnFailureListener(error -> {
                    showState("Could not load services. Tap to retry.");
                    stateView.setClickable(true);
                    stateView.setOnClickListener(v -> loadServices());
                });
    }

    private void showServiceDialog(int position) {
        Map<String, Object> existing = position < 0 ? null : services.get(position);
        EditText name = new EditText(this);
        name.setHint("Service name");
        name.setSingleLine(true);
        if (existing != null && existing.get("name") instanceof String) {
            name.setText((String) existing.get("name"));
        }

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
        fields.addView(name);
        fields.addView(price);

        new AlertDialog.Builder(this)
                .setTitle(existing == null ? getString(R.string.add_service) : "Edit service")
                .setView(fields)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    String serviceName = name.getText().toString().trim();
                    double servicePrice;
                    try {
                        servicePrice = Double.parseDouble(price.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, "Enter a valid price.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (serviceName.isEmpty() || servicePrice <= 0) {
                        Toast.makeText(this, "Enter a service name and a price above zero.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Map<String, Object> service = new HashMap<>();
                    service.put("name", serviceName);
                    service.put("pricePerKg", servicePrice);
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
            return new Holder(getLayoutInflater().inflate(R.layout.item_provider_service, parent, false));
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

            Holder(View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.tv_service_name);
                price = itemView.findViewById(R.id.tv_service_price);
                edit = itemView.findViewById(R.id.btn_service_edit);
                remove = itemView.findViewById(R.id.btn_service_remove);
            }
        }
    }
}
