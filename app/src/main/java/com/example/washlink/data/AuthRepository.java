package com.example.washlink.data;

import androidx.annotation.NonNull;

import com.example.washlink.models.Provider;
import com.example.washlink.models.UserAccount;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * Wraps Firebase Auth + Firestore for everything account-related.
 *
 * WHY ONE SHARED REPOSITORY INSTEAD OF PER-SCREEN LOGIC:
 * Customer and provider accounts both live in Firebase Auth's single user
 * pool - the only thing distinguishing "customer" from "provider" is the
 * `role` field on their users/{uid} Firestore document. If every Activity
 * queried Firestore itself, role-checking logic (and any bug in it) would
 * be duplicated 4+ times. Here, it exists exactly once.
 *
 * FIRESTORE SCHEMA THIS CREATES:
 *   users/{uid}              -> UserAccount (role: "customer" | "provider" | "admin")
 *   providers/{uid}          -> Provider (only created when role == "provider";
 *                                uid matches the same uid as their UserAccount,
 *                                so looking up "this user's business" is a direct
 *                                document read, not a query)
 *
 * SECURITY RULES YOU STILL NEED TO WRITE (not covered by this file):
 * Firestore's default "test mode" allows anyone to read/write anything for 30
 * days, which is NOT safe to submit or ship. Before that window closes, add
 * rules roughly like:
 *   - users/{uid}: only that uid (or an admin) can read/write their own doc
 *   - providers/{uid}: anyone signed in can READ (customers browse providers),
 *     only that provider's uid can WRITE their own doc
 *   - bookings/{id}: only the booking's customerId or providerId can read/write it
 * This file only handles the client-side logic; rules are configured in the
 * Firebase console (Firestore Database > Rules) or a rules file, not in Java.
 */
public class AuthRepository {

    private static AuthRepository instance;

    private final FirebaseAuth auth;
    private final FirebaseFirestore db;

    public interface AuthCallback {
        void onSuccess(UserAccount user);
        void onError(String message);
    }

    public interface RoleCallback {
        void onResult(UserAccount user); // user is null if nobody is logged in
        void onError(String message);
    }

    public interface SimpleCallback {
        void onSuccess();
        void onError(String message);
    }

    private AuthRepository() {
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
    }

    public static synchronized AuthRepository getInstance() {
        if (instance == null) {
            instance = new AuthRepository();
        }
        return instance;
    }

