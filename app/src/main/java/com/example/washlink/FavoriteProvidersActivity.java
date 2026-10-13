package com.example.washlink;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.washlink.data.AuthGuard;
import com.example.washlink.data.BookingPricing;
import com.example.washlink.data.ProviderCapabilities;
import com.example.washlink.models.UserAccount;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FavoriteProvidersActivity extends AppCompatActivity {
    private final List<FavoriteProvider> providers = new ArrayList<>();
    private RecyclerView recyclerView;
    private TextView stateView;
    private FavoriteAdapter adapter;
    private String customerId;
    private int loadGeneration;
    private boolean loadFailed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_customer_favorite_providers);
        AuthGuard.requireRole(this, UserAccount.ROLE_CUSTOMER, SignInActivity.class);

        customerId = FirebaseAuth.getInstance().getUid();
        recyclerView = findViewById(R.id.rv_favorite_providers);
        stateView = findViewById(R.id.tv_favorite_state);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FavoriteAdapter();
        recyclerView.setAdapter(adapter);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        stateView.setOnClickListener(v -> {
            if (loadFailed) loadFavorites();
        });
        loadFavorites();
    }

    private void loadFavorites() {
        int generation = ++loadGeneration;
        loadFailed = false;
        providers.clear();
        adapter.notifyDataSetChanged();
        showState(getString(R.string.favorite_loading), false);
        if (customerId == null) {
            showState(getString(R.string.favorite_load_error), true);
            return;
        }

        FirebaseFirestore.getInstance().collection("users").document(customerId)
                .collection("favoriteProviders").get()
                .addOnSuccessListener(snapshot -> {
                    if (generation != loadGeneration || isFinishing() || isDestroyed()) return;
                    if (snapshot.isEmpty()) {
                        showState(getString(R.string.favorite_empty), false);
                        return;
                    }
                    final int[] pending = {snapshot.size()};
                    final boolean[] readFailed = {false};
                    for (DocumentSnapshot favorite : snapshot.getDocuments()) {
                        FirebaseFirestore.getInstance().collection("providers")
                                .document(favorite.getId()).get()
                                .addOnSuccessListener(provider -> {
                                    if (generation != loadGeneration || isFinishing()
                                            || isDestroyed()) return;
                                    providers.add(toFavoriteProvider(favorite, provider));
                                    finishProviderRead(generation, pending, readFailed);
                                })
                                .addOnFailureListener(error -> {
                                    if (generation != loadGeneration || isFinishing()
                                            || isDestroyed()) return;
                                    readFailed[0] = true;
                                    finishProviderRead(generation, pending, readFailed);
                                });
                    }
                })
                .addOnFailureListener(error -> {
                    if (generation != loadGeneration || isFinishing() || isDestroyed()) return;
                    loadFailed = true;
                    showState(getString(R.string.favorite_load_error), true);
                });
    }

    private void finishProviderRead(int generation, int[] pending, boolean[] readFailed) {
        pending[0]--;
        if (pending[0] != 0 || generation != loadGeneration) return;
        if (readFailed[0]) {
            loadFailed = true;
            showState(getString(R.string.favorite_load_error), true);
            return;
        }
        providers.sort((first, second) ->
                first.name.compareToIgnoreCase(second.name));
        adapter.notifyDataSetChanged();
        if (providers.isEmpty()) {
            showState(getString(R.string.favorite_empty), false);
        } else {
            stateView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private FavoriteProvider toFavoriteProvider(DocumentSnapshot favorite,
                                                DocumentSnapshot provider) {
        if (!provider.exists() || !Boolean.TRUE.equals(provider.get("isApproved"))) {
            return toUnavailableProvider(favorite);
        }
        String name = stringValue(provider.get("businessName"),
                stringValue(favorite.get("businessName"), getString(R.string.favorite_empty_name)));
        double minimumPrice = Double.MAX_VALUE;
        Object serviceData = provider.get("services");
        if (serviceData instanceof List<?>) {
            for (Object item : (List<?>) serviceData) {
                if (!(item instanceof Map<?, ?>)) continue;
                Map<?, ?> service = (Map<?, ?>) item;
                Object serviceName = service.get("name");
                Object amount = service.get("pricePerKg");
                if (!Boolean.FALSE.equals(service.get("isAvailable"))
                        && serviceName instanceof String
                        && !((String) serviceName).trim().isEmpty()
                        && amount instanceof Number
                        && ((Number) amount).doubleValue() > 0) {
                    minimumPrice = Math.min(minimumPrice, ((Number) amount).doubleValue());
                }
            }
        }
        Object ratingValue = provider.get("rating");
        Object reviewsValue = provider.get("reviewCount");
        Object openValue = provider.get("isOpen");
        String rating = ratingValue instanceof Number
                ? String.format(Locale.US, "%.1f", ((Number) ratingValue).doubleValue())
                : getString(R.string.provider_not_rated);
        String reviews = reviewsValue instanceof Number
                && ((Number) reviewsValue).intValue() > 0
                ? getString(R.string.provider_review_count, ((Number) reviewsValue).intValue())
                : getString(R.string.provider_no_reviews);
        String status = openValue instanceof Boolean
                ? getString((Boolean) openValue ? R.string.open_now : R.string.closed)
                : getString(R.string.provider_availability_unlisted);
        String bookingMode = ProviderCapabilities.preferredBookingMode(
                provider.get("supportsDropoff"), provider.get("supportsPickupDelivery"));
        String price = minimumPrice == Double.MAX_VALUE
                ? getString(R.string.provider_contact_for_quote)
                : getString(R.string.provider_price_from,
                BookingPricing.format((int) Math.round(minimumPrice)));
        return new FavoriteProvider(favorite.getId(), name, rating, reviews, status, price,
                stringValue(provider.get("phone"), ""),
                stringValue(provider.get("address"), ""), bookingMode, true);
    }

    private FavoriteProvider toUnavailableProvider(DocumentSnapshot favorite) {
        return new FavoriteProvider(favorite.getId(),
                stringValue(favorite.get("businessName"), getString(R.string.favorite_empty_name)),
                getString(R.string.provider_not_rated), getString(R.string.favorite_unavailable),
                getString(R.string.favorite_unavailable), getString(R.string.favorite_unavailable),
                "", "", "Pickup & Delivery", false);
    }

    private String stringValue(Object value, String fallback) {
        return value instanceof String && !((String) value).trim().isEmpty()
                ? (String) value : fallback;
    }

    private void showState(String message, boolean retry) {
        stateView.setText(message);
        stateView.setVisibility(View.VISIBLE);
        stateView.setClickable(retry);
        stateView.setFocusable(retry);
        recyclerView.setVisibility(View.GONE);
    }

    private void removeFavorite(FavoriteProvider provider) {
        if (customerId == null) return;
        FirebaseFirestore.getInstance().collection("users").document(customerId)
                .collection("favoriteProviders").document(provider.id).delete()
                .addOnSuccessListener(unused -> {
                    int index = providers.indexOf(provider);
                    if (index >= 0) {
                        providers.remove(index);
                        adapter.notifyItemRemoved(index);
                    }
                    if (providers.isEmpty()) showState(getString(R.string.favorite_empty), false);
                    Toast.makeText(this, R.string.favorite_removed, Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(error -> Toast.makeText(this,
                        R.string.favorite_update_error, Toast.LENGTH_LONG).show());
    }

    private void openProvider(FavoriteProvider provider) {
        if (!provider.available) return;
        Intent intent = new Intent(this, ProviderDetailsActivity.class);
        intent.putExtra("provider_id", provider.id);
        intent.putExtra("provider_name", provider.name);
        intent.putExtra("provider_rating", provider.rating);
        intent.putExtra("provider_reviews", provider.reviews);
        intent.putExtra("provider_status", provider.status);
        intent.putExtra("provider_price", provider.price);
        intent.putExtra("provider_phone", provider.phone);
        intent.putExtra("provider_address", provider.address);
        intent.putExtra("selected_service", provider.bookingMode);
        intent.putExtra("provider_image_res", R.drawable.ic_provider_business);
        startActivity(intent);
    }

    private static final class FavoriteProvider {
        final String id;
        final String name;
        final String rating;
        final String reviews;
        final String status;
        final String price;
        final String phone;
        final String address;
        final String bookingMode;
        final boolean available;

        FavoriteProvider(String id, String name, String rating, String reviews, String status,
                         String price, String phone, String address, String bookingMode,
                         boolean available) {
            this.id = id;
            this.name = name;
            this.rating = rating;
            this.reviews = reviews;
            this.status = status;
            this.price = price;
            this.phone = phone;
            this.address = address;
            this.bookingMode = bookingMode;
            this.available = available;
        }
    }

    private final class FavoriteAdapter
            extends RecyclerView.Adapter<FavoriteAdapter.Holder> {
        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.activity_customer_favorite_provider_item, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            FavoriteProvider provider = providers.get(position);
            holder.name.setText(provider.name);
            holder.rating.setText(provider.rating);
            holder.reviews.setText(provider.reviews);
            holder.status.setText(provider.status);
            holder.price.setText(provider.price);
            holder.remove.setOnClickListener(v -> removeFavorite(provider));
            holder.itemView.setAlpha(provider.available ? 1f : 0.72f);
            holder.itemView.setOnClickListener(v -> openProvider(provider));
        }

        @Override
        public int getItemCount() {
            return providers.size();
        }

        final class Holder extends RecyclerView.ViewHolder {
            final TextView name = itemView.findViewById(R.id.tv_favorite_provider_name);
            final TextView rating = itemView.findViewById(R.id.tv_favorite_provider_rating);
            final TextView reviews = itemView.findViewById(R.id.tv_favorite_provider_reviews);
            final TextView status = itemView.findViewById(R.id.tv_favorite_provider_status);
            final TextView price = itemView.findViewById(R.id.tv_favorite_provider_price);
            final TextView remove = itemView.findViewById(R.id.btn_remove_favorite);

            Holder(View itemView) {
                super(itemView);
            }
        }
    }
}
