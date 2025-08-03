package com.example.drysync;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.LinearLayout;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private LinearLayout navHome, navInventory, navStats, navRack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);  // Reference to your activity_main.xml

        // Initialize the navigation items
        navHome = findViewById(R.id.nav_home);
        navInventory = findViewById(R.id.nav_inventory);
        navStats = findViewById(R.id.nav_stats);
        navRack = findViewById(R.id.nav_rack);

        // Set up the click listeners for each navigation item
        navHome.setOnClickListener(v -> loadFragment(new HomeFragment()));  // Home Fragment
        navRack.setOnClickListener(v -> loadFragment(new RackFragment()));  // Rack Fragment
        navStats.setOnClickListener(v -> loadFragment(new StatsFragment())); // Stats Fragment

        // Set default fragment
        if (savedInstanceState == null) {
            loadFragment(new HomeFragment());  // Load Home Fragment initially
        }
    }

    // Method to load fragments dynamically
    private void loadFragment(Fragment fragment) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_container, fragment);  // Replace the container with the new fragment
        transaction.addToBackStack(null);  // Optional: Add to back stack if you want back navigation
        transaction.commit();  // Commit the transaction
    }

    @SuppressLint("MissingSuperCall")
    @Override
    public void onBackPressed() {
//        checker if the current fragment is the root
        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (currentFragment instanceof HomeFragment) {
//            If on the root fragment (HomeFragment), close the app
            finish();
        } else {
            getSupportFragmentManager().popBackStack();
        }
    }
}
