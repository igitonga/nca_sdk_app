package com.example.ncasdk;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import com.example.ncasdk.http.FlushManager;
import com.example.ncasdk.http.Network;
import com.example.ncasdk.model.MetricEvent;
import com.example.ncasdk.impl.MetricsCollector; // Matches your package import
import com.example.ncasdk.store.Store;

public class Metrics {
    private static final String TAG = "Metrics";
    private static volatile Metrics instance; // Marked volatile for double-checked locking safety

    private final Context appContext;
    private final String appToken; // Added missing field declaration
    private final Store storage;
    private final FlushManager flushManager;
    private final Network network = new Network();

    private boolean isTrackingStarted = false; // Added missing state boolean flag
    private static boolean isFirstActivityLaunched = false;
    private long warmStartStartTime = 0;
    private ANRTracker anrTracker;

    // Private constructor handles core infrastructure assignments
    private Metrics(Context context, String apiUrl, String appToken) {
        this.appContext = context.getApplicationContext();
        this.appToken = appToken;
        this.storage = new Store(this.appContext);

        // Initialize the background worker pipeline engine
        this.flushManager = new FlushManager(this.appContext, apiUrl);
    }

    /**
     * Public thread-safe SDK entry point. Safe to invoke on the Main Thread.
     */
    public static void init(Context context, final String apiUrl, final String appToken) {
        if (context == null || apiUrl == null || appToken == null) {
            Log.e(TAG, "Initialization failed: parameters cannot be null.");
            return;
        }

        if (instance == null) {
            synchronized (Metrics.class) { // Corrected locking class reference
                if (instance == null) {
                    // Corrected instantiation to use the true class name 'Metrics'
                    instance = new Metrics(context, apiUrl, appToken);

                    // Offload file reading and lifecycle hook registrations to a background runner
                    instance.bootAsync();
                }
            }
        }
    }

    public static Metrics getInstance() {
        return instance;
    }

    /**
     * Dispatches heavy setup logic away from the main UI pipeline thread.
     */
    private void bootAsync() {
        // We reuse the FlushManager's single-thread scheduler to execute our bootstrap sequence
        flushManager.getScheduler().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    // 1. Core structural metrics instance compilation
                    MetricsCollector collector = MetricsCollector.getInstance(appContext);
                    collector.setFlushManager(flushManager);

                    // 2. Safely attach listener trackers to the Main thread UI loops
                    registerAutomatedTrackers();

                    syncHistoricalCacheLogs();

                    boolean here = network.registerAppTokenOnBackend(appToken);

                    flushManager.start();

                    Log.i(TAG, "Metrics SDK initialized asynchronously and securely." + here);
                } catch (Exception e) {
                    Log.e(TAG, "Critical failure during SDK background setup process sequence", e);
                }
            }
        });
    }

    private void registerAutomatedTrackers() {
        // Enforce registration to occur safely on the main loop pipeline thread structure
        new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                if (isTrackingStarted) return;

                Application app = (Application) appContext;
                app.registerActivityLifecycleCallbacks(new AppLifecycleTracker());
                app.registerActivityLifecycleCallbacks(new SessionTracker());
                app.registerActivityLifecycleCallbacks(new ScreenTracker());

                // Keep global crash handler active
                Thread.setDefaultUncaughtExceptionHandler(new CrashTracker(appContext));
                isTrackingStarted = true;
            }
        });
    }

    private void syncHistoricalCacheLogs() {
        // Drain past crashes
        int pastCrashes = storage.getAndClearCrashCount();
        if (pastCrashes > 0) {
            MetricsCollector.getInstance(appContext).trackCrash(
                    new RuntimeException("Historical Crash recovered from previous session storage data.")
            );
        }

        // Drain past ANRs
        int pastAnrs = storage.getAndClearAnrCount();
        if (pastAnrs > 0) {
            MetricsCollector.getInstance(appContext).trackCustomEvent("historical_anr_count", String.valueOf(pastAnrs));
        }
    }

    public static void recordEvent(String eventJsonPayload) {
        Log.d(TAG, "Metric Saved and Buffered: " + eventJsonPayload);
    }

    void handleAppGoingToBackground() {
        warmStartStartTime = SystemClock.uptimeMillis();

        if (anrTracker != null) {
            anrTracker.stopWatching();
            anrTracker = null;
            Log.d(TAG, "ANR Watchdog stopped.");
        }
    }

    void handleActivityDrawn() {
        if (!isFirstActivityLaunched) {
            isFirstActivityLaunched = true;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // System process launch clock offsets
                long processStartTime = System.currentTimeMillis() - SystemClock.elapsedRealtime();
                long currentUptime = SystemClock.uptimeMillis();
                long coldStartTimeMs = currentUptime - processStartTime;

                MetricsCollector.getInstance(appContext).trackAppStart(coldStartTimeMs);
            }
        } else if (warmStartStartTime > 0) {
            long warmStartTimeMs = SystemClock.uptimeMillis() - warmStartStartTime;
            warmStartStartTime = 0;

            MetricsCollector.getInstance(appContext).trackAppStart(warmStartTimeMs);
        }
    }

    /**
     * Backward-compatible router method fallback.
     * Maps older incoming metric name keys onto the new Collector.
     */
    public void recordMetric(String metricName, long valueMs) {
        MetricsCollector collector = MetricsCollector.getInstance(appContext);

        if ("anr_count".equals(metricName)) {
            collector.trackCustomEvent("historical_anr_count", String.valueOf(valueMs));
        } else {
            collector.trackCustomEvent(metricName, String.valueOf(valueMs));
        }
    }

    public void handleAppForegrounded() {
        if (anrTracker == null || !anrTracker.isAlive()) {
            anrTracker = new ANRTracker(appContext);
            anrTracker.start();
        }
    }
}