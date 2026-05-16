package com.example.ncasdk;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

public class AppLifecycleTracker implements Application.ActivityLifecycleCallbacks {
    private int visibleActivities = 0;

    Metrics metrics = Metrics.getInstance();

    @Override
    public void onActivityStarted(Activity activity) {
        visibleActivities++;

        metrics.handleAppForegrounded();
    }

    @Override
    public void onActivityResumed(Activity activity) {
        metrics.handleActivityDrawn();
    }

    @Override
    public void onActivityStopped(Activity activity) {
        visibleActivities--;
        if (visibleActivities == 0) {
            metrics.handleAppGoingToBackground();
        }
    }

    @Override public void onActivityCreated(Activity a, Bundle b) {}
    @Override public void onActivityPaused(Activity a) {}
    @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
    @Override public void onActivityDestroyed(Activity a) {}
}
