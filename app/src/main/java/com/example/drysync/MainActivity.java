package com.example.drysync;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

public class MainActivity extends AppCompatActivity {

    private LinearLayout navHome, navInventory, navStats, navRack;
    private LinearLayout[] tabs; // for selected-state handling

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);  // Reference to your activity_main.xml

        // Initialize the navigation items
        navHome = findViewById(R.id.nav_home);
        navInventory = findViewById(R.id.nav_inventory);
        navStats = findViewById(R.id.nav_stats);
        navRack = findViewById(R.id.nav_rack);

        // Set up bottom nav (clicks + selected state)
        setupBottomNav();

        // Set default fragment + selected tab once
        if (savedInstanceState == null) {
            loadFragment(new HomeFragment());   // Load Home Fragment initially
            setSelectedTab(navHome);
        }
    }

    private void setupBottomNav() {
        tabs = new LinearLayout[] { navHome, navInventory, navStats, navRack };

        View.OnClickListener navClick = v -> {
            int id = v.getId();
            if (id == R.id.nav_home) {
                loadFragment(new HomeFragment());
                setSelectedTab(navHome);
            } else if (id == R.id.nav_inventory) {
                // Your new batches UI is here per your mapping
                loadFragment(new BatchListFragment());
                setSelectedTab(navInventory);
            } else if (id == R.id.nav_stats) {
                loadFragment(new StatsFragment());
                setSelectedTab(navStats);
            } else if (id == R.id.nav_rack) {
                loadFragment(new RackFragment()); // or new BatchListFragment() if you prefer
                setSelectedTab(navRack);
            }
        };

        for (LinearLayout t : tabs) t.setOnClickListener(navClick);
    }

    private void setSelectedTab(LinearLayout selected) {
        if (tabs == null) return;
        for (LinearLayout t : tabs) t.setSelected(false);
        selected.setSelected(true);


        selected.animate().scaleX(1.04f).scaleY(1.04f).setDuration(120).withEndAction(
             () -> selected.animate().scaleX(1f).scaleY(1f).setDuration(80)
        ).start();
    }

    // Method to load fragments dynamically
    private void loadFragment(Fragment fragment) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.setReorderingAllowed(true);
        transaction.replace(R.id.fragment_container, fragment);
        transaction.addToBackStack(null);  // keep if you want back navigation between tabs
        transaction.commit();
    }

    @SuppressLint("MissingSuperCall")
    @Override
    public void onBackPressed() {
        // checker if the current fragment is the root
        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (currentFragment instanceof HomeFragment) {
            // If on the root fragment (HomeFragment), close the app
            finish();
        } else {
            getSupportFragmentManager().popBackStack();
        }
    }
}
