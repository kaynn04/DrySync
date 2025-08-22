package com.example.drysync;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
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
    FirebaseFirestore db; // Firestore

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.register_page);

        etFullName = findViewById(R.id.etFullName);
        etPhone = findViewById(R.id.etPhone);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnRegister = findViewById(R.id.btnRegister);


        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        btnRegister.setOnClickListener(v -> {
            String fullname = etFullName.getText().toString().trim();
            String phone = etPhone.getText().toString().trim();
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            if (TextUtils.isEmpty(fullname)) {
                etFullName.setError("Full name required");
                return;
            }
            if (TextUtils.isEmpty(phone) || phone.length() < 11) {
                etPhone.setError("Phone number must be 11 digits");
                return;
            }
            if (TextUtils.isEmpty(email)) {
                etFullName.setError("Email Required");
                return;
            }
            if (TextUtils.isEmpty(password) || password.length() < 6) {
                etFullName.setError("Password must be at least 6 characters");
                return;
            }

            mAuth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(task -> {
                        if (!task.isSuccessful()) {
                            Toast.makeText(RegisterActivity.this, "Error: " + (task.getException() != null ? task.getException().getMessage() : "Unknown"), Toast.LENGTH_SHORT).show();
                            return;
                        }

                        // ---- Set displayName in Firebase Auth ----
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user == null) {
                            Toast.makeText(this, "Registration succeeded, but user is null.", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        UserProfileChangeRequest req = new UserProfileChangeRequest.Builder()
                                .setDisplayName(fullname)
                                .build();

                        user.updateProfile(req).addOnCompleteListener(upd -> {
                            Map<String, Object> doc = new HashMap<>();
                            doc.put("displayName", fullname);
                            doc.put("phone", phone);
                            doc.put("email", email);
                            doc.put("createdAt", FieldValue.serverTimestamp());

                            db.collection("users")
                                    .document(user.getUid())
                                    .set(doc, SetOptions.merge())
                                    .addOnSuccessListener(unused -> {
                                        Toast.makeText(RegisterActivity.this, "Registration Successful", Toast.LENGTH_SHORT).show();
                                        finish(); // go back to login
                                    })
                                    .addOnFailureListener(e -> {
                                        Toast.makeText(RegisterActivity.this, "Saved user but Firestore failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                        finish(); // still finish; you can choose to stay instead
                                    });
                        });
                    });
        });
    }
}
