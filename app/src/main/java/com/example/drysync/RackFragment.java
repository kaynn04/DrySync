package com.example.drysync;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class RackFragment extends Fragment {

    // Track last known status per slot to detect transitions
    private final HashMap<Integer, String> lastStatus = new HashMap<>();
    // Prevent duplicate pickers for the same slot while one dialog is open
    private final HashSet<Integer> dialogOpenForSlot = new HashSet<>();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_rack, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View root, @Nullable Bundle savedInstanceState) {
        GridLayout gridLayout = root.findViewById(R.id.my_grid_layout);
        LayoutInflater inflater = LayoutInflater.from(getContext());

        for (int i = 1; i <= 10; i++) {
            View slotView = inflater.inflate(R.layout.slot_item, gridLayout, false);

            TextView title = slotView.findViewById(R.id.slotTitle);
            TextView tvValue = slotView.findViewById(R.id.valueText);
            Switch statusSwitch = slotView.findViewById(R.id.statusSwitch);
            LinearLayout layout = slotView.findViewById(R.id.layout);

            final int slot = i;
            title.setText("Wood Slot " + slot);

            // --- SENSOR STATUS (drives all actions) ---
            FirebaseHelper.retrieveStringData("Sensors/" + slot + "/Status", new FirebaseHelper.StringDataCallback() {
                @Override public void onStringReceived(String value) {
                    String prev = lastStatus.get(slot);
                    String now = value == null ? "" : value.trim();
                    lastStatus.put(slot, now);

                    // UI look
                    if ("Inactive".equalsIgnoreCase(now)) {
                        tvValue.setVisibility(View.INVISIBLE);
                        statusSwitch.setVisibility(View.INVISIBLE);
                        layout.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.rack_background));
                        title.setTextColor(ContextCompat.getColor(requireContext(), R.color.brown));
                        tvValue.setTextColor(ContextCompat.getColor(requireContext(), R.color.brown));
                    } else {
                        tvValue.setVisibility(View.VISIBLE);
                        statusSwitch.setVisibility(View.VISIBLE);
                        layout.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.rack_background_inactive));
                        title.setTextColor(Color.WHITE);
                        tvValue.setTextColor(Color.WHITE);
                    }
                    statusSwitch.setChecked("Complete".equalsIgnoreCase(now));

                    // --- Transition-based logic ---
                    // Became ACTIVE -> prompt to assign (only time user can choose a batch)
                    if (!equalsIgnoreCase(prev, "Active") && "Active".equalsIgnoreCase(now)) {
                        maybePromptAssign(slot);
                    }

                    // Became COMPLETE -> ask to finish (if assigned)
                    if (!equalsIgnoreCase(prev, "Complete") && "Complete".equalsIgnoreCase(now)) {
                        maybePromptFinish(slot);
                    }

                    // Returned to INACTIVE from something else -> auto action
                    if (prev != null && !"Inactive".equalsIgnoreCase(prev) && "Inactive".equalsIgnoreCase(now)) {
                        handleAutoOnInactive(slot, prev);
                    }
                }
                @Override public void onError(String errorMessage) {
                    Log.e("RackFragment", "Status error: " + errorMessage);
                }
            });

            // --- SENSOR VALUE (display only) ---
            FirebaseHelper.retrieveFloatData("Sensors/" + slot + "/Value", new FirebaseHelper.FloatDataCallback() {
                @Override public void onFloatReceived(float value) { tvValue.setText(value + "%"); }
                @Override public void onError(String errorMessage) {
                    Log.e("RackFragment", "Value error: " + errorMessage);
                    tvValue.setText("Error");
                }
            });

            // --- CARD TAP: no manual assign when Inactive; info only when Active/Complete ---
            slotView.setOnClickListener(v -> {
                String status = lastStatus.get(slot);
                if (status == null) status = "Inactive";

                DatabaseReference slotRef = FirebaseDatabase.getInstance().getReference("RackSlots").child(String.valueOf(slot));
                String finalStatus = status;
                slotRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot ds) {
                        String batchId = ds.child("batchId").getValue(String.class);
                        Integer pcs = ds.child("pcs").getValue(Integer.class);
                        boolean hasAssignment = (batchId != null && pcs != null && pcs > 0);

                        if ("Inactive".equalsIgnoreCase(finalStatus)) {
                            toast("Slot " + slot + " is empty. Wait for sensor to become Active.");
                            return;
                        }

                        if ("Active".equalsIgnoreCase(finalStatus)) {
                            if (hasAssignment) {
                                toast("Slot " + slot + " is occupied by " + batchId + ".");
                            } else {
                                // Slot is Active and unassigned (user may have missed the popup) — show picker
                                showAssignDialogOne(slot);
                            }
                            return;
                        }

                        if ("Complete".equalsIgnoreCase(finalStatus)) {
                            if (hasAssignment) {
                                promptFinish(slot, batchId);
                            } else {
                                toast("Slot " + slot + " is Complete (no batch recorded).");
                            }
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {
                        toast("Error: " + error.getMessage());
                    }
                });
            });

            // Layout params for grid cell
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0; params.height = 350; params.bottomMargin = 50;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            slotView.setLayoutParams(params);
            gridLayout.addView(slotView);
        }
    }

    // ---- Auto actions on returning to Inactive ----
    private void handleAutoOnInactive(int slot, String previousStatus) {
        DatabaseReference slotRef = FirebaseDatabase.getInstance().getReference("RackSlots").child(String.valueOf(slot));
        slotRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot ds) {
                String batchId = ds.child("batchId").getValue(String.class);
                Integer pcs = ds.child("pcs").getValue(Integer.class);
                boolean hasAssignment = (batchId != null && pcs != null && pcs > 0);

                if (!hasAssignment) return; // nothing to do

                if ("Complete".equalsIgnoreCase(previousStatus)) {
                    // It was dry, and now wood was removed → auto-finish
                    FirebaseHelper.finishSlot(slot, err -> {
                        if (err != null) toast("Auto-finish failed: " + err);
                        else toast("Slot " + slot + " auto-finished after removal.");
                    });
                } else {
                    // It was active but not completed → wood removed early → auto-clear (return to remaining)
                    FirebaseHelper.clearSlot(slot, err -> {
                        if (err != null) toast("Auto-clear failed: " + err);
                        else toast("Slot " + slot + " auto-cleared.");
                    });
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { /* no-op */ }
        });
    }

    // ---- Prompt to assign exactly ONE piece (only allowed when Active) ----
    private void maybePromptAssign(int slot) {
        if (dialogOpenForSlot.contains(slot)) return;

        // If already assigned, skip
        DatabaseReference slotRef = FirebaseDatabase.getInstance().getReference("RackSlots").child(String.valueOf(slot));
        slotRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot ds) {
                String batchId = ds.child("batchId").getValue(String.class);
                Integer pcs = ds.child("pcs").getValue(Integer.class);
                boolean hasAssignment = (batchId != null && pcs != null && pcs > 0);
                if (!hasAssignment && isAdded()) showAssignDialogOne(slot);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { /* no-op */ }
        });
    }

    // ---- Prompt to finish when sensor becomes Complete ----
    private void maybePromptFinish(int slot) {
        DatabaseReference slotRef = FirebaseDatabase.getInstance().getReference("RackSlots").child(String.valueOf(slot));
        slotRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot ds) {
                String batchId = ds.child("batchId").getValue(String.class);
                Integer pcs = ds.child("pcs").getValue(Integer.class);
                boolean hasAssignment = (batchId != null && pcs != null && pcs > 0);
                if (hasAssignment && isAdded()) promptFinish(slot, batchId);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { /* no-op */ }
        });
    }

    private void promptFinish(int slot, String batchId) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Slot " + slot + " is COMPLETE")
                .setMessage("Move 1 piece from In-Rack → Finished for batch " + batchId + "?")
                .setPositiveButton("Mark Finished", (d, w) ->
                        FirebaseHelper.finishSlot(slot, err -> {
                            if (err != null) toast("Finish failed: " + err);
                            else toast("Batch updated and slot cleared.");
                        }))
                .setNegativeButton("Later", null)
                .show();
    }

    // ---- Batch picker (ONE piece) ----
    private void showAssignDialogOne(int slot) {
        if (!isAdded()) return;
        if (dialogOpenForSlot.contains(slot)) return;

        FirebaseHelper.loadBatchesOnce((allBatches, err) -> {
            if (err != null) { toast("Load batches failed: " + err); return; }

            // Show only batches with remaining > 0; if none, show all (but block assign)
            List<WoodBatch> selectable = new ArrayList<>();
            for (WoodBatch wb : allBatches) if (wb != null && wb.getRemaining() > 0) selectable.add(wb);
            final List<WoodBatch> display = selectable.isEmpty() ? allBatches : selectable;
            if (display.isEmpty()) { toast("No batches found under /Batches."); return; }

            String[] labels = new String[display.size()];
            for (int i = 0; i < display.size(); i++) {
                WoodBatch wb = display.get(i);
                int rem = wb.getRemaining();
                labels[i] = wb.getBatchId() + " • remaining: " + rem + (rem <= 0 ? " (FULL)" : "");
            }

            dialogOpenForSlot.add(slot);

            final int[] selected = {0};
            androidx.appcompat.app.AlertDialog dlg = new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Assign Batch to Slot " + slot)
                    .setSingleChoiceItems(labels, 0, (d, which) -> selected[0] = which)
                    .setPositiveButton("Assign", (d, w) -> {
                        WoodBatch chosen = display.get(selected[0]);
                        if (chosen.getRemaining() <= 0) { toast("Selected batch is FULL. Pick another."); return; }
                        FirebaseHelper.assignBatchToSlot(chosen.getBatchId(), slot, 1, resErr -> {
                            if (resErr != null) toast("Assign failed: " + resErr);
                            else toast("Assigned 1 piece from " + chosen.getBatchId() + " to slot " + slot);
                        });
                    })
                    .setNegativeButton("Cancel", null)
                    .create();

            dlg.setOnDismissListener(x -> dialogOpenForSlot.remove(slot));
            dlg.show();
        });
    }

    private static boolean equalsIgnoreCase(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equalsIgnoreCase(b);
    }

    private void toast(String s) {
        Toast.makeText(requireContext(), s, Toast.LENGTH_SHORT).show();
    }
}
