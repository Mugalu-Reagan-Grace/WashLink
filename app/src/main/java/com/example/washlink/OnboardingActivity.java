package com.example.washlink;

import android.content.Intent;
import android.os.Bundle;
import com.example.washlink.ui.customer.MainActivity;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class OnboardingActivity extends AppCompatActivity {

    private Button btn_get_started;
    private Button btn_sign_in;
    private ImageView heroImage;
    private TextView title;
    private TextView subtitle;
    private View indicatorOne;
    private View indicatorTwo;
    private int currentPage;
    private float touchStartX;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_onboarding);

        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            Intent intent = new Intent(OnboardingActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        btn_get_started = findViewById(R.id.btn_get_started);
        btn_sign_in = findViewById(R.id.btn_sign_in);
        heroImage = findViewById(R.id.iv_hero);
        title = findViewById(R.id.tv_title);
        subtitle = findViewById(R.id.tv_subtitle);
        indicatorOne = findViewById(R.id.indicator_one);
        indicatorTwo = findViewById(R.id.indicator_two);

        btn_get_started.setOnClickListener(v -> getStartedButtonClicked());
        btn_sign_in.setOnClickListener(v -> signInButtonClicked());
        View.OnTouchListener swipeListener = (v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                touchStartX = event.getX();
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                float distance = event.getX() - touchStartX;
                if (Math.abs(distance) > 80) {
                    showPage(distance < 0 ? 1 : 0);
                }
                return true;
            }
            return true;
        };
        heroImage.setOnTouchListener(swipeListener);
        indicatorOne.setOnClickListener(v -> showPage(0));
        indicatorTwo.setOnClickListener(v -> showPage(1));
    }

    private void showPage(int page) {
        currentPage = page;
        boolean secondPage = page == 1;
        heroImage.animate()
                .alpha(0f)
                .setDuration(120)
                .withEndAction(() -> {
                    heroImage.setImageResource(secondPage
                            ? R.drawable.img_onboarding_hero
                            : R.drawable.man_carrying_clothes_one_hand);
                    title.setText(secondPage
                            ? R.string.onboarding_slide_two_title
                            : R.string.onboarding_title);
                    subtitle.setText(secondPage
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
    }

    private void getStartedButtonClicked() {
        Intent intent = new Intent(OnboardingActivity.this, CreateAccountActivity.class);
        startActivity(intent);
    }

    private void signInButtonClicked() {
        Intent intent = new Intent(OnboardingActivity.this, SignInActivity.class);
        startActivity(intent);
    }
}
