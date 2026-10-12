package com.example.washlink.data;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.content.Context;
import android.util.TypedValue;
import android.widget.ImageView;

import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.StorageMetadata;

import java.io.InputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ProfilePhotoManager {
    public interface UploadCallback {
        void onSuccess(String downloadUrl);
        void onError(String message);
    }

    private static final ExecutorService IMAGE_EXECUTOR = Executors.newFixedThreadPool(2);

    private ProfilePhotoManager() { }

    public static void upload(Context context, Uri image, String uid, UploadCallback callback) {
        String contentType = context.getContentResolver().getType(image);
        if (!isSupportedImage(contentType)) {
            callback.onError("Choose a JPEG, PNG, WebP, or GIF image.");
            return;
        }
        StorageReference reference = FirebaseStorage.getInstance().getReference()
                .child("profilePictures").child(uid).child("avatar");
        StorageMetadata metadata = new StorageMetadata.Builder()
                .setContentType(contentType)
                .build();
        reference.putFile(image, metadata)
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        throw task.getException() == null
                                ? new IllegalStateException("Image upload failed.")
                                : task.getException();
                    }
                    return reference.getDownloadUrl();
                })
                .addOnSuccessListener(uri -> callback.onSuccess(uri.toString()))
                .addOnFailureListener(error -> callback.onError(error.getMessage() == null
                        ? "Could not upload profile picture." : error.getMessage()));
    }

    public static void display(ImageView view, String savedUrl, String authPhotoUrl,
                               String email) {
        if (savedUrl != null && savedUrl.startsWith("content://")) {
            try {
                view.setImageURI(Uri.parse(savedUrl));
                return;
            } catch (IllegalArgumentException ignored) {
                // Continue to the account photo fallback if a legacy local image is unavailable.
            }
        }
        String fallbackUrl = isRemoteUrl(authPhotoUrl) ? authPhotoUrl : gravatarUrl(email);
        String sourceUrl = isRemoteUrl(savedUrl) ? savedUrl : fallbackUrl;
        view.setTag(sourceUrl);
        view.setImageBitmap(initialsBitmap(view, email));
        IMAGE_EXECUTOR.execute(() -> {
            Bitmap bitmap = downloadBitmap(sourceUrl);
            if (bitmap == null && !sourceUrl.equals(fallbackUrl)) {
                bitmap = downloadBitmap(fallbackUrl);
            }
            Bitmap result = bitmap;
            view.post(() -> {
                if (sourceUrl.equals(view.getTag()) && result != null) {
                    view.setImageBitmap(result);
                }
            });
        });
    }

    private static Bitmap downloadBitmap(String address) {
        if (!isRemoteUrl(address)) return null;
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(address).openConnection();
            connection.setConnectTimeout(7000);
            connection.setReadTimeout(7000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "WashLink");
            try (InputStream input = connection.getInputStream()) {
                return BitmapFactory.decodeStream(input);
            }
        } catch (IOException | IllegalArgumentException ignored) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static boolean isRemoteUrl(String value) {
        return value != null && (value.startsWith("https://") || value.startsWith("http://"));
    }

    private static boolean isSupportedImage(String contentType) {
        return "image/jpeg".equals(contentType) || "image/png".equals(contentType)
                || "image/webp".equals(contentType) || "image/gif".equals(contentType);
    }

    private static String gravatarUrl(String email) {
        try {
            String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte part : hash) hex.append(String.format(Locale.ROOT, "%02x", part));
            return "https://gravatar.com/avatar/" + hex + "?d=identicon&s=256";
        } catch (NoSuchAlgorithmException ignored) {
            return "https://gravatar.com/avatar/?d=identicon&s=256";
        }
    }

    private static Bitmap initialsBitmap(ImageView view, String email) {
        int size = Math.max(1, (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                96, view.getResources().getDisplayMetrics()));
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.rgb(225, 237, 250));
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
        paint.setColor(Color.rgb(22, 82, 230));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(size * 0.4f);
        String initials = email == null || email.trim().isEmpty()
                ? "W" : email.trim().substring(0, 1).toUpperCase(Locale.ROOT);
        canvas.drawText(initials, size / 2f, size / 2f - (paint.ascent() + paint.descent()) / 2f,
                paint);
        return bitmap;
    }
}
