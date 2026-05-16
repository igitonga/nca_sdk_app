package com.example.ncasdk;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;

import com.example.ncasdk.store.Store;

public class CrashTracker implements Thread.UncaughtExceptionHandler{
    private static final String TAG = "Metric-Crash";
    private final Thread.UncaughtExceptionHandler defaultSystemHandler;
    private final Context context;

    public CrashTracker(Context context) {
        this.context = context.getApplicationContext();
        // Save the system's original crash handler so we don't break default Android crash behavior
        this.defaultSystemHandler = Thread.getDefaultUncaughtExceptionHandler();
    }

    @Override
    public void uncaughtException(@NonNull Thread thread, @NonNull Throwable throwable) {
        try {
            Store store = new Store(context);
            store.incrementCrashCount();

        } catch (Exception e) {
            Log.e(TAG, "Error saving crash metric internally", e);
        } finally {
            // CRITICAL: Hand control back to Android so the app can finish crashing gracefully
            if (defaultSystemHandler != null) {
                defaultSystemHandler.uncaughtException(thread, throwable);
            }
        }
    }
}
