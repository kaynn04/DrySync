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
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
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

    private final HashMap<Integer, String> lastStatus = new HashMap<>();
    private final HashSet<Integer> assignDialogOpenFor = new HashSet<>();
    private final HashSet<Integer> sizeDialogOpenFor   = new HashSet<>();
    private final HashSet<Integer> finishDialogOpenFor = new HashSet<>();
    private final HashSet<Integer> promptedComplete    = new HashSet<>();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
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

            // -------- Sensor status listener --------
            FirebaseHelper.retrieveStringData("Sensors/" + slot + "/Status", new FirebaseHelper.StringDataCallback() {
                @Override public void onStringReceived(String value) {
                    String prev = lastStatus.get(slot);
                    String now = (value == null ? "" : value.trim());
                    lastStatus.put(slot, now);

                    // UI
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

                    // Switch reflects "Complete"
                    boolean isComplete = "Complete".equalsIgnoreCase(now);
                    if (statusSwitch.isChecked() != isComplete) {
                        statusSwitch.setOnCheckedChangeListener(null);
                        statusSwitch.setChecked(isComplete);
                        statusSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
                            if (!btn.isPressed()) return;
                            if (isChecked) confirmFinish(slot, null);
                        });
                    }

                    // Transitions
                    if (!equalsIgnoreCase(prev, "Active") && "Active".equalsIgnoreCase(now)) {
                        // Only prompt if NOT fully assigned
                        maybePromptAssign(slot);
                    }
                    if (!equalsIgnoreCase(prev, "Complete") && "Complete".equalsIgnoreCase(now)) {
                        maybePromptFinish(slot);
                        promptedComplete.add(slot);
                    }
                    if ("Complete".equalsIgnoreCase(prev) && !"Complete".equalsIgnoreCase(now)) {
                        promptedComplete.remove(slot);
                    }
                    if (prev != null && !"Inactive".equalsIgnoreCase(prev) && "Inactive".equalsIgnoreCase(now)) {
                        handleAutoOnInactive(slot, prev);
                    }
                }
                @Override public void onError(String errorMessage) {
                    Log.e("RackFragment", "Status error: " + errorMessage);
                }
            });

            // Sensor value
            FirebaseHelper.retrieveFloatData("Sensors/" + slot + "/Value", new FirebaseHelper.FloatDataCallback() {
                @Override public void onFloatReceived(float value) { tvValue.setText(value + "%"); }
                @Override public void onError(String errorMessage) {
                    Log.e("RackFragment", "Value error: " + errorMessage);
                    tvValue.setText("Error");
                }
            });

            // -------- Card tap behavior --------
            slotView.setOnClickListener(v -> {
                String status = lastStatus.get(slot);
                if (status == null) status = "Inactive";

                DatabaseReference slotRef = FirebaseDatabase.getInstance()
                        .getReference("RackSlots").child(String.valueOf(slot));

                String finalStatus = status;
                slotRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot ds) {
                        String batchId = ds.child("batchId").getValue(String.class);
                        Integer pcs    = ds.child("pcs").getValue(Integer.class);
                        String sizeKey = ds.child("sizeKey").getValue(String.class);
                        boolean hasAssignment = (batchId != null && pcs != null && pcs > 0);
                        boolean hasSize = (sizeKey != null && !sizeKey.trim().isEmpty());

                        if ("Inactive".equalsIgnoreCase(finalStatus)) {
                            toast("Slot " + slot + " is empty. Wait for sensor to become Active.");
                            return;
                        }

                        if ("Active".equalsIgnoreCase(finalStatus)) {
                            if (!hasAssignment) {
                                showAssignWizard(slot); // Batch -> Size -> Confirm
                            } else if (!hasSize) {
                                showSizeStep(slot, batchId); // only size missing
                            } else {
                                showSizePickerForBatch(slot, batchId); // change size if needed
                            }
                            return;
                        }

                        if ("Complete".equalsIgnoreCase(finalStatus)) {
                            if (hasAssignment) confirmFinish(slot, batchId);
                            else toast("Slot " + slot + " is Complete (no batch recorded).");
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {
                        toast("Error: " + error.getMessage());
                    }
                });
            });

            // Manual toggle to Complete
            statusSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
                if (!btn.isPressed()) return;
                if (isChecked) confirmFinish(slot, null);
            });

            // Grid params
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0; params.height = 350; params.bottomMargin = 50;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            slotView.setLayoutParams(params);
            gridLayout.addView(slotView);
        }
    }

    // ------------------ Prompt only when needed ------------------

    /** Only opens a dialog if the slot is NOT fully assigned (batch + sizeKey). */
    private void maybePromptAssign(int slot) {
        if (assignDialogOpenFor.contains(slot)) return;

        DatabaseReference slotRef = FirebaseDatabase.getInstance()
                .getReference("RackSlots").child(String.valueOf(slot));

        slotRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot ds) {
                String batchId = ds.child("batchId").getValue(String.class);
                Integer pcs    = ds.child("pcs").getValue(Integer.class);
                String sizeKey = ds.child("sizeKey").getValue(String.class);

                boolean hasBatch   = (batchId != null && pcs != null && pcs > 0);
                boolean hasSize    = (sizeKey != null && !sizeKey.trim().isEmpty());
                boolean fullAssign = hasBatch && hasSize;

                if (!isAdded() || fullAssign) return; // do nothing if already complete assignment

                if (hasBatch) {
                    // batch exists but missing size → go directly to size step
                    showSizeStep(slot, batchId);
                } else {
                    // no assignment → run full wizard
                    showAssignWizard(slot);
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { /* no-op */ }
        });
    }

    // ------------------ Assign Wizard: Batch -> Size -> Confirm (single write) ------------------

    private void showAssignWizard(int slot) {
        if (assignDialogOpenFor.contains(slot)) return;
        assignDialogOpenFor.add(slot);

        FirebaseHelper.loadBatchesOnce((allBatches, err) -> {
            if (err != null) { toast("Load batches failed: " + err); assignDialogOpenFor.remove(slot); return; }

            List<WoodBatch> selectable = new ArrayList<>();
            for (WoodBatch wb : allBatches) if (wb != null && wb.getRemaining() > 0) selectable.add(wb);
            final List<WoodBatch> batches = selectable.isEmpty() ? allBatches : selectable;
            if (batches.isEmpty()) { toast("No batches found."); assignDialogOpenFor.remove(slot); return; }

            String[] labels = new String[batches.size()];
            for (int i = 0; i < batches.size(); i++) {
                WoodBatch wb = batches.get(i);
                labels[i] = wb.getBatchId() + " • remaining: " + wb.getRemaining();
            }

            final int[] selBatch = {0};
            AlertDialog step1 = new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Assign to Slot " + slot + " — Pick Batch")
                    .setSingleChoiceItems(labels, 0, (d, which) -> selBatch[0] = which)
                    .setPositiveButton("Next", (d, w) -> {
                        String batchId = batches.get(selBatch[0]).getBatchId();
                        showSizeStep(slot, batchId);
                    })
                    .setNegativeButton("Cancel", (d,w) -> assignDialogOpenFor.remove(slot))
                    .create();

            step1.show();
        });
    }

    private void showSizeStep(int slot, @NonNull String batchId) {
        FirebaseHelper.loadBatchSizeLines(batchId, (lines, err2) -> {
            if (err2 != null) { toast("Load sizes failed: " + err2); assignDialogOpenFor.remove(slot); return; }

            List<FirebaseHelper.SizeLine> avail = new ArrayList<>();
            for (FirebaseHelper.SizeLine sl : lines) if (sl.remaining() > 0) avail.add(sl);
            if (avail.isEmpty()) { toast("No available sizes in " + batchId + "."); assignDialogOpenFor.remove(slot); return; }

            String[] sizeLabels = new String[avail.size()];
            for (int i = 0; i < avail.size(); i++) {
                FirebaseHelper.SizeLine sl = avail.get(i);
                sizeLabels[i] = sl.remaining() + " pcs • " + sl.label();
            }
            final int[] selSize = {0};

            AlertDialog step2 = new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Assign to Slot " + slot + " — Pick Size")
                    .setSingleChoiceItems(sizeLabels, 0, (d, which) -> selSize[0] = which)
                    .setPositiveButton("Confirm", (d, w) -> {
                        FirebaseHelper.SizeLine chosen = avail.get(selSize[0]);
                        // SINGLE write (no double minus)
                        FirebaseHelper.assignBatchAndSize(batchId, slot, chosen.key, err3 -> {
                            if (err3 != null) toast("Assign failed: " + err3);
                            else toast("Assigned " + batchId + " (" + chosen.label() + ") to slot " + slot);
                        });
                        assignDialogOpenFor.remove(slot);
                    })
                    .setNegativeButton("Back", (d,w) -> {
                        // back to batch step (keep guard set)
                        showAssignWizard(slot);
                    })
                    .setOnDismissListener(x -> assignDialogOpenFor.remove(slot))
                    .create();

            step2.show();
        });
    }

    // ------------------ Complete / Auto logic ------------------

    private void maybePromptFinish(int slot) {
        if (finishDialogOpenFor.contains(slot)) return;
        if (promptedComplete.contains(slot)) return;
        DatabaseReference slotRef = FirebaseDatabase.getInstance().getReference("RackSlots").child(String.valueOf(slot));
        slotRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot ds) {
                String batchId = ds.child("batchId").getValue(String.class);
                Integer pcs = ds.child("pcs").getValue(Integer.class);
                if (batchId != null && pcs != null && pcs > 0 && isAdded()) confirmFinish(slot, batchId);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { }
        });
    }

    private void handleAutoOnInactive(int slot, String previousStatus) {
        DatabaseReference slotRef = FirebaseDatabase.getInstance().getReference("RackSlots").child(String.valueOf(slot));
        slotRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot ds) {
                String batchId = ds.child("batchId").getValue(String.class);
                Integer pcs    = ds.child("pcs").getValue(Integer.class);
                boolean hasAssignment = (batchId != null && pcs != null && pcs > 0);
                if (!hasAssignment) return;

                if ("Complete".equalsIgnoreCase(previousStatus)) {
                    FirebaseHelper.finishSlot(slot, err -> {
                        if (err != null) toast("Auto-finish failed: " + err);
                        else toast("Slot " + slot + " auto-finished after removal.");
                    });
                } else {
                    FirebaseHelper.clearSlot(slot, err -> {
                        if (err != null) toast("Auto-clear failed: " + err);
                        else toast("Slot " + slot + " auto-cleared.");
                    });
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { }
        });
    }

    private void confirmFinish(int slot, @Nullable String knownBatchId) {
        if (finishDialogOpenFor.contains(slot)) return;
        finishDialogOpenFor.add(slot);

        DatabaseReference slotRef = FirebaseDatabase.getInstance().getReference("RackSlots").child(String.valueOf(slot));
        slotRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot ds) {
                String batchId = (knownBatchId != null) ? knownBatchId : ds.child("batchId").getValue(String.class);
                Integer pcs    = ds.child("pcs").getValue(Integer.class);
                boolean hasAssignment = (batchId != null && pcs != null && pcs > 0);

                AlertDialog dlg = new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Finish slot " + slot + "?")
                        .setMessage(hasAssignment
                                ? "Move 1 piece from In-Rack → Finished for batch " + batchId + "?"
                                : "No batch recorded for this slot.")
                        .setPositiveButton(hasAssignment ? "Mark Finished" : "OK", (d, w) -> {
                            if (!hasAssignment) return;
                            FirebaseHelper.finishSlot(slot, err -> {
                                if (err != null) toast("Finish failed: " + err);
                                else toast("Batch updated and slot cleared.");
                            });
                        })
                        .setNegativeButton("Cancel", null)
                        .create();

                dlg.setOnDismissListener(x -> finishDialogOpenFor.remove(slot));
                dlg.show();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {
                finishDialogOpenFor.remove(slot);
                toast("Error: " + error.getMessage());
            }
        });
    }

    // ------------------ Size picker for already-assigned Active slot ------------------

    private void showSizePickerForBatch(int slot, @NonNull String batchId) {
        if (sizeDialogOpenFor.contains(slot)) return;
        sizeDialogOpenFor.add(slot);

        DatabaseReference slotRef = FirebaseDatabase.getInstance().getReference("RackSlots").child(String.valueOf(slot));
        slotRef.child("sizeKey").get().addOnCompleteListener(prevTask -> {
            String currentKey = null;
            if (prevTask.isSuccessful() && prevTask.getResult() != null) {
                Object v = prevTask.getResult().getValue();
                currentKey = (v == null) ? null : String.valueOf(v);
            }

            String finalCurrentKey = currentKey;
            FirebaseHelper.loadBatchSizeLines(batchId, (lines, err) -> {
                if (err != null) { toast("Load sizes failed: " + err); sizeDialogOpenFor.remove(slot); return; }
                List<FirebaseHelper.SizeLine> avail = new ArrayList<>();
                for (FirebaseHelper.SizeLine sl : lines) if (sl.remaining() > 0) avail.add(sl);
                if (avail.isEmpty()) { toast("No available sizes in " + batchId + "."); sizeDialogOpenFor.remove(slot); return; }

                String[] labels = new String[avail.size()];
                int pre = 0;
                for (int i = 0; i < avail.size(); i++) {
                    FirebaseHelper.SizeLine sl = avail.get(i);
                    labels[i] = sl.remaining() + " pcs • " + sl.label();
                    if (finalCurrentKey != null && finalCurrentKey.equals(sl.key)) pre = i;
                }
                final int[] sel = { pre };

                AlertDialog dlg = new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Pick Size (" + batchId + ")")
                        .setSingleChoiceItems(labels, pre, (d, which) -> sel[0] = which)
                        .setPositiveButton("Save", (d, w) -> {
                            FirebaseHelper.SizeLine chosen = avail.get(sel[0]);
                            // Net adjust counts: old size -1, new size +1 (batch total net 0)
                            FirebaseHelper.assignBatchAndSize(batchId, slot, chosen.key, err2 -> {
                                if (err2 != null) toast("Size save failed: " + err2);
                                else toast("Size set: " + chosen.label());
                            });
                        })
                        .setNegativeButton("Cancel", null)
                        .create();

                dlg.setOnDismissListener(x -> sizeDialogOpenFor.remove(slot));
                dlg.show();
            });
        });
    }

    // ------------------ Utils ------------------

    private static boolean equalsIgnoreCase(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equalsIgnoreCase(b);
    }

    private void toast(String s) { Toast.makeText(requireContext(), s, Toast.LENGTH_SHORT).show(); }
}
