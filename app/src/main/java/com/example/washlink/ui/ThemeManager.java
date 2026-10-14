package com.example.washlink.ui;

import android.content.Context;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Button;

import androidx.core.view.ViewCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.InsetDrawable;

import com.example.washlink.R;

public final class ThemeManager {
    private static final String PREFS = "washlink_preferences";
    public static final String ROLE_CUSTOMER = "customer";
    public static final String ROLE_PROVIDER = "provider";
    private static final String ACTIVE_ROLE = "active_role";
    private static final String SIGNED_IN = "signed_in";
    private static final String LEGACY_ACCENT = "accent";
    private static final String LEGACY_DARK_MODE = "dark_mode";
    private static final String MODE_LIGHT = "light";
    private static final String MODE_DARK = "dark";
    private static final String MODE_CREAM = "cream";
    private ThemeManager() { }

    public static int accent(Context context) {
        return accent(context, activeRole(context));
    }

    public static int accent(Context context, String role) {
        String name = accentName(context, role);
        if ("violet".equals(name)) return Color.rgb(124, 58, 237);
        if ("emerald".equals(name)) return Color.rgb(5, 150, 105);
        if ("amber".equals(name)) return Color.rgb(217, 119, 6);
        return Color.rgb(22, 82, 230);
    }

    public static String accentName(Context context) {
        return accentName(context, activeRole(context));
    }

    public static String accentName(Context context, String role) {
        String fallback = ROLE_CUSTOMER.equals(normalizeRole(role))
            ? legacyAccent(context) : "blue";
        return preferences(context).getString(accentKey(role), fallback);
    }

    public static void setAccent(Context context, String accent) {
        setAccent(context, activeRole(context), accent);
    }

    public static void setAccent(Context context, String role, String accent) {
        preferences(context).edit().putString(accentKey(role), accent).apply();
    }

    public static boolean isDarkMode(Context context) {
        return MODE_DARK.equals(themeMode(context, activeRole(context)));
    }

    public static boolean isDarkMode(Context context, String role) {
        return MODE_DARK.equals(themeMode(context, role));
    }

    public static void setDarkMode(Context context, boolean enabled) {
        setThemeMode(context, activeRole(context), enabled ? MODE_DARK : MODE_LIGHT);
    }

    public static String activeRole(Context context) {
        return normalizeRole(preferences(context).getString(ACTIVE_ROLE, ROLE_CUSTOMER));
    }

    public static void setActiveRole(Context context, String role) {
        rememberActiveRole(context, role);
        preferences(context).edit().putBoolean(SIGNED_IN, true).apply();
        applySavedNightMode(context);
    }

    public static void resetSignedOut(Context context) {
        preferences(context).edit()
                .putBoolean(SIGNED_IN, false)
                .putString(ACTIVE_ROLE, ROLE_CUSTOMER)
                .apply();
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
    }

    public static void rememberActiveRole(Context context, String role) {
        String normalized = normalizeRole(role);
        preferences(context).edit().putString(ACTIVE_ROLE, normalized).apply();
    }

    public static String themeMode(Context context, String role) {
        String normalized = normalizeRole(role);
        String key = themeKey(normalized);
        if (preferences(context).contains(key)) {
            return preferences(context).getString(key, MODE_LIGHT);
        }
        // Preserve existing customers' saved appearance when upgrading. Provider
        // preferences start independently with the comfortable light palette.
        if (ROLE_CUSTOMER.equals(normalized)
                && preferences(context).contains(LEGACY_DARK_MODE)) {
            return preferences(context).getBoolean(LEGACY_DARK_MODE, false)
                    ? MODE_DARK : MODE_LIGHT;
        }
        return MODE_LIGHT;
    }

    public static String themeLabel(Context context, String role) {
        String mode = themeMode(context, role);
        if (MODE_DARK.equals(mode)) return "Dark";
        if (MODE_CREAM.equals(mode)) return "Eye-friendly cream";
        return "Light";
    }

    public static void setThemeMode(Context context, String role, String mode) {
        if (!MODE_DARK.equals(mode) && !MODE_CREAM.equals(mode)) mode = MODE_LIGHT;
        preferences(context).edit().putString(themeKey(role), mode).apply();
    }

