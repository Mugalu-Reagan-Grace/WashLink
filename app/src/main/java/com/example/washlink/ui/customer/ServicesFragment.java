package com.example.washlink.ui.customer;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.washlink.DropOffActivity;
import com.example.washlink.PickupAddressActivity;
import com.example.washlink.R;

public class ServicesFragment extends CustomerTabFragment {
    @Override
    public View onCreateView(@NonNull android.view.LayoutInflater inflater,
                             @Nullable android.view.ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_customer_services, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        bindNavigation(view, MainActivity.TAB_SERVICES);
        view.findViewById(R.id.card_drop_off).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), DropOffActivity.class)));
        view.findViewById(R.id.card_pickup_delivery).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), PickupAddressActivity.class)));
    }
}
