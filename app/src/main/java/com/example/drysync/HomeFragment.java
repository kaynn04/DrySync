package com.example.drysync;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class HomeFragment extends Fragment {

    private DatabaseReference databaseReference;

    private TextView temp_text, humid_text;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_home, container, false);

        temp_text = view.findViewById(R.id.temperature_text);
        humid_text = view.findViewById(R.id.humidity_text);

        FirebaseHelper.retrieveFloatData("Environment/Temperature", new FirebaseHelper.FloatDataCallback() {
            @Override
            public void onFloatReceived(float value) {
                temp_text.setText(value + "°C");
                Log.e("FirebaseDebug", "Temperature: " + value);
            }

            @Override
            public void onError(String errorMessage) {
                temp_text.setText(errorMessage);
                Log.e("FirebaseDebug", "Error: " + errorMessage);
            }
        });
        FirebaseHelper.retrieveFloatData("Environment/Humidity", new FirebaseHelper.FloatDataCallback() {
            @Override
            public void onFloatReceived(float value) {
                humid_text.setText(value + "%");
                Log.e("FirebaseDebug", "Humidity: " + value);
            }

            @Override
            public void onError(String errorMessage) {
                humid_text.setText(errorMessage);
                Log.e("FirebaseDebug", "Error: " + errorMessage);
            }
        });
        // Inflate the layout for this fragment
        return view;
    }

}
