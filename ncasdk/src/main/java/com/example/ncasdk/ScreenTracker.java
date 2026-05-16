package com.example.ncasdk;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
public class ScreenTracker implements Application.ActivityLifecycleCallbacks{
    private static final String TAG = "Metrics-Screen";

    private long screenStartTimeMs = 0;
    private String currentScreenName = null;

    Metrics metrics = Metrics.getInstance();

    @Override
    public void onActivityResumed(Activity activity) {
        // Capture the name of the screen the user just entered
        currentScreenName = activity.getClass().getSimpleName();
        // Start the stopwatch
        screenStartTimeMs = SystemClock.elapsedRealtime();
    }

    @Override
    public void onActivityPaused(Activity activity) {
        // Make sure we are calculating for the correct screen
        if (currentScreenName != null && currentScreenName.equals(activity.getClass().getSimpleName())) {
            long timeSpentMs = SystemClock.elapsedRealtime() - screenStartTimeMs;

            Log.d(TAG, "Exited screen: " + currentScreenName + " after " + timeSpentMs + "ms");

            // Forward the metric payload to the main SDK pipeline
            metrics.recordMetric("screen_view", timeSpentMs);
        }

        // Reset state for the next screen transition
        screenStartTimeMs = 0;
        currentScreenName = null;
    }

    // Unused lifecycle overrides required by the interface
    @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}
    @Override public void onActivityStarted(Activity activity) {}
    @Override public void onActivityStopped(Activity activity) {}
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
    @Override public void onActivityDestroyed(Activity activity) {}
}
