package com.example.washlink;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class NearbyProvidersActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_nearby_providers);

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
        for (View filter : filters) {
            if (filter != null) {
                filter.setOnClickListener(v -> {
                    for (View item : filters) {
                        if (item != null) {
                            item.setBackgroundResource(R.drawable.bg_chip_unselected);
                            if (item instanceof TextView) {
                                ((TextView) item).setTextColor(ContextCompat.getColor(this, R.color.chip_unselected_text));
                            }
                        }
                    }
                    v.setBackgroundResource(R.drawable.bg_chip_selected);
                    if (v instanceof TextView) {
                        ((TextView) v).setTextColor(ContextCompat.getColor(this, R.color.white));
                    }
                });
            }
        }

        RecyclerView recyclerView = findViewById(R.id.rv_providers);
        if (recyclerView != null) {
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
            recyclerView.setAdapter(new ProviderAdapter(createSampleProviders(), provider -> {
                // Open provider details first
                Intent intent = new Intent(this, ProviderDetailsActivity.class);
                intent.putExtra("provider_name", provider.name);
                intent.putExtra("provider_rating", provider.rating);
                intent.putExtra("provider_reviews", provider.reviews);
                intent.putExtra("provider_distance", provider.distance);
                intent.putExtra("provider_status", provider.status);
                intent.putExtra("provider_price", provider.price);
                intent.putExtra("provider_phone", provider.phone);
                intent.putExtra("provider_image_res", provider.imageRes);
                startActivity(intent);
                // simple slide animation
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            }));
        }
    }

    private List<Provider> createSampleProviders() {
        return new ArrayList<>(Arrays.asList(
                new Provider("CleanWave Laundry", "4.8", "214 reviews", "0.8 km away", "Open now", "From UGX 20,000 /kg", "+256701234567", R.drawable.bg_thumbnail_placeholder),
                new Provider("FreshPress Wash", "4.9", "328 reviews", "1.2 km away", "Open now", "From UGX 22,000 /kg", "+256702345678", R.drawable.bg_thumbnail_placeholder),
                new Provider("BlueNest Laundry", "4.6", "196 reviews", "2.1 km away", "Closed", "From UGX 18,000 /kg", "+256703456789", R.drawable.bg_thumbnail_placeholder)
        ));
    }

    private static class Provider {
        final String name;
        final String rating;
        final String reviews;
        final String distance;
        final String status;
        final String price;
        final String phone;
        final int imageRes;

        Provider(String name, String rating, String reviews, String distance, String status, String price, String phone, int imageRes) {
            this.name = name;
            this.rating = rating;
            this.reviews = reviews;
            this.distance = distance;
            this.status = status;
            this.price = price;
            this.phone = phone;
            this.imageRes = imageRes;
        }
    }

    private static class ProviderAdapter extends RecyclerView.Adapter<ProviderAdapter.ProviderViewHolder> {
        private final List<Provider> providers;
        private final ProviderClickListener listener;

        ProviderAdapter(List<Provider> providers, ProviderClickListener listener) {
            this.providers = providers;
            this.listener = listener;
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
                holder.status.setText("Closed");
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
