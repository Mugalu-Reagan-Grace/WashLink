package com.example.washlink.data;

import android.app.Activity;
import android.content.Intent;

import com.example.washlink.models.UserAccount;
import com.google.firebase.auth.FirebaseAuth;

public final class AuthGuard {

    private AuthGuard() {
    }

    public static void requireRole(Activity activity, String requiredRole, Class<?> fallback) {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            redirect(activity, fallback);
            return;
        }

        AuthRepository.getInstance().getCurrentSession(new AuthRepository.RoleCallback() {
            @Override
            public void onResult(UserAccount user) {
                if (user == null || !requiredRole.equals(user.getRole())) {
                    FirebaseAuth.getInstance().signOut();
                    redirect(activity, fallback);
                }
            }

            @Override
            public void onError(String message) {
                FirebaseAuth.getInstance().signOut();
                redirect(activity, fallback);
            }
        });
    }

    private static void redirect(Activity activity, Class<?> target) {
        Intent intent = new Intent(activity, target);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.finish();
    }
}
