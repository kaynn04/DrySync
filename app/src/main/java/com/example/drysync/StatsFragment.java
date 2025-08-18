package com.example.drysync;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

public class StatsFragment extends Fragment {

    // --- Temperature views ---
    private CircularProgressIndicator tempProgress;
    private TextView temperatureText, tvTempTarget, tvTempLastUpdated;
    private TextView tvTempMin, tvTempMax, tvTempAvg, tvTempTrend;

    // --- Humidity views ---
    private CircularProgressIndicator humidProgress;
    private TextView humidityText, tvHumidTarget, tvHumidLastUpdated;
    private TextView tvHumidMin, tvHumidMax, tvHumidAvg, tvHumidTrend;

    public StatsFragment() { }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_stats, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        // --- bind Temperature ---
        tempProgress       = v.findViewById(R.id.tempProgress);
        temperatureText    = v.findViewById(R.id.temperature_text);
        tvTempTarget       = v.findViewById(R.id.tvTempTarget);
        tvTempLastUpdated  = v.findViewById(R.id.tvTempLastUpdated);
        tvTempMin          = v.findViewById(R.id.tvTempMin);
        tvTempMax          = v.findViewById(R.id.tvTempMax);
        tvTempAvg          = v.findViewById(R.id.tvTempAvg);
        tvTempTrend        = v.findViewById(R.id.tvTempTrend);

        // --- bind Humidity ---
        humidProgress      = v.findViewById(R.id.humidProgress);
        humidityText       = v.findViewById(R.id.humidity_text);
        tvHumidTarget      = v.findViewById(R.id.tvHumidTarget);
        tvHumidLastUpdated = v.findViewById(R.id.tvHumidLastUpdated);
        tvHumidMin         = v.findViewById(R.id.tvHumidMin);
        tvHumidMax         = v.findViewById(R.id.tvHumidMax);
        tvHumidAvg         = v.findViewById(R.id.tvHumidAvg);
        tvHumidTrend       = v.findViewById(R.id.tvHumidTrend);

        // TODO: hook these to Firebase/live data.
        // Demo values:
        setTemperature(25);
        setTemperatureTarget("Target: 20–35°C");
        setTemperatureLastUpdated(System.currentTimeMillis());
        setTemperatureStats(22, 31, 26.4f, +1);

        setHumidity(62);
        setHumidityTarget("Ideal: 45% – 60%");
        setHumidityLastUpdated(System.currentTimeMillis());
        setHumidityStats(48, 72, 58.0f, +3);
    }

    // ===== Temperature =====
    private void setTemperature(int celsius) {
        if (tempProgress != null) {
            tempProgress.setIndeterminate(false);
            tempProgress.setMax(60);
            int clamped = Math.max(0, Math.min(60, celsius));
            tempProgress.setProgressCompat(clamped, true);
        }
        if (temperatureText != null) {
            temperatureText.setText(celsius + "°C");
        }
    }

    private void setTemperatureTarget(@NonNull String txt) {
        if (tvTempTarget != null) tvTempTarget.setText(txt);
    }

    private void setTemperatureLastUpdated(long millis) {
        if (tvTempLastUpdated != null) {
            String when = DateFormat.getDateTimeInstance().format(new Date(millis));
            tvTempLastUpdated.setText("Last update: " + when);
        }
    }

    /**
     * @param min       min temp in last 24h (°C)
     * @param max       max temp in last 24h (°C)
     * @param average   average temp in last 24h (°C)
     * @param delta1h   change vs 1 hour ago (°C), can be negative
     */
    private void setTemperatureStats(int min, int max, float average, int delta1h) {
        if (tvTempMin != null)  tvTempMin.setText(min + "°C");
        if (tvTempMax != null)  tvTempMax.setText(max + "°C");
        if (tvTempAvg != null)  tvTempAvg.setText(formatOneDecimal(average) + "°C");
        if (tvTempTrend != null) tvTempTrend.setText(formatSigned(delta1h) + "°C");
    }

    // ===== Humidity =====
    private void setHumidity(int percent) {
        if (humidProgress != null) {
            humidProgress.setIndeterminate(false);
            humidProgress.setMax(100);
            int clamped = Math.max(0, Math.min(100, percent));
            humidProgress.setProgressCompat(clamped, true);
        }
        if (humidityText != null) {
            humidityText.setText(percent + "%");
        }
    }

    private void setHumidityTarget(@NonNull String txt) {
        if (tvHumidTarget != null) tvHumidTarget.setText(txt);
    }

    private void setHumidityLastUpdated(long millis) {
        if (tvHumidLastUpdated != null) {
            String when = DateFormat.getDateTimeInstance().format(new Date(millis));
            tvHumidLastUpdated.setText("Last update: " + when);
        }
    }

    /**
     * @param min       min RH% in last 24h
     * @param max       max RH% in last 24h
     * @param average   average RH% in last 24h
     * @param delta1h   change vs 1 hour ago (percentage points), can be negative
     */
    private void setHumidityStats(int min, int max, float average, int delta1h) {
        if (tvHumidMin != null)   tvHumidMin.setText(min + "%");
        if (tvHumidMax != null)   tvHumidMax.setText(max + "%");
        if (tvHumidAvg != null)   tvHumidAvg.setText(formatOneDecimal(average) + "%");
        if (tvHumidTrend != null) tvHumidTrend.setText(formatSigned(delta1h) + "%");
    }

    // ===== helpers =====
    private String formatOneDecimal(float value) {
        // Uses current locale (e.g., 26.4)
        return String.format(Locale.getDefault(), "%.1f", value);
    }

    private String formatSigned(int value) {
        // Adds + for positive, keeps 0, and – for negative
        return (value > 0 ? "+" : "") + value;
    }
}
