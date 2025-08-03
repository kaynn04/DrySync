package com.example.drysync;

import android.content.Intent;
import android.os.Bundle;
import android.text.Layout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class RackFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_rack, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        GridLayout gridLayout = view.findViewById(R.id.my_grid_layout);  // Make sure this ID exists in fragment_rack.xml
        LayoutInflater inflater = LayoutInflater.from(getContext());

        for (int i = 1; i <= 10; i++) {
            View slotView = inflater.inflate(R.layout.slot_item, gridLayout, false);

            TextView title = slotView.findViewById(R.id.slotTitle);
            title.setText("Wood Slot " + i);

            final int slotNumber = i;
            slotView.setOnClickListener(v -> openSlotDetails(slotNumber));

            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = 180;
            params.bottomMargin = 50;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            slotView.setLayoutParams(params);
            gridLayout.addView(slotView);
        }
    }

    private void openSlotDetails(int slotNumber) {
        Intent intent = new Intent(getContext(), SlotDetailsActivity.class);
        intent.putExtra("slot_number", slotNumber);
        startActivity(intent);
    }
}
