package com.example.washlink;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.washlink.data.BookingPricing;
import com.example.washlink.data.ProviderCapabilities;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FieldValue;

import java.util.ArrayList;
import java.util.HashMap;
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
        ImageButton favoriteButton = findViewById(R.id.btn_favorite_provider);
        ChipGroup serviceGroup = findViewById(R.id.group_provider_services);

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
        if (favoriteButton != null && providerId != null && !providerId.trim().isEmpty()) {
            bindFavoriteButton(favoriteButton, providerId,
                    nonEmpty(name, getString(R.string.provider_business_name_fallback)));
        } else if (favoriteButton != null) {
            favoriteButton.setVisibility(View.GONE);
        }
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
                ArrayList<String> selectedNames = new ArrayList<>();
                double combinedPricePerKg = 0;
                if (serviceGroup != null) {
                    for (int i = 0; i < serviceGroup.getChildCount(); i++) {
                        View child = serviceGroup.getChildAt(i);
                        if (child instanceof Chip && ((Chip) child).isChecked()
                                && child.getTag() instanceof ProviderServiceOption) {
                            ProviderServiceOption selected = (ProviderServiceOption) child.getTag();
                            selectedNames.add(selected.name);
                            combinedPricePerKg += selected.pricePerKg;
                        }
                    }
                }
                if (selectedNames.isEmpty()) {
                    Toast.makeText(this, "This provider has no services available to book.",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                boolean dropOff = "Drop Off".equalsIgnoreCase(selectedService);
                Intent intent = new Intent(ProviderDetailsActivity.this,
                        dropOff ? DropOffActivity.class : PickupAddressActivity.class);
                intent.putExtra("selected_service", selectedService == null
                        ? "Pickup & Delivery" : selectedService);
                intent.putExtra("selected_laundry_service",
                        android.text.TextUtils.join(", ", selectedNames));
                intent.putStringArrayListExtra("selected_laundry_services", selectedNames);
                intent.putExtra("price_per_kg", combinedPricePerKg);
                intent.putExtra("provider_id", providerId);
                intent.putExtra("provider_name", name);
                intent.putExtra("provider_phone", phone);
                intent.putExtra("provider_address", address);
                startActivity(intent);
            });
        }

        if (serviceGroup != null && providerId != null && !providerId.trim().isEmpty()) {
            loadProviderServices(providerId, serviceGroup, bookBtn,
                    !"Closed".equalsIgnoreCase(status), selectedService, status, statusTv);
            loadRecentReviews(providerId, findViewById(R.id.tv_recent_reviews));
        } else if (bookBtn != null) {
            bookBtn.setEnabled(false);
        }

        BottomNavHelper.bind(this);
    }

    private void bindFavoriteButton(ImageButton button, String providerId, String providerName) {
        String customerId = FirebaseAuth.getInstance().getUid();
        if (customerId == null) {
            button.setEnabled(false);
            return;
        }
        button.setContentDescription(getString(R.string.favorite_add_description));
        button.setEnabled(false);
        button.setImageResource(R.drawable.ic_favorite_border);
        com.google.firebase.firestore.DocumentReference favorite = FirebaseFirestore.getInstance()
                .collection("users").document(customerId).collection("favoriteProviders")
                .document(providerId);
        favorite.get().addOnSuccessListener(document -> {
            if (isFinishing() || isDestroyed()) return;
            boolean[] saved = {document.exists()};
            setFavoriteButton(button, saved[0]);
            button.setEnabled(true);
            button.setOnClickListener(v -> {
                button.setEnabled(false);
                if (saved[0]) {
                    favorite.delete().addOnSuccessListener(unused -> {
                        saved[0] = false;
                        setFavoriteButton(button, false);
                        button.setEnabled(true);
                        Toast.makeText(this, R.string.favorite_removed, Toast.LENGTH_SHORT).show();
                    }).addOnFailureListener(error -> {
                        button.setEnabled(true);
                        Toast.makeText(this, R.string.favorite_update_error, Toast.LENGTH_LONG).show();
                    });
                } else {
                    Map<String, Object> data = new HashMap<>();
                    data.put("providerId", providerId);
                    data.put("businessName", providerName);
                    data.put("createdAt", FieldValue.serverTimestamp());
                    favorite.set(data).addOnSuccessListener(unused -> {
                        saved[0] = true;
                        setFavoriteButton(button, true);
                        button.setEnabled(true);
                        Toast.makeText(this, R.string.favorite_added, Toast.LENGTH_SHORT).show();
                    }).addOnFailureListener(error -> {
                        button.setEnabled(true);
                        Toast.makeText(this, R.string.favorite_update_error, Toast.LENGTH_LONG).show();
                    });
                }
            });
        }).addOnFailureListener(error -> {
            if (isFinishing() || isDestroyed()) return;
            button.setEnabled(false);
            Toast.makeText(this, R.string.favorite_load_error, Toast.LENGTH_LONG).show();
        });
    }

    private void setFavoriteButton(ImageButton button, boolean saved) {
        button.setImageResource(saved ? R.drawable.ic_favorite_filled
                : R.drawable.ic_favorite_border);
        button.setContentDescription(getString(saved
                ? R.string.favorite_remove_description : R.string.favorite_add_description));
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

    private void loadProviderServices(String providerId, ChipGroup serviceGroup, Button bookButton,
                                      boolean providerOpen, String selectedService, String providerStatus,
                                      TextView statusView) {
        TextView priceView = findViewById(R.id.tv_provider_price);
        if (bookButton != null) bookButton.setEnabled(false);
        FirebaseFirestore.getInstance().collection("providers").document(providerId).get()
                .addOnSuccessListener(document -> {
                    boolean supportsSelectedService = ProviderCapabilities.supportsBookingMode(
                            selectedService, document.get("supportsDropoff"),
                            document.get("supportsPickupDelivery"));
                    boolean approved = Boolean.TRUE.equals(document.get("isApproved"));
                    Object openValue = document.get("isOpen");
                    boolean providerCurrentlyOpen = !(openValue instanceof Boolean)
                            || (Boolean) openValue;
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
                    serviceGroup.removeAllViews();
                    for (ProviderServiceOption option : serviceOptions) {
                        Chip chip = new Chip(this);
                        chip.setText(option.toString());
                        chip.setCheckable(true);
                        chip.setClickable(true);
                        chip.setTag(option);
                        chip.setTextColor(ColorStateList.valueOf(getColor(R.color.text_primary)));
                        chip.setChipBackgroundColor(new ColorStateList(
                                new int[][]{{android.R.attr.state_checked}, new int[]{}},
                                new int[]{Color.rgb(226, 240, 255), Color.WHITE}));
                        chip.setChipStrokeColorResource(R.color.divider_gray);
                        chip.setOnCheckedChangeListener((button, checked) -> {
                            double selectedPrice = 0;
                            for (int i = 0; i < serviceGroup.getChildCount(); i++) {
                                View child = serviceGroup.getChildAt(i);
                                if (child instanceof Chip && ((Chip) child).isChecked()
                                        && child.getTag() instanceof ProviderServiceOption) {
                                    selectedPrice += ((ProviderServiceOption) child.getTag()).pricePerKg;
                                }
                            }
                            if (priceView != null) {
                                priceView.setText(selectedPrice > 0
                                        ? getString(R.string.provider_price_from,
                                        BookingPricing.format((int) Math.round(selectedPrice)))
                                        : getString(R.string.provider_contact_for_quote));
                            }
                        });
                        serviceGroup.addView(chip);
                    }
                    boolean hasServices = !serviceOptions.isEmpty() && approved;
                    serviceGroup.setEnabled(hasServices);
                    ArrayList<String> repeatServiceNames = getIntent()
                            .getStringArrayListExtra("repeat_service_names");
                    if (bookButton != null) {
                        bookButton.setEnabled(hasServices && providerCurrentlyOpen
                                && supportsSelectedService);
                        bookButton.setText(!approved ? R.string.provider_approval_pending
                                : !supportsSelectedService
                                ? R.string.provider_service_mode_unavailable
                                : hasServices ? R.string.provider_book_action
                                : R.string.provider_no_services);
                    }
                    if (statusView != null) {
                        if (!approved) {
                            statusView.setText(R.string.provider_approval_pending);
                        } else if (supportsSelectedService) {
                            if (openValue instanceof Boolean) {
                                statusView.setText((Boolean) openValue
                                        ? R.string.open_now : R.string.closed);
                            } else {
                                statusView.setText(providerStatus);
                            }
                        } else {
                            statusView.setText(R.string.provider_service_mode_unavailable);
                        }
                        statusView.setClickable(false);
                        statusView.setFocusable(false);
                        statusView.setOnClickListener(null);
                        if (!approved || !supportsSelectedService) {
                            statusView.setTextColor(getColor(R.color.text_secondary));
                        } else if (openValue instanceof Boolean) {
                            statusView.setTextColor(getColor((Boolean) openValue
                                    ? R.color.success_green : R.color.text_secondary));
                        }
                    }
                    serviceGroup.setVisibility(approved ? View.VISIBLE : View.GONE);
                    for (int index = 0; index < serviceGroup.getChildCount(); index++) {
                        View child = serviceGroup.getChildAt(index);
                        if (!(child instanceof Chip) || !(child.getTag() instanceof ProviderServiceOption)
                                || repeatServiceNames == null) continue;
                        String optionName = ((ProviderServiceOption) child.getTag()).name;
                        for (String repeatName : repeatServiceNames) {
                            if (optionName.equalsIgnoreCase(repeatName)) {
                                ((Chip) child).setChecked(true);
                                break;
                            }
                        }
                    }
                    if (!hasServices) {
                        TextView price = findViewById(R.id.tv_provider_price);
                        if (price != null) price.setText(approved
                                ? R.string.provider_no_services : R.string.provider_approval_pending);
                    }
                })
                .addOnFailureListener(error -> {
                    if (statusView != null) {
                        statusView.setText(R.string.provider_services_load_error);
                        statusView.setClickable(true);
                        statusView.setFocusable(true);
                        statusView.setOnClickListener(v -> loadProviderServices(
                                providerId, serviceGroup, bookButton, providerOpen, selectedService,
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
