package com.example.washlink.ui.customer;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.example.washlink.data.AuthRepository;
import com.example.washlink.models.UserAccount;
import com.example.washlink.PaymentActivity;
import com.example.washlink.R;
import com.example.washlink.SignInActivity;
import com.google.firebase.auth.FirebaseAuth;

public class ProfileFragment extends CustomerTabFragment {
    private final AuthRepository authRepository = AuthRepository.getInstance();
    private UserAccount account;
    private String photoUri = "";
    private ImageView avatar;
    private ActivityResultLauncher<String[]> imagePicker;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        imagePicker = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri == null || account == null) return;
            try {
                requireContext().getContentResolver().takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                avatar.setImageURI(uri);
                photoUri = uri.toString();
                saveProfile();
            } catch (SecurityException | IllegalArgumentException e) {
                Toast.makeText(requireContext(), "Unable to access that image.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public View onCreateView(@NonNull android.view.LayoutInflater inflater,
                             @Nullable android.view.ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_customer_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        bindNavigation(view, MainActivity.TAB_PROFILE);
        view.findViewById(R.id.iv_bell).setOnClickListener(v -> openNotifications());
        avatar = view.findViewById(R.id.iv_avatar);
        avatar.setOnClickListener(v -> imagePicker.launch(new String[]{"image/*"}));
        view.findViewById(R.id.row_order_history).setOnClickListener(v ->
                ((MainActivity) requireActivity()).showTab(MainActivity.TAB_HISTORY));
        view.findViewById(R.id.row_payment_methods).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), PaymentActivity.class)));
        view.findViewById(R.id.row_password).setOnClickListener(v ->
                authRepository.sendPasswordReset(new AuthRepository.SimpleCallback() {
                    @Override public void onSuccess() {
                        Toast.makeText(requireContext(), "Password reset instructions sent.", Toast.LENGTH_SHORT).show();
                    }
                    @Override public void onError(String message) {
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                    }
                }));
        view.findViewById(R.id.btn_logout).setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(requireContext(), SignInActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });
        loadProfile(view);
    }

    private void loadProfile(View view) {
        authRepository.getCurrentUserAccount(new AuthRepository.AuthCallback() {
            @Override public void onSuccess(UserAccount user) {
                if (!isAdded()) return;
                account = user;
                ((TextView) view.findViewById(R.id.tv_profile_name))
                        .setText(value(user.getName(), "Name not provided"));
                ((TextView) view.findViewById(R.id.tv_profile_email))
                        .setText(value(user.getEmail(), "Email not provided"));
                ((TextView) view.findViewById(R.id.tv_profile_phone))
                        .setText(value(user.getPhone(), "Phone number not provided"));
                ((TextView) view.findViewById(R.id.tv_saved_address_value))
                        .setText(value(user.getAddress(), getString(R.string.profile_saved_addresses_desc)));
                photoUri = value(user.getPhotoUri(), "");
                if (!photoUri.isEmpty()) {
                    try { avatar.setImageURI(Uri.parse(photoUri)); }
                    catch (SecurityException | IllegalArgumentException ignored) { avatar.setImageDrawable(null); }
                }
            }
            @Override public void onError(String message) {
                if (isAdded()) Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveProfile() {
        if (account == null) return;
        authRepository.updateCustomerProfile(value(account.getName(), ""),
                value(account.getPhone(), ""), value(account.getAddress(), ""), photoUri,
                new AuthRepository.SimpleCallback() {
                    @Override public void onSuccess() { }
                    @Override public void onError(String message) {
                        if (isAdded()) Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private String value(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }
}
