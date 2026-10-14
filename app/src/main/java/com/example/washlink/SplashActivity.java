package com.example.washlink;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.example.washlink.data.AuthRepository;
import com.example.washlink.models.UserAccount;
import com.google.firebase.auth.FirebaseAuth;
import com.example.washlink.ui.customer.MainActivity;
import com.example.washlink.ui.ThemeManager;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_customer_splash);

        new Handler(Looper.getMainLooper()).postDelayed(this::goToNextScreen, 2000);
    }

    private void goToNextScreen() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            ThemeManager.resetSignedOut(this);
            openScreen(OnboardingActivity.class);
            return;
        }

        AuthRepository.getInstance().getCurrentSession(new AuthRepository.RoleCallback() {
            @Override
            public void onResult(UserAccount user) {
                if (user == null) {
                    ThemeManager.resetSignedOut(SplashActivity.this);
                    FirebaseAuth.getInstance().signOut();
                    openScreen(OnboardingActivity.class);
                    return;
                }

                Class<?> destination;
                if (UserAccount.ROLE_PROVIDER.equals(user.getRole())) {
                    ThemeManager.setActiveRole(SplashActivity.this, ThemeManager.ROLE_PROVIDER);
                    destination = ProviderDashboardActivity.class;
                } else if (UserAccount.ROLE_ADMIN.equals(user.getRole())) {
                    ThemeManager.setActiveRole(SplashActivity.this, ThemeManager.ROLE_CUSTOMER);
                    destination = AdminManagementActivity.class;
                } else if (UserAccount.ROLE_CUSTOMER.equals(user.getRole())) {
                    ThemeManager.setActiveRole(SplashActivity.this, ThemeManager.ROLE_CUSTOMER);
                    destination = MainActivity.class;
                } else {
                    ThemeManager.resetSignedOut(SplashActivity.this);
                    FirebaseAuth.getInstance().signOut();
                    openScreen(OnboardingActivity.class);
                    return;
                }
                openScreen(destination);
            }

            @Override
            public void onError(String message) {
                ThemeManager.resetSignedOut(SplashActivity.this);
                FirebaseAuth.getInstance().signOut();
                openScreen(OnboardingActivity.class);
            }
        });
    }

    private void openScreen(Class<?> targetActivity) {
        Intent intent = new Intent(SplashActivity.this, targetActivity);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
