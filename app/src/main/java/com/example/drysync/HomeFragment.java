package com.example.drysync;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class HomeFragment extends Fragment {

    // ---- paths (change if your schema differs) ----
    private static final String SENSORS_PATH = "Sensors";
    private static final String BATCHES_PATH = "batches";

    private TextView temp_text, humid_text;

    // Tiles
    private TextView tvActive, tvInactive, tvComplete, tvDrying, tvIncoming, tvAvailable;

    // Firebase refs + listeners
    private DatabaseReference sensorsRef, batchesRef;
    private ValueEventListener sensorsListener, batchesListener;

    private static final int LOW_STOCK_THRESHOLD = 10;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        // Temperature/Humidity (your existing)
        temp_text = view.findViewById(R.id.temperature_text);
        humid_text = view.findViewById(R.id.humidity_text);

        FirebaseHelper.retrieveFloatData("Environment/Temperature", new FirebaseHelper.FloatDataCallback() {
            @Override public void onFloatReceived(float value) {
                temp_text.setText(value + "°C");
                Log.e("FirebaseDebug", "Temperature: " + value);
            }
            @Override public void onError(String errorMessage) {
                temp_text.setText(errorMessage);
                Log.e("FirebaseDebug", "Error: " + errorMessage);
            }
        });
        FirebaseHelper.retrieveFloatData("Environment/Humidity", new FirebaseHelper.FloatDataCallback() {
            @Override public void onFloatReceived(float value) {
                humid_text.setText(value + "%");
                Log.e("FirebaseDebug", "Humidity: " + value);
            }
            @Override public void onError(String errorMessage) {
                humid_text.setText(errorMessage);
                Log.e("FirebaseDebug", "Error: " + errorMessage);
            }
        });

        // ---- Bind stats tiles ----
        tvActive   = view.findViewById(R.id.tvActive);
        tvInactive = view.findViewById(R.id.tvInactive);
        tvComplete = view.findViewById(R.id.tvComplete);
        tvDrying   = view.findViewById(R.id.tvDrying);
        tvIncoming = view.findViewById(R.id.tvIncoming);
        tvAvailable= view.findViewById(R.id.tvAvailable);

        // ---- Realtime listeners ----
        sensorsRef = FirebaseDatabase.getInstance().getReference(SENSORS_PATH);
        batchesRef = FirebaseDatabase.getInstance().getReference(BATCHES_PATH);

        attachSensorsListener();
        attachBatchesListener();

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Detach listeners to avoid leaks
        if (sensorsRef != null && sensorsListener != null) {
            sensorsRef.removeEventListener(sensorsListener);
            sensorsListener = null;
        }
        if (batchesRef != null && batchesListener != null) {
            batchesRef.removeEventListener(batchesListener);
            batchesListener = null;
        }
    }

    // ---------------- Sensors -> Active / Inactive / Complete / Drying ----------------
    private void attachSensorsListener() {
        sensorsListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                int active = 0, inactive = 0, complete = 0, drying = 0;

                for (DataSnapshot slot : snapshot.getChildren()) {
                    String status = null;
                    Object raw = slot.child("Status").getValue();
                    if (raw != null) status = String.valueOf(raw);

                    String s = status == null ? "" : status.trim().toLowerCase();

                    if (s.equals("inactive")) {
                        inactive++;
                    } else if (s.equals("complete")) {
                        complete++;
                    } else {
                        // any other value means it's currently active and still drying
                        active++;
                        drying++;
                    }
                }

                setTextSafe(tvActive,   String.valueOf(active));
                setTextSafe(tvInactive, String.valueOf(inactive));
                setTextSafe(tvComplete, String.valueOf(complete));
                setTextSafe(tvDrying,   String.valueOf(drying));
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {
                Log.w("HomeFragment", "Sensors listener cancelled: " + error.getMessage());
            }
        };
        sensorsRef.addValueEventListener(sensorsListener);
    }

    // ---------------- Batches -> Incoming / Available ----------------
    private void attachBatchesListener() {
        batchesListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                int lowStock = 0;
                int available = 0;

                for (DataSnapshot batch : snapshot.getChildren()) {
                    int total    = asInt(batch.child("totalQuantity").getValue(), 0);
                    int inRack   = firstInt(batch.child("inRackCount").getValue(),
                            batch.child("inRack").getValue());
                    int finished = firstInt(batch.child("finishedCount").getValue(),
                            batch.child("finished").getValue());

                    int remaining = Math.max(0, total - inRack - finished);

                    if (remaining > 0) available++;                                  // unchanged
                    if (remaining > 0 && remaining < LOW_STOCK_THRESHOLD) lowStock++; // NEW
                }

                // Reuse tvIncoming to show Low Stock Batches
                setTextSafe(tvIncoming, String.valueOf(lowStock));
                setTextSafe(tvAvailable, String.valueOf(available));
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {
                Log.w("HomeFragment", "Batches listener cancelled: " + error.getMessage());
            }
        };
        batchesRef.addValueEventListener(batchesListener);
    }


    // ---------------- helpers ----------------
    private void setTextSafe(TextView tv, String txt) {
        if (tv != null) tv.setText(txt);
    }

    private int asInt(Object v, int def) {
        if (v instanceof Number) return ((Number) v).intValue();
        if (v instanceof String) {
            try { return Integer.parseInt((String) v); } catch (Exception ignore) {}
        }
        return def;
    }

    private int firstInt(Object... vals) {
        for (Object v : vals) {
            if (v instanceof Number) return ((Number) v).intValue();
            if (v instanceof String) {
                try { return Integer.parseInt((String) v); } catch (Exception ignore) {}
            }
        }
        return 0;
    }
}
