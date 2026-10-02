package com.example.washlink.data;

import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;

public final class PushTokenManager {
    private static final String TAG = "PushTokenManager";

    private PushTokenManager() {}

    public static void registerCurrentUser() {
        FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(PushTokenManager::saveToken)
                .addOnFailureListener(error ->
                        Log.w(TAG, "Could not retrieve push notification token", error));
    }

    public static void saveToken(String token) {
        if (token == null || token.trim().isEmpty()) return;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        String uid = user.getUid();
        FirebaseFirestore.getInstance().collection("users").document(uid)
                .update("pushToken", token)
                .addOnFailureListener(error ->
                        Log.w(TAG, "Could not save push notification token", error));
    }
}