    // ------------------------------------------------------------------
    // Session check - this is what SplashActivity calls to decide whether
    // to route to Onboarding, customer Home, or the provider Dashboard.
    // ------------------------------------------------------------------
    public void getCurrentSession(RoleCallback callback) {
        FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser == null) {
            callback.onResult(null);
            return;
        }
        db.collection("users").document(firebaseUser.getUid()).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        UserAccount user = doc.toObject(UserAccount.class);
                        callback.onResult(user);
                    } else {
                        // Auth account exists but Firestore doc doesn't - treat as logged out
                        // rather than crashing on a null role downstream.
                        callback.onResult(null);
                    }
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // ------------------------------------------------------------------
    // Customer registration (Create Account screen)
    // ------------------------------------------------------------------
    public void registerCustomer(String name, String email, String phone, String password,
                                  AuthCallback callback) {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    FirebaseUser firebaseUser = result.getUser();
                    if (firebaseUser == null) {
                        callback.onError("Account creation failed unexpectedly.");
                        return;
                    }
                    UserAccount user = new UserAccount(
                            firebaseUser.getUid(), name, email, phone, UserAccount.ROLE_CUSTOMER);
                    UserProfileChangeRequest profile = new UserProfileChangeRequest.Builder()
                            .setDisplayName(name)
                            .build();
                    firebaseUser.updateProfile(profile);
                    firebaseUser.sendEmailVerification();
                    db.collection("users").document(user.getUid()).set(user)
                            .addOnSuccessListener(unused -> callback.onSuccess(user))
                            .addOnFailureListener(e -> callback.onError(e.getMessage()));
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // ------------------------------------------------------------------
    // Provider registration (Provider Registration screen)
    // Creates BOTH the users/{uid} doc (role=provider) AND the
    // providers/{uid} business profile doc in one flow.
    // ------------------------------------------------------------------
    public void registerProvider(String businessName, String ownerName, String email,
                                  String phone, String address, String password,
                                  AuthCallback callback) {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    FirebaseUser firebaseUser = result.getUser();
                    if (firebaseUser == null) {
                        callback.onError("Account creation failed unexpectedly.");
                        return;
                    }
                    String uid = firebaseUser.getUid();
                    UserAccount user = new UserAccount(
                            uid, ownerName, email, phone, UserAccount.ROLE_PROVIDER);
                    Provider provider = new Provider(uid, businessName, ownerName, email, phone, address);
                    firebaseUser.sendEmailVerification();

                    Map<String, Object> userDoc = new HashMap<>();
                    userDoc.put("uid", user.getUid());
                    userDoc.put("name", user.getName());
                    userDoc.put("email", user.getEmail());
                    userDoc.put("phone", user.getPhone());
                    userDoc.put("role", user.getRole());
                    userDoc.put("createdAt", user.getCreatedAt());

                    // Write both documents. If the provider doc write fails after the user
                    // doc succeeds, the account still exists but without a business profile -
                    // acceptable for now, but worth reconciling with a Cloud Function later
                    // rather than leaving it as a silent partial failure.
                    db.collection("users").document(uid).set(userDoc)
                            .addOnSuccessListener(unused ->
                                    db.collection("providers").document(uid).set(provider)
                                            .addOnSuccessListener(unused2 -> callback.onSuccess(user))
                                            .addOnFailureListener(e -> callback.onError(e.getMessage())))
                            .addOnFailureListener(e -> callback.onError(e.getMessage()));
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // ------------------------------------------------------------------
    // Login - shared by BOTH customer Sign In and Provider Login screens.
    // The role check happens here, not in the UI, so a customer account
    // can be blocked from the provider login screen (and vice versa) in
    // exactly one place.
    // ------------------------------------------------------------------
    public void login(String email, String password, String requiredRole, AuthCallback callback) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    FirebaseUser firebaseUser = result.getUser();
                    if (firebaseUser == null) {
                        callback.onError("Sign in failed unexpectedly.");
                        return;
                    }
                    if (!firebaseUser.isEmailVerified()) {
                        auth.signOut();
                        callback.onError("Please verify your email before signing in.");
                        return;
                    }
                    db.collection("users").document(firebaseUser.getUid()).get()
                            .addOnSuccessListener(doc -> {
                                if (!doc.exists()) {
                                    auth.signOut();
                                    callback.onError("Account data not found.");
                                    return;
                                }
                                UserAccount user = doc.toObject(UserAccount.class);
                                if (user == null || user.getRole() == null) {
                                    auth.signOut();
                                    callback.onError("Account role is not configured.");
                                    return;
                                }
                                if (requiredRole != null
                                        && !requiredRole.equals(user.getRole())) {
                                    // e.g. a customer trying to sign in on the Provider Login screen
                                    auth.signOut();
                                    callback.onError("This account is not registered as a "
                                            + requiredRole + ".");
                                    return;
                                }
                                callback.onSuccess(user);
                            })
                            .addOnFailureListener(e -> callback.onError(e.getMessage()));
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void ensureGoogleCustomer(FirebaseUser firebaseUser, AuthCallback callback) {
        if (firebaseUser == null) {
            callback.onError("Google sign-in failed unexpectedly.");
            return;
        }

        db.collection("users").document(firebaseUser.getUid()).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        UserAccount user = doc.toObject(UserAccount.class);
                        if (user == null || !UserAccount.ROLE_CUSTOMER.equals(user.getRole())) {
                            auth.signOut();
                            callback.onError("This account is not registered as a customer.");
                            return;
                        }
                        callback.onSuccess(user);
                        return;
                    }

                    String name = firebaseUser.getDisplayName() == null
                            ? "" : firebaseUser.getDisplayName();
                    String email = firebaseUser.getEmail() == null
                            ? "" : firebaseUser.getEmail();
                    UserAccount user = new UserAccount(
                            firebaseUser.getUid(), name, email, "", UserAccount.ROLE_CUSTOMER);
                    db.collection("users").document(user.getUid()).set(user)
                            .addOnSuccessListener(unused -> callback.onSuccess(user))
                            .addOnFailureListener(e -> callback.onError(e.getMessage()));
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void logout() {
        auth.signOut();
    }

    public String getCurrentUserId() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    public boolean isLoggedIn() {
        return auth.getCurrentUser() != null;
    }
}