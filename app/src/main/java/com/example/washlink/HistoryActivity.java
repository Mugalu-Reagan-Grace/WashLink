package com.example.washlink;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class HistoryActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        String state = getIntent() != null ? getIntent().getStringExtra("state") : "list";
        if ("loading".equals(state)) {
            setContentView(R.layout.activity_history_loading);
        } else if ("empty".equals(state)) {
            setContentView(R.layout.activity_history_empty);
        } else if ("error".equals(state)) {
            setContentView(R.layout.activity_history_error);
        } else {
            setContentView(R.layout.activity_history);
        }

        BottomNavHelper.bind(this);

        View back = findViewById(R.id.btn_back);
        if (back != null) back.setOnClickListener(v -> finish());

        FrameLayout bell = findViewById(R.id.iv_bell);
        if (bell != null) bell.setOnClickListener(v ->
                Toast.makeText(this, "Notifications enabled", Toast.LENGTH_SHORT).show());

        MaterialButton bookButton = findViewById(R.id.btn_book_service);
        if (bookButton != null) {
            bookButton.setOnClickListener(v -> {
                Intent intent = new Intent(HistoryActivity.this, SelectServiceActivity.class);
                startActivity(intent);
            });
        }

        RecyclerView rv = findViewById(R.id.rv_order_history);
        if (rv != null) {
            rv.setLayoutManager(new LinearLayoutManager(this));
            // TODO: replace with real data source. For now show empty list so layout remains functional.
            List<String> sample = new ArrayList<>();
            // sample.add("Order #1234 - Completed"); // add items here if you want to preview
            rv.setAdapter(new SimpleAdapter(sample));
        }
    }

    private static class SimpleAdapter extends RecyclerView.Adapter<SimpleAdapter.VH> {
        private final List<String> items;

        SimpleAdapter(List<String> items) {
            this.items = items != null ? items : new ArrayList<>();
        }

        static class VH extends RecyclerView.ViewHolder {
            TextView title;
            TextView subtitle;
            VH(View itemView) {
                super(itemView);
                title = itemView.findViewById(android.R.id.text1);
                subtitle = itemView.findViewById(android.R.id.text2);
            }
        }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_2, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(VH holder, int position) {
            String s = items.get(position);
            holder.title.setText(s);
            holder.subtitle.setText("Details");
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }
}
