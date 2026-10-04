package com.example.washlink;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class NearbyProvidersActivity extends AppCompatActivity {
    private static final int LOCATION_REQUEST = 411;
    private final List<Provider> allProviders = new ArrayList<>();
    private ProviderAdapter adapter;
    private TextView resultCount;
    private TextView stateView;
    private RecyclerView recyclerView;
    private String filter = "all";
    private String query = "";
    private String selectedService;
    private Location userLocation;
    private boolean providersLoaded;
    private boolean providerLoadFailed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_nearby_providers);
        com.example.washlink.data.AuthGuard.requireRole(this,
                com.example.washlink.models.UserAccount.ROLE_CUSTOMER, SignInActivity.class);
        selectedService = getIntent().getStringExtra("selected_service");
        if (selectedService == null || selectedService.trim().isEmpty()) selectedService = "Pickup & Delivery";

        findViewById(R.id.btn_open_map).setOnClickListener(v ->
                startActivity(new Intent(this, NearbyLaundryMapActivity.class)));

        View backButton = findViewById(R.id.btn_back);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        FrameLayout bellLayout = findViewById(R.id.iv_bell);
        if (bellLayout != null) {
            bellLayout.setOnClickListener(v -> {
                    Intent intent = new Intent(NearbyProvidersActivity.this, NotificationsActivity.class);
                    startActivity(intent);
                });
        }

        TextView allFilter = findViewById(R.id.chip_filter_all);
        TextView nearestFilter = findViewById(R.id.chip_filter_nearest);
        TextView topRatedFilter = findViewById(R.id.chip_filter_top_rated);
        TextView lowestPriceFilter = findViewById(R.id.chip_filter_lowest_price);

        View[] filters = new View[]{allFilter, nearestFilter, topRatedFilter, lowestPriceFilter};
        String[] filterValues = {"all", "nearest", "top", "price"};
        for (int i = 0; i < filters.length; i++) {
            View item = filters[i];
            final String value = filterValues[i];
            if (item != null) {
                item.setOnClickListener(v -> {
                    filter = value;
                    for (View chip : filters) {
                        if (chip != null) {
                            chip.setBackgroundResource(R.drawable.bg_chip_unselected);
                            if (chip instanceof TextView) {
                                ((TextView) chip).setTextColor(ContextCompat.getColor(this, R.color.chip_unselected_text));
                            }
                        }
                    }
                    v.setBackgroundResource(R.drawable.bg_chip_selected);
                    if (v instanceof TextView) {
                        ((TextView) v).setTextColor(ContextCompat.getColor(this, R.color.white));
                    }
                    applyFilter();
                    if ("nearest".equals(value)) requestNearestLocation();
                });
            }
        }

        recyclerView = findViewById(R.id.rv_providers);
        resultCount = findViewById(R.id.tv_result_count);
        stateView = findViewById(R.id.tv_providers_state);
        if (recyclerView != null) {
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
            adapter = new ProviderAdapter(new ArrayList<>(), provider -> {
                Intent intent = new Intent(this, ProviderDetailsActivity.class);
                intent.putExtra("provider_id", provider.id);
                intent.putExtra("provider_name", provider.name);
                intent.putExtra("provider_rating", provider.rating);
                intent.putExtra("provider_reviews", provider.reviews);
                intent.putExtra("provider_distance", provider.distance);
                intent.putExtra("provider_status", provider.status);
                intent.putExtra("provider_price", provider.price);
                intent.putExtra("provider_phone", provider.phone);
                intent.putExtra("provider_address", provider.address);
                intent.putExtra("selected_service", selectedService);
                intent.putExtra("provider_image_res", provider.imageRes);
                startActivity(intent);
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            });
            recyclerView.setAdapter(adapter);
            EditText search = findViewById(R.id.et_search);
            search.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    query = s.toString().trim().toLowerCase(Locale.ROOT);
                    applyFilter();
                }
                @Override public void afterTextChanged(android.text.Editable s) { }
            });
        }
        loadProviders();
    }

    private void loadProviders() {
        providersLoaded = false;
        providerLoadFailed = false;
        if (adapter != null) adapter.replace(Collections.emptyList());
        if (resultCount != null) resultCount.setText("Loading providers...");
        showState("Loading providers...");
        FirebaseFirestore.getInstance().collection("providers").get()
                .addOnSuccessListener(snapshot -> {
                    providersLoaded = true;
                    providerLoadFailed = false;
                    allProviders.clear();
                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                        if (Boolean.FALSE.equals(document.get("isApproved"))) continue;
                        String name = stringValue(document.get("businessName"));
                        if (name.isEmpty()) continue;
                        double minPrice = Double.MAX_VALUE;
                        Object serviceData = document.get("services");
                        if (serviceData instanceof List<?>) {
                            for (Object item : (List<?>) serviceData) {
                                if (item instanceof Map<?, ?>) {
                                    Object price = ((Map<?, ?>) item).get("pricePerKg");
                                    if (price instanceof Number) {
                                        minPrice = Math.min(minPrice, ((Number) price).doubleValue());
                                    }
                                }
                            }
                        }
                        Object ratingValue = document.get("rating");
                        double rating = ratingValue instanceof Number ? ((Number) ratingValue).doubleValue() : -1;
                        Object reviewsValue = document.get("reviewCount");
                        int reviewCount = reviewsValue instanceof Number ? ((Number) reviewsValue).intValue() : 0;
                        Object distanceValue = document.get("distanceKm");
                        double distanceKm = distanceValue instanceof Number
                                ? ((Number) distanceValue).doubleValue() : Double.MAX_VALUE;
                        Object latitudeValue = document.get("latitude");
                        Object longitudeValue = document.get("longitude");
                        double latitude = latitudeValue instanceof Number
                                ? ((Number) latitudeValue).doubleValue() : 0d;
                        double longitude = longitudeValue instanceof Number
                                ? ((Number) longitudeValue).doubleValue() : 0d;
                        Object availability = document.get("isOpen");
                        String status = availability instanceof Boolean
                                ? ((Boolean) availability ? "Open now" : "Closed")
                                : "Availability not listed";
                        String priceText = minPrice == Double.MAX_VALUE ? "Contact for quote"
                                : String.format(Locale.US, "From UGX %,.0f /kg", minPrice);
                        String distanceText = distanceKm == Double.MAX_VALUE ? "Distance unavailable"
                                : String.format(Locale.getDefault(), "%.1f km away", distanceKm);
                        String ratingText = rating < 0 ? "New" : String.format(Locale.US, "%.1f", rating);
                        Provider provider = new Provider(document.getId(), name, ratingText,
                                reviewCount + " reviews", distanceText, status, priceText,
                                stringValue(document.get("phone")), stringValue(document.get("address")),
                                rating, reviewCount, distanceKm, minPrice, R.drawable.bg_thumbnail_placeholder);
                        provider.setCoordinates(latitude, longitude);
                        provider.updateDistance(userLocation);
                        allProviders.add(provider);
                    }
                    applyFilter();
                })
                .addOnFailureListener(error -> {
                    allProviders.clear();
                    providerLoadFailed = true;
                    if (resultCount != null) resultCount.setText("Providers unavailable");
                    showState("Could not load providers. Tap to retry.");
                });
    }

    private void requestNearestLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_REQUEST);
            return;
        }
        com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(this)
                .getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location == null) {
                        Toast.makeText(this, R.string.map_location_unavailable,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    userLocation = location;
                    for (Provider provider : allProviders) {
                        provider.updateDistance(userLocation);
                    }
                    applyFilter();
                })
                .addOnFailureListener(error -> Toast.makeText(this,
                        R.string.map_location_unavailable, Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != LOCATION_REQUEST) return;
        boolean granted = false;
        for (int result : grantResults) {
            if (result == PackageManager.PERMISSION_GRANTED) {
                granted = true;
                break;
            }
        }
        if (granted) {
            requestNearestLocation();
        } else {
            Toast.makeText(this, R.string.map_location_permission, Toast.LENGTH_SHORT).show();
        }
    }

    private String stringValue(Object value) {
        return value instanceof String ? (String) value : "";
    }

    private void applyFilter() {
        if (adapter == null || !providersLoaded) return;
        List<Provider> visible = new ArrayList<>();
        for (Provider provider : allProviders) {
            if (query.isEmpty() || provider.name.toLowerCase(Locale.ROOT).contains(query)) {
                visible.add(provider);
            }
        }
        if ("nearest".equals(filter)) {
            Collections.sort(visible, Comparator.comparingDouble(provider -> provider.distanceKm));
        } else if ("top".equals(filter)) {
            Collections.sort(visible, Comparator.comparingDouble(
                    (Provider provider) -> provider.ratingValue).reversed());
        } else if ("price".equals(filter)) {
            Collections.sort(visible, Comparator.comparingDouble(provider -> provider.priceValue));
        }
        adapter.replace(visible);
        if (resultCount != null) resultCount.setText(visible.size() + " providers found");
        if (visible.isEmpty()) showState(allProviders.isEmpty()
                ? "No laundry providers are registered yet."
                : "No providers match your search.");
        else showList();
    }

    private void showState(String message) {
        if (stateView != null) {
            stateView.setText(message);
            stateView.setVisibility(View.VISIBLE);
            stateView.setClickable(providerLoadFailed);
            stateView.setFocusable(providerLoadFailed);
            stateView.setOnClickListener(providerLoadFailed ? v -> loadProviders() : null);
        }
        if (recyclerView != null) recyclerView.setVisibility(View.GONE);
    }

    private void showList() {
        if (stateView != null) stateView.setVisibility(View.GONE);
        if (recyclerView != null) recyclerView.setVisibility(View.VISIBLE);
    }

    private static class Provider {
        final String id;
        final String name;
        final String rating;
        final String reviews;
        String distance;
        final String status;
        final String price;
        final String phone;
        final String address;
        final double ratingValue;
        final int reviewCount;
        double distanceKm;
        final double priceValue;
        final int imageRes;
        double latitude;
        double longitude;

        Provider(String id, String name, String rating, String reviews, String distance,
                 String status, String price, String phone, String address, double ratingValue,
                 int reviewCount, double distanceKm, double priceValue, int imageRes) {
            this.id = id;
            this.name = name;
            this.rating = rating;
            this.reviews = reviews;
            this.distance = distance;
            this.status = status;
            this.price = price;
            this.phone = phone;
            this.address = address;
            this.ratingValue = ratingValue;
            this.reviewCount = reviewCount;
            this.distanceKm = distanceKm;
            this.priceValue = priceValue;
            this.imageRes = imageRes;
        }

        void setCoordinates(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }

        void updateDistance(Location origin) {
            if (origin == null || (latitude == 0d && longitude == 0d)) return;
            float[] result = new float[1];
            Location.distanceBetween(origin.getLatitude(), origin.getLongitude(),
                    latitude, longitude, result);
            distanceKm = result[0] / 1000d;
            distance = String.format(Locale.getDefault(), "%.1f km away", distanceKm);
        }
    }

    private static class ProviderAdapter extends RecyclerView.Adapter<ProviderAdapter.ProviderViewHolder> {
        private final List<Provider> providers = new ArrayList<>();
        private final ProviderClickListener listener;

        ProviderAdapter(List<Provider> providers, ProviderClickListener listener) {
            this.providers.addAll(providers);
            this.listener = listener;
        }

        void replace(List<Provider> newProviders) {
            providers.clear();
            providers.addAll(newProviders);
            notifyDataSetChanged();
        }

        @Override
        public ProviderViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_provider, parent, false);
            return new ProviderViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ProviderViewHolder holder, int position) {
            Provider provider = providers.get(position);
            holder.name.setText(provider.name);
            holder.rating.setText(provider.rating);
            holder.reviews.setText("(" + provider.reviews + ")");
            holder.distance.setText(provider.distance);
            holder.price.setText(provider.price);
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onProviderClicked(provider);
                }
            });

            if ("Open now".equalsIgnoreCase(provider.status)) {
                holder.status.setBackgroundResource(R.drawable.bg_open_now_pill);
                holder.status.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.open_now_text));
                holder.status.setText(R.string.open_now);
            } else {
                holder.status.setBackgroundResource(R.drawable.bg_closed_pill);
                holder.status.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.text_secondary));
                holder.status.setText(provider.status);
            }
        }

        @Override
        public int getItemCount() {
            return providers.size();
        }

        interface ProviderClickListener {
            void onProviderClicked(Provider provider);
        }

        static class ProviderViewHolder extends RecyclerView.ViewHolder {
            final TextView name;
            final TextView rating;
            final TextView reviews;
            final TextView distance;
            final TextView status;
            final TextView price;

            ProviderViewHolder(View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.tv_provider_name);
                rating = itemView.findViewById(R.id.tv_rating_value);
                reviews = itemView.findViewById(R.id.tv_review_count);
                distance = itemView.findViewById(R.id.tv_distance);
                status = itemView.findViewById(R.id.tv_status);
                price = itemView.findViewById(R.id.tv_price);
            }
        }
    }
}
