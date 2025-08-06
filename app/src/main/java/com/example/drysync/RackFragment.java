package com.example.drysync;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Layout;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.util.Objects;

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
            TextView TVvalue = slotView.findViewById(R.id.valueText);
            Switch statusSwitch = slotView.findViewById(R.id.statusSwitch);
            LinearLayout layout = slotView.findViewById(R.id.layout);

            title.setText("Wood Slot " + i );

            final int slotNumber = i;

            FirebaseHelper.retrieveStringData("Sensors/" + i + "/Status", new FirebaseHelper.StringDataCallback() {
                @Override
                public void onStringReceived(String value) {
                    Log.d("FirebaseDebug", "Retrieved Status:" + value);
                    if (Objects.equals(value, "Inactive")){
                        TVvalue.setVisibility(View.INVISIBLE);
                        statusSwitch.setVisibility(View.INVISIBLE);
                        layout.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.rack_background));
                    } else {
                        TVvalue.setVisibility(View.VISIBLE);
                        statusSwitch.setVisibility(View.VISIBLE);
                        layout.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.rack_background_inactive));
                        title.setTextColor(Color.WHITE);
                        TVvalue.setTextColor(Color.WHITE);
                    }

                    if (Objects.equals(value, "Complete")){
                        statusSwitch.setChecked(true);
                    } else {
                        statusSwitch.setChecked(false);
                    }
                }

                @Override
                public void onError(String errorMessage) {
                    Log.e("FirebaseDebug", "Error: " + errorMessage);
                }
            });
            FirebaseHelper.retrieveFloatData("Sensors/" + i + "/Value", new FirebaseHelper.FloatDataCallback() {
                @Override
                public void onFloatReceived(float value) {
                    Log.d("FirebaseDebug", "Retrieved Value:" + value);
                    TVvalue.setText(value + "%");
                }

                @Override
                public void onError(String errorMessage) {
                    Log.e("FirebaseDebug", "Error: " + errorMessage);
                    TVvalue.setText("Error");
                }
            });

            //slotView.setOnClickListener(v -> openSlotDetails(slotNumber));

            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = 200;
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
