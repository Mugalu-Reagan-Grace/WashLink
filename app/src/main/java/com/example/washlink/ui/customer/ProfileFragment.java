package com.example.washlink.ui.customer;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;

import com.example.washlink.PaymentActivity;
import com.example.washlink.R;
import com.example.washlink.SignInActivity;
import com.example.washlink.data.AuthRepository;
import com.example.washlink.models.UserAccount;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.List;

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
                saveProfile(uri.toString(), () -> {
                    photoUri = uri.toString();
                    if (avatar != null) avatar.setImageURI(uri);
                });
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
        view.findViewById(R.id.btn_back).setOnClickListener(v -> {
            if (getActivity() != null) getActivity().onBackPressed();
        });

        avatar = view.findViewById(R.id.iv_avatar);
        if (avatar != null) {
            avatar.setOnClickListener(v -> imagePicker.launch(new String[]{"image/*"}));
        }
        view.findViewById(R.id.card_account_info).setOnClickListener(v -> {
            if (account == null) loadProfile(view);
            else showProfileDialog();
        });

        view.findViewById(R.id.row_saved_addresses).setOnClickListener(v -> showAddressDialog());
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
        view.findViewById(R.id.row_profile_support).setOnClickListener(v ->
                openSupportEmail("WashLink support request"));
        view.findViewById(R.id.row_profile_feedback).setOnClickListener(v ->
                openSupportEmail("WashLink feedback"));
        view.findViewById(R.id.row_profile_privacy).setOnClickListener(v ->
                showInformationDialog(getString(R.string.profile_privacy),
                        "WashLink uses your account, contact, address, and order information to provide laundry pickup, delivery, payment, and support services. We do not sell your personal information."));
        view.findViewById(R.id.row_profile_terms).setOnClickListener(v ->
                showInformationDialog(getString(R.string.profile_terms),
                        "Use WashLink responsibly and provide accurate booking information. Pickup times, prices, cancellations, and refunds are subject to the service details shown before you confirm an order."));
        view.findViewById(R.id.row_profile_about).setOnClickListener(v ->
                showInformationDialog(getString(R.string.profile_about),
                        "WashLink connects customers with laundry providers for pickup, delivery, and drop-off services.\n\nVersion 1.0.0"));
        view.findViewById(R.id.btn_logout).setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(requireContext(), SignInActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });
        loadProfile(view);
    }

    private void loadProfile(View view) {
        if (view == null) return;
        TextView name = view.findViewById(R.id.tv_profile_name);
        TextView email = view.findViewById(R.id.tv_profile_email);
        TextView phone = view.findViewById(R.id.tv_profile_phone);
        TextView savedAddress = view.findViewById(R.id.tv_saved_address_value);
        if (name != null) name.setText("Loading account...");
        if (email != null) email.setText("");
        if (phone != null) phone.setText("");
        authRepository.getCurrentUserAccount(new AuthRepository.AuthCallback() {
            @Override public void onSuccess(UserAccount user) {
                if (!isAdded() || getView() != view) return;
                account = user;

                if (name != null) name.setText(value(user.getName(), "Name not provided"));
                if (email != null) email.setText(value(user.getEmail(), "Email not provided"));
                if (phone != null) phone.setText(value(user.getPhone(), "Phone number not provided"));
                if (savedAddress != null) {
                    savedAddress.setText(value(user.getAddress(), getString(R.string.profile_saved_addresses_desc)));
                }

                photoUri = value(user.getPhotoUri(), "");
                if (!photoUri.isEmpty() && avatar != null) {
                    try {
                        avatar.setImageURI(Uri.parse(photoUri));
                    } catch (SecurityException | IllegalArgumentException e) {
                        Toast.makeText(requireContext(),
                                "Saved profile picture is unavailable on this device.",
                                Toast.LENGTH_SHORT).show();
                    }
                }
            }
            @Override public void onError(String message) {
                if (!isAdded() || getView() != view) return;
                account = null;
                if (name != null) name.setText("Could not load profile. Tap to retry.");
                if (view.findViewById(R.id.card_account_info) != null) {
                    view.findViewById(R.id.card_account_info).setOnClickListener(v -> loadProfile(view));
                }
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showAddressDialog() {
        if (account == null) {
            View current = getView();
            if (current != null) loadProfile(current);
            return;
        }

        List<String> addresses = new ArrayList<>();
        if (account.getSavedAddresses() != null) {
            for (String address : account.getSavedAddresses()) addUniqueAddress(addresses, address);
        }
        addUniqueAddress(addresses, account.getAddress());

        LinearLayout list = new LinearLayout(requireContext());
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(24, 0, 24, 0);
        if (addresses.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText("No saved addresses yet.");
            empty.setPadding(8, 20, 8, 20);
            list.addView(empty);
        }

        AlertDialog[] dialogRef = new AlertDialog[1];
        for (String address : new ArrayList<>(addresses)) {
            LinearLayout row = new LinearLayout(requireContext());
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setOrientation(LinearLayout.HORIZONTAL);
            TextView label = new TextView(requireContext());
            label.setText(address);
            label.setPadding(8, 12, 8, 12);
            row.addView(label, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            Button use = new Button(requireContext());
            use.setText("Use");
            use.setOnClickListener(v -> persistAddresses(addresses, address, () -> {
                if (dialogRef[0] != null) dialogRef[0].dismiss();
            }));
            row.addView(use);

            Button remove = new Button(requireContext());
            remove.setText("Remove");
            remove.setOnClickListener(v -> new AlertDialog.Builder(requireContext())
                    .setMessage("Remove this saved address?")
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton("Remove", (confirm, which) -> {
                        List<String> updated = new ArrayList<>(addresses);
                        updated.removeIf(item -> item.equalsIgnoreCase(address));
                        String primary = account.getAddress() != null
                                && address.equalsIgnoreCase(account.getAddress())
                                ? (updated.isEmpty() ? "" : updated.get(0)) : account.getAddress();
                        persistAddresses(updated, primary, () -> {
                            if (dialogRef[0] != null) dialogRef[0].dismiss();
                            showAddressDialog();
                        });
                    })
                    .show());
            row.addView(remove);
            list.addView(row);
        }

        ScrollView scroll = new ScrollView(requireContext());
        scroll.addView(list);
        dialogRef[0] = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.profile_saved_addresses)
                .setMessage(R.string.profile_address_dialog_message)
                .setView(scroll)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Add address", (dialog, which) -> showNewAddressDialog(addresses))
                .show();
    }

    private void showNewAddressDialog(List<String> addresses) {
        EditText input = new EditText(requireContext());
        input.setHint(R.string.enter_your_address);
        input.setSingleLine(false);
        input.setPadding(32, 24, 32, 24);
        new AlertDialog.Builder(requireContext())
                .setTitle("Add saved address")
                .setView(input)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    String address = input.getText().toString().trim();
                    if (address.isEmpty()) {
                        Toast.makeText(requireContext(), "Enter an address.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    List<String> updated = new ArrayList<>(addresses);
                    addUniqueAddress(updated, address);
                    String primary = account.getAddress() == null || account.getAddress().trim().isEmpty()
                            ? address : account.getAddress();
                    persistAddresses(updated, primary, this::showAddressDialog);
                })
                .show();
    }

    private void persistAddresses(List<String> addresses, String primaryAddress, Runnable onSuccess) {
        if (account == null) return;
        List<String> cleaned = new ArrayList<>();
        for (String address : addresses) addUniqueAddress(cleaned, address);
        authRepository.updateCustomerAddresses(cleaned, primaryAddress,
                new AuthRepository.SimpleCallback() {
                    @Override public void onSuccess() {
                        if (!isAdded()) return;
                        account.setAddress(primaryAddress == null ? "" : primaryAddress.trim());
                        addUniqueAddress(cleaned, account.getAddress());
                        account.setSavedAddresses(cleaned);
                        updateSavedAddressLabel(account.getAddress());
                        onSuccess.run();
                    }
                    @Override public void onError(String message) {
                        if (isAdded()) Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void addUniqueAddress(List<String> addresses, String address) {
        if (address == null || address.trim().isEmpty()) return;
        String cleaned = address.trim();
        for (String existing : addresses) {
            if (existing != null && cleaned.equalsIgnoreCase(existing.trim())) return;
        }
        addresses.add(cleaned);
    }

    private void updateSavedAddressLabel(String address) {
        View currentView = getView();
        TextView savedAddress = currentView == null ? null
                : currentView.findViewById(R.id.tv_saved_address_value);
        if (savedAddress != null) {
            savedAddress.setText(address.isEmpty()
                    ? getString(R.string.profile_saved_addresses_desc) : address);
        }
    }

    private void showProfileDialog() {
        if (account == null) {
            Toast.makeText(requireContext(), "Loading account details...", Toast.LENGTH_SHORT).show();
            return;
        }

        LinearLayout fields = new LinearLayout(requireContext());
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.setPadding(32, 0, 32, 0);

        EditText nameInput = new EditText(requireContext());
        nameInput.setHint("Full name");
        nameInput.setSingleLine(true);
        nameInput.setText(account.getName());
        nameInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        fields.addView(nameInput);

        EditText phoneInput = new EditText(requireContext());
        phoneInput.setHint("Phone number");
        phoneInput.setSingleLine(true);
        phoneInput.setText(account.getPhone());
        phoneInput.setInputType(InputType.TYPE_CLASS_PHONE);
        fields.addView(phoneInput);

        new AlertDialog.Builder(requireContext())
                .setTitle("Edit profile")
                .setView(fields)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    String name = nameInput.getText().toString().trim();
                    String phone = phoneInput.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(requireContext(), "Enter your name.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    updateProfile(name, phone, account.getAddress(), () -> {
                        account.setName(name);
                        account.setPhone(phone);
                        View currentView = getView();
                        if (currentView != null) {
                            TextView profileName = currentView.findViewById(R.id.tv_profile_name);
                            TextView profilePhone = currentView.findViewById(R.id.tv_profile_phone);
                            if (profileName != null) profileName.setText(name);
                            if (profilePhone != null) {
                                profilePhone.setText(value(phone, "Phone number not provided"));
                            }
                        }
                    });
                })
                .show();
    }

    private void updateProfile(String name, String phone, String address, Runnable onSuccess) {
        authRepository.updateCustomerProfile(value(name, ""), value(phone, ""),
                value(address, ""), photoUri, new AuthRepository.SimpleCallback() {
                    @Override public void onSuccess() {
                        if (isAdded()) onSuccess.run();
                    }
                    @Override public void onError(String message) {
                        if (isAdded()) Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void openSupportEmail(String subject) {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:" + getString(R.string.profile_support_email)));
        intent.putExtra(Intent.EXTRA_SUBJECT, subject);
        if (intent.resolveActivity(requireActivity().getPackageManager()) != null) {
            startActivity(intent);
        } else {
            Toast.makeText(requireContext(), getString(R.string.profile_support_email), Toast.LENGTH_LONG).show();
        }
    }

    private void showInformationDialog(String title, String message) {
        new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void saveProfile(String updatedPhotoUri, Runnable onSuccess) {
        if (account == null) return;
        authRepository.updateCustomerProfile(value(account.getName(), ""),
                value(account.getPhone(), ""), value(account.getAddress(), ""), updatedPhotoUri,
                new AuthRepository.SimpleCallback() {
                    @Override public void onSuccess() {
                        if (isAdded()) onSuccess.run();
                    }
                    @Override public void onError(String message) {
                        if (isAdded()) Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private String value(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }
}
