package com.example.ncasdk;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.ncasdk.store.Store;

public class ANRTracker extends Thread{
    private static final String TAG = "Metric-ANR";
    private static final int ANR_TIMEOUT_MS = 5000; // 5 seconds threshold

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Context context;
    private volatile boolean isRunning = true;
    private volatile boolean pingAck = false;

    private final Runnable pingRunnable = new Runnable() {
        @Override
        public void run() {
            pingAck = true; // Main thread completed the work and set ack to true
        }
    };

    public ANRTracker(Context context) {
        this.context = context.getApplicationContext();
        setName("MyMetricsSDK-AnrWatchdog");
    }

    @Override
    public void run() {
        while (isRunning) {
            pingAck = false;

            // Post the token runnable to the main thread queue
            mainHandler.post(pingRunnable);

            try {
                // Wait for the timeout threshold duration
                Thread.sleep(ANR_TIMEOUT_MS);
            } catch (InterruptedException e) {
                Log.d(TAG, "Watchdog interrupted.");
                return;
            }

            // Check if the main thread executed our runnable during our sleep window
            if (!pingAck) {
                Log.e(TAG, "ANR Detected! The main thread has been frozen for over " + ANR_TIMEOUT_MS + "ms");

                // Track this inside our local storage cache synchronously
                Store store = new Store(context);
                store.incrementAnrCount();

                // Wait until the main thread finally recovers before tracking another ANR
                while (!pingAck && isRunning) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
        }
    }

    public void stopWatching() {
        this.isRunning = false;
        interrupt();
    }
}
