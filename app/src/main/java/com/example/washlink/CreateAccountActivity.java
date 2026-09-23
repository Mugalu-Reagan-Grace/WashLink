package com.example.washlink;

import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.washlink.data.AuthRepository;
import com.example.washlink.ui.customer.MainActivity;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.FirebaseAuthMissingActivityForRecaptchaException;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.FirebaseException;
import com.google.firebase.FirebaseTooManyRequestsException;

import java.util.concurrent.TimeUnit;

public class CreateAccountActivity extends AppCompatActivity {

    private static final String TAG = "CreateAccountActivity";
    private static final int RC_SIGN_IN = 9001;

    private EditText nameEt;
    private EditText phoneEt;
    private EditText emailEt;
    private EditText passwordEt;
    private EditText confirmPasswordEt;
    private CheckBox termsCheckBox;

    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;
    private String verificationId;
    private PhoneAuthProvider.ForceResendingToken resendToken;
    private PhoneAuthCredential verifiedPhoneCredential;
    private boolean phoneVerificationInProgress;
    private MaterialButton signUpButton;
    private MaterialButton verifyPhoneButton;
    private MaterialButton resendPhoneButton;

    private static final String STATE_VERIFICATION_ID = "verification_id";
    private static final String STATE_VERIFICATION_IN_PROGRESS = "phone_verification_in_progress";
    private static final String STATE_PHONE_NUMBER = "verification_phone_number";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_create_account);

        mAuth = FirebaseAuth.getInstance();

        nameEt = findViewById(R.id.et_name_reg);
        phoneEt = findViewById(R.id.et_phone_reg);
        emailEt = findViewById(R.id.et_email_reg);
        passwordEt = findViewById(R.id.et_password_reg);
        confirmPasswordEt = findViewById(R.id.et_confirm_password_reg);
        termsCheckBox = findViewById(R.id.checkbox_terms);
        signUpButton = findViewById(R.id.btn_sign_up);
        verifyPhoneButton = findViewById(R.id.btn_verify_phone);
        resendPhoneButton = findViewById(R.id.btn_resend_phone);
        View googleButton = findViewById(R.id.btn_google_outlined);

        signUpButton.setOnClickListener(v -> signUpButtonClicked());
        verifyPhoneButton.setOnClickListener(v -> {
            if (verificationId == null) {
                verifyPhone();
            } else {
                verifyPhoneCode();
                verifyPhoneButton.setText(R.string.verify_phone_code);
            }
        });
        resendPhoneButton.setOnClickListener(v -> verifyPhone(true));

        if (savedInstanceState != null) {
            verificationId = savedInstanceState.getString(STATE_VERIFICATION_ID);
            String savedPhone = savedInstanceState.getString(STATE_PHONE_NUMBER);
            if (savedPhone != null) {
                phoneEt.setText(savedPhone);
            }
            phoneVerificationInProgress = savedInstanceState.getBoolean(
                    STATE_VERIFICATION_IN_PROGRESS, false);
            if (phoneVerificationInProgress) {
                verifyPhoneButton.setText(R.string.verify_phone_code);
                resendPhoneButton.setVisibility(View.VISIBLE);
            }
        }
        googleButton.setOnClickListener(v -> continueWithGoogleButtonClicked());

        // Configure Google Sign-In if default_web_client_id is present in strings
        try {
            String webClientId = getString(R.string.default_web_client_id);
            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(webClientId)
                    .requestEmail()
                    .build();
            mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
        } catch (Resources.NotFoundException e) {
            // default_web_client_id not found — disable Google sign-in and log guidance
            mGoogleSignInClient = null;
            Log.i(TAG, "default_web_client_id string not found. Add your OAuth client ID to strings.xml to enable Google Sign-In.");
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null && !phoneVerificationInProgress) {
            reload();
        }
    }

    private void signUpButtonClicked() {
        final String fullName = nameEt.getText().toString().trim();
        final String phone = normalizePhoneNumber(phoneEt.getText().toString());
        final String email = emailEt.getText().toString().trim();
        String password = passwordEt.getText().toString();
        String confirmPassword = confirmPasswordEt.getText().toString();

        if (fullName.isEmpty() || email.isEmpty() || phone.isEmpty()
                || password.isEmpty() || confirmPassword.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!password.equals(confirmPassword)) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!termsCheckBox.isChecked()) {
            Toast.makeText(this, "You must agree to the terms and conditions", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isE164(phone)) {
            Toast.makeText(this, "Enter a valid international phone number, e.g. +256700123456.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        if (verifiedPhoneCredential == null || !phoneVerificationInProgress) {
            Toast.makeText(this, "Verify your phone number before signing up.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        signUpButton.setEnabled(false);
        AuthRepository.getInstance().completeCustomerRegistration(fullName, email, phone, password,
                verifiedPhoneCredential, new AuthRepository.AuthCallback() {
                    @Override
                    public void onSuccess(com.example.washlink.models.UserAccount user) {
                        Toast.makeText(CreateAccountActivity.this,
                                "Account created. Verify your email before signing in.",
                                Toast.LENGTH_LONG).show();
                        mAuth.signOut();
                        Intent intent = new Intent(CreateAccountActivity.this,
                                SuccessActivity.class);
                        startActivity(intent);
                        finish();
                    }

                    @Override
                    public void onError(String message) {
                        signUpButton.setEnabled(true);
                        Log.w(TAG, "Customer registration failed: " + message);
                        Toast.makeText(CreateAccountActivity.this, message,
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void verifyPhone() {
                verifyPhone(false);
    }

    private void verifyPhone(boolean resend) {
                String phoneNumber = normalizePhoneNumber(phoneEt.getText().toString());
                if (!isE164(phoneNumber)) {
                    Toast.makeText(this, "Enter the phone number in international format, e.g. +256700123456.",
                            Toast.LENGTH_LONG).show();
                    return;
                }

                PhoneAuthOptions.Builder optionsBuilder = PhoneAuthOptions.newBuilder(mAuth)
                        .setPhoneNumber(phoneNumber)
                        .setTimeout(60L, TimeUnit.SECONDS)
                        .setActivity(this)
                        .setCallbacks(phoneCallbacks);
                if (resend && resendToken != null) {
                    optionsBuilder.setForceResendingToken(resendToken);
                }
                phoneVerificationInProgress = true;
                PhoneAuthProvider.verifyPhoneNumber(optionsBuilder.build());
    }

    private final PhoneAuthProvider.OnVerificationStateChangedCallbacks phoneCallbacks =
                    new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                            @Override
                            public void onVerificationCompleted(PhoneAuthCredential credential) {
                                verifiedPhoneCredential = credential;
                                phoneVerificationInProgress = true;
                                signUpButton.setEnabled(true);
                                verifyPhoneButton.setText(R.string.phone_verified);
                                verifyPhoneButton.setEnabled(false);
                                resendPhoneButton.setVisibility(View.GONE);
                                Toast.makeText(CreateAccountActivity.this,
                                        R.string.phone_verified, Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onVerificationFailed(FirebaseException e) {
                                phoneVerificationInProgress = false;
                                verifiedPhoneCredential = null;
                                signUpButton.setEnabled(false);
                                resendPhoneButton.setVisibility(View.GONE);
                                if (e instanceof FirebaseAuthInvalidCredentialsException) {
                                    Toast.makeText(CreateAccountActivity.this,
                                            "Enter a valid phone number.", Toast.LENGTH_SHORT).show();
                                } else if (e instanceof FirebaseTooManyRequestsException) {
                                    Toast.makeText(CreateAccountActivity.this,
                                            "SMS quota exceeded. Try again later.", Toast.LENGTH_LONG).show();
                                } else if (e instanceof FirebaseAuthMissingActivityForRecaptchaException) {
                                    Toast.makeText(CreateAccountActivity.this,
                                            "Phone verification could not start. Try again.", Toast.LENGTH_LONG).show();
                                } else {
                                    Toast.makeText(CreateAccountActivity.this,
                                            "Phone verification failed: " + e.getMessage(),
                                            Toast.LENGTH_LONG).show();
                                }
                            }

                            @Override
                            public void onCodeSent(String id,
                                                           PhoneAuthProvider.ForceResendingToken token) {
                                        verificationId = id;
                                        resendToken = token;
                                        phoneVerificationInProgress = true;
                                        verifyPhoneButton.setText(R.string.verify_phone_code);
                                        resendPhoneButton.setVisibility(View.VISIBLE);
                                        Toast.makeText(CreateAccountActivity.this,
                                                "Verification code sent.", Toast.LENGTH_SHORT).show();
                                    }
                                };

    private void verifyPhoneCode() {
                String code = ((EditText) findViewById(R.id.et_phone_code)).getText().toString().trim();
                if (verificationId == null || code.length() != 6) {
                            Toast.makeText(this, "Enter the 6-digit verification code.",
                                    Toast.LENGTH_SHORT).show();
                            return;
                }
                PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, code);
                verifiedPhoneCredential = credential;
                phoneVerificationInProgress = true;
                signUpButton.setEnabled(true);
                verifyPhoneButton.setText(R.string.phone_verified);
                verifyPhoneButton.setEnabled(false);
                resendPhoneButton.setVisibility(View.GONE);
                Toast.makeText(this, R.string.phone_verified, Toast.LENGTH_SHORT).show();
    }

    private String normalizePhoneNumber(String rawPhone) {
                String normalized = rawPhone.replaceAll("[\\s().-]", "");
                if (normalized.startsWith("00")) {
                    normalized = "+" + normalized.substring(2);
                }
                return normalized;
    }

    private boolean isE164(String phoneNumber) {
                return phoneNumber.matches("^\\+[1-9][0-9]{7,14}$");
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
                outState.putString(STATE_VERIFICATION_ID, verificationId);
                outState.putBoolean(STATE_VERIFICATION_IN_PROGRESS, phoneVerificationInProgress);
                outState.putString(STATE_PHONE_NUMBER, phoneEt.getText().toString());
                super.onSaveInstanceState(outState);
    }

    private void continueWithGoogleButtonClicked() {
        if (mGoogleSignInClient == null) {
            Toast.makeText(this, "Google Sign-In not configured. Add default_web_client_id to strings.xml.", Toast.LENGTH_LONG).show();
            return;
        }
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                if (account != null) {
                    String idToken = account.getIdToken();
                    AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
                    mAuth.signInWithCredential(credential)
                            .addOnCompleteListener(this, task1 -> {
                                if (task1.isSuccessful()) {
                                    Log.d(TAG, "signInWithCredential:success");
                                    FirebaseUser user = mAuth.getCurrentUser();
                                    AuthRepository.getInstance().ensureGoogleCustomer(user,
                                            new AuthRepository.AuthCallback() {
                                                @Override
                                                public void onSuccess(com.example.washlink.models.UserAccount account) {
                                                    Intent intent = new Intent(CreateAccountActivity.this,
                                                            AddPhoneNumberActivity.class);
                                                    intent.putExtra(AddPhoneNumberActivity.EXTRA_USER_NAME,
                                                            account.getName());
                                                    intent.putExtra(AddPhoneNumberActivity.EXTRA_USER_UID,
                                                            account.getUid());
                                                    startActivity(intent);
                                                    finish();
                                                }

                                                @Override
                                                public void onError(String message) {
                                                    Toast.makeText(CreateAccountActivity.this,
                                                            message, Toast.LENGTH_SHORT).show();
                                                }
                                            });
                                } else {
                                    Log.w(TAG, "signInWithCredential:failure", task1.getException());
                                    Toast.makeText(CreateAccountActivity.this, "Google sign-in failed.", Toast.LENGTH_SHORT).show();
                                    updateUI(null);
                                }
                            });
                }
            } catch (ApiException e) {
                Log.w(TAG, "Google sign in failed", e);
                Toast.makeText(this, "Google sign-in failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void updateUI(FirebaseUser user) {
        if (user != null) {
            Intent intent = new Intent(CreateAccountActivity.this, MainActivity.class);
            intent.putExtra("first_time_signup", true);
            startActivity(intent);
            finish();
        } else {
            // Stay on screen or show an error — already handled where appropriate
        }
    }

    private void reload() {
        Intent intent = new Intent(CreateAccountActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}
