package com.example.washlink;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.washlink.data.AuthRepository;
import com.google.android.material.button.MaterialButton;

public class AddPhoneNumberActivity extends AppCompatActivity {

    public static final String EXTRA_USER_NAME = "extra_user_name";
    public static final String EXTRA_USER_UID = "extra_user_uid";

    private EditText phoneInput;
    private MaterialButton saveButton;
    private String userName;
    private String userUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_phone_number);

        userName = getIntent().getStringExtra(EXTRA_USER_NAME);
        userUid = getIntent().getStringExtra(EXTRA_USER_UID);

        TextView welcomeName = findViewById(R.id.tv_welcome_name);
        String firstName = userName == null || userName.trim().isEmpty()
                ? "there" : userName.trim().split("\\s+")[0];
        welcomeName.setText(getString(R.string.welcome_user_name, firstName));

        phoneInput = findViewById(R.id.et_phone);
        saveButton = findViewById(R.id.btn_save_continue);
        saveButton.setOnClickListener(v -> savePhoneNumber());
    }

    private void savePhoneNumber() {
        String localNumber = phoneInput.getText() == null
                ? "" : phoneInput.getText().toString().trim().replaceAll("\\s+", "");

        if (TextUtils.isEmpty(localNumber)) {
            Toast.makeText(this, "Enter your phone number.", Toast.LENGTH_SHORT).show();
            return;
        }

        String fullNumber = localNumber.startsWith("+")
                ? localNumber : "+256" + localNumber;
        if (!fullNumber.matches("^\\+[1-9][0-9]{7,14}$")) {
            Toast.makeText(this, "Enter a valid phone number.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (userUid == null || userUid.trim().isEmpty()) {
            Toast.makeText(this, "Your account session has expired. Please sign in again.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        setLoading(true);
        AuthRepository.getInstance().updatePhoneNumber(userUid, fullNumber,
                new AuthRepository.SimpleCallback() {
                    @Override
                    public void onSuccess() {
                        goToSuccess();
                    }

                    @Override
                    public void onError(String message) {
                        setLoading(false);
                        Toast.makeText(AddPhoneNumberActivity.this,
                                message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void goToSuccess() {
        Intent intent = new Intent(this, SuccessActivity.class);
        intent.putExtra(SuccessActivity.EXTRA_USER_NAME, userName);
        startActivity(intent);
        finish();
    }

    private void setLoading(boolean loading) {
        saveButton.setEnabled(!loading);
        saveButton.setText(loading ? "Saving..." : getString(R.string.save_and_continue));
    }
}
