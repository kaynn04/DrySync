// MainActivity.java
package com.example.drysync;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.bumptech.glide.Glide;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    // Bottom nav
    private LinearLayout navHome, navInventory, navStats, navRack;
    private LinearLayout[] tabs; // for selected-state handling

    // Drawer
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private ImageView burgerIcon;

    private final FirebaseAuth mAuth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        // 🔻 Enable immersive after content view is set
        enableImmersiveMode();

        // Drawer wiring
        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navigationView);
        if (navigationView != null) {
            navigationView.setNavigationItemSelectedListener(this);
            setupNavHeader(); // ⬅️ Populate dynamic header
        }

        // Burger button
        burgerIcon = findViewById(R.id.burger_icon);
        if (burgerIcon != null) {
            burgerIcon.setOnClickListener(v -> {
                if (drawerLayout != null) drawerLayout.openDrawer(GravityCompat.START);
            });
        }

        // Bottom nav wiring
        navHome = findViewById(R.id.nav_home);
        navInventory = findViewById(R.id.nav_inventory);
        navStats = findViewById(R.id.nav_stats);
        navRack = findViewById(R.id.nav_rack);

        setupBottomNav();

        if (savedInstanceState == null) {
            loadFragment(new HomeFragment());
            setSelectedTab(navHome);
        }
    }

    // ⬇️ Immersive mode helper
    private void enableImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        controller.hide(WindowInsetsCompat.Type.systemBars());
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enableImmersiveMode();
    }

    private void setupBottomNav() {
        tabs = new LinearLayout[]{ navHome, navInventory, navStats, navRack };

        View.OnClickListener navClick = v -> {
            int id = v.getId();
            if (id == R.id.nav_home) {
                loadFragment(new HomeFragment());
                setSelectedTab(navHome);
            } else if (id == R.id.nav_inventory) {
                loadFragment(new BatchListFragment());
                setSelectedTab(navInventory);
            } else if (id == R.id.nav_stats) {
                loadFragment(new StatsFragment());
                setSelectedTab(navStats);
            } else if (id == R.id.nav_rack) {
                loadFragment(new RackFragment());
                setSelectedTab(navRack);
            }
        };

        for (LinearLayout t : tabs) if (t != null) t.setOnClickListener(navClick);
    }

    private void clearBottomSelection() {
        if (tabs == null) return;
        for (LinearLayout t : tabs) if (t != null) t.setSelected(false);
    }

    private void setSelectedTab(LinearLayout selected) {
        if (tabs == null || selected == null) return;
        for (LinearLayout t : tabs) if (t != null) t.setSelected(false);
        selected.setSelected(true);

        selected.animate()
                .scaleX(1.04f).scaleY(1.04f).setDuration(120)
                .withEndAction(() -> selected.animate().scaleX(1f).scaleY(1f).setDuration(80))
                .start();
    }

    // Load fragments into the same container the bottom nav uses
    private void loadFragment(Fragment fragment) {
        FragmentTransaction tx = getSupportFragmentManager().beginTransaction();
        tx.setReorderingAllowed(true);
        tx.replace(R.id.fragment_container, fragment);
        tx.addToBackStack(null);
        tx.commit();
    }

    // Drawer item → load a Fragment
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        clearBottomSelection();

        if (id == R.id.nav_profile) {
            loadFragment(new ProfileFragment());
        } else if (id == R.id.nav_notifications) {
            loadFragment(new NotificationsFragment());
        } else if (id == R.id.nav_settings) {
            loadFragment(new SettingsFragment());
        } else if (id == R.id.nav_help) {
            loadFragment(new HelpFragment());
        } else if (id == R.id.nav_about) {
            loadFragment(new AboutFragment());
        } else if (id == R.id.nav_privacy) {
            loadFragment(new PrivacyFragment());
        } else if (id == R.id.nav_logout) {
            confirmLogout();
        }

        if (drawerLayout != null) drawerLayout.closeDrawers();
        return true;
    }

    private void confirmLogout() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Log out")
                .setMessage("Are you sure you want to log out?")
                .setPositiveButton("Log out", (d, w) -> {
                    FirebaseAuth.getInstance().signOut();
                    Intent i = new Intent(this, SplashActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @SuppressLint("MissingSuperCall")
    @Override
    public void onBackPressed() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
            return;
        }

        Fragment current = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (current instanceof HomeFragment) {
            finish();
        } else {
            getSupportFragmentManager().popBackStack();
        }
    }

    // 🔹 Setup Navigation Header dynamically (Firestore + Auth fallback)
    public void setupNavHeader() {
        if (navigationView == null) return;

        View headerView = navigationView.getHeaderView(0);

        TextView tvUserName = headerView.findViewById(R.id.tvUserName);
        TextView tvUserEmail = headerView.findViewById(R.id.tvUserEmail);
        ImageView imgAvatar = headerView.findViewById(R.id.imgAvatar);

        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        tvUserName.setText(user.getDisplayName() != null ? user.getDisplayName() : "User");
        tvUserEmail.setText(user.getEmail());

        // 🔸 Step 1: Try Firestore for custom avatar
        DocumentReference ref = db.collection("users").document(user.getUid());
        ref.get().addOnSuccessListener(snap -> {
            if (snap.exists() && snap.contains("photoUrl")) {
                String photoUrl = snap.getString("photoUrl");
                if (photoUrl != null && !photoUrl.isEmpty()) {
                    Glide.with(this)
                            .load(photoUrl)
                            .placeholder(R.drawable.ic_person_24)
                            .circleCrop()
                            .into(imgAvatar);
                    return; // done
                }
            }

            // 🔸 Step 2: fallback to FirebaseAuth photoUrl
            Uri authPhoto = user.getPhotoUrl();
            if (authPhoto != null) {
                Glide.with(this)
                        .load(authPhoto)
                        .placeholder(R.drawable.ic_person_24)
                        .circleCrop()
                        .into(imgAvatar);
            } else {
                imgAvatar.setImageResource(R.drawable.ic_person_24);
            }
        });
    }
}
