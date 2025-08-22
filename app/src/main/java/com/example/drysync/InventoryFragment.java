package com.example.drysync;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class InventoryFragment extends Fragment {

    private RecyclerView recycler;
    private TextInputEditText searchInput;
    private FloatingActionButton fabAdd;
    private BatchAdapter adapter;
    private FirebaseHelper.RTHandle batchesSub;

    private final List<WoodBatch> fullList = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_inventory, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        recycler = v.findViewById(R.id.batchRecycler);
        searchInput = v.findViewById(R.id.searchInput);
        fabAdd = v.findViewById(R.id.fabAddBatch);

        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new BatchAdapter();
        recycler.setAdapter(adapter);

        // Realtime subscription
        batchesSub = FirebaseHelper.observeBatchesRealtime(new FirebaseHelper.BatchesRealtimeCallback() {
            @Override public void onUpdate(List<WoodBatch> list) {
                // Optional: newest arrival first
                Collections.sort(list, (a, b) -> Long.compare(b.getArrivalDateMillis(), a.getArrivalDateMillis()));
                fullList.clear();
                fullList.addAll(list);
                applyFilter(getSafe(searchInput));
            }
            @Override public void onError(@NonNull String error) {
                Toast.makeText(requireContext(), "Inventory load error: " + error, Toast.LENGTH_SHORT).show();
            }
        });

        // Search
        if (searchInput != null) {
            searchInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) { applyFilter(getSafe(searchInput)); }
            });
        }

        // Add Batch (hook your dialog here)
        fabAdd.setOnClickListener(view ->
                Toast.makeText(requireContext(), "TODO: Open Add Batch dialog", Toast.LENGTH_SHORT).show()
        );
    }

    private void applyFilter(@NonNull String query) {
        String q = query.trim().toLowerCase();
        if (q.isEmpty()) {
            adapter.submit(fullList);
            return;
        }
        List<WoodBatch> filtered = new ArrayList<>();
        for (WoodBatch wb : fullList) {
            String id = wb.getBatchId();
            if (id != null && id.toLowerCase().contains(q)) filtered.add(wb);
        }
        adapter.submit(filtered);
    }

    private static String getSafe(@Nullable TextInputEditText et) {
        if (et == null || et.getText() == null) return "";
        return et.getText().toString();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (batchesSub != null) batchesSub.remove();
        batchesSub = null;
        recycler = null;
        searchInput = null;
        fabAdd = null;
    }

    // =========================
    // Adapter + tiny utilities
    // =========================

    private static class BatchAdapter extends RecyclerView.Adapter<BatchVH> {
        private final List<WoodBatch> items = new ArrayList<>();

        void submit(List<WoodBatch> newItems) {
            DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override public int getOldListSize() { return items.size(); }
                @Override public int getNewListSize() { return newItems.size(); }
                @Override public boolean areItemsTheSame(int oldPos, int newPos) {
                    String a = items.get(oldPos).getBatchId();
                    String b = newItems.get(newPos).getBatchId();
                    return a != null && a.equals(b);
                }
                @Override public boolean areContentsTheSame(int oldPos, int newPos) {
                    WoodBatch o = items.get(oldPos);
                    WoodBatch n = newItems.get(newPos);
                    return o.getTotalQuantity() == n.getTotalQuantity()
                            && o.getInRackCount() == n.getInRackCount()
                            && o.getFinishedCount() == n.getFinishedCount()
                            && o.getArrivalDateMillis() == n.getArrivalDateMillis();
                }
            });
            items.clear();
            items.addAll(newItems);
            diff.dispatchUpdatesTo(this);
        }

        @NonNull @Override
        public BatchVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_batch_card, parent, false);
            return new BatchVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull BatchVH h, int position) {
            WoodBatch wb = items.get(position);

            String batchId = wb.getBatchId() == null ? "(no id)" : wb.getBatchId();
            int total     = wb.getTotalQuantity();
            int inRack    = wb.getInRackCount();
            int finished  = wb.getFinishedCount();
            int remaining = wb.getRemaining();

            // Bind
            if (h.tvBatchId != null)   h.tvBatchId.setText(batchId);
            if (h.tvQuantity != null)  h.tvQuantity.setText("Total Qty: " + total + " pcs");
            if (h.chipArrival != null) h.chipArrival.setText(formatArrival(wb.getArrivalDateMillis()));
            if (h.tvExtra != null)     h.tvExtra.setText(formatCountsLine(inRack, finished, remaining));

            // Low stock badge (<= 10% of total OR <= 5 pcs; and > 0)
            int lowThreshold = Math.max(5, (int) Math.ceil(total * 0.10));
            if (h.chipLow != null) {
                h.chipLow.setVisibility((remaining > 0 && remaining <= lowThreshold) ? View.VISIBLE : View.GONE);
            }

            // Dim when fully allocated
            styleByRemaining(h.itemView, remaining);

            // Optional: click to edit batch
            // h.itemView.setOnClickListener(v -> { ... });
        }

        @Override
        public int getItemCount() { return items.size(); }
    }

    private static class BatchVH extends RecyclerView.ViewHolder {
        final View cardRoot;
        final TextView tvBatchId;
        final Chip chipArrival;
        final TextView tvQuantity;
        final TextView tvExtra;
        final Chip chipLow;

        BatchVH(@NonNull View itemView) {
            super(itemView);
            cardRoot    = itemView.findViewById(R.id.cardRoot);
            tvBatchId   = itemView.findViewById(R.id.tvBatchId);
            chipArrival = itemView.findViewById(R.id.chipArrival);
            tvQuantity  = itemView.findViewById(R.id.tvQuantity);
            tvExtra     = itemView.findViewById(R.id.tvExtra);
            chipLow     = itemView.findViewById(R.id.chipLow);
        }
    }

    // ---- Tiny utilities ----

    /** Compact counts line matching your layout order */
    private static String formatCountsLine(int inRack, int finished, int remaining) {
        return "In rack: " + inRack + "  •  Finished: " + finished + "  •  Remaining: " + remaining;
    }

    /** Arrival date pretty-print */
    private static String formatArrival(long millis) {
        if (millis <= 0) return "—";
        return DateFormat.getDateInstance(DateFormat.MEDIUM).format(millis);
    }

    /** Dim/grey the card if fully allocated */
    private static void styleByRemaining(@NonNull View root, int remaining) {
        root.setAlpha(remaining == 0 ? 0.5f : 1f);
    }
}
