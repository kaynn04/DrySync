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
        TextView moisture = findViewById(R.id.moistureText);

        title.setText("Wood Slot " + slotNumber);
        moisture.setText("Moisture: " + getFakeMoisture(slotNumber) + "%");
    }

    private int getFakeMoisture(int slot) {
        return 15 + (slot % 5);  // fake moisture value for now
    }
}
