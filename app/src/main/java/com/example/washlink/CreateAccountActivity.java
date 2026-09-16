package com.example.washlink;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

public class CreateAccountActivity extends AppCompatActivity {

    private static final String TAG = "CreateAccountActivity";

    private EditText nameEt;
    private EditText emailEt;
    private EditText passwordEt;
    private EditText confirmPasswordEt;
    private CheckBox termsCheckBox;
    private MaterialButton signUpBtn;
    private View googleButton;

    private FirebaseAuth mAuth;

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
        signUpBtn = findViewById(R.id.btn_sign_up);
        googleButton = findViewById(R.id.btn_google_outlined);

        signUpBtn.setOnClickListener(v -> signUpButtonClicked());
        googleButton.setOnClickListener(v -> continueWithGoogleButtonClicked());
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
        String fullName = nameEt.getText().toString().trim();
        String email = emailEt.getText().toString().trim();
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
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "createUserWithEmail:success");
                            FirebaseUser user = mAuth.getCurrentUser();
                            updateUI(user);
                        } else {
                            Log.w(TAG, "createUserWithEmail:failure", task.getException());
                            Toast.makeText(CreateAccountActivity.this, "Authentication failed.",
                                    Toast.LENGTH_SHORT).show();
                            updateUI(null);
                        }
                    }
                });
    }

    private void continueWithGoogleButtonClicked() {
        Toast.makeText(this, "Google sign-up is not available in demo mode", Toast.LENGTH_SHORT).show();
    }

    private void updateUI(FirebaseUser user) {
        if (user != null) {
            Intent intent = new Intent(CreateAccountActivity.this, SignInActivity.class);
            intent.putExtra("first_time_signup", true);
            startActivity(intent);
            finish();
        } else {
        }
    }

    private void reload() {
        Intent intent = new Intent(CreateAccountActivity.this, HomeActivity.class);
        startActivity(intent);
        finish();
    }
}
