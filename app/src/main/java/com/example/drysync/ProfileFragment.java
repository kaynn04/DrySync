package com.example.drysync;

import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

// at the top of the file:
import android.content.Context;
import android.content.SharedPreferences;


public class ProfileFragment extends Fragment {

    // 🔧 Replace with your Cloudinary details
    private static final String CLOUD_NAME = "dvildmlda";
    private static final String UPLOAD_PRESET = "drysync_upload"; // unsigned

    private ImageView imgAvatar, btnChangeAvatar;
    private TextInputEditText etName, etEmail, etPhone, etCurrentPassword, etNewPassword, etConfirmPassword;
    private MaterialButton btnSave;

    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private ActivityResultLauncher<String> pickImageLauncher;
    private Uri pendingAvatarUri;        // local image to upload
    private String uploadedPhotoUrl;     // https url from Cloudinary (if any)

    private final OkHttpClient http = new OkHttpClient();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        imgAvatar = v.findViewById(R.id.imgAvatar);
        btnChangeAvatar = v.findViewById(R.id.btnChangeAvatar);
        etName = v.findViewById(R.id.etName);
        etEmail = v.findViewById(R.id.etEmail);
        etPhone = v.findViewById(R.id.etPhone);
        etCurrentPassword = v.findViewById(R.id.etCurrentPassword);
        etNewPassword = v.findViewById(R.id.etNewPassword);
        etConfirmPassword = v.findViewById(R.id.etConfirmPassword);
        btnSave = v.findViewById(R.id.btnSave);

        pickImageLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(),
                new ActivityResultCallback<Uri>() {
                    @Override
                    public void onActivityResult(Uri uri) {
                        if (uri != null) {
                            pendingAvatarUri = uri;
                            imgAvatar.setImageURI(uri); // preview local
                        }
                    }
                });

        imgAvatar.setOnClickListener(view -> pickImageLauncher.launch("image/*"));
        btnChangeAvatar.setOnClickListener(view -> pickImageLauncher.launch("image/*"));

        loadFromFirebase();
        btnSave.setOnClickListener(view -> saveToCloudAndFirebase());
    }

    private void loadFromFirebase() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            toast("Not logged in");
            return;
        }
        if (user.getDisplayName() != null) etName.setText(user.getDisplayName());
        if (user.getEmail() != null) etEmail.setText(user.getEmail());
        // If you want to show remote avatar: use Glide/Picasso with user.getPhotoUrl()

        DocumentReference ref = db.collection("users").document(user.getUid());
        ref.get().addOnSuccessListener(snap -> {
            if (snap.exists()) {
                String phone = snap.getString("phone");
                String photo = snap.getString("photoUrl");
                if (!TextUtils.isEmpty(phone)) etPhone.setText(phone);
                // if (!TextUtils.isEmpty(photo)) Glide.with(this).load(photo).into(imgAvatar);
                uploadedPhotoUrl = photo;
            }
        });
    }

    private void saveToCloudAndFirebase() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            toast("Not logged in");
            return;
        }

        final String name = textOf(etName);
        final String email = textOf(etEmail);
        final String phone = textOf(etPhone);
        final String current = textOf(etCurrentPassword);
        final String newPass = textOf(etNewPassword);
        final String confirmPass = textOf(etConfirmPassword);

        if (TextUtils.isEmpty(name)) { toast("Please enter your name"); return; }
        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            toast("Please enter a valid email"); return;
        }

        boolean wantsPasswordChange = !TextUtils.isEmpty(newPass) || !TextUtils.isEmpty(confirmPass) || !TextUtils.isEmpty(current);
        if (wantsPasswordChange) {
            if (TextUtils.isEmpty(current)) { toast("Enter current password"); return; }
            if (TextUtils.isEmpty(newPass) || newPass.length() < 6) { toast("New password must be at least 6 characters"); return; }
            if (!TextUtils.equals(newPass, confirmPass)) { toast("New passwords do not match"); return; }
        }

        runWithProgress(true);

        // If a new avatar is chosen, upload to Cloudinary first; else proceed to Auth/Firestore.
        if (pendingAvatarUri != null) {
            uploadToCloudinary(pendingAvatarUri, new CloudinaryCallback() {
                @Override
                public void onSuccess(String secureUrl) {
                    uploadedPhotoUrl = secureUrl;
                    updateAuthProfileAndFirestore(user, name, email, phone, uploadedPhotoUrl,
                            wantsPasswordChange, current, newPass);
                }
                @Override
                public void onError(String message) {
                    runWithProgress(false);
                    toast("Avatar upload failed: " + message);
                }
            });
        } else {
            updateAuthProfileAndFirestore(user, name, email, phone, uploadedPhotoUrl,
                    wantsPasswordChange, current, newPass);
        }
    }

    // ----- Cloudinary unsigned upload -----
    private interface CloudinaryCallback {
        void onSuccess(String secureUrl);
        void onError(String message);
    }

    private void uploadToCloudinary(@NonNull Uri localImage, @NonNull CloudinaryCallback cb) {
        try {
            byte[] data = readAllBytesFromUri(localImage);

            MultipartBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", "avatar.jpg",
                            RequestBody.create(data, MediaType.parse("image/jpeg")))
                    .addFormDataPart("upload_preset", UPLOAD_PRESET)
                    // Optional: auto-foldering (if set in preset) or:
                    // .addFormDataPart("folder", "avatars")
                    .build();

            String url = "https://api.cloudinary.com/v1_1/" + CLOUD_NAME + "/image/upload";
            Request request = new Request.Builder().url(url).post(requestBody).build();

            http.newCall(request).enqueue(new Callback() {
                @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    runOnUi(() -> cb.onError(e.getMessage()));
                }
                @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        runOnUi(() -> cb.onError("HTTP " + response.code()));
                        return;
                    }
                    String body = response.body().string();
                    String secureUrl = extractSecureUrl(body);
                    if (TextUtils.isEmpty(secureUrl)) {
                        runOnUi(() -> cb.onError("No secure_url in response"));
                    } else {
                        runOnUi(() -> cb.onSuccess(secureUrl));
                    }
                }
            });
        } catch (Exception e) {
            cb.onError(e.getMessage());
        }
    }

    private String extractSecureUrl(String json) {
        try {
            JSONObject obj = new JSONObject(json);
            return obj.optString("secure_url", null);
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] readAllBytesFromUri(@NonNull Uri uri) throws IOException {
        try (InputStream in = requireContext().getContentResolver().openInputStream(uri);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) != -1) bos.write(buf, 0, r);
            return bos.toByteArray();
        }
    }

    // ----- Update Auth + Firestore (no Storage) -----
    private void updateAuthProfileAndFirestore(FirebaseUser user,
                                               String name,
                                               String email,
                                               String phone,
                                               @Nullable String photoUrl,
                                               boolean wantsPasswordChange,
                                               String currentPassword,
                                               String newPassword) {

        // 1) Update Auth profile (displayName & photo)
        UserProfileChangeRequest.Builder b = new UserProfileChangeRequest.Builder()
                .setDisplayName(name);
        if (!TextUtils.isEmpty(photoUrl)) b.setPhotoUri(Uri.parse(photoUrl));

        user.updateProfile(b.build())
                .addOnSuccessListener(unused -> {
                    // 2) Save extras to Firestore
                    Map<String, Object> data = new HashMap<>();
                    data.put("displayName", name);
                    data.put("email", email);
                    if (!TextUtils.isEmpty(photoUrl)) data.put("photoUrl", photoUrl);
                    if (!TextUtils.isEmpty(phone)) data.put("phone", phone);

                    db.collection("users").document(user.getUid())
                            .set(data, SetOptions.merge())
                            .addOnSuccessListener(unused1 -> updateSensitiveFields(user, email, wantsPasswordChange, currentPassword, newPassword))
                            .addOnFailureListener(e -> {
                                runWithProgress(false);
                                toast("Failed to save profile: " + e.getMessage());
                            });
                })
                .addOnFailureListener(e -> {
                    runWithProgress(false);
                    toast("Profile update failed: " + e.getMessage());
                });
    }

    private void updateSensitiveFields(FirebaseUser user,
                                       String newEmail,
                                       boolean wantsPasswordChange,
                                       String currentPassword,
                                       String newPassword) {
        boolean wantsEmailChange = !TextUtils.equals(user.getEmail(), newEmail);

        if (!wantsEmailChange && !wantsPasswordChange) {
            runWithProgress(false);
            toast("Profile saved");
            clearPasswordFields();
            return;
        }

        String currentEmail = user.getEmail();
        if (TextUtils.isEmpty(currentEmail)) {
            runWithProgress(false);
            toast("Missing current email for re-auth");
            return;
        }

        AuthCredential cred = EmailAuthProvider.getCredential(currentEmail, currentPassword);
        user.reauthenticate(cred).addOnSuccessListener(unused -> {
            if (wantsEmailChange) {
                user.updateEmail(newEmail).addOnFailureListener(e ->
                        toast("Email update failed: " + e.getMessage()));
            }
            if (wantsPasswordChange) {
                user.updatePassword(newPassword).addOnFailureListener(e ->
                        toast("Password update failed: " + e.getMessage()));
            }
            runWithProgress(false);
            toast("Profile saved");
            clearPasswordFields();
        }).addOnFailureListener(e -> {
            runWithProgress(false);
            toast("Re-auth failed: " + e.getMessage());
        });
    }

    private void clearPasswordFields() {
        etCurrentPassword.setText("");
        etNewPassword.setText("");
        etConfirmPassword.setText("");
    }

    // ----- small utils -----
    private String textOf(TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }

    private void runWithProgress(boolean running) {
        // TODO hook a ProgressBar/Dialog if you like
    }

    private void runOnUi(Runnable r) {
        if (isAdded()) requireActivity().runOnUiThread(r);
    }

    private void toast(String m) {
        Toast.makeText(requireContext(), m, Toast.LENGTH_SHORT).show();
    }
}
