package com.example.ncasdk.store;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;
public class Store {
    private static final String PREF_NAME = "sdk_metrics_storage";
    private static final String KEY_EVENTS = "queued_events";
    private static final String KEY_CRASH_COUNT = "crash_count";
    private static final String KEY_ANR_COUNT = "anr_count";
    private final SharedPreferences prefs;

    public Store(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public synchronized void incrementCrashCount() {
        int currentCrashes = prefs.getInt(KEY_CRASH_COUNT, 0);
        prefs.edit().putInt(KEY_CRASH_COUNT, currentCrashes + 1).commit();
    }

    public synchronized int getAndClearCrashCount() {
        int crashes = prefs.getInt(KEY_CRASH_COUNT, 0);
        if (crashes > 0) {
            // Reset back to zero so we don't report the same crashes twice
            prefs.edit().putInt(KEY_CRASH_COUNT, 0).apply();
        }
        return crashes;
    }

    public synchronized void incrementAnrCount() {
        int currentAnrs = prefs.getInt(KEY_ANR_COUNT, 0);
        prefs.edit().putInt(KEY_ANR_COUNT, currentAnrs + 1).commit(); // Commit immediately to disk
    }

    public synchronized int getAndClearAnrCount() {
        int anrs = prefs.getInt(KEY_ANR_COUNT, 0);
        if (anrs > 0) {
            prefs.edit().putInt(KEY_ANR_COUNT, 0).apply();
        }
        return anrs;
    }
}
