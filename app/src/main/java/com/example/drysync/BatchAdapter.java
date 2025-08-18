package com.example.drysync;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.text.DateFormat;
import java.util.Date;

public class BatchAdapter extends ListAdapter<WoodBatch, BatchAdapter.VH> {

    public interface OnBatchClickListener {
        void onBatchClick(@NonNull WoodBatch batch);
    }

    private final OnBatchClickListener clickListener;

    public BatchAdapter(OnBatchClickListener clickListener) {
        super(DIFF);
        setHasStableIds(true);
        this.clickListener = clickListener;
    }

    private static final DiffUtil.ItemCallback<WoodBatch> DIFF =
            new DiffUtil.ItemCallback<WoodBatch>() {
                @Override
                public boolean areItemsTheSame(@NonNull WoodBatch o, @NonNull WoodBatch n) {
                    String a = o.getBatchId(), b = n.getBatchId();
                    return a != null && a.equals(b);
                }
                @Override
                public boolean areContentsTheSame(@NonNull WoodBatch o, @NonNull WoodBatch n) {
                    return o.getTotalQuantity()     == n.getTotalQuantity()
                            && o.getArrivalDateMillis() == n.getArrivalDateMillis()
                            && o.getInRackCount()       == n.getInRackCount()
                            && o.getFinishedCount()     == n.getFinishedCount();
                }
            };

    @Override public long getItemId(int position) {
        WoodBatch item = getItem(position);
        String id = item != null && item.getBatchId() != null ? item.getBatchId() : "";
        return id.hashCode();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_batch_card, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        WoodBatch item = getItem(position);
        holder.bind(item);
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null && item != null) clickListener.onBatchClick(item);
        });
    }

    static class VH extends RecyclerView.ViewHolder {
        private final TextView tvBatchId, tvQuantity, tvExtra;
        private final DateFormat df = DateFormat.getDateInstance();

        VH(@NonNull View itemView) {
            super(itemView);
            tvBatchId = itemView.findViewById(R.id.tvBatchId);
            tvQuantity = itemView.findViewById(R.id.tvQuantity);
            tvExtra   = itemView.findViewById(R.id.tvExtra);  // <-- now present in XML
        }

        void bind(WoodBatch item) {
            if (item == null) return;
            tvBatchId.setText(item.getBatchId());
            tvQuantity.setText("Total Qty: " + item.getTotalQuantity() + " pcs");

            // Compose the extra line
            int inRack = item.getInRackCount();
            int finished = item.getFinishedCount();
            int remaining = Math.max(0, item.getTotalQuantity() - inRack - finished);
            if (tvExtra != null) {
                tvExtra.setText("In rack: " + inRack + "  •  Finished: " + finished + "  •  Remaining: " + remaining);
            }
        }
    }
}
