package com.example.washlink.ui.customer;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.washlink.NearbyProvidersActivity;
import com.example.washlink.R;

public class HomeFragment extends CustomerTabFragment {
    @Override
    public View onCreateView(@NonNull android.view.LayoutInflater inflater,
                             @Nullable android.view.ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_customer_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        bindNavigation(view, MainActivity.TAB_HOME);
        view.findViewById(R.id.btn_book_service).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), NearbyProvidersActivity.class)));
        view.findViewById(R.id.iv_bell).setOnClickListener(v -> openNotifications());
        view.findViewById(R.id.row_order_history).setOnClickListener(v ->
                ((MainActivity) requireActivity()).showTab(MainActivity.TAB_HISTORY));
        view.findViewById(R.id.row_profile_settings).setOnClickListener(v ->
                ((MainActivity) requireActivity()).showTab(MainActivity.TAB_PROFILE));
        View details = view.findViewById(R.id.tv_view_details);
        if (details != null) {
            details.setOnClickListener(v ->
                    ((MainActivity) requireActivity()).showTab(MainActivity.TAB_TRACKING));
        }
    }
}
