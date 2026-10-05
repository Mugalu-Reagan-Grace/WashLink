package com.example.washlink;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;
import android.widget.AdapterView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.washlink.data.BookingPricing;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ProviderDetailsActivity extends AppCompatActivity {
    private final List<ProviderServiceOption> serviceOptions = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_provider_details);
        com.example.washlink.data.AuthGuard.requireRole(this,
                com.example.washlink.models.UserAccount.ROLE_CUSTOMER, SignInActivity.class);

        ImageView back = findViewById(R.id.btn_back);
        if (back != null) back.setOnClickListener(v -> finish());

        ImageView avatar = findViewById(R.id.iv_provider_avatar);
        TextView nameTv = findViewById(R.id.tv_provider_name);
        TextView ratingTv = findViewById(R.id.tv_provider_rating);
        TextView reviewsTv = findViewById(R.id.tv_provider_reviews);
        TextView distanceTv = findViewById(R.id.tv_provider_distance);
        TextView statusTv = findViewById(R.id.tv_provider_status);
        TextView priceTv = findViewById(R.id.tv_provider_price);
        Button callBtn = findViewById(R.id.btn_call_provider_details);
        View chatBtn = findViewById(R.id.btn_chat_provider);
        View whatsappBtn = findViewById(R.id.btn_whatsapp_provider);
        Button bookBtn = findViewById(R.id.btn_book_provider);
        Spinner serviceSpinner = findViewById(R.id.spinner_provider_services);

        Intent in = getIntent();
        String name = in != null ? in.getStringExtra("provider_name") : null;
        String rating = in != null ? in.getStringExtra("provider_rating") : null;
        String reviews = in != null ? in.getStringExtra("provider_reviews") : null;
        String distance = in != null ? in.getStringExtra("provider_distance") : null;
        String status = in != null ? in.getStringExtra("provider_status") : null;
        String price = in != null ? in.getStringExtra("provider_price") : null;
        String phone = in != null ? in.getStringExtra("provider_phone") : null;
        String providerId = in != null ? in.getStringExtra("provider_id") : null;
        String address = in != null ? in.getStringExtra("provider_address") : null;
        String selectedService = in != null ? in.getStringExtra("selected_service") : null;
        int imageRes = in != null ? in.getIntExtra("provider_image_res",
                R.drawable.ic_provider_business) : R.drawable.ic_provider_business;

        if (avatar != null) avatar.setImageResource(imageRes);
        if (nameTv != null) nameTv.setText(nonEmpty(name, getString(
                R.string.provider_business_name_fallback)));
        if (ratingTv != null) ratingTv.setText(nonEmpty(rating,
                getString(R.string.provider_not_rated)));
        if (reviewsTv != null) reviewsTv.setText(nonEmpty(reviews,
                getString(R.string.provider_no_reviews)));
        if (distanceTv != null) distanceTv.setText(nonEmpty(distance,
                getString(R.string.provider_distance_unavailable)));
        if (statusTv != null) statusTv.setText(nonEmpty(status,
                getString(R.string.provider_availability_unlisted)));
        if (priceTv != null) priceTv.setText(nonEmpty(price,
                getString(R.string.provider_contact_for_quote)));

        if (callBtn != null) {
            final String phoneFinal = phone;
            boolean hasPhone = phoneFinal != null && !phoneFinal.trim().isEmpty();
            callBtn.setEnabled(hasPhone);
            callBtn.setText(hasPhone ? R.string.provider_call : R.string.provider_phone_unavailable);
            callBtn.setAlpha(hasPhone ? 1f : 0.55f);
            callBtn.setOnClickListener(v -> {
                if (phoneFinal == null || phoneFinal.trim().isEmpty()) return;
                Intent dial = new Intent(Intent.ACTION_DIAL,
                        Uri.fromParts("tel", phoneFinal.trim(), null));
                if (dial.resolveActivity(getPackageManager()) != null) {
                    startActivity(dial);
                } else {
                    Toast.makeText(this, R.string.provider_call_unavailable,
                            Toast.LENGTH_SHORT).show();
                }
            });
        }
        if (chatBtn != null) {
            chatBtn.setEnabled(providerId != null && !providerId.trim().isEmpty());
            chatBtn.setOnClickListener(v -> {
                if (providerId == null || providerId.trim().isEmpty()) return;
                Intent chat = new Intent(this, ChatActivity.class);
                chat.putExtra(ChatActivity.EXTRA_PROVIDER_ID, providerId);
                chat.putExtra(ChatActivity.EXTRA_PROVIDER_NAME, name);
                startActivity(chat);
            });
        }
        if (whatsappBtn != null) {
            boolean hasPhone = phone != null && !phone.trim().isEmpty();
            whatsappBtn.setEnabled(hasPhone);
            whatsappBtn.setAlpha(hasPhone ? 1f : 0.55f);
            whatsappBtn.setOnClickListener(v -> openWhatsApp(phone));
        }

        if (bookBtn != null) {
            bookBtn.setEnabled(false);
            bookBtn.setOnClickListener(v -> {
                int selectedIndex = serviceSpinner == null ? -1 : serviceSpinner.getSelectedItemPosition();
                if (selectedIndex < 0 || selectedIndex >= serviceOptions.size()) {
                    Toast.makeText(this, "This provider has no services available to book.",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                ProviderServiceOption serviceOption = serviceOptions.get(selectedIndex);
                boolean dropOff = "Drop Off".equalsIgnoreCase(selectedService);
                Intent intent = new Intent(ProviderDetailsActivity.this,
                        dropOff ? DropOffActivity.class : PickupAddressActivity.class);
                intent.putExtra("selected_service", selectedService == null
                        ? "Pickup & Delivery" : selectedService);
                intent.putExtra("selected_laundry_service", serviceOption.name);
                intent.putExtra("price_per_kg", serviceOption.pricePerKg);
                intent.putExtra("provider_id", providerId);
                intent.putExtra("provider_name", name);
                intent.putExtra("provider_phone", phone);
                intent.putExtra("provider_address", address);
                startActivity(intent);
            });
        }

        if (serviceSpinner != null && providerId != null && !providerId.trim().isEmpty()) {
            loadProviderServices(providerId, serviceSpinner, bookBtn,
                    !"Closed".equalsIgnoreCase(status), status, statusTv);
            serviceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (position >= 0 && position < serviceOptions.size() && priceTv != null) {
                        priceTv.setText(getString(R.string.provider_price_from,
                                BookingPricing.format((int) Math.round(
                                        serviceOptions.get(position).pricePerKg))));
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) { }
            });
            loadRecentReviews(providerId, findViewById(R.id.tv_recent_reviews));
        } else if (bookBtn != null) {
            bookBtn.setEnabled(false);
        }

        BottomNavHelper.bind(this);
    }

    private void openWhatsApp(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            Toast.makeText(this, R.string.provider_phone_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        String number = phone.replaceAll("[^0-9]", "");
        if (number.startsWith("0")) {
            number = "256" + number.substring(1);
        } else if (!number.startsWith("256") && number.length() == 9) {
            number = "256" + number;
        }
        if (number.isEmpty()) {
            Toast.makeText(this, R.string.provider_phone_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent whatsapp = new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://wa.me/" + number));
        if (whatsapp.resolveActivity(getPackageManager()) != null) {
            startActivity(whatsapp);
        } else {
            Toast.makeText(this, R.string.provider_whatsapp_unavailable,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void loadRecentReviews(String providerId, TextView reviewsView) {
        if (reviewsView == null) return;
        reviewsView.setText(R.string.provider_reviews_loading);
        reviewsView.setOnClickListener(null);
        FirebaseFirestore.getInstance().collection("providers").document(providerId)
                .collection("reviews").orderBy("createdAt",
                        com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(5).get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.isEmpty()) {
                        reviewsView.setText(R.string.provider_no_reviews);
                        return;
                    }
                    StringBuilder text = new StringBuilder(
                            getString(R.string.provider_recent_reviews)).append("\n\n");
                    for (com.google.firebase.firestore.DocumentSnapshot review
                            : snapshot.getDocuments()) {
                        Object rating = review.get("rating");
                        Object reviewer = review.get("customerName");
                        Object body = review.get("reviewText");
                        text.append(rating instanceof Number ? rating : "—")
                                .append("/5 · ")
                                .append(reviewer instanceof String ? reviewer : "WashLink customer");
                        if (body instanceof String && !((String) body).trim().isEmpty()) {
                            text.append('\n').append((String) body);
                        }
                        text.append("\n\n");
                    }
                    reviewsView.setText(text.toString().trim());
                })
                .addOnFailureListener(error -> {
                    reviewsView.setText(R.string.provider_reviews_load_error);
                    reviewsView.setOnClickListener(v -> loadRecentReviews(providerId, reviewsView));
                });
    }

    private void loadProviderServices(String providerId, Spinner spinner, Button bookButton,
                                      boolean providerOpen, String providerStatus,
                                      TextView statusView) {
        if (bookButton != null) bookButton.setEnabled(false);
        FirebaseFirestore.getInstance().collection("providers").document(providerId).get()
                .addOnSuccessListener(document -> {
                    serviceOptions.clear();
                    Object value = document.get("services");
                    if (value instanceof List<?>) {
                        for (Object item : (List<?>) value) {
                            if (!(item instanceof Map<?, ?>)) continue;
                            Map<?, ?> data = (Map<?, ?>) item;
                            Object name = data.get("name");
                            Object price = data.get("pricePerKg");
                            if (!Boolean.FALSE.equals(data.get("isAvailable"))
                                    && name instanceof String && !((String) name).trim().isEmpty()
                                    && price instanceof Number && ((Number) price).doubleValue() > 0) {
                                serviceOptions.add(new ProviderServiceOption(
                                        (String) name, ((Number) price).doubleValue()));
                            }
                        }
                    }
                    List<String> labels = new ArrayList<>();
                    for (ProviderServiceOption option : serviceOptions) labels.add(option.toString());
                    if (labels.isEmpty()) labels.add(getString(R.string.provider_no_services));
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                            android.R.layout.simple_spinner_item, labels);
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spinner.setAdapter(adapter);
                    boolean hasServices = !serviceOptions.isEmpty();
                    spinner.setEnabled(hasServices);
                    if (bookButton != null) {
                        bookButton.setEnabled(hasServices && providerOpen);
                        bookButton.setText(hasServices ? R.string.provider_book_action
                                : R.string.provider_no_services);
                    }
                    if (statusView != null) {
                        statusView.setText(providerStatus);
                        statusView.setClickable(false);
                        statusView.setFocusable(false);
                        statusView.setOnClickListener(null);
                    }
                    if (!hasServices) {
                        TextView price = findViewById(R.id.tv_provider_price);
                        if (price != null) price.setText(R.string.provider_no_services);
                    }
                })
                .addOnFailureListener(error -> {
                    if (statusView != null) {
                        statusView.setText(R.string.provider_services_load_error);
                        statusView.setClickable(true);
                        statusView.setFocusable(true);
                        statusView.setOnClickListener(v -> loadProviderServices(
                                providerId, spinner, bookButton, providerOpen,
                                providerStatus, statusView));
                    }
                });
    }

    private String nonEmpty(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private static final class ProviderServiceOption {
        final String name;
        final double pricePerKg;

        ProviderServiceOption(String name, double pricePerKg) {
            this.name = name;
            this.pricePerKg = pricePerKg;
        }

        @Override
        public String toString() {
            return name + " · " + BookingPricing.format((int) Math.round(pricePerKg)) + " / kg";
        }
    }

    @Override
    public void finish() {
        super.finish();
        // reverse slide
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
}
