package com.example.washlink;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.washlink.data.AuthRepository;
import com.example.washlink.models.UserAccount;
import com.google.firebase.auth.FirebaseAuth;
import com.example.washlink.ui.customer.MainActivity;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_splash);

        new Handler(Looper.getMainLooper()).postDelayed(this::goToNextScreen, 2000);
    }

    private void goToNextScreen() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            openScreen(OnboardingActivity.class);
            return;
        }

        AuthRepository.getInstance().getCurrentSession(new AuthRepository.RoleCallback() {
            @Override
            public void onResult(UserAccount user) {
                if (user == null) {
                    FirebaseAuth.getInstance().signOut();
                    openScreen(OnboardingActivity.class);
                    return;
                }

                Class<?> destination;
                if (UserAccount.ROLE_PROVIDER.equals(user.getRole())) {
                    destination = ProviderDashboardActivity.class;
                } else if (UserAccount.ROLE_ADMIN.equals(user.getRole())) {
                    destination = AdminManagementActivity.class;
                } else if (UserAccount.ROLE_CUSTOMER.equals(user.getRole())) {
                    destination = MainActivity.class;
                } else {
                    FirebaseAuth.getInstance().signOut();
                    openScreen(OnboardingActivity.class);
                    return;
                }
                openScreen(destination);
            }

            @Override
            public void onError(String message) {
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
