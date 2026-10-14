package com.example.washlink.ui.customer;

import android.content.Intent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import android.content.res.ColorStateList;
import com.example.washlink.ui.ThemeManager;

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
        int accent = ThemeManager.accent(requireContext(), ThemeManager.ROLE_CUSTOMER);
        int inactive = ContextCompat.getColor(requireContext(), R.color.nav_inactive);
        int[] ids = {R.id.nav_home, R.id.nav_services, R.id.nav_tracking,
                R.id.nav_history, R.id.nav_profile};
        String[] tabs = {MainActivity.TAB_HOME, MainActivity.TAB_SERVICES,
                MainActivity.TAB_TRACKING, MainActivity.TAB_HISTORY, MainActivity.TAB_PROFILE};
        for (int i = 0; i < ids.length; i++) {
            View item = root.findViewById(ids[i]);
            if (item instanceof android.view.ViewGroup) {
                boolean active = tabs[i].equals(activeTab);
                ImageView icon = item.findViewById(android.R.id.icon);
                if (icon == null && ((android.view.ViewGroup) item).getChildCount() > 0
                        && ((android.view.ViewGroup) item).getChildAt(0) instanceof ImageView) {
                    icon = (ImageView) ((android.view.ViewGroup) item).getChildAt(0);
                }
                TextView label = ((android.view.ViewGroup) item).getChildCount() > 1
                        ? (TextView) ((android.view.ViewGroup) item).getChildAt(1) : null;
                if (icon != null) ImageViewCompat.setImageTintList(icon,
                        ColorStateList.valueOf(active ? accent : inactive));
                if (label != null) label.setTextColor(active ? accent : inactive);
            }
        }
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
