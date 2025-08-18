package com.example.drysync;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.*;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;
import java.util.Random;
import java.util.TimeZone;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BatchListFragment extends Fragment {

    private RecyclerView recycler;
    private TextInputEditText searchInput;
    private FloatingActionButton fab;

    private BatchAdapter adapter;
    private final List<WoodBatch> allBatches = new ArrayList<>();
    private String currentQuery = "";

    // Firebase (Realtime Database)
    private DatabaseReference batchesRef;
    private ValueEventListener batchesListener;

    public BatchListFragment() { super(R.layout.fragment_inventory); }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        recycler = view.findViewById(R.id.batchRecycler);
        searchInput = view.findViewById(R.id.searchInput);
        fab = view.findViewById(R.id.fabAddBatch);

        // RecyclerView
        LinearLayoutManager lm = new LinearLayoutManager(requireContext());
        recycler.setLayoutManager(lm);
        recycler.setHasFixedSize(true);
        recycler.addItemDecoration(new DividerItemDecoration(requireContext(), lm.getOrientation()));

        adapter = new BatchAdapter(this::showEditDialog);
        recycler.setAdapter(adapter);

        // Firebase ref to /batches
        batchesRef = FirebaseDatabase.getInstance().getReference("batches");

        // Live subscribe to /batches
        batchesListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<WoodBatch> list = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    // Try POJO first (requires no-arg ctor + setters)
                    WoodBatch wb = child.getValue(WoodBatch.class);

                    // Fallback/manual mapping (and alias key support)
                    String id = valueAsString(child.child("batchId").getValue());
                    Integer total = valueAsInt(child.child("totalQuantity").getValue());
                    Long arrival = valueAsLong(child.child("arrivalDateMillis").getValue());

                    // Read counts with alias support
                    Integer inRack = firstInt(
                            child.child("inRackCount").getValue(),
                            child.child("inRack").getValue()           // alias
                    );
                    Integer finished = firstInt(
                            child.child("finishedCount").getValue(),
                            child.child("finished").getValue()        // alias
                    );

                    if (wb == null) {
                        // Build manually if POJO mapping failed (e.g., no setters)
                        if (id != null && total != null && arrival != null) {
                            wb = new WoodBatch(id, total, arrival);
                            // only set if your model has these methods; ignore otherwise
                            try { wb.setInRackCount(inRack != null ? inRack : 0); } catch (Throwable ignored) {}
                            try { wb.setFinishedCount(finished != null ? finished : 0); } catch (Throwable ignored) {}
                        }
                    } else {
                        // POJO mapped — but make sure aliases also flow into fields if present
                        try {
                            if (inRack != null)    wb.setInRackCount(inRack);
                            if (finished != null)  wb.setFinishedCount(finished);
                        } catch (Throwable ignored) {}
                    }

                    if (wb != null) list.add(wb);
                }
                list.sort((a,b) -> Long.compare(b.getArrivalDateMillis(), a.getArrivalDateMillis()));
                allBatches.clear();
                allBatches.addAll(list);
                filterAndShow(currentQuery);
            }

            // helpers
            private String valueAsString(Object v) { return v == null ? null : String.valueOf(v); }
            private Integer valueAsInt(Object v) {
                if (v instanceof Number) return ((Number) v).intValue();
                try { return v == null ? null : Integer.parseInt(String.valueOf(v)); } catch (Exception e) { return null; }
            }
            private Long valueAsLong(Object v) {
                if (v instanceof Number) return ((Number) v).longValue();
                try { return v == null ? null : Long.parseLong(String.valueOf(v)); } catch (Exception e) { return null; }
            }
            private Integer firstInt(Object... vals) {
                for (Object v : vals) {
                    Integer i = valueAsInt(v);
                    if (i != null) return i;
                }
                return null;
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {
                // optional: log/toast
            }
        };
        batchesRef.addValueEventListener(batchesListener);

        fab.setOnClickListener(v -> showAddDialogAutoDate());

        // Search
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { filterAndShow(s == null ? "" : s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (batchesRef != null && batchesListener != null) {
            batchesRef.removeEventListener(batchesListener);
            batchesListener = null;
        }
    }

    // ---------- Add: auto date, only quantity ----------
    private void showAddDialogAutoDate() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_add_batch, null, false);

        TextView tvId = dialogView.findViewById(R.id.tvAutoBatchId);
        TextInputEditText etQty = dialogView.findViewById(R.id.etQuantity);
        MaterialButton btnSave = dialogView.findViewById(R.id.btnSave);

        // Chips (optional)
        Chip c50  = dialogView.findViewById(R.id.chipQty50);
        Chip c100 = dialogView.findViewById(R.id.chipQty100);
        Chip c200 = dialogView.findViewById(R.id.chipQty200);
        View.OnClickListener chipClick = v -> {
            if (v instanceof Chip) {
                CharSequence t = ((Chip) v).getText();
                etQty.setText(t);
                if (etQty.getText() != null) etQty.setSelection(etQty.getText().length());
            }
        };
        if (c50  != null) c50.setOnClickListener(chipClick);
        if (c100 != null) c100.setOnClickListener(chipClick);
        if (c200 != null) c200.setOnClickListener(chipClick);

        final long arrivalNow = System.currentTimeMillis();
        String autoId = generateBatchId();
        tvId.setText(autoId);

        btnSave.setEnabled(false);
        etQty.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable e) {
                btnSave.setEnabled(isValidQty(e));
                if (etQty.getError() != null) etQty.setError(null);
            }
            private boolean isValidQty(Editable e) {
                if (e == null) return false;
                String t = e.toString().trim();
                if (t.isEmpty()) return false;
                try { return Integer.parseInt(t) > 0; } catch (NumberFormatException ex) { return false; }
            }
        });

        final androidx.appcompat.app.AlertDialog alert =
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setView(dialogView)
                        .create();

        btnSave.setOnClickListener(v -> {
            String qtyStr = etQty.getText() == null ? "" : etQty.getText().toString().trim();
            Integer qty = null;
            try { qty = Integer.parseInt(qtyStr); } catch (NumberFormatException ignored) {}
            if (qty == null || qty <= 0) { etQty.setError("Enter valid quantity"); return; }

            // Build new batch object
            WoodBatch newBatch = new WoodBatch(autoId, qty, arrivalNow);

            // Write to /batches/{batchId}
            batchesRef.child(autoId)
                    .setValue(newBatch)
                    .addOnSuccessListener(unused -> {
                        hideKeyboard(etQty);
                        alert.dismiss(); // list refreshes via listener
                        recycler.scrollToPosition(0);
                    })
                    .addOnFailureListener(e -> etQty.setError("Failed: " + e.getMessage()));
        });

        alert.show();
    }

    // ---------- Edit: set In rack / Finished ----------
    private void showEditDialog(@NonNull WoodBatch batch) {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_edit_batch, null, false);

        TextView tvBatchId    = dialogView.findViewById(R.id.tvBatchId);
        TextView tvTotalQty   = dialogView.findViewById(R.id.tvTotalQty);
        TextView tvRemaining  = dialogView.findViewById(R.id.tvRemaining);
        TextInputEditText etInRack  = dialogView.findViewById(R.id.etInRack);
        TextInputEditText etFinished= dialogView.findViewById(R.id.etFinished);
        MaterialButton btnSave      = dialogView.findViewById(R.id.btnSaveEdit);
        MaterialButton btnCancel    = dialogView.findViewById(R.id.btnCancelEdit);

        tvBatchId.setText(batch.getBatchId());
        tvTotalQty.setText("Total: " + batch.getTotalQuantity());
        etInRack.setText(String.valueOf(batch.getInRackCount()));
        etFinished.setText(String.valueOf(batch.getFinishedCount()));
        tvRemaining.setText("Remaining: " + batch.getRemaining());

        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { update(); }
            @Override public void afterTextChanged(Editable s) { update(); }
            private void update() {
                int inRack = parseInt(etInRack.getText());
                int done   = parseInt(etFinished.getText());
                int total  = batch.getTotalQuantity();
                int remaining = total - inRack - done;
                tvRemaining.setText("Remaining: " + Math.max(0, remaining));
                boolean ok = inRack >= 0 && done >= 0 && (inRack + done) <= total;
                btnSave.setEnabled(ok);
                etInRack.setError(null); etFinished.setError(null);
                if (!ok) {
                    if ((inRack + done) > total) etFinished.setError("Sum exceeds total");
                    else {
                        if (inRack < 0) etInRack.setError("≥ 0");
                        if (done < 0)   etFinished.setError("≥ 0");
                    }
                }
            }
        };
        etInRack.addTextChangedListener(watcher);
        etFinished.addTextChangedListener(watcher);

        final androidx.appcompat.app.AlertDialog alert =
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setView(dialogView)
                        .create();

        btnSave.setOnClickListener(v -> {
            int inRack = parseInt(etInRack.getText());
            int done   = parseInt(etFinished.getText());
            int total  = batch.getTotalQuantity();
            if (inRack < 0 || done < 0 || inRack + done > total) return;

            // If fully finished -> delete the node instead of updating.
            if ((inRack + done) == total) {
                batchesRef.child(batch.getBatchId())
                        .removeValue()
                        .addOnSuccessListener(unused -> {
                            // Optional: show a toast/snackbar here
                            alert.dismiss(); // list auto-refreshes via your listener
                        })
                        .addOnFailureListener(e -> etFinished.setError("Delete failed: " + e.getMessage()));
                return;
            }

            // Otherwise update counts (write both new + legacy keys)
            Map<String,Object> map = new HashMap<>();
            map.put("inRackCount", inRack);
            map.put("finishedCount", done);
            map.put("inRack", inRack);     // legacy alias
            map.put("finished", done);     // legacy alias

            batchesRef.child(batch.getBatchId())
                    .updateChildren(map)
                    .addOnSuccessListener(unused -> alert.dismiss())
                    .addOnFailureListener(e -> etFinished.setError("Failed: " + e.getMessage()));
        });

        btnCancel.setOnClickListener(v -> alert.dismiss());
        alert.show();
    }


    // ----- helpers -----
    private int parseInt(Editable e) {
        try { return Integer.parseInt(e == null ? "" : e.toString().trim()); }
        catch (Exception ex) { return 0; }
    }

    private void filterAndShow(String queryRaw) {
        currentQuery = queryRaw == null ? "" : queryRaw.trim().toLowerCase(Locale.getDefault());
        if (currentQuery.isEmpty()) {
            adapter.submitList(new ArrayList<>(allBatches));
            return;
        }
        List<WoodBatch> filtered = new ArrayList<>();
        for (WoodBatch wb : allBatches) {
            String id = wb.getBatchId();
            if (id != null && id.toLowerCase(Locale.getDefault()).contains(currentQuery)) {
                filtered.add(wb);
            }
        }
        adapter.submitList(filtered);
    }

    private void hideKeyboard(View anyView) {
        try {
            InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(anyView.getWindowToken(), 0);
        } catch (Exception ignored) {}
    }

    private String generateBatchId() {
        Date now = Calendar.getInstance(TimeZone.getTimeZone("Asia/Manila")).getTime();
        String datePart = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(now);
        int serial = new Random().nextInt(9000) + 1000; // 1000–9999
        return "BATCH-" + datePart + "-" + serial;
    }

    private Integer safeInt(Object o) {
        if (o instanceof Number) return ((Number) o).intValue();
        if (o instanceof String) try { return Integer.parseInt((String) o); } catch (Exception ignored){}
        return null;
    }
    private Long safeLong(Object o) {
        if (o instanceof Number) return ((Number) o).longValue();
        if (o instanceof String) try { return Long.parseLong((String) o); } catch (Exception ignored){}
        return null;
    }
}
