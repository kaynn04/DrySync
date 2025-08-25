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

    // ---------- PATHS ----------
    private static final String BATCHES_PATH   = "batches";
    private static final String RACKSLOTS_PATH = "RackSlots";
    private static final String SENSORS_PATH   = "Sensors"; // read-only

    // ---------- CALLBACKS ----------
    public interface SimpleCallback { void onComplete(@Nullable String error); }

    public interface StringDataCallback {
        void onStringReceived(String value);
        void onError(String errorMessage);
    }

    public interface FloatDataCallback {
        void onFloatReceived(float value);
        void onError(String errorMessage);
    }

    public interface BatchesCallback { void onLoaded(List<WoodBatch> list, @Nullable String error); }

    public interface BatchesRealtimeCallback {
        void onUpdate(List<WoodBatch> list);
        void onError(@NonNull String error);
    }

    // Size lines (multi-size inventory per batch)
    public interface SizeLinesCallback { void onLoaded(List<SizeLine> lines, @Nullable String error); }

    /** Represents one size line under /batches/{id}/sizes/{key} */
    public static class SizeLine {
        public String key;      // child key under sizes (index or push-id)
        public int quantity;    // planned pieces for this size
        public double lengthFt; // e.g., 5.0
        public double widthIn;  // e.g., 3.0
        public int inRack;      // live count in racks for this size
        public int finished;    // finished pieces for this size
        public int remaining() { return Math.max(0, quantity - inRack - finished); }
        public String label() {
            String len = (lengthFt == Math.rint(lengthFt)) ? String.valueOf((long)lengthFt) : String.valueOf(lengthFt);
            String wid = (widthIn  == Math.rint(widthIn )) ? String.valueOf((long)widthIn ) : String.valueOf(widthIn );
            return len + " ft × " + wid + " in";
        }
    }

    // ---------- SENSOR READERS ----------
    public static ValueEventListener retrieveStringData(String path, StringDataCallback cb) {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference(path);
        ValueEventListener vel = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                Object v = snapshot.getValue();
                cb.onStringReceived(v == null ? "" : String.valueOf(v));
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { cb.onError(error.getMessage()); }
        };
        ref.addValueEventListener(vel);
        return vel;
    }

    public static ValueEventListener retrieveFloatData(String path, FloatDataCallback cb) {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference(path);
        ValueEventListener vel = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                Object v = snapshot.getValue();
                try {
                    float f = (v instanceof Number) ? ((Number) v).floatValue()
                            : (v != null)           ? Float.parseFloat(String.valueOf(v))
                            : 0f;
                    cb.onFloatReceived(f);
                } catch (Exception e) { cb.onError("Parse error: " + e.getMessage()); }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { cb.onError(error.getMessage()); }
        };
        ref.addValueEventListener(vel);
        return vel;
    }

    // ---------- BATCH / SIZE LOADERS ----------
    public static void loadBatchesOnce(@NonNull BatchesCallback cb) {
        FirebaseDatabase.getInstance().getReference(BATCHES_PATH)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot snap) {
                        List<WoodBatch> out = new ArrayList<>();
                        for (DataSnapshot child : snap.getChildren()) out.add(parseBatchSnapshot(child));
                        cb.onLoaded(out, null);
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {
                        cb.onLoaded(Collections.emptyList(), error.getMessage());
                    }
                });
    }

    /** Load structured size lines saved by BatchListFragment */
    public static void loadBatchSizeLines(@NonNull String batchId, @NonNull SizeLinesCallback cb) {
        DatabaseReference ref = FirebaseDatabase.getInstance()
                .getReference(BATCHES_PATH).child(batchId).child("sizes");

        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot ds) {
                List<SizeLine> out = new ArrayList<>();
                if (!ds.exists()) { cb.onLoaded(out, null); return; }

                for (DataSnapshot child : ds.getChildren()) {
                    SizeLine sl = new SizeLine();
                    sl.key       = child.getKey();
                    sl.quantity  = asInt(child.child("quantity").getValue(), 0);
                    sl.lengthFt  = asDouble(child.child("lengthFt").getValue(), 0d);
                    sl.widthIn   = asDouble(child.child("widthIn").getValue(), 0d);
                    sl.inRack    = asInt(child.child("inRack").getValue(), 0);
                    sl.finished  = asInt(child.child("finished").getValue(), 0);

                    if (sl.key != null && sl.quantity > 0 && sl.lengthFt > 0 && sl.widthIn > 0) out.add(sl);
                }
                cb.onLoaded(out, null);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {
                cb.onLoaded(new ArrayList<>(), error.getMessage());
            }
        });
    }

    // ---------- ASSIGN / FINISH / CLEAR ----------
    /** Assign qty (typically 1) from a batch to a slot (NO size). Prefer assignBatchToSlotWithSizeRef to do it in one step. */
    public static void assignBatchToSlot(@NonNull String newBatchId, int slot, int qty, @Nullable SimpleCallback cb) {
        if (qty <= 0) { if (cb != null) cb.onComplete("Quantity must be > 0"); return; }

        DatabaseReference root = FirebaseDatabase.getInstance().getReference();
        DatabaseReference newBatchRef = root.child(BATCHES_PATH).child(newBatchId);

        newBatchRef.get().addOnCompleteListener(tNew -> {
            if (!tNew.isSuccessful() || !tNew.getResult().exists()) {
                if (cb != null) cb.onComplete("Batch not found: " + newBatchId);
                return;
            }

            DatabaseReference slotRef = root.child(RACKSLOTS_PATH).child(String.valueOf(slot));
            slotRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override public void onDataChange(@NonNull DataSnapshot ds) {
                    String oldBatchId = ds.child("batchId").getValue(String.class);
                    Integer oldPcs    = ds.child("pcs").getValue(Integer.class);
                    String oldSizeKey = ds.child("sizeKey").getValue(String.class);
                    if (oldPcs == null) oldPcs = 0;

                    Map<String, Object> updates = new HashMap<>();

                    if (oldBatchId != null && oldPcs > 0) {
                        updates.put(BATCHES_PATH + "/" + oldBatchId + "/inRackCount", ServerValue.increment(-oldPcs));
                        if (oldSizeKey != null) {
                            updates.put(BATCHES_PATH + "/" + oldBatchId + "/sizes/" + oldSizeKey + "/inRack",
                                    ServerValue.increment(-oldPcs));
                        }
                    }

                    updates.put(BATCHES_PATH + "/" + newBatchId + "/inRackCount", ServerValue.increment(qty));

                    updates.put(RACKSLOTS_PATH + "/" + slot + "/batchId", newBatchId);
                    updates.put(RACKSLOTS_PATH + "/" + slot + "/pcs", qty);
                    updates.put(RACKSLOTS_PATH + "/" + slot + "/sizeKey", null);
                    updates.put(RACKSLOTS_PATH + "/" + slot + "/sizeLenFt", null);
                    updates.put(RACKSLOTS_PATH + "/" + slot + "/sizeWidIn", null);

                    root.updateChildren(updates, (error, ref) -> {
                        if (cb != null) cb.onComplete(error == null ? null : error.getMessage());
                    });
                }
                @Override public void onCancelled(@NonNull DatabaseError error) {
                    if (cb != null) cb.onComplete(error.getMessage());
                }
            });
        });
    }

    /**
     * Assign exactly 1 piece to a slot from a specific size line of a batch (ONE-STEP).
     * Use this after the user has picked BOTH batch and size to avoid double counting.
     */
    public static void assignBatchToSlotWithSizeRef(@NonNull String batchId, int slot, @NonNull String sizeKey,
                                                    @Nullable SimpleCallback cb) {
        DatabaseReference root = FirebaseDatabase.getInstance().getReference();
        DatabaseReference batchRef = root.child(BATCHES_PATH).child(batchId);
        DatabaseReference sizeRef  = batchRef.child("sizes").child(sizeKey);
        DatabaseReference slotRef  = root.child(RACKSLOTS_PATH).child(String.valueOf(slot));

        batchRef.get().addOnCompleteListener(tb -> {
            if (!tb.isSuccessful() || !tb.getResult().exists()) {
                if (cb != null) cb.onComplete("Batch not found: " + batchId);
                return;
            }
            sizeRef.get().addOnCompleteListener(ts -> {
                if (!ts.isSuccessful() || !ts.getResult().exists()) {
                    if (cb != null) cb.onComplete("Size line not found: " + sizeKey);
                    return;
                }

                double lenFt = asDouble(ts.getResult().child("lengthFt").getValue(), 0d);
                double widIn = asDouble(ts.getResult().child("widthIn").getValue(), 0d);

                slotRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot ds) {
                        String oldBatchId = ds.child("batchId").getValue(String.class);
                        Integer oldPcs    = ds.child("pcs").getValue(Integer.class);
                        String oldSizeKey = ds.child("sizeKey").getValue(String.class);
                        if (oldPcs == null) oldPcs = 0;

                        Map<String, Object> up = new HashMap<>();

                        // Decrement previous assignment, if any (net 0 when only changing size/batch)
                        if (oldBatchId != null && oldPcs > 0) {
                            up.put(BATCHES_PATH + "/" + oldBatchId + "/inRackCount", ServerValue.increment(-oldPcs));
                            if (oldSizeKey != null) {
                                up.put(BATCHES_PATH + "/" + oldBatchId + "/sizes/" + oldSizeKey + "/inRack",
                                        ServerValue.increment(-oldPcs));
                            }
                        }

                        // Increment new batch + size inRack
                        up.put(BATCHES_PATH + "/" + batchId + "/inRackCount", ServerValue.increment(1));
                        up.put(BATCHES_PATH + "/" + batchId + "/sizes/" + sizeKey + "/inRack", ServerValue.increment(1));

                        // Set slot metadata
                        up.put(RACKSLOTS_PATH + "/" + slot + "/batchId", batchId);
                        up.put(RACKSLOTS_PATH + "/" + slot + "/pcs", 1);
                        up.put(RACKSLOTS_PATH + "/" + slot + "/sizeKey", sizeKey);
                        up.put(RACKSLOTS_PATH + "/" + slot + "/sizeLenFt", lenFt);
                        up.put(RACKSLOTS_PATH + "/" + slot + "/sizeWidIn", widIn);

                        root.updateChildren(up, (err, ref) -> {
                            if (cb != null) cb.onComplete(err == null ? null : err.getMessage());
                        });
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {
                        if (cb != null) cb.onComplete(error.getMessage());
                    }
                });
            });
        });
    }

    /** Convenience alias — call this once after user selects batch + size to avoid double minus. */
    public static void assignBatchAndSize(@NonNull String batchId, int slot, @NonNull String sizeKey,
                                          @Nullable SimpleCallback cb) {
        assignBatchToSlotWithSizeRef(batchId, slot, sizeKey, cb);
    }

    /** Finish (1 pc) and clear slot; updates both total + per-size counters if sizeKey exists. */
    public static void finishSlot(int slot, @Nullable SimpleCallback cb) {
        DatabaseReference root = FirebaseDatabase.getInstance().getReference();
        DatabaseReference slotRef = root.child(RACKSLOTS_PATH).child(String.valueOf(slot));

        slotRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot ds) {
                String batchId = ds.child("batchId").getValue(String.class);
                Integer pcs    = ds.child("pcs").getValue(Integer.class);
                String sizeKey = ds.child("sizeKey").getValue(String.class);
                if (batchId == null || pcs == null || pcs <= 0) {
                    if (cb != null) cb.onComplete("Slot empty or invalid.");
                    return;
                }

                DatabaseReference batchRef = root.child(BATCHES_PATH).child(batchId);
                batchRef.get().addOnCompleteListener(t -> {
                    Map<String, Object> updates = new HashMap<>();
                    if (t.isSuccessful() && t.getResult().exists()) {
                        updates.put(BATCHES_PATH + "/" + batchId + "/inRackCount",   ServerValue.increment(-1));
                        updates.put(BATCHES_PATH + "/" + batchId + "/finishedCount", ServerValue.increment(1));
                        if (sizeKey != null) {
                            updates.put(BATCHES_PATH + "/" + batchId + "/sizes/" + sizeKey + "/inRack",   ServerValue.increment(-1));
                            updates.put(BATCHES_PATH + "/" + batchId + "/sizes/" + sizeKey + "/finished", ServerValue.increment(1));
                        }
                    }
                    updates.put(RACKSLOTS_PATH + "/" + slot + "/batchId", null);
                    updates.put(RACKSLOTS_PATH + "/" + slot + "/pcs", 0);
                    updates.put(RACKSLOTS_PATH + "/" + slot + "/sizeKey", null);
                    updates.put(RACKSLOTS_PATH + "/" + slot + "/sizeLenFt", null);
                    updates.put(RACKSLOTS_PATH + "/" + slot + "/sizeWidIn", null);

                    root.updateChildren(updates, (error, ref) -> {
                        if (cb != null) cb.onComplete(error == null ? null : error.getMessage());
                    });
                });
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {
                if (cb != null) cb.onComplete(error.getMessage());
            }
        });
    }

    /** Clear slot (undo in-rack) and clear size metadata. */
    public static void clearSlot(int slot, @Nullable SimpleCallback cb) {
        DatabaseReference root = FirebaseDatabase.getInstance().getReference();
        DatabaseReference slotRef = root.child(RACKSLOTS_PATH).child(String.valueOf(slot));

        slotRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot ds) {
                String batchId = ds.child("batchId").getValue(String.class);
                Integer pcs    = ds.child("pcs").getValue(Integer.class);
                String sizeKey = ds.child("sizeKey").getValue(String.class);
                if (batchId == null || pcs == null || pcs <= 0) {
                    if (cb != null) cb.onComplete("Slot already empty.");
                    return;
                }

                DatabaseReference batchRef = root.child(BATCHES_PATH).child(batchId);
                batchRef.get().addOnCompleteListener(t -> {
                    Map<String, Object> updates = new HashMap<>();
                    if (t.isSuccessful() && t.getResult().exists()) {
                        updates.put(BATCHES_PATH + "/" + batchId + "/inRackCount", ServerValue.increment(-pcs));
                        if (sizeKey != null) {
                            updates.put(BATCHES_PATH + "/" + batchId + "/sizes/" + sizeKey + "/inRack", ServerValue.increment(-pcs));
                        }
                    }
                    updates.put(RACKSLOTS_PATH + "/" + slot + "/batchId", null);
                    updates.put(RACKSLOTS_PATH + "/" + slot + "/pcs", 0);
                    updates.put(RACKSLOTS_PATH + "/" + slot + "/sizeKey", null);
                    updates.put(RACKSLOTS_PATH + "/" + slot + "/sizeLenFt", null);
                    updates.put(RACKSLOTS_PATH + "/" + slot + "/sizeWidIn", null);

                    root.updateChildren(updates, (error, ref) -> {
                        if (cb != null) cb.onComplete(error == null ? null : error.getMessage());
                    });
                });
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {
                if (cb != null) cb.onComplete(error.getMessage());
            }
        });
    }

    // ---------- REALTIME BATCH OBSERVER (optional) ----------
    public static class RTHandle {
        public final DatabaseReference ref;
        public final ValueEventListener listener;
        public RTHandle(DatabaseReference ref, ValueEventListener listener) { this.ref = ref; this.listener = listener; }
        public void remove() { if (ref != null && listener != null) ref.removeEventListener(listener); }
    }

    public static RTHandle observeBatchesRealtime(@NonNull BatchesRealtimeCallback cb) {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference(BATCHES_PATH);
        ValueEventListener vel = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                List<WoodBatch> out = new ArrayList<>();
                for (DataSnapshot child : snap.getChildren()) out.add(parseBatchSnapshot(child));
                cb.onUpdate(out);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { cb.onError(error.getMessage()); }
        };
        ref.addValueEventListener(vel);
        return new RTHandle(ref, vel);
    }

    // ---------- INTERNAL HELPERS ----------
    private static WoodBatch parseBatchSnapshot(@NonNull DataSnapshot child) {
        Object totalObj    = child.child("totalQuantity").getValue();
        Object inRackObj   = child.child("inRackCount").getValue();
        Object finishedObj = child.child("finishedCount").getValue();
        Object arrivalObj  = child.child("arrivalDateMillis").getValue();

        WoodBatch wb = new WoodBatch();
        wb.setBatchId(child.getKey()); // take id from key
        wb.setTotalQuantity(asInt(totalObj, 0));
        wb.setInRackCount(asInt(inRackObj, 0));
        wb.setFinishedCount(asInt(finishedObj, 0));
        wb.setArrivalDateMillis(asLong(arrivalObj, 0L));
        return wb;
    }

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

    private static double asDouble(Object v, double def) {
        if (v == null) return def;
        if (v instanceof Number) return ((Number) v).doubleValue();
        try { return Double.parseDouble(String.valueOf(v)); } catch (Exception e) { return def; }
    }

    @SuppressWarnings("unused")
    private static void log(String m) { Log.d("FirebaseHelper", m); }
}
