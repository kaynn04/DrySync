package com.example.drysync;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    EditText etFullName, etEmail, etPassword, etPhone;
    Button btnRegister;
    FirebaseAuth mAuth;
    FirebaseFirestore db;
    TextView tvLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.register_page);

        etFullName = findViewById(R.id.etFullName);
        etPhone = findViewById(R.id.etPhone);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnRegister = findViewById(R.id.btnRegister);
        tvLogin = findViewById(R.id.tvLogin);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        btnRegister.setOnClickListener(v -> registerUser());
        tvLogin.setOnClickListener(v -> startActivity(new Intent(this, LoginActivity.class)));
    }

    private void registerUser() {
        String fullname = etFullName.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        // --- Input Validation ---
        if (TextUtils.isEmpty(fullname)) {
            etFullName.setError("Full name required");
            return;
        }
        if (TextUtils.isEmpty(phone) || phone.length() < 11) {
            etPhone.setError("Phone number must be at least 11 digits");
            return;
        }
        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Valid email required");
            return;
        }
        if (TextUtils.isEmpty(password) || password.length() < 6) {
            etPassword.setError("Password must be at least 6 characters");
            return;
        }

        // --- Check if email already exists ---
        mAuth.fetchSignInMethodsForEmail(email)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null &&
                            !task.getResult().getSignInMethods().isEmpty()) {
                        etEmail.setError("Email already registered");
                        return;
                    }
                    // Email is free -> proceed to create account
                    createFirebaseUser(fullname, phone, email, password);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to check email: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void createFirebaseUser(String fullname, String phone, String email, String password) {
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Toast.makeText(this, "Error: " +
                                        (task.getException() != null ? task.getException().getMessage() : "Unknown"),
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user == null) {
                        Toast.makeText(this, "Registration succeeded, but user is null.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Send email verification
                    user.sendEmailVerification()
                            .addOnCompleteListener(emailTask -> {
                                if (emailTask.isSuccessful()) {
                                    Toast.makeText(this, "Verification email sent to " + email, Toast.LENGTH_LONG).show();
                                } else {
                                    Toast.makeText(this, "Failed to send verification email: " +
                                                    (emailTask.getException() != null ? emailTask.getException().getMessage() : "Unknown"),
                                            Toast.LENGTH_LONG).show();
                                }
                            });

                    // Update display name
                    UserProfileChangeRequest req = new UserProfileChangeRequest.Builder()
                            .setDisplayName(fullname)
                            .build();

                    user.updateProfile(req).addOnCompleteListener(upd -> saveUserToFirestore(user, fullname, phone, email));
                });
    }


    private void saveUserToFirestore(FirebaseUser user, String fullname, String phone, String email) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("displayName", fullname);
        doc.put("phone", phone);
        doc.put("email", email);
        doc.put("createdAt", FieldValue.serverTimestamp());

        db.collection("users")
                .document(user.getUid())
                .set(doc, SetOptions.merge())
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Registration Successful", Toast.LENGTH_SHORT).show();
                    finish(); // Go back to login
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Saved user but Firestore failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    finish();
                });
    }
}
