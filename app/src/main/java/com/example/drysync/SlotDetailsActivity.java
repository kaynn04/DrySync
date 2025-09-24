package com.example.drysync;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class SlotDetailsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.slot_details);

        int slotNumber = getIntent().getIntExtra("slot_number", -1);

        TextView title = findViewById(R.id.slotTitle);
        TextView etaView = findViewById(R.id.etaText);

        title.setText("Wood Slot " + slotNumber);

    }

}