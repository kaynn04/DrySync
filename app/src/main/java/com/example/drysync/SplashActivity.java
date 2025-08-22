package com.example.drysync;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser != null) {
            // User already logged in → go to main/home
            startActivity(new Intent(SplashActivity.this, MainActivity.class));
        } else {
            // User not logged in → go to login
            startActivity(new Intent(SplashActivity.this, LoginActivity.class));
        }

        finish(); // close Splash so user can’t go back here
    }
}
