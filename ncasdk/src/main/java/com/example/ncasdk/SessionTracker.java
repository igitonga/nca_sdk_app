package com.example.ncasdk;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

public class SessionTracker implements Application.ActivityLifecycleCallbacks {
    private static final String TAG = "Metric-Session";

    private int visibleActivities = 0;
    private long sessionStartTimeMs = 0;

    Metrics metrics = Metrics.getInstance();

    @Override
    public void onActivityStarted(Activity activity) {
        // If visibleActivities is 0, the app was in the background and is now coming to the foreground.
        if (visibleActivities == 0) {
            sessionStartTimeMs = SystemClock.elapsedRealtime();
            Log.d(TAG, "Session started at " + sessionStartTimeMs + "ms");
        }
        visibleActivities++;
    }

    @Override
    public void onActivityStopped(Activity activity) {
        visibleActivities--;

        // If visibleActivities hits 0, all screens are hidden. The app is now in the background.
        if (visibleActivities == 0 && sessionStartTimeMs > 0) {
            long sessionEndTimeMs = SystemClock.elapsedRealtime();
            long sessionDurationMs = sessionEndTimeMs - sessionStartTimeMs;

            Log.d(TAG, "Session ended. Duration: " + sessionDurationMs + "ms");

            // Pass the metric data back to the main SDK pipeline to sync with your API
            metrics.recordMetric("session_duration_ms", sessionDurationMs);

            // Reset the start time
            sessionStartTimeMs = 0;
        }
    }

    // Unused lifecycle methods required by the interface
    @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}
    @Override public void onActivityResumed(Activity activity) {}
    @Override public void onActivityPaused(Activity activity) {}
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
    @Override public void onActivityDestroyed(Activity activity) {}
}
