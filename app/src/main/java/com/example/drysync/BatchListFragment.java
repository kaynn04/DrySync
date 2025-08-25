package com.example.drysync;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

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
                    WoodBatch wb = child.getValue(WoodBatch.class);

                    String id = valueAsString(child.child("batchId").getValue());
                    Integer total = valueAsInt(child.child("totalQuantity").getValue());
                    Long arrival = valueAsLong(child.child("arrivalDateMillis").getValue());

                    Integer inRack = firstInt(
                            child.child("inRackCount").getValue(),
                            child.child("inRack").getValue()
                    );
                    Integer finished = firstInt(
                            child.child("finishedCount").getValue(),
                            child.child("finished").getValue()
                    );

                    if (wb == null) {
                        if (id != null && total != null && arrival != null) {
                            wb = new WoodBatch(id, total, arrival);
                            try { wb.setInRackCount(inRack != null ? inRack : 0); } catch (Throwable ignored) {}
                            try { wb.setFinishedCount(finished != null ? finished : 0); } catch (Throwable ignored) {}
                        }
                    } else {
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

            @Override public void onCancelled(@NonNull DatabaseError error) { /* optional */ }
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

    // ---------- Add: auto date, MULTI-SIZE LINES with clickable rows ----------

    private interface IdCallback { void onId(@Nullable String id, @Nullable String error); }

    /** Generate B-YYYYMMDD-#### in Asia/Manila using a Firebase transaction at /counters/batches/YYYYMMDD */
    private void generateBatchIdManilaTxn(@NonNull IdCallback cb) {
        TimeZone tz = TimeZone.getTimeZone("Asia/Manila");
        Calendar cal = Calendar.getInstance(tz);
        SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd", Locale.US);
        df.setTimeZone(tz);
        String dateKey = df.format(cal.getTime());

        DatabaseReference counterRef = FirebaseDatabase.getInstance()
                .getReference("counters").child("batches").child(dateKey);

        counterRef.runTransaction(new Transaction.Handler() {
            @NonNull @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                Long v = currentData.getValue(Long.class);
                if (v == null) v = 0L;
                currentData.setValue(v + 1L);
                return Transaction.success(currentData);
            }
            @Override
            public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot snap) {
                if (error != null || !committed || snap == null) {
                    cb.onId(null, error != null ? error.getMessage() : "Counter not committed");
                    return;
                }
                Long seqL = snap.getValue(Long.class);
                long seq = (seqL == null ? 1L : seqL);
                String id = String.format(Locale.US, "B-%s-%04d", dateKey, seq);
                cb.onId(id, null);
            }
        });
    }

    /** Client-side housekeeping: delete /counters/batches/* except today’s key. */
    private void cleanupOldCountersExcept(@NonNull String keepDateKey) {
        DatabaseReference base = FirebaseDatabase.getInstance()
                .getReference("counters").child("batches");
        base.get()
                .addOnSuccessListener(snap -> {
                    Map<String, Object> updates = new HashMap<>();
                    for (DataSnapshot c : snap.getChildren()) {
                        String k = c.getKey();
                        if (k != null && !k.equals(keepDateKey)) {
                            updates.put(k, null); // delete
                        }
                    }
                    if (!updates.isEmpty()) {
                        base.updateChildren(updates);
                    }
                })
                .addOnFailureListener(e -> {
                    // Optional: log/Toast if you want. Safe to ignore if rules block it.
                });
    }

    private void showAddDialogAutoDate() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_add_batch, null, false);

        TextView tvId = dialogView.findViewById(R.id.tvAutoBatchId);
        TextView tvTotalAuto = dialogView.findViewById(R.id.tvTotalAuto);
        TextView tvSizePreview = dialogView.findViewById(R.id.tvSizePreview);
        MaterialButton btnAddSize = dialogView.findViewById(R.id.btnAddSize);
        MaterialButton btnSave = dialogView.findViewById(R.id.btnSave);
        LinearLayout sizesContainer = dialogView.findViewById(R.id.sizesContainer);

        final long arrivalNow = System.currentTimeMillis();

        // ← Don’t allocate an ID yet; only on Save
        tvId.setText("ID will be generated on Save");
        btnSave.setEnabled(false);

        LayoutInflater infl = LayoutInflater.from(requireContext());

        // Recalc totals + preview + save enabled
        Runnable recalc = new Runnable() {
            @Override public void run() {
                int total = 0;
                StringBuilder prev = new StringBuilder();
                for (int i = 0; i < sizesContainer.getChildCount(); i++) {
                    View row = sizesContainer.getChildAt(i);
                    TextInputEditText etQty = row.findViewById(R.id.etQty);
                    TextInputEditText etLen = row.findViewById(R.id.etLen);
                    TextInputEditText etWid = row.findViewById(R.id.etWid);

                    Integer q = parseIntOrNull(text(etQty));
                    Double lf = parseDblOrNull(text(etLen));
                    Double wi = parseDblOrNull(text(etWid));

                    if (q != null && q > 0) total += q;
                    if (q != null && q > 0 && lf != null && lf > 0 && wi != null && wi > 0) {
                        if (prev.length() > 0) prev.append("\n");
                        prev.append(q).append(" pcs = ")
                                .append(trim(lf)).append(" ft × ").append(trim(wi)).append(" in");
                    }
                }
                tvTotalAuto.setText("Total: " + total + " pcs");
                tvSizePreview.setText(prev.length() == 0 ? "—" : prev.toString());

                // Enable Save when there’s at least one valid size line
                btnSave.setEnabled(total > 0 && prev.length() > 0);
            }
        };

        // Add a row (whole row clickable to focus inputs)
        View.OnClickListener addRow = v1 -> {
            View row = infl.inflate(R.layout.item_size_row, sizesContainer, false);

            LinearLayout rowRoot = row.findViewById(R.id.rowRoot); // ensure this id exists
            TextInputEditText etQty = row.findViewById(R.id.etQty);
            TextInputEditText etLen = row.findViewById(R.id.etLen);
            TextInputEditText etWid = row.findViewById(R.id.etWid);
            View btnDelete = row.findViewById(R.id.btnDelete);

            TextWatcher w = new SimpleTW(recalc);
            etQty.addTextChangedListener(w);
            etLen.addTextChangedListener(w);
            etWid.addTextChangedListener(w);

            if (rowRoot != null) {
                rowRoot.setOnClickListener(v2 -> {
                    if (isEmpty(etQty)) focusAndShowKeyboard(etQty);
                    else if (isEmpty(etLen)) focusAndShowKeyboard(etLen);
                    else if (isEmpty(etWid)) focusAndShowKeyboard(etWid);
                    else focusAndShowKeyboard(etQty);
                });
            }

            btnDelete.setOnClickListener(v2 -> {
                sizesContainer.removeView(row);
                recalc.run();
            });

            sizesContainer.addView(row);
            recalc.run();
        };

        btnAddSize.setOnClickListener(addRow);
        addRow.onClick(null); // start with one empty row

        final androidx.appcompat.app.AlertDialog alert =
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setView(dialogView)
                        .create();

        btnSave.setOnClickListener(v -> {
            // Validate rows and build sizes payload
            int total = 0;
            List<Map<String, Object>> sizes = new ArrayList<>();
            for (int i = 0; i < sizesContainer.getChildCount(); i++) {
                View row = sizesContainer.getChildAt(i);
                TextInputEditText etQty = row.findViewById(R.id.etQty);
                TextInputEditText etLen = row.findViewById(R.id.etLen);
                TextInputEditText etWid = row.findViewById(R.id.etWid);

                Integer q = parseIntOrNull(text(etQty));
                Double lf = parseDblOrNull(text(etLen));
                Double wi = parseDblOrNull(text(etWid));

                if (q == null || q <= 0) { if (etQty != null) etQty.setError("Required"); continue; }
                if (lf == null || lf <= 0) { if (etLen != null) etLen.setError("Required"); continue; }
                if (wi == null || wi <= 0) { if (etWid != null) etWid.setError("Required"); continue; }

                total += q;

                Map<String, Object> line = new HashMap<>();
                line.put("quantity", q);
                line.put("lengthFt", lf);
                line.put("widthIn", wi);
                line.put("inRack", 0);
                line.put("finished", 0);
                sizes.add(line);
            }

            if (total <= 0 || sizes.isEmpty()) {
                tvSizePreview.setText("Add at least one valid size line");
                return;
            }

            // --- DAILY CLEANUP (client-side): keep only today's counter ---
            TimeZone tz = TimeZone.getTimeZone("Asia/Manila");
            Calendar cal = Calendar.getInstance(tz);
            SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd", Locale.US);
            df.setTimeZone(tz);
            String dateKey = df.format(cal.getTime());
            cleanupOldCountersExcept(dateKey);
            // --------------------------------------------------------------

            // Allocate ID NOW (transaction) to avoid gaps from cancelled dialogs
            int finalTotal = total;
            generateBatchIdManilaTxn((id, err) -> {
                String finalId = id;
                if (finalId == null) {
                    // fallback: random id in Manila timezone (rare)
                    Date now = Calendar.getInstance(TimeZone.getTimeZone("Asia/Manila")).getTime();
                    String datePart = new SimpleDateFormat("yyyyMMdd", Locale.US).format(now);
                    int serial = new Random().nextInt(9000) + 1000;
                    finalId = "B-" + datePart + "-" + serial;
                }

                tvId.setText(finalId);

                WoodBatch newBatch = new WoodBatch(finalId, finalTotal, arrivalNow);
                DatabaseReference ref = FirebaseDatabase.getInstance().getReference("batches");

                String finalId1 = finalId;
                ref.child(finalId)
                        .setValue(newBatch)
                        .addOnSuccessListener(unused ->
                                ref.child(finalId1).child("sizes").setValue(sizes)
                                        .addOnSuccessListener(u2 -> {
                                            hideKeyboard(tvSizePreview);
                                            alert.dismiss();
                                            recycler.scrollToPosition(0);
                                        })
                                        .addOnFailureListener(e ->
                                                tvSizePreview.setText("Sizes save failed: " + e.getMessage())
                                        )
                        )
                        .addOnFailureListener(e ->
                                tvSizePreview.setText("Batch save failed: " + e.getMessage())
                        );
            });
        });

        alert.show();
    }


    // ---------- EDIT DIALOG: REALTIME read-only with your badges layout ----------
    private void showEditDialog(@NonNull WoodBatch batch) {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_edit_batch, null, false);

        TextView tvBatchId    = dialogView.findViewById(R.id.tvBatchId);
        TextView tvTotalQty   = dialogView.findViewById(R.id.tvTotalQty);
        TextView tvRemaining  = dialogView.findViewById(R.id.tvRemaining);
        MaterialButton btnSave      = dialogView.findViewById(R.id.btnSaveEdit);
        MaterialButton btnCancel    = dialogView.findViewById(R.id.btnCancelEdit);

        // sizes container (read-only rows)
        LinearLayout sizesReadonlyContainer = dialogView.findViewById(R.id.sizesReadonlyContainer);

        tvBatchId.setText(batch.getBatchId());

        final androidx.appcompat.app.AlertDialog alert =
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setView(dialogView)
                        .create();

        // Realtime listener on /batches/{id}/sizes
        DatabaseReference sizesRef = batchesRef.child(batch.getBatchId()).child("sizes");
        ValueEventListener sizesListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                sizesReadonlyContainer.removeAllViews();

                int totalQty = 0;
                int totalInRack = 0;
                int totalFinished = 0;

                if (!snap.hasChildren()) {
                    TextView t = new TextView(requireContext());
                    t.setText("No sizes recorded for this batch");
                    t.setTextSize(13f);
                    t.setTextColor(0xFF7A7A7A);
                    sizesReadonlyContainer.addView(t);

                    tvTotalQty.setText("Total: 0");
                    tvRemaining.setText("Remaining: 0");
                    return;
                }

                LayoutInflater infl = LayoutInflater.from(requireContext());
                for (DataSnapshot line : snap.getChildren()) {
                    Integer q = valueAsInt(line.child("quantity").getValue());
                    Double lf = valueAsDouble(line.child("lengthFt").getValue());
                    Double wi = valueAsDouble(line.child("widthIn").getValue());
                    Integer inR = valueAsInt(line.child("inRack").getValue());
                    Integer fin = valueAsInt(line.child("finished").getValue());

                    int qty = q == null ? 0 : q;
                    int inRack = inR == null ? 0 : inR;
                    int finished = fin == null ? 0 : fin;
                    int remaining = Math.max(0, qty - inRack - finished);

                    totalQty += qty;
                    totalInRack += inRack;
                    totalFinished += finished;

                    // Inflate your badges layout and fill it
                    View row = infl.inflate(R.layout.item_size_readonly, sizesReadonlyContainer, false);

                    TextView tvTitle        = row.findViewById(R.id.tvSizeTitle);
                    TextView badgeInRack    = row.findViewById(R.id.badgeInRack);
                    TextView badgeFinished  = row.findViewById(R.id.badgeFinished);
                    TextView badgeRemaining = row.findViewById(R.id.badgeRemaining);

                    tvTitle.setText(formatTitle(lf, wi, qty));
                    badgeInRack.setText("In rack: " + inRack);
                    badgeFinished.setText("Finished: " + finished);
                    badgeRemaining.setText("Remaining: " + remaining);

                    sizesReadonlyContainer.addView(row);
                }

                int remainingAll = Math.max(0, totalQty - totalInRack - totalFinished);
                tvTotalQty.setText("Total: " + totalQty);
                tvRemaining.setText("Remaining: " + remainingAll);
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {
                sizesReadonlyContainer.removeAllViews();
                TextView t = new TextView(requireContext());
                t.setText("Failed to load sizes: " + error.getMessage());
                t.setTextSize(13f);
                t.setTextColor(0xFFC62828);
                sizesReadonlyContainer.addView(t);
            }
        };
        sizesRef.addValueEventListener(sizesListener);

        // Read-only dialog: Save = Close, hide Cancel
        btnSave.setText("Close");
        btnCancel.setVisibility(View.GONE);
        btnSave.setOnClickListener(v -> alert.dismiss());

        alert.setOnDismissListener(d -> sizesRef.removeEventListener(sizesListener));
        alert.show();
    }

    // --- helpers inside BatchListFragment ---

    private Integer valueAsInt(Object v) {
        if (v instanceof Number) return ((Number) v).intValue();
        try { return v == null ? null : Integer.parseInt(String.valueOf(v)); } catch (Exception e) { return null; }
    }
    private Double valueAsDouble(Object v) {
        if (v instanceof Number) return ((Number) v).doubleValue();
        try { return v == null ? null : Double.parseDouble(String.valueOf(v)); } catch (Exception e) { return null; }
    }
    private String trimNum(Double d) {
        if (d == null) return "";
        return (d == Math.rint(d)) ? String.valueOf(d.longValue()) : String.valueOf(d);
    }
    private String formatSizeLine(@Nullable Integer q, @Nullable Double lf, @Nullable Double wi) {
        String qty = (q == null ? "0" : String.valueOf(q));
        String len = (lf == null ? "?" : trimNum(lf));
        String wid = (wi == null ? "?" : trimNum(wi));
        return qty + " pcs  =  " + len + " ft × " + wid + " in";
    }

    private String formatSizeLineFull(int qty, @Nullable Double lf, @Nullable Double wi,
                                      int inRack, int finished, int remaining) {
        String len = (lf == null ? "?" : trimNum(lf));
        String wid = (wi == null ? "?" : trimNum(wi));
        return qty + " pcs  =  " + len + " ft × " + wid + " in\n"
                + "in rack: " + inRack + "   •   finished: " + finished + "   •   remaining: " + remaining;
    }

    // Title used for your badges row: "4 ft × 3 in  •  Qty: 10 pcs"
    private String formatTitle(@Nullable Double lf, @Nullable Double wi, int qty) {
        String len = (lf == null ? "?" : trimNum(lf));
        String wid = (wi == null ? "?" : trimNum(wi));
        return len + " ft × " + wid + " in  •  Qty: " + qty + " pcs";
    }

    // ----- other helpers -----
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

    // (Fallback local ID if ever needed)
    private String generateBatchId() {
        Date now = Calendar.getInstance(TimeZone.getTimeZone("Asia/Manila")).getTime();
        String datePart = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(now);
        int serial = new Random().nextInt(9000) + 1000; // 1000–9999
        return "B-" + datePart + "-" + serial;
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

    // == tiny helpers for sizes dialog & row click ==
    private static class SimpleTW implements TextWatcher {
        private final Runnable cb;
        SimpleTW(Runnable cb){ this.cb = cb; }
        @Override public void beforeTextChanged(CharSequence s,int a,int b,int c){}
        @Override public void onTextChanged(CharSequence s,int a,int b,int c){}
        @Override public void afterTextChanged(Editable s){ if(cb!=null) cb.run(); }
    }

    @NonNull
    private static String text(@Nullable TextInputEditText et){
        return et==null||et.getText()==null? "" : et.getText().toString().trim();
    }

    @Nullable
    private static Integer parseIntOrNull(@NonNull String s){
        try { return s.isEmpty()? null: Integer.parseInt(s); } catch(Exception e){ return null; }
    }

    @Nullable
    private static Double parseDblOrNull(@NonNull String s){
        try { return s.isEmpty()? null: Double.parseDouble(s); } catch(Exception e){ return null; }
    }

    @NonNull
    private static String trim(@Nullable Double d){
        if (d == null) return "";
        return (d == Math.rint(d)) ? String.valueOf(d.longValue()) : String.valueOf(d);
    }

    private boolean isEmpty(@Nullable TextInputEditText et) {
        return et == null || et.getText() == null || et.getText().toString().trim().isEmpty();
    }

    private void focusAndShowKeyboard(@NonNull TextInputEditText et) {
        et.requestFocus();
        et.post(() -> {
            try {
                InputMethodManager imm =
                        (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT);
            } catch (Exception ignored) {}
        });
    }
}
