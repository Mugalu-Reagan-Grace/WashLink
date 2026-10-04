package com.example.washlink;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class OnboardingActivity extends AppCompatActivity {

    public static final String EXTRA_SHOW_ONBOARDING = "extra_show_onboarding";

    private Button btn_get_started;
    private Button btn_onboarding_next;
    private TextView btn_sign_in;
    private View btn_onboarding_skip;
    private ImageView heroImage;
    private TextView title;
    private TextView subtitle;
    private View indicatorOne;
    private View indicatorTwo;
    private View indicatorThree;
    private View indicatorFour;
    private WebView trackingAnimation;
    private WebView deliveryAnimation;
    private WebView locationAnimation;
    private int currentPage;
    private float touchStartX;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_onboarding);

        boolean forceShowOnboarding = getIntent() != null && getIntent().getBooleanExtra(EXTRA_SHOW_ONBOARDING, false);
        if (FirebaseAuth.getInstance().getCurrentUser() != null && !forceShowOnboarding) {
            Intent intent = new Intent(OnboardingActivity.this, SplashActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        btn_get_started = findViewById(R.id.btn_get_started);
        btn_onboarding_next = findViewById(R.id.btn_onboarding_next);
        btn_sign_in = findViewById(R.id.btn_sign_in);
        btn_onboarding_skip = findViewById(R.id.btn_onboarding_skip);
        View providerSignup = findViewById(R.id.btn_provider_signup);
        View providerSignIn = findViewById(R.id.btn_provider_sign_in);
        heroImage = findViewById(R.id.iv_hero);
        title = findViewById(R.id.tv_title);
        subtitle = findViewById(R.id.tv_subtitle);
        indicatorOne = findViewById(R.id.indicator_one);
        indicatorTwo = findViewById(R.id.indicator_two);
        indicatorThree = findViewById(R.id.indicator_three);
        indicatorFour = findViewById(R.id.indicator_four);
        trackingAnimation = findViewById(R.id.tracking_animation);
        deliveryAnimation = findViewById(R.id.delivery_animation);
        locationAnimation = findViewById(R.id.location_animation);
        configureTrackingAnimation();
        configureDeliveryAnimation();
        configureLocationAnimation();

        btn_get_started.setOnClickListener(v -> getStartedButtonClicked());
        btn_onboarding_next.setOnClickListener(v -> showPage(currentPage + 1));
        btn_onboarding_skip.setOnClickListener(v -> showPage(3));
        btn_sign_in.setOnClickListener(v -> signInButtonClicked());
        providerSignup.setOnClickListener(v ->
                startActivity(new Intent(this, ProviderRegistrationActivity.class)));
        providerSignIn.setOnClickListener(v ->
                startActivity(new Intent(this, ProviderLoginActivity.class)));
        View.OnTouchListener swipeListener = (v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                touchStartX = event.getX();
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                float distance = event.getX() - touchStartX;
                if (Math.abs(distance) > 80) {
                    showPage(distance < 0 ? currentPage + 1 : currentPage - 1);
                }
                return true;
            }
            return true;
        };
        heroImage.setOnTouchListener(swipeListener);
        trackingAnimation.setOnTouchListener(swipeListener);
        deliveryAnimation.setOnTouchListener(swipeListener);
        locationAnimation.setOnTouchListener(swipeListener);
        indicatorOne.setOnClickListener(v -> showPage(0));
        indicatorTwo.setOnClickListener(v -> showPage(1));
        indicatorThree.setOnClickListener(v -> showPage(2));
        indicatorFour.setOnClickListener(v -> showPage(3));
        showPage(currentPage);
    }

    private void showPage(int page) {
        currentPage = Math.max(0, Math.min(page, 3));
        boolean secondPage = currentPage == 1;
        boolean thirdPage = currentPage == 2;
        boolean fourthPage = currentPage == 3;
        heroImage.setVisibility(secondPage || thirdPage || fourthPage ? View.GONE : View.VISIBLE);
        deliveryAnimation.setVisibility(secondPage ? View.VISIBLE : View.GONE);
        trackingAnimation.setVisibility(thirdPage ? View.VISIBLE : View.GONE);
        locationAnimation.setVisibility(fourthPage ? View.VISIBLE : View.GONE);
        if (secondPage) {
            deliveryAnimation.reload();
        }
        if (thirdPage) {
            trackingAnimation.reload();
        }
        if (fourthPage) {
            locationAnimation.reload();
        }
        heroImage.animate()
                .alpha(0f)
                .setDuration(120)
                .withEndAction(() -> {
                    if (!secondPage && !thirdPage && !fourthPage) {
                        heroImage.setImageResource(R.drawable.man_carrying_clothes_one_hand);
                    }
                    title.setText(fourthPage
                            ? R.string.onboarding_slide_four_title
                            : thirdPage
                            ? R.string.onboarding_slide_three_title
                            : secondPage
                            ? R.string.onboarding_slide_two_title
                            : R.string.onboarding_title);
                    subtitle.setText(fourthPage
                            ? R.string.onboarding_slide_four_subtitle
                            : thirdPage
                            ? R.string.onboarding_slide_three_subtitle
                            : secondPage
                            ? R.string.onboarding_slide_two_subtitle
                            : R.string.onboarding_subtitle);
                    heroImage.animate()
                            .alpha(1f)
                            .setDuration(180)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                }).start();
        indicatorOne.setBackgroundResource(secondPage
                ? R.drawable.dot_inactive_indicator : R.drawable.dot_active_indicator);
        indicatorTwo.setBackgroundResource(secondPage
                ? R.drawable.dot_active_indicator : R.drawable.dot_inactive_indicator);
        indicatorThree.setBackgroundResource(thirdPage
                ? R.drawable.dot_active_indicator : R.drawable.dot_inactive_indicator);
        indicatorFour.setBackgroundResource(fourthPage
                ? R.drawable.dot_active_indicator : R.drawable.dot_inactive_indicator);

        int accountActionVisibility = fourthPage ? View.VISIBLE : View.GONE;
        btn_onboarding_next.setVisibility(fourthPage ? View.GONE : View.VISIBLE);
        btn_onboarding_skip.setVisibility(fourthPage ? View.GONE : View.VISIBLE);
        btn_get_started.setVisibility(accountActionVisibility);
        findViewById(R.id.btn_provider_signup).setVisibility(accountActionVisibility);
        btn_sign_in.setVisibility(accountActionVisibility);
        findViewById(R.id.btn_provider_sign_in).setVisibility(accountActionVisibility);
    }

    private void configureTrackingAnimation() {
        WebSettings settings = trackingAnimation.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        trackingAnimation.setBackgroundColor(Color.TRANSPARENT);
        trackingAnimation.loadUrl("file:///android_asset/onboarding_tracking.svg");
    }

    private void configureDeliveryAnimation() {
        WebSettings settings = deliveryAnimation.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        deliveryAnimation.setBackgroundColor(Color.TRANSPARENT);
        deliveryAnimation.loadUrl("file:///android_asset/onboarding_delivered.svg");
    }

    private void configureLocationAnimation() {
        WebSettings settings = locationAnimation.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        locationAnimation.setBackgroundColor(Color.TRANSPARENT);
        locationAnimation.loadUrl("file:///android_asset/onboarding_location_review.svg");
    }

    private void getStartedButtonClicked() {
        Intent intent = new Intent(OnboardingActivity.this, CreateAccountActivity.class);
        startActivity(intent);
        finish();
    }

    private void signInButtonClicked() {
        Intent intent = new Intent(OnboardingActivity.this, SignInActivity.class);
        startActivity(intent);
        finish();
    }
}
