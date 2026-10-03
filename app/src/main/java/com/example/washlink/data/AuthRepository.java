package com.example.washlink.data;

import androidx.annotation.NonNull;

import com.example.washlink.models.Provider;
import com.example.washlink.models.UserAccount;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
 * Firestore access is restricted by the repository's firestore.rules file;
 * deploy that rules file to Firebase when changing the backend configuration.
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
        registerProvider(businessName, ownerName, email, phone, address, password,
                0d, 0d, callback);
    }

    public void registerProvider(String businessName, String ownerName, String email,
                                  String phone, String address, String password,
                                  double latitude, double longitude,
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
                    provider.setLatitude(latitude);
                    provider.setLongitude(longitude);
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

    public void getCurrentUserAccount(AuthCallback callback) {
        FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser == null) {
            callback.onError("You are not signed in.");
            return;
        }
        db.collection("users").document(firebaseUser.getUid()).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        callback.onError("Account data not found.");
                        return;
                    }
                    UserAccount user = doc.toObject(UserAccount.class);
                    if (user == null) {
                        callback.onError("Account data not found.");
                    } else {
                        callback.onSuccess(user);
                    }
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage() == null
                        ? "Could not load account data." : e.getMessage()));
    }

    public void updateCustomerProfile(String name, String phone, String address,
                                      String photoUri, SimpleCallback callback) {
        updateCustomerProfile(name, phone, address, photoUri, null, callback);
    }

    public void updateCustomerProfile(String name, String phone, String address,
                                      String photoUri, List<String> savedAddresses,
                                      SimpleCallback callback) {
        FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser == null) {
            callback.onError("You are not signed in.");
            return;
        }
        Map<String, Object> updates = new HashMap<>();
        updates.put("name", name);
        updates.put("phone", phone);
        updates.put("address", address);
        updates.put("photoUri", photoUri);
        if (savedAddresses != null) {
            updates.put("savedAddresses", new ArrayList<>(savedAddresses));
        }
        db.collection("users").document(firebaseUser.getUid()).update(updates)
                .addOnSuccessListener(unused -> {
                    firebaseUser.updateProfile(new UserProfileChangeRequest.Builder()
                            .setDisplayName(name)
                            .build())
                            .addOnSuccessListener(profileUpdate -> callback.onSuccess())
                            .addOnFailureListener(e -> callback.onError(
                                    "Your account details were saved, but the sign-in profile "
                                            + "could not be synchronized: " + e.getMessage()));
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void updatePhoneNumber(String uid, String phone, SimpleCallback callback) {
        if (uid == null || uid.trim().isEmpty()) {
            callback.onError("Account identifier is missing.");
            return;
        }
        db.collection("users").document(uid)
                .update("phone", phone)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(
                        e.getMessage() == null ? "Could not save your phone number." : e.getMessage()));
    }

    public void addSavedAddress(String address, SimpleCallback callback) {
        FirebaseUser firebaseUser = auth.getCurrentUser();
        String normalizedAddress = address == null ? "" : address.trim();
        if (firebaseUser == null) {
            callback.onError("You are not signed in.");
            return;
        }
        if (normalizedAddress.isEmpty()) {
            callback.onError("Address cannot be empty.");
            return;
        }
        com.google.firebase.firestore.DocumentReference userRef =
                db.collection("users").document(firebaseUser.getUid());
        db.runTransaction(transaction -> {
            com.google.firebase.firestore.DocumentSnapshot snapshot = transaction.get(userRef);
            if (!snapshot.exists()
                    || !UserAccount.ROLE_CUSTOMER.equals(snapshot.getString("role"))) {
                throw new IllegalStateException("Customer account data not found.");
            }
            List<String> addresses = normalizedAddresses(snapshot.get("savedAddresses"));
            addUniqueAddress(addresses, normalizedAddress);
            Map<String, Object> updates = new HashMap<>();
            updates.put("savedAddresses", addresses);
            String primary = snapshot.getString("address");
            if (primary == null || primary.trim().isEmpty()) {
                updates.put("address", normalizedAddress);
            }
            transaction.update(userRef, updates);
            return null;
        }).addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(e.getMessage() == null
                        ? "Could not save the address." : e.getMessage()));
    }

    public void updateCustomerAddresses(List<String> savedAddresses, String primaryAddress,
                                        SimpleCallback callback) {
        FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser == null) {
            callback.onError("You are not signed in.");
            return;
        }
        List<String> normalized = normalizedAddresses(savedAddresses);
        String primary = primaryAddress == null ? "" : primaryAddress.trim();
        if (!primary.isEmpty()) addUniqueAddress(normalized, primary);
        com.google.firebase.firestore.DocumentReference userRef =
                db.collection("users").document(firebaseUser.getUid());
        db.runTransaction(transaction -> {
            com.google.firebase.firestore.DocumentSnapshot snapshot = transaction.get(userRef);
            if (!snapshot.exists()
                    || !UserAccount.ROLE_CUSTOMER.equals(snapshot.getString("role"))) {
                throw new IllegalStateException("Customer account data not found.");
            }
            Map<String, Object> updates = new HashMap<>();
            updates.put("savedAddresses", normalized);
            updates.put("address", primary);
            transaction.update(userRef, updates);
            return null;
        }).addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(e.getMessage() == null
                        ? "Could not save your addresses." : e.getMessage()));
    }

    private List<String> normalizedAddresses(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof List<?>) {
            for (Object item : (List<?>) value) {
                if (item instanceof String) addUniqueAddress(result, (String) item);
            }
        }
        return result;
    }

    private void addUniqueAddress(List<String> addresses, String value) {
        if (value == null || value.trim().isEmpty()) return;
        String normalized = value.trim();
        for (String address : addresses) {
            if (normalized.equalsIgnoreCase(address)) return;
        }
        addresses.add(normalized);
    }

    public void sendPasswordReset(SimpleCallback callback) {
        FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser == null || firebaseUser.getEmail() == null) {
            callback.onError("No email address is associated with this account.");
            return;
        }
        auth.sendPasswordResetEmail(firebaseUser.getEmail())
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void linkPhoneCredential(AuthCredential credential, SimpleCallback callback) {
        FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser == null) {
            callback.onError("Account creation session expired.");
            return;
        }
        firebaseUser.linkWithCredential(credential)
                .addOnSuccessListener(result -> callback.onSuccess())
                .addOnFailureListener(e -> {
                    if (e instanceof FirebaseAuthUserCollisionException) {
                        callback.onError("This phone number is already linked to another account.");
                    } else {
                        callback.onError(e.getMessage());
                    }
                });
    }

    public void deleteCurrentCustomerProfile(SimpleCallback callback) {
        String uid = getCurrentUserId();
        if (uid == null) {
            callback.onSuccess();
            return;
        }
        db.collection("users").document(uid).delete()
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void completeCustomerRegistration(String name, String email, String phone,
                                             String password, AuthCredential phoneCredential,
                                             AuthCallback callback) {
        if (phoneCredential == null) {
            callback.onError("Phone verification is incomplete. Verify your phone number first.");
            return;
        }

        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    FirebaseUser firebaseUser = result.getUser();
                    if (firebaseUser == null) {
                        callback.onError("Account creation failed unexpectedly.");
                        return;
                    }

                    firebaseUser.updateProfile(new UserProfileChangeRequest.Builder()
                            .setDisplayName(name)
                            .build());

                    firebaseUser.linkWithCredential(phoneCredential)
                            .addOnSuccessListener(linkResult -> {
                                UserAccount user = new UserAccount(
                                        firebaseUser.getUid(), name, email, phone,
                                        UserAccount.ROLE_CUSTOMER);
                                firebaseUser.sendEmailVerification();

                                db.collection("users").document(user.getUid()).set(user)
                                        .addOnSuccessListener(unused -> callback.onSuccess(user))
                                        .addOnFailureListener(e -> cleanUpFailedRegistration(
                                                firebaseUser, user.getUid(), "Failed to save your account profile.", callback));
                            })
                            .addOnFailureListener(e -> {
                                String message = e instanceof FirebaseAuthUserCollisionException
                                        ? "This phone number is already linked to another account."
                                        : e.getMessage();
                                cleanUpFailedRegistration(firebaseUser, firebaseUser.getUid(), message,
                                        callback);
                            });
                })
                .addOnFailureListener(e -> {
                    if (e instanceof FirebaseAuthUserCollisionException) {
                        callback.onError("An account with this email already exists.");
                    } else {
                        callback.onError(e.getMessage());
                    }
                });
    }

    private void cleanUpFailedRegistration(FirebaseUser firebaseUser, String uid,
                                           String message, AuthCallback callback) {
        if (uid != null) {
            db.collection("users").document(uid).delete();
        }
        if (firebaseUser != null) {
            firebaseUser.delete();
        }
        callback.onError(message);
    }

    public String getCurrentUserId() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    public boolean isLoggedIn() {
        return auth.getCurrentUser() != null;
    }
}