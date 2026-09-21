package com.example.washlink;

import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

public class SignInActivity extends AppCompatActivity {

    private static final String TAG = "SignInActivity";
    private static final int RC_SIGN_IN = 9001;

    private EditText email_et, password_et;

    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_sign_in);

        mAuth = FirebaseAuth.getInstance();

        email_et = findViewById(R.id.et_email_login);
        password_et = findViewById(R.id.et_password_login);
        findViewById(R.id.cb_remember_me);
        Button signInButton = findViewById(R.id.btn_sign_in);
        View googleButton = findViewById(R.id.btn_google_outlined);
        TextView forgotPasswordText = findViewById(R.id.tv_forgot_password);

        signInButton.setOnClickListener(v -> signInButtonClicked());
        googleButton.setOnClickListener(v -> continueWithGoogleButtonClicked());
        forgotPasswordText.setOnClickListener(v -> forgotPasswordButtonClicked());

        try {
            String webClientId = getString(R.string.default_web_client_id);
            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(webClientId)
                    .requestEmail()
                    .build();
            mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
        } catch (Resources.NotFoundException e) {
            mGoogleSignInClient = null;
            Log.i(TAG, "default_web_client_id not found. Configure Google Sign-In in Firebase.");
        }
    }

    private void signInButtonClicked() {
        String user_email = email_et.getText().toString().trim();
        String user_password = password_et.getText().toString();

        if (user_email.isEmpty() || user_password.isEmpty()) {
            Toast.makeText(this, "User email and password are required", Toast.LENGTH_SHORT).show();
            return;
        }

        mAuth.signInWithEmailAndPassword(user_email, user_password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "signInWithEmail:success");
                        FirebaseUser user = mAuth.getCurrentUser();
                        updateUI(user);
                    } else {
                        Log.w(TAG, "signInWithEmail:failure", task.getException());
                        Toast.makeText(SignInActivity.this, "Invalid email or password", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void continueWithGoogleButtonClicked() {
        if (mGoogleSignInClient == null) {
            Toast.makeText(this, "Google sign-in is not configured. Check Firebase Google auth setup.", Toast.LENGTH_LONG).show();
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
                if (account == null) {
                    Toast.makeText(this, "Google sign-in was cancelled", Toast.LENGTH_SHORT).show();
                    return;
                }

                String idToken = account.getIdToken();
                AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
                mAuth.signInWithCredential(credential)
                        .addOnCompleteListener(this, authTask -> {
                            if (authTask.isSuccessful()) {
                                Log.d(TAG, "signInWithCredential:success");
                                FirebaseUser user = mAuth.getCurrentUser();
                                updateUI(user);
                            } else {
                                Log.w(TAG, "signInWithCredential:failure", authTask.getException());
                                Toast.makeText(SignInActivity.this, "Google sign-in failed", Toast.LENGTH_SHORT).show();
                            }
                        });
            } catch (ApiException e) {
                Log.w(TAG, "Google sign-in failed", e);
                Toast.makeText(this, "Google sign-in failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void forgotPasswordButtonClicked() {
        String email = email_et.getText().toString().trim();

        if (email.isEmpty()) {
            Toast.makeText(this, "Enter your email to reset your password", Toast.LENGTH_SHORT).show();
            return;
        }

        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Password reset email sent", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Unable to send reset email", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void updateUI(FirebaseUser user) {
        if (user == null) {
            return;
        }

        boolean firstTimeSignup = getIntent() != null && getIntent().getBooleanExtra("first_time_signup", false);
        Intent intent;

        if (firstTimeSignup) {
            intent = new Intent(SignInActivity.this, SuccessActivity.class);
        } else {
            intent = new Intent(SignInActivity.this, HomeActivity.class);
        }

        startActivity(intent);
        finish();
    }
}
