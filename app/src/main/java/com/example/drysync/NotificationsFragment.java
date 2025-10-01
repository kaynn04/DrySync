package com.example.drysync;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class NotificationsFragment extends Fragment {

    private RecyclerView recyclerView;
    private NotificationsAdapter adapter;
    private List<String> notificationsList = new ArrayList<>();
    private DatabaseReference notifRef;

    private final HashSet<Integer> alreadyAlertedSlots = new HashSet<>();
    private final HashMap<Integer, String> slotStatusMap = new HashMap<>();

    private static final String CHANNEL_ID = "wood_moisture_alerts";
    private static final int NOTIF_PERMISSION_REQUEST = 101;

    public NotificationsFragment() {}

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_notification, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.notificationsRecycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        adapter = new NotificationsAdapter(notificationsList);
        recyclerView.setAdapter(adapter);

        notifRef = FirebaseDatabase.getInstance().getReference("Notifications");

        // ✅ Ask notification permission on Android 13+
        requestNotificationPermission();

        // Create notification channel
        createNotificationChannel();

        // Load notifications into RecyclerView
        notifRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                notificationsList.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    String message = child.child("message").getValue(String.class);
                    Long timestamp = child.child("timestamp").getValue(Long.class);
                    if (message != null && timestamp != null) {
                        notificationsList.add(message + " • " +
                                DateFormat.format("hh:mm a", timestamp));
                    }
                }
                Collections.reverse(notificationsList);
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("NotificationsFragment", "Error: " + error.getMessage());
            }
        });

        // Monitor slots
        for (int i = 0; i <= 9; i++) {
            final int slot = i + 1;

            DatabaseReference statusRef = FirebaseDatabase.getInstance()
                    .getReference("Sensors")
                    .child(String.valueOf(i))
                    .child("Status");

            statusRef.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    String status = snapshot.getValue(String.class);
                    if (status != null) {
                        slotStatusMap.put(slot, status);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {}
            });

            DatabaseReference valueRef = FirebaseDatabase.getInstance()
                    .getReference("Sensors")
                    .child(String.valueOf(i))
                    .child("Value");

            valueRef.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    Float rawVal = snapshot.getValue(Float.class);
                    if (rawVal != null) {
                        double moisture = convertRawMoisture(rawVal.intValue());
                        String status = slotStatusMap.get(slot);

                        if ("Complete".equalsIgnoreCase(status) && moisture <= 12.0) {
                            if (!alreadyAlertedSlots.contains(slot)) {
                                alreadyAlertedSlots.add(slot);

                                long now = System.currentTimeMillis();
                                String alert = "[Slot " + slot + "] Moisture Critical: " +
                                        String.format("%.1f", moisture) + "% (" + status + ")";

                                notifRef.child("slot_" + slot).child("message").setValue(alert);
                                notifRef.child("slot_" + slot).child("timestamp").setValue(now);

                                // Show system notification
                                showSystemNotification(slot, alert);
                            }
                        } else {
                            alreadyAlertedSlots.remove(slot);
                            notifRef.child("slot_" + slot).removeValue();
                        }
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {}
            });
        }
    }

    // ✅ Ask for notification permission (Android 13+)
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (requireContext().checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                        NOTIF_PERMISSION_REQUEST);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIF_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("NotificationsFragment", "POST_NOTIFICATIONS granted");
            } else {
                Log.w("NotificationsFragment", "POST_NOTIFICATIONS denied by user");
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Moisture Alerts";
            String description = "Notifies when wood moisture is below threshold";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager =
                    requireContext().getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private void showSystemNotification(int slot, String alertText) {
        // For Android 13+, check permission before showing
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (requireContext().checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(requireContext(), CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("Wood Moisture Alert")
                .setContentText(alertText)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(requireContext());
        notificationManager.notify(slot, builder.build());
    }

    private double convertRawMoisture(int input) {
        int maxInput = 1023;
        double maxOutput = 24.0;
        if (input < 0) input = 0;
        if (input > maxInput) input = maxInput;
        return (input / (double) maxInput) * maxOutput;
    }
}
