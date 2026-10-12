package com.example.washlink.ui;

import android.app.Activity;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.view.WindowInsetsControllerCompat;

import java.util.WeakHashMap;

public final class BlueHeaderStyler {
    private static final int BLUE = Color.rgb(22, 82, 230);
    private static final int DARK_BLUE = Color.rgb(15, 63, 184);
    private static final int LIGHT_HEADER_TEXT = 0xFFDCE6FF;
    private static final WeakHashMap<Activity, StylingState> STATES = new WeakHashMap<>();

    private BlueHeaderStyler() { }

    public static void apply(Activity activity) {
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null || content.getChildCount() == 0) return;

        activity.getWindow().setStatusBarColor(DARK_BLUE);
        new WindowInsetsControllerCompat(activity.getWindow(), activity.getWindow().getDecorView())
                .setAppearanceLightStatusBars(false);

        StylingState state = STATES.get(activity);
        if (state == null || state.content != content) {
            state = new StylingState(content);
            STATES.put(activity, state);
            StylingState listenerState = state;
            content.getViewTreeObserver().addOnGlobalLayoutListener(
                    () -> styleCurrentViews(activity, listenerState));
        }
        styleCurrentViews(activity, state);
    }

    private static void styleCurrentViews(Activity activity, StylingState state) {
        if (activity.isFinishing() || state.content.getChildCount() == 0) return;
        View screen = state.content.getChildAt(0);
        View headerTitle = findHeaderTitle(activity, screen);
        View bottomNav = findView(activity, screen,
                "bottom_nav_bar", "provider_chat_nav", "provider_chat_thread_nav");

        if (bottomNav != state.bottomNav) {
            state.bottomNav = bottomNav;
            if (bottomNav != null) styleBottomNavigation(bottomNav);
        }
        if (headerTitle == state.headerTitle && screen == state.screen) return;
        state.screen = screen;
        state.headerTitle = headerTitle;
        if (headerTitle == null) return;

        if (isHomeHeader(activity, screen, headerTitle)) {
            styleHomeHeader(activity, screen, headerTitle);
        } else {
            styleScreenHeader(activity, screen, headerTitle);
        }
    }

    private static View findHeaderTitle(Activity activity, View screen) {
        View homeName = findView(activity, screen, "tv_user_name");
        if (homeName != null && findView(activity, screen, "promo_banner") != null) {
            return homeName;
        }
        return findView(activity, screen, "provider_dashboard_header", "tv_header_title",
                "tv_chat_title", "tv_notifications_title", "tv_rider_title", "tv_title");
    }

    private static boolean isHomeHeader(Activity activity, View screen, View headerTitle) {
        return headerTitle == findView(activity, screen, "tv_user_name")
                && findView(activity, screen, "promo_banner") != null;
    }

    private static void styleHomeHeader(Activity activity, View screen, View name) {
        View scroll = findView(activity, screen, "scroll_content");
        View avatar = findView(activity, screen, "iv_avatar");
        if (avatar == null || scroll == null
                || !(scroll.getParent() instanceof ConstraintLayout)) return;
        ConstraintLayout homeRoot = (ConstraintLayout) scroll.getParent();
        addHomeBlueBar(activity, homeRoot);
        styleHeaderText((TextView) name, Color.WHITE);
        View address = findView(activity, screen, "tv_user_address");
        if (address instanceof TextView) styleHeaderText((TextView) address, LIGHT_HEADER_TEXT);
        styleHeaderIcon(findView(activity, screen, "iv_chat_inbox"));
        styleHeaderIcon(findView(activity, screen, "iv_bell"));
        View chat = findView(activity, screen, "iv_chat_inbox");
        if (chat != null) chat.setBackgroundColor(Color.TRANSPARENT);
        View bell = findView(activity, screen, "iv_bell");
        if (bell != null) bell.setBackgroundColor(Color.TRANSPARENT);
    }

    private static void addHomeBlueBar(Activity activity, ConstraintLayout screen) {
        if (screen.findViewWithTag("washlink_home_header_bar") != null) return;
        View backdrop = new View(activity);
        backdrop.setTag("washlink_home_header_bar");
        backdrop.setBackgroundColor(BLUE);
        ConstraintLayout.LayoutParams params = new ConstraintLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 70));
        params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
        params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
        params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
        screen.addView(backdrop, 0, params);
    }

    private static void styleScreenHeader(Activity activity, View screen, View title) {
        View toolbar = findToolbarGroup(activity, screen, title);
        if (toolbar == null) return;

        if (toolbar instanceof ConstraintLayout) {
            View anchor = findView(activity, toolbar, "btn_back", "btn_provider_menu",
                    "btn_chat_back", "btn_chat_inbox_back", "btn_rider_back");
            if (anchor == null) anchor = title;
            addBlueBackdrop(activity, (ConstraintLayout) toolbar, anchor,
                    "washlink_screen_header_bar");
            styleConstraintHeaderChildren(activity, (ViewGroup) toolbar, anchor, title);
        } else if (toolbar.getParent() instanceof ConstraintLayout
                && toolbar.getId() != View.NO_ID) {
            View anchor = findView(activity, toolbar, "btn_back", "btn_provider_menu",
                    "btn_chat_back", "btn_chat_inbox_back", "btn_rider_back");
            if (anchor == null) anchor = toolbar;
            addBlueBackdrop(activity, (ConstraintLayout) toolbar.getParent(), anchor,
                    "washlink_screen_header_bar");
            toolbar.setBackgroundColor(Color.TRANSPARENT);
            styleHeaderTree(toolbar);
        } else if (toolbar.getParent() instanceof LinearLayout) {
            ViewGroup.MarginLayoutParams params =
                    (ViewGroup.MarginLayoutParams) toolbar.getLayoutParams();
            int left = params.leftMargin;
            int right = params.rightMargin;
            params.setMargins(0, params.topMargin, 0, params.bottomMargin);
            toolbar.setLayoutParams(params);
            toolbar.setPadding(toolbar.getPaddingLeft() + left, toolbar.getPaddingTop(),
                    toolbar.getPaddingRight() + right, toolbar.getPaddingBottom());
            toolbar.setBackgroundColor(BLUE);
            styleHeaderTree(toolbar);
        } else {
            toolbar.setBackgroundColor(BLUE);
            styleHeaderTree(toolbar);
        }
    }

    private static View findToolbarGroup(Activity activity, View screen, View title) {
        View knownToolbar = findView(activity, screen, "provider_dashboard_header");
        if (knownToolbar != null) return knownToolbar;
        if (title.getParent() instanceof ConstraintLayout) return (View) title.getParent();

        View directChild = title;
        while (directChild.getParent() instanceof View
                && directChild.getParent() != screen) {
            directChild = (View) directChild.getParent();
        }
        if (directChild.getParent() == screen && directChild instanceof LinearLayout) {
            return directChild;
        }
        if (title.getParent() instanceof ViewGroup) return (View) title.getParent();
        return null;
    }

    private static void addBlueBackdrop(Activity activity, ConstraintLayout parent,
                                        View bottomAnchor, String tag) {
        if (parent.findViewWithTag(tag) != null || bottomAnchor.getId() == View.NO_ID) return;
        View backdrop = new View(activity);
        backdrop.setTag(tag);
        backdrop.setBackgroundColor(BLUE);
        ConstraintLayout.LayoutParams params = new ConstraintLayout.LayoutParams(0, 0);
        params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
        params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
        params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
        params.bottomToBottom = bottomAnchor.getId();
        params.topMargin = -parent.getPaddingTop();
        params.leftMargin = -parent.getPaddingLeft();
        params.rightMargin = -parent.getPaddingRight();
        parent.addView(backdrop, 0, params);
    }

    private static void styleConstraintHeaderChildren(Activity activity, ViewGroup parent,
                                                       View anchor, View title) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child.getTag() instanceof String
                    && ((String) child.getTag()).startsWith("washlink_")) continue;
            String name = resourceName(activity, child);
            if (child == title || isTitle(name)) {
                if (child instanceof TextView) styleHeaderText((TextView) child, Color.WHITE);
            } else if (isHeaderIconName(name)) {
                styleHeaderIcon(child);
            }
        }
        if (anchor instanceof ImageView) ((ImageView) anchor).setColorFilter(Color.WHITE);
    }

    private static void styleHeaderTree(View view) {
        if (view instanceof TextView) {
            styleHeaderText((TextView) view, Color.WHITE);
        } else if (view instanceof ImageView) {
            ((ImageView) view).setColorFilter(Color.WHITE);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                styleHeaderTree(group.getChildAt(i));
            }
        }
    }

    private static void styleHeaderIcon(View view) {
        if (view instanceof ImageView) {
            ((ImageView) view).setColorFilter(Color.WHITE);
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                styleHeaderIcon(group.getChildAt(i));
            }
        }
    }

    private static void styleBottomNavigation(View navigation) {
        navigation.setBackgroundColor(BLUE);
        styleNavigationTree(navigation);
    }

    private static void styleNavigationTree(View view) {
        if (view instanceof ImageView) {
            ((ImageView) view).setColorFilter(Color.WHITE);
        } else if (view instanceof TextView) {
            ((TextView) view).setTextColor(Color.WHITE);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                styleNavigationTree(group.getChildAt(i));
            }
        }
    }

    private static void styleHeaderText(TextView view, int color) {
        view.setTextColor(color);
    }

    private static boolean isTitle(String resourceName) {
        return resourceName.equals("tv_header_title")
                || resourceName.equals("tv_chat_title")
                || resourceName.equals("tv_notifications_title")
                || resourceName.equals("tv_rider_title")
                || resourceName.equals("tv_title")
                || resourceName.equals("tv_edit");
    }

    private static boolean isHeaderIconName(String resourceName) {
        return resourceName.contains("back") || resourceName.contains("menu")
                || resourceName.contains("bell") || resourceName.contains("chat");
    }

    private static View findView(Activity activity, View root, String... names) {
        for (String name : names) {
            int id = activity.getResources().getIdentifier(name, "id",
                    activity.getPackageName());
            if (id != 0) {
                View view = root.findViewById(id);
                if (view != null) return view;
            }
        }
        return null;
    }

    private static String resourceName(Activity activity, View view) {
        if (view.getId() == View.NO_ID) return "";
        return activity.getResources().getResourceEntryName(view.getId());
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static final class StylingState {
        final ViewGroup content;
        View screen;
        View headerTitle;
        View bottomNav;

        StylingState(ViewGroup content) {
            this.content = content;
        }
    }
}
