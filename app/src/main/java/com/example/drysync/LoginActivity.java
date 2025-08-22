package com.example.drysync;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.drysync.MainActivity;
import com.google.firebase.Firebase;
import com.google.firebase.auth.FirebaseAuth;

public class LoginActivity extends AppCompatActivity {

   EditText etEmail, etPassword;
   Button btnLogin;
   TextView tvRegister;
   FirebaseAuth mAuth;

   @Override
   protected void onCreate(Bundle savedInstanceState) {
   super.onCreate(savedInstanceState);
   setContentView(R.layout.login_page);

   etEmail = findViewById(R.id.etUsername);
   etPassword = findViewById(R.id.etPassword);
   btnLogin = findViewById(R.id.btnLogin);
   tvRegister = findViewById(R.id.tvRegister);

   mAuth = FirebaseAuth.getInstance();

   btnLogin.setOnClickListener(v -> {
       String email = etEmail.getText().toString().trim();
       String password = etPassword.getText().toString().trim();

       if (TextUtils.isEmpty(email)) {
           etEmail.setError("Email required");
           return;
       }
       if (TextUtils.isEmpty(password)) {
           etPassword.setError("Password required");
           return;
       }

       mAuth.signInWithEmailAndPassword(email, password)
               .addOnCompleteListener(task -> {
                   if (task.isSuccessful()) {
                       Toast.makeText(LoginActivity.this, "Login Successful", Toast.LENGTH_SHORT).show();
                       startActivity(new Intent(LoginActivity.this, MainActivity.class));
                       finish();
                   } else {
                       Toast.makeText(LoginActivity.this, "Error: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                   }
               });
        });

       tvRegister.setOnClickListener(v -> {
           startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
       });
   }
}