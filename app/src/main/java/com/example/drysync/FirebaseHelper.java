package com.example.drysync;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.database.*;

public class FirebaseHelper {
    public interface FloatDataCallback {
        void onFloatReceived(float value);
        void onError(String errorMessage);
    }
    public static void retrieveFloatData(String path, FloatDataCallback callback) {
        DatabaseReference databaseReference = FirebaseDatabase.getInstance().getReference();
        databaseReference.child(path).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Log.e("FirebaseDebug", "DataSnapshot received for: " + path);
                if (snapshot.exists()) {
                    try {
                        Object value = snapshot.getValue();
                        Log.e("FirebaseDebug", "Raw value: " + value);

                        if (value instanceof Number) {
                            callback.onFloatReceived(((Number) value).floatValue());
                        } else if (value instanceof String) {
                            try {
                                float parsed = Float.parseFloat((String) value);
                                callback.onFloatReceived(parsed);
                            } catch (NumberFormatException e) {
                                callback.onError("Invalid float string");
                            }
                        } else {
                            callback.onError("Unexpected type: " + value.getClass().getSimpleName());
                        }
                    } catch (Exception e) {
                        callback.onError("Parse error: " + e.getMessage());
                    }
                } else {
                    callback.onError("No data at path");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError("Firebase error: " + error.getMessage());
            }
        });
    }

    public interface StringDataCallback {
        void onStringReceived(String value);
        void onError(String errorMessage);
    }
    public static void retrieveStringData(String path, StringDataCallback callback) {
        DatabaseReference databaseReference = FirebaseDatabase.getInstance().getReference();
        databaseReference.child(path).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Log.e("FirebaseDebug", "DataSnapshot received for: " + path);
                if (snapshot.exists()) {
                    try {
                        Object value = snapshot.getValue();
                        Log.e("FirebaseDebug", "Raw value: " + value);

                        if (value != null) {
                            callback.onStringReceived(value.toString());
                        } else {
                            callback.onError("Null value at path");
                        }
                    } catch (Exception e) {
                        callback.onError("Parse error: " + e.getMessage());
                    }
                } else {
                    callback.onError("No data at path");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError("Firebase error: " + error.getMessage());
            }
        });
    }

}
