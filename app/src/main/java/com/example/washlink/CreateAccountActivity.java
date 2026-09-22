package com.example.washlink;

import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.washlink.data.AuthRepository;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

public class CreateAccountActivity extends AppCompatActivity {

    private static final String TAG = "CreateAccountActivity";
    private static final int RC_SIGN_IN = 9001;

    private EditText nameEt;
    private EditText emailEt;
    private EditText passwordEt;
    private EditText confirmPasswordEt;
    private CheckBox termsCheckBox;

    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_create_account);

        mAuth = FirebaseAuth.getInstance();

        nameEt = findViewById(R.id.et_name_reg);
        emailEt = findViewById(R.id.et_email_reg);
        passwordEt = findViewById(R.id.et_password_reg);
        confirmPasswordEt = findViewById(R.id.et_confirm_password_reg);
        termsCheckBox = findViewById(R.id.checkbox_terms);
        MaterialButton signUpBtn = findViewById(R.id.btn_sign_up);
        View googleButton = findViewById(R.id.btn_google_outlined);

        signUpBtn.setOnClickListener(v -> signUpButtonClicked());
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
        if (currentUser != null) {
            reload();
        }
    }

    private void signUpButtonClicked() {
        final String fullName = nameEt.getText().toString().trim();
        final String email = emailEt.getText().toString().trim();
        String password = passwordEt.getText().toString();
        String confirmPassword = confirmPasswordEt.getText().toString();

        if (fullName.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
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

        AuthRepository.getInstance().registerCustomer(fullName, email, "", password,
                new AuthRepository.AuthCallback() {
                    @Override
                    public void onSuccess(com.example.washlink.models.UserAccount user) {
                        Toast.makeText(CreateAccountActivity.this,
                                "Account created. Verify your email before signing in.",
                                Toast.LENGTH_LONG).show();
                        mAuth.signOut();
                        Intent intent = new Intent(CreateAccountActivity.this, SuccessActivity.class);
                        startActivity(intent);
                        finish();
                    }

                    @Override
                    public void onError(String message) {
                        Log.w(TAG, "Customer registration failed: " + message);
                        Toast.makeText(CreateAccountActivity.this, message,
                                Toast.LENGTH_SHORT).show();
                    }
                });
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
                                                            SuccessActivity.class);
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
            Intent intent = new Intent(CreateAccountActivity.this, HomeActivity.class);
            intent.putExtra("first_time_signup", true);
            startActivity(intent);
            finish();
        } else {
            // Stay on screen or show an error — already handled where appropriate
        }
    }

    private void reload() {
        Intent intent = new Intent(CreateAccountActivity.this, HomeActivity.class);
        startActivity(intent);
        finish();
    }
}
