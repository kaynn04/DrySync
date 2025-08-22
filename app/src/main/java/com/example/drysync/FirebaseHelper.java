package com.example.drysync;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FirebaseHelper {

    // ========= SENSOR HELPERS (used by Rack UI) =========

    public interface StringDataCallback {
        void onStringReceived(String value);
        void onError(String errorMessage);
    }

    /** Live-updates a String value (e.g., Sensors/{i}/Status). Returns the listener if you want to remove it later. */
    public static ValueEventListener retrieveStringData(String path, StringDataCallback cb) {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference(path);
        ValueEventListener vel = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                Object v = snapshot.getValue();
                cb.onStringReceived(v == null ? "" : String.valueOf(v));
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {
                cb.onError(error.getMessage());
            }
        };
        ref.addValueEventListener(vel);
        return vel;
    }

    public interface FloatDataCallback {
        void onFloatReceived(float value);
        void onError(String errorMessage);
    }

    /** Live-updates a numeric value (e.g., Sensors/{i}/Value). Accepts Number or numeric String. */
    public static ValueEventListener retrieveFloatData(String path, FloatDataCallback cb) {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference(path);
        ValueEventListener vel = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                Object v = snapshot.getValue();
                try {
                    float f;
                    if (v instanceof Number) f = ((Number) v).floatValue();
                    else if (v != null)      f = Float.parseFloat(String.valueOf(v));
                    else                     f = 0f;
                    cb.onFloatReceived(f);
                } catch (Exception e) {
                    cb.onError("Parse error: " + e.getMessage());
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {
                cb.onError(error.getMessage());
            }
        };
        ref.addValueEventListener(vel);
        return vel;
    }

    // ========= INVENTORY & RACK OPERATIONS =========

    public interface SimpleCallback { void onComplete(@Nullable String error); }

    /**
     * Assign (or reassign) a batch + qty to a rack slot.
     * Writes:
     *   - RackSlots/{slot}/batchId = newBatchId
     *   - RackSlots/{slot}/pcs     = qty
     *   - Batches/{...}/inRackCount adjusted atomically:
     *       * if slot previously empty:                   +qty
     *       * if same batch reassigned (qty changed):     +(qty - oldPcs)
     *       * if different old batch:                     oldBatch -oldPcs, newBatch +qty
     */
    public static void assignBatchToSlot(@NonNull String newBatchId, int slot, int qty, @Nullable SimpleCallback cb) {
        if (qty <= 0) { if (cb != null) cb.onComplete("Quantity must be > 0"); return; }

        DatabaseReference root = FirebaseDatabase.getInstance().getReference();
        DatabaseReference slotRef = root.child("RackSlots").child(String.valueOf(slot));

        slotRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot ds) {
                String oldBatchId = ds.child("batchId").getValue(String.class);
                Integer oldPcs    = ds.child("pcs").getValue(Integer.class);
                if (oldPcs == null) oldPcs = 0;

                Map<String, Object> updates = new HashMap<>();

                if (oldBatchId == null || oldPcs == 0) {
                    // Previously empty
                    updates.put("batches/" + newBatchId + "/inRackCount", ServerValue.increment(qty));
                } else if (newBatchId.equals(oldBatchId)) {
                    // Same batch, adjust by delta
                    int delta = qty - oldPcs;
                    if (delta != 0) {
                        updates.put("batches/" + newBatchId + "/inRackCount", ServerValue.increment(delta));
                    }
                } else {
                    // Different batch: subtract old, add new
                    updates.put("batches/" + oldBatchId + "/inRackCount", ServerValue.increment(-oldPcs));
                    updates.put("batches/" + newBatchId + "/inRackCount", ServerValue.increment(qty));
                }

                // Set new slot assignment
                updates.put("RackSlots/" + slot + "/batchId", newBatchId);
                updates.put("RackSlots/" + slot + "/pcs", qty);

                root.updateChildren(updates, (error, ref) -> {
                    if (cb != null) cb.onComplete(error == null ? null : error.getMessage());
                });
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {
                if (cb != null) cb.onComplete(error.getMessage());
            }
        });
    }

    /**
     * Mark a slot as finished:
     *   - Batches/{batchId}/inRackCount   -= pcs
     *   - Batches/{batchId}/finishedCount += pcs
     *   - Clears slot (batchId=null, pcs=0)
     */
    public static void finishSlot(int slot, @Nullable SimpleCallback cb) {
        DatabaseReference root = FirebaseDatabase.getInstance().getReference();
        DatabaseReference slotRef = root.child("RackSlots").child(String.valueOf(slot));
        slotRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot ds) {
                String batchId = ds.child("batchId").getValue(String.class);
                Integer pcs    = ds.child("pcs").getValue(Integer.class);
                if (batchId == null || pcs == null || pcs <= 0) {
                    if (cb != null) cb.onComplete("Slot empty or invalid.");
                    return;
                }
                Map<String, Object> updates = new HashMap<>();
                updates.put("batches/" + batchId + "/inRackCount",   ServerValue.increment(-pcs));
                updates.put("batches/" + batchId + "/finishedCount", ServerValue.increment(pcs));
                updates.put("RackSlots/" + slot + "/batchId", null);
                updates.put("RackSlots/" + slot + "/pcs", 0);

                root.updateChildren(updates, (error, ref) -> {
                    if (cb != null) cb.onComplete(error == null ? null : error.getMessage());
                });
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {
                if (cb != null) cb.onComplete(error.getMessage());
            }
        });
    }

    /**
     * Clear a slot (return pcs back to available = total - inRack - finished).
     *   - Batches/{batchId}/inRackCount -= pcs
     *   - Clears slot (batchId=null, pcs=0)
     */
    public static void clearSlot(int slot, @Nullable SimpleCallback cb) {
        DatabaseReference root = FirebaseDatabase.getInstance().getReference();
        DatabaseReference slotRef = root.child("RackSlots").child(String.valueOf(slot));
        slotRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot ds) {
                String batchId = ds.child("batchId").getValue(String.class);
                Integer pcs    = ds.child("pcs").getValue(Integer.class);
                if (batchId == null || pcs == null || pcs <= 0) {
                    if (cb != null) cb.onComplete("Slot already empty.");
                    return;
                }
                Map<String, Object> updates = new HashMap<>();
                updates.put("batches/" + batchId + "/inRackCount", ServerValue.increment(-pcs));
                updates.put("RackSlots/" + slot + "/batchId", null);
                updates.put("RackSlots/" + slot + "/pcs", 0);

                root.updateChildren(updates, (error, ref) -> {
                    if (cb != null) cb.onComplete(error == null ? null : error.getMessage());
                });
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {
                if (cb != null) cb.onComplete(error.getMessage());
            }
        });
    }

    // ========= BATCH LOADING (robust to String/Long/Double/missing) =========

    // Realtime subscription handle
    public static class RTHandle {
        public final DatabaseReference ref;
        public final ValueEventListener listener;
        public RTHandle(DatabaseReference ref, ValueEventListener listener) {
            this.ref = ref; this.listener = listener;
        }
        public void remove() {
            if (ref != null && listener != null) ref.removeEventListener(listener);
        }
    }

    public interface BatchesCallback { void onLoaded(List<WoodBatch> list, @Nullable String error); }
    public interface BatchesRealtimeCallback {
        void onUpdate(List<WoodBatch> list);
        void onError(@NonNull String error);
    }

    // ---- Coercion helpers to parse Firebase numbers safely ----
    private static int asInt(Object v, int def) {
        if (v == null) return def;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(String.valueOf(v)); } catch (Exception e) { return def; }
    }
    private static long asLong(Object v, long def) {
        if (v == null) return def;
        if (v instanceof Number) return ((Number) v).longValue();
        try { return Long.parseLong(String.valueOf(v)); } catch (Exception e) { return def; }
    }

    // Build a WoodBatch robustly from a child snapshot
    private static WoodBatch parseBatchSnapshot(@NonNull DataSnapshot child) {
        Object totalObj    = child.child("totalQuantity").getValue();
        Object inRackObj   = child.child("inRackCount").getValue();
        Object finishedObj = child.child("finishedCount").getValue();
        Object arrivalObj  = child.child("arrivalDateMillis").getValue();
        String keyId       = child.getKey();

        WoodBatch wb = new WoodBatch();
        wb.setBatchId(keyId); // use key as id even if node doesn't store "batchId"
        wb.setTotalQuantity(asInt(totalObj, 0));
        wb.setInRackCount(asInt(inRackObj, 0));
        wb.setFinishedCount(asInt(finishedObj, 0));
        wb.setArrivalDateMillis(asLong(arrivalObj, 0L));
        return wb;
    }

    /** One-time load of all batches (robust). */
    public static void loadBatchesOnce(@NonNull BatchesCallback cb) {
        FirebaseDatabase.getInstance().getReference("batches")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot snap) {
                        List<WoodBatch> out = new ArrayList<>();
                        for (DataSnapshot child : snap.getChildren()) {
                            out.add(parseBatchSnapshot(child));
                        }
                        cb.onLoaded(out, null);
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {
                        cb.onLoaded(Collections.emptyList(), error.getMessage());
                    }
                });
    }

    /** Observe all batches in realtime. Call handle.remove() to stop listening. */
    public static RTHandle observeBatchesRealtime(@NonNull BatchesRealtimeCallback cb) {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("batches");
        ValueEventListener vel = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                List<WoodBatch> out = new ArrayList<>();
                for (DataSnapshot child : snap.getChildren()) {
                    out.add(parseBatchSnapshot(child));
                }
                cb.onUpdate(out);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {
                cb.onError(error.getMessage());
            }
        };
        ref.addValueEventListener(vel);
        return new RTHandle(ref, vel);
    }

    // ========= (Optional) Logging =========
    private static void log(String msg) { Log.d("FirebaseHelper", msg); }
}
