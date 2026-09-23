package com.example.washlink.ui.customer;

import android.content.Intent;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.washlink.NotificationsActivity;
import com.example.washlink.R;

public abstract class CustomerTabFragment extends Fragment {
    protected void bindNavigation(@Nullable View root, String activeTab) {
        if (root == null || getActivity() == null) return;
        bind(root, R.id.nav_home, MainActivity.TAB_HOME);
        bind(root, R.id.nav_services, MainActivity.TAB_SERVICES);
        bind(root, R.id.nav_tracking, MainActivity.TAB_TRACKING);
        bind(root, R.id.nav_history, MainActivity.TAB_HISTORY);
        bind(root, R.id.nav_profile, MainActivity.TAB_PROFILE);
    }

    private void bind(View root, int id, String tab) {
        View item = root.findViewById(id);
        if (item != null) {
            item.setOnClickListener(v -> ((MainActivity) requireActivity()).showTab(tab));
        }
    }

    protected void openNotifications() {
        startActivity(new Intent(requireContext(), NotificationsActivity.class));
    }
}