    public static void changeTheme(Activity activity, String role, String mode) {
        String oldMode = themeMode(activity, role);
        setThemeMode(activity, role, mode);
        boolean dark = MODE_DARK.equals(mode);
        if (MODE_DARK.equals(oldMode) != dark) {
            AppCompatDelegate.setDefaultNightMode(dark
                    ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        }
        activity.recreate();
    }

    public static void applySavedNightMode(Context context) {
        if (!preferences(context).getBoolean(SIGNED_IN, false)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            return;
        }
        AppCompatDelegate.setDefaultNightMode(MODE_DARK.equals(
                themeMode(context, activeRole(context)))
                ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
    }

    public static void prepareActivity(AppCompatActivity activity) {
        if (!preferences(activity).getBoolean(SIGNED_IN, false)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            return;
        }
        String mode = themeMode(activity, activeRole(activity));
        AppCompatDelegate.setDefaultNightMode(MODE_DARK.equals(mode)
                ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        if (MODE_CREAM.equals(mode)) {
            activity.setTheme(R.style.Theme_WashLink_Cream);
        }
    }

    public static void showAppearancePicker(Activity activity, String role) {
        String[] labels = {"Light", "Dark", "Eye-friendly cream"};
        String[] modes = {MODE_LIGHT, MODE_DARK, MODE_CREAM};
        String current = themeMode(activity, role);
        int checked = MODE_DARK.equals(current) ? 1 : MODE_CREAM.equals(current) ? 2 : 0;
        new AlertDialog.Builder(activity)
                .setTitle("Appearance")
                .setMessage("Choose a comfortable display. Cream uses a warm background with dark, high-contrast text.")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    String selected = modes[which];
                    changeTheme(activity, role, selected);
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String normalizeRole(String role) {
        return ROLE_PROVIDER.equals(role) ? ROLE_PROVIDER : ROLE_CUSTOMER;
    }

    private static String themeKey(String role) {
        return "theme_" + normalizeRole(role);
    }

    private static String accentKey(String role) {
        return "accent_" + normalizeRole(role);
    }

    private static String legacyAccent(Context context) {
        return preferences(context).getString(LEGACY_ACCENT, "blue");
    }

    public static void apply(View view, Context context) {
        boolean cream = MODE_CREAM.equals(themeMode(context, activeRole(context)));
        apply(view, context, cream);
        if (cream && context instanceof Activity) {
            Activity activity = (Activity) context;
            activity.getWindow().setStatusBarColor(
                context.getColor(R.color.cream_raised));
            View decor = activity.getWindow().getDecorView();
            decor.setSystemUiVisibility(decor.getSystemUiVisibility()
                | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }

    private static void apply(View view, Context context, boolean cream) {
        int blue = context.getColor(R.color.laundr_blue);
        int darkBlue = context.getColor(R.color.laundr_blue_dark);
        int accent = accent(context);
        String resourceName = "";
        try {
            if (view.getId() != View.NO_ID) {
                resourceName = context.getResources().getResourceEntryName(view.getId());
            }
        } catch (Resources.NotFoundException ignored) { }
        View promo = view.findViewById(R.id.promo_banner);
        if (promo != null) {
            GradientDrawable background = new GradientDrawable();
            background.setColor(accent);
            background.setCornerRadius(16 * context.getResources().getDisplayMetrics().density);
            promo.setBackground(background);
        }
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            int currentTextColor = text.getCurrentTextColor();
            if (MODE_DARK.equals(themeMode(context, activeRole(context)))
                    && currentTextColor == context.getColor(
                    R.color.white)) {
                text.setTextColor(context.getColor(R.color.on_accent));
            }
            if (cream) {
                if (currentTextColor == context.getColor(
                        R.color.text_primary)) {
                    text.setTextColor(context.getColor(
                            R.color.cream_text_primary));
                } else if (currentTextColor == context.getColor(
                        R.color.text_secondary)) {
                    text.setTextColor(context.getColor(
                            R.color.cream_text_secondary));
                }
            }
            if (currentTextColor == blue || currentTextColor == darkBlue) {
                text.setTextColor(accent);
            }
        }
        if (view instanceof Button && !resourceName.contains("logout")
                && !resourceName.contains("call_provider_details")
                && !resourceName.contains("chat_provider")
                && !resourceName.contains("whatsapp_provider")) {
            int buttonColor = accent;
            if (resourceName.equals("btn_book_service")) {
                buttonColor = Color.rgb(Math.max(0, Color.red(accent) - 35),
                        Math.max(0, Color.green(accent) - 35),
                        Math.max(0, Color.blue(accent) - 35));
            }
            ViewCompat.setBackgroundTintList(view, ColorStateList.valueOf(buttonColor));
            ((Button) view).setTextColor(Color.WHITE);
        }
        if (view instanceof ImageView && isThemedIcon(resourceName)) {
            ImageViewCompat.setImageTintList((ImageView) view,
                    ColorStateList.valueOf(accent));
        }
        if (view instanceof ImageView
            && MODE_DARK.equals(themeMode(context, activeRole(context)))) {
            ColorStateList currentTint = ImageViewCompat.getImageTintList((ImageView) view);
            if (currentTint != null && currentTint.getDefaultColor()
                == context.getColor(R.color.white)) {
            ImageViewCompat.setImageTintList((ImageView) view,
                ColorStateList.valueOf(context.getColor(
                    R.color.on_accent)));
            }
        }
        // Do not tint arbitrary images; photos and illustrations must retain their artwork.
        if (view.getBackground() instanceof ColorDrawable) {
            int backgroundColor = ((ColorDrawable) view.getBackground()).getColor();
            if (backgroundColor == blue || backgroundColor == darkBlue) {
                view.setBackgroundColor(accent);
            }
        }
        if (cream) recolorCreamBackground(view, context);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                apply(group.getChildAt(i), context, cream);
            }

        }
    }

    private static void recolorCreamBackground(View view, Context context) {
        Drawable background = view.getBackground();
        if (background == null) return;
        int white = context.getColor(R.color.white);
        int input = context.getColor(R.color.input_bg);
        int offer = context.getColor(R.color.offer_card_bg);
        int creamSurface = context.getColor(R.color.cream_surface);
        int creamRaised = context.getColor(R.color.cream_raised);
        recolorDrawable(background.mutate(), white, input, offer, creamSurface, creamRaised);
    }

    private static void recolorDrawable(Drawable drawable, int white, int input, int offer,
                                        int creamSurface, int creamRaised) {
        if (drawable instanceof ColorDrawable) {
            int color = ((ColorDrawable) drawable).getColor();
            if (color == white) ((ColorDrawable) drawable).setColor(creamSurface);
            else if (color == input || color == offer) {
                ((ColorDrawable) drawable).setColor(creamRaised);
            }
        } else if (drawable instanceof GradientDrawable) {
            GradientDrawable gradient = (GradientDrawable) drawable;
            ColorStateList colors = gradient.getColor();
            if (colors != null) {
                int color = colors.getDefaultColor();
                if (color == white) gradient.setColor(creamSurface);
                else if (color == input || color == offer) gradient.setColor(creamRaised);
            }
        } else if (drawable instanceof LayerDrawable) {
            LayerDrawable layers = (LayerDrawable) drawable;
            for (int i = 0; i < layers.getNumberOfLayers(); i++) {
                recolorDrawable(layers.getDrawable(i), white, input, offer,
                        creamSurface, creamRaised);
            }
        } else if (drawable instanceof InsetDrawable) {
            Drawable inner = ((InsetDrawable) drawable).getDrawable();
            if (inner != null) recolorDrawable(inner, white, input, offer,
                creamSurface, creamRaised);
        }
    }

    private static boolean isThemedIcon(String name) {
        if (name.contains("payment") || name.contains("card_chip")
                || name.contains("chip_card")
                || name.contains("airtel") || name.contains("mtn")
                || name.contains("cash")) {
            return false;
        }
        return name.contains("icon") || name.contains("nav_") || name.contains("bell")
                || name.contains("back") || name.contains("filter") || name.contains("provider")
                || name.contains("chevron") || name.contains("location")
                || name.contains("calendar") || name.contains("clock");
    }
}
