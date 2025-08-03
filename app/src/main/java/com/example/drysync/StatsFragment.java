package com.example.drysync;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

public class StatsFragment extends Fragment {

    private Spinner statSpinner;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_stats, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        statSpinner = view.findViewById(R.id.stat_type_spinner);

        // Step 1: Create the adapter with your custom item layout (for selected view)
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                R.layout.spinner_item, // layout for the Spinner’s selected item
                new String[]{"Temperature", "Humidity"}
        );

        // Step 2: Set the dropdown layout (when the Spinner is opened)
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);

        // Step 3: Apply the adapter to the Spinner
        statSpinner.setAdapter(adapter);

        // Default fragment
        loadFragment(new TemperatureFragment());

        statSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View selectedView, int position, long id) {
                if (position == 0) {
                    loadFragment(new TemperatureFragment());
                } else {
                    loadFragment(new HumidityFragment());
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void loadFragment(Fragment fragment) {
        FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
        transaction.replace(R.id.stat_content_container, fragment);
        transaction.commit();
    }
}
