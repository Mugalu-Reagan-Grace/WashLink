package com.example.washlink;

import android.content.Intent;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

public class ProviderDetailsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_provider_details);

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
        Button bookBtn = findViewById(R.id.btn_book_provider);

        Intent in = getIntent();
        String name = in != null ? in.getStringExtra("provider_name") : "Provider";
        String rating = in != null ? in.getStringExtra("provider_rating") : "N/A";
        String reviews = in != null ? in.getStringExtra("provider_reviews") : "";
        String distance = in != null ? in.getStringExtra("provider_distance") : "";
        String status = in != null ? in.getStringExtra("provider_status") : "";
        String price = in != null ? in.getStringExtra("provider_price") : "";
        String phone = in != null ? in.getStringExtra("provider_phone") : "+256700000000";
        int imageRes = in != null ? in.getIntExtra("provider_image_res", R.drawable.bg_thumbnail_placeholder) : R.drawable.bg_thumbnail_placeholder;

        if (avatar != null) avatar.setImageResource(imageRes);
        if (nameTv != null) nameTv.setText(name);
        if (ratingTv != null) ratingTv.setText(rating);
        if (reviewsTv != null) reviewsTv.setText(reviews);
        if (distanceTv != null) distanceTv.setText(distance);
        if (statusTv != null) statusTv.setText(status);
        if (priceTv != null) priceTv.setText(price);

        if (callBtn != null) {
            final String phoneFinal = phone;
            callBtn.setOnClickListener(v -> {
                Intent dial = new Intent(Intent.ACTION_DIAL);
                dial.setData(Uri.parse("tel:" + phoneFinal));
                startActivity(dial);
            });
        }

        if (bookBtn != null) {
            bookBtn.setOnClickListener(v -> {
                Intent intent = new Intent(ProviderDetailsActivity.this, PickupAddressActivity.class);
                intent.putExtra("selected_service", "Pickup & Delivery");
                intent.putExtra("selected_provider", name);
                intent.putExtra("provider_name", name);
                intent.putExtra("provider_address", in.getStringExtra("provider_address"));
                startActivity(intent);
            });
        }

        BottomNavHelper.bind(this);
    }

    @Override
    public void finish() {
        super.finish();
        // reverse slide
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
}
