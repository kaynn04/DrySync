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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class StatsFragment extends Fragment {

    // --- Temperature views ---
    private CircularProgressIndicator tempProgress;
    private TextView temperatureText, tvTempTarget, tvTempLastUpdated;
    private TextView tvTempMin, tvTempMax, tvTempAvg, tvTempTrend;
    private TextView tvTempStatus; // NEW: shows status vs target range

    // --- Humidity views ---
    private CircularProgressIndicator humidProgress;
    private TextView humidityText, tvHumidTarget, tvHumidLastUpdated;
    private TextView tvHumidMin, tvHumidMax, tvHumidAvg, tvHumidTrend;
    private TextView tvHumidStatus; // NEW: shows status vs target range

    // Rolling datasets for last 24h
    private final List<Sample> tempHistory = new ArrayList<>();
    private final List<Sample> humidHistory = new ArrayList<>();

    private static class Sample {
        long timeMillis;
        float value;
        Sample(long t, float v) { timeMillis = t; value = v; }
    }

    // Target ranges for wood drying environment
    private static final int TEMP_MIN_TARGET = 20;  // °C
    private static final int TEMP_MAX_TARGET = 35;  // °C
    private static final int HUMID_MIN_TARGET = 45; // %
    private static final int HUMID_MAX_TARGET = 60; // %

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
        tvTempStatus       = v.findViewById(R.id.tvTempStatus); // NEW

        // --- bind Humidity ---
        humidProgress      = v.findViewById(R.id.humidProgress);
        humidityText       = v.findViewById(R.id.humidity_text);
        tvHumidTarget      = v.findViewById(R.id.tvHumidTarget);
        tvHumidLastUpdated = v.findViewById(R.id.tvHumidLastUpdated);
        tvHumidMin         = v.findViewById(R.id.tvHumidMin);
        tvHumidMax         = v.findViewById(R.id.tvHumidMax);
        tvHumidAvg         = v.findViewById(R.id.tvHumidAvg);
        tvHumidTrend       = v.findViewById(R.id.tvHumidTrend);
        tvHumidStatus      = v.findViewById(R.id.tvHumidStatus); // NEW

        // Firebase callbacks
        FirebaseHelper.retrieveFloatData("Environment/Temperature", new FirebaseHelper.FloatDataCallback() {
            @Override public void onFloatReceived(float value) {
                long now = System.currentTimeMillis();
                addSample(tempHistory, now, value, 24 * 60 * 60 * 1000L);
                updateTemperatureStats();
                setTemperature((int) value); // also updates status
                setTemperatureLastUpdated(now);
            }
            @Override public void onError(String errorMessage) {
                temperatureText.setText(errorMessage);
            }
        });

        FirebaseHelper.retrieveFloatData("Environment/Humidity", new FirebaseHelper.FloatDataCallback() {
            @Override public void onFloatReceived(float value) {
                long now = System.currentTimeMillis();
                addSample(humidHistory, now, value, 24 * 60 * 60 * 1000L);
                updateHumidityStats();
                setHumidity((int) value); // also updates status
                setHumidityLastUpdated(now);
            }
            @Override public void onError(String errorMessage) {
                humidityText.setText(errorMessage);
            }
        });
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

        // Update status label (wood drying target check)
        if (tvTempStatus != null) {
            if (celsius < TEMP_MIN_TARGET) {
                tvTempStatus.setText("Status: Too Low");
                tvTempStatus.setTextColor(getResources().getColor(android.R.color.holo_blue_dark));
            } else if (celsius > TEMP_MAX_TARGET) {
                tvTempStatus.setText("Status: Too High");
                tvTempStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            } else {
                tvTempStatus.setText("Status: OK");
                tvTempStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            }
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

        // Update status label (wood drying target check)
        if (tvHumidStatus != null) {
            if (percent < HUMID_MIN_TARGET) {
                tvHumidStatus.setText("Status: Too Low");
                tvHumidStatus.setTextColor(getResources().getColor(android.R.color.holo_blue_dark));
            } else if (percent > HUMID_MAX_TARGET) {
                tvHumidStatus.setText("Status: Too High");
                tvHumidStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            } else {
                tvHumidStatus.setText("Status: OK");
                tvHumidStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            }
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

    private void setHumidityStats(int min, int max, float average, int delta1h) {
        if (tvHumidMin != null)   tvHumidMin.setText(min + "%");
        if (tvHumidMax != null)   tvHumidMax.setText(max + "%");
        if (tvHumidAvg != null)   tvHumidAvg.setText(formatOneDecimal(average) + "%");
        if (tvHumidTrend != null) tvHumidTrend.setText(formatSigned(delta1h) + "%");
    }

    // === Rolling dataset helpers ===
    private void addSample(List<Sample> history, long now, float value, long windowMillis) {
        history.add(new Sample(now, value));
        long cutoff = now - windowMillis;
        while (!history.isEmpty() && history.get(0).timeMillis < cutoff) {
            history.remove(0);
        }
    }

    private void updateTemperatureStats() {
        if (tempHistory.isEmpty()) return;
        float min = Float.MAX_VALUE, max = Float.MIN_VALUE, sum = 0;
        for (Sample s : tempHistory) {
            min = Math.min(min, s.value);
            max = Math.max(max, s.value);
            sum += s.value;
        }
        float avg = sum / tempHistory.size();
        long oneHourAgo = System.currentTimeMillis() - 3600_000;
        float value1hAgo = tempHistory.get(0).value;
        for (int i = tempHistory.size() - 1; i >= 0; i--) {
            if (tempHistory.get(i).timeMillis <= oneHourAgo) {
                value1hAgo = tempHistory.get(i).value;
                break;
            }
        }
        int delta1h = Math.round(tempHistory.get(tempHistory.size() - 1).value - value1hAgo);
        setTemperatureStats(Math.round(min), Math.round(max), avg, delta1h);
    }

    private void updateHumidityStats() {
        if (humidHistory.isEmpty()) return;
        float min = Float.MAX_VALUE, max = Float.MIN_VALUE, sum = 0;
        for (Sample s : humidHistory) {
            min = Math.min(min, s.value);
            max = Math.max(max, s.value);
            sum += s.value;
        }
        float avg = sum / humidHistory.size();
        long oneHourAgo = System.currentTimeMillis() - 3600_000;
        float value1hAgo = humidHistory.get(0).value;
        for (int i = humidHistory.size() - 1; i >= 0; i--) {
            if (humidHistory.get(i).timeMillis <= oneHourAgo) {
                value1hAgo = humidHistory.get(i).value;
                break;
            }
        }
        int delta1h = Math.round(humidHistory.get(humidHistory.size() - 1).value - value1hAgo);
        setHumidityStats(Math.round(min), Math.round(max), avg, delta1h);
    }

    // ===== helpers =====
    private String formatOneDecimal(float value) {
        return String.format(Locale.getDefault(), "%.1f", value);
    }

    private String formatSigned(int value) {
        return (value > 0 ? "+" : "") + value;
    }
}
