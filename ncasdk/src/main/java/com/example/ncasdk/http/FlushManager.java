package com.example.ncasdk.http;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import com.example.ncasdk.model.MetricEvent;
import com.example.ncasdk.impl.MetricsCollector;
import com.example.ncasdk.store.Store;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class FlushManager {
    private static final String TAG = "FlushManager";
    private static final int MAX_BATCH_SIZE = 50;
    private static final int FLUSH_INTERVAL_SECONDS = 30;
    private static final int MAX_RETRIES = 3;

    private final Context appContext;
    private final String endpointUrl;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean isFlushing = new AtomicBoolean(false);
    private final Store storage;

    public FlushManager(Context context, String endpointUrl) {
        this.appContext = context.getApplicationContext();
        this.endpointUrl = endpointUrl;
        this.storage = new Store(appContext);
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    /**
     * Starts the periodic 30-second background flush scheduler loop.
     */
    public void start() {
        scheduler.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Time trigger fired. Initiating batch flush...");
                flushQueue();
            }
        }, FLUSH_INTERVAL_SECONDS, FLUSH_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Call this whenever an item is added to check if the size trigger (>= 50) is reached.
     */
    public void checkSizeTrigger(int currentQueueSize) {
        if (currentQueueSize >= MAX_BATCH_SIZE) {
            Log.d(TAG, "Size trigger fired (Queue size: " + currentQueueSize + "). Initiating explicit flush...");
            scheduler.execute(new Runnable() {
                @Override
                public void run() {
                    flushQueue();
                }
            });
        }
    }

    /**
     * Drains the concurrent queue and dispatches items over the network one by one
     * matching the structured payload requirement.
     */
    private void flushQueue() {
        // Prevent concurrent overlapping flush execution runs
        if (!isFlushing.compareAndSet(false, true)) {
            Log.d(TAG, "Flush routine already in progress. Skipping execution loop.");
            return;
        }

        try {
            MetricsCollector collector = MetricsCollector.getInstance(appContext);
            List<MetricEvent> batch = new ArrayList<>();

            // Poll up to 50 items out of the queue atomically
            while (batch.size() < MAX_BATCH_SIZE) {
                MetricEvent event = collector.getEventQueue().poll();
                if (event == null) break; // Queue is fully empty
                batch.add(event);
            }

            if (batch.isEmpty()) {
                isFlushing.set(false);
                return;
            }

            Log.d(TAG, "Processing " + batch.size() + " items from pipeline queue...");

            // Track dynamic items that fail network registration so we can restore them if needed
            List<MetricEvent> failedEvents = new ArrayList<>();

            for (MetricEvent event : batch) {
                // Compile the event into your target platform structural format
                String jsonPayload = buildStructuredPayload(event);

                // Execute network payload delivery with exponential back-off constraints
                boolean success = executePostWithRetry(jsonPayload);

                if (!success) {
                    failedEvents.add(event);
                }
            }

            if (failedEvents.isEmpty()) {
                Log.i(TAG, "Successfully synced batch of " + batch.size() + " individual events to API.");
            } else {
                Log.e(TAG, "Failed to sync " + failedEvents.size() + " out of " + batch.size() + " events after max retries. Purging to avoid blockages.");
                // Optional: Re-enqueue failedEvents back to collector queue if you want a reliable offline buffer fallback
            }

        } catch (Exception e) {
            Log.e(TAG, "Exception running internal flush operational block", e);
        } finally {
            isFlushing.set(false);
        }
    }

    /**
     * Formats an isolated MetricEvent into your exact target backend specification format schema.
     */
    private String buildStructuredPayload(MetricEvent event) {
        // Hardcoded example or pull dynamically if your appTokenId is saved elsewhere
        int appTokenId = 7;

        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"app_token_id\":").append(appTokenId).append(",");
        sb.append("\"event_type\":\"").append(escapeJson(event.getEventType())).append("\",");
        sb.append("\"session_id\":\"").append(escapeJson(event.getSessionId().toString())).append("\",");
        sb.append("\"device_id\":\"").append(escapeJson(event.getDeviceId())).append("\"");

        // Conditionally map optional metric text properties
        if (event.getValue() != 0.0) { // Assuming 0.0 is the default unassigned state
            sb.append(",\"value\":\"").append(String.valueOf(event.getValue())).append("\"");
        }
        if (event.getUnit() != null && !event.getUnit().trim().isEmpty()) {
            sb.append(",\"unit\":\"").append(escapeJson(event.getUnit())).append("\"");
        }

        Map<String, String> attrsMap = event.getAttributes();
        // 👇 Fetch real-time battery percentage right here at flush time
        float currentBattery = getBatteryLevelPct();

        if (attrsMap != null && !attrsMap.isEmpty()) {
            StringBuilder mapBuilder = new StringBuilder();
            mapBuilder.append("{");

            if (currentBattery >= 0.0f) {
                mapBuilder.append("\"battery_level_pct\":").append(currentBattery);
                if (attrsMap != null && !attrsMap.isEmpty()) {
                    mapBuilder.append(",");
                }
            }

            int count = 0;
            for (java.util.Map.Entry<String, String> entry : attrsMap.entrySet()) {
                mapBuilder.append("\"").append(escapeJson(entry.getKey())).append("\":")
                        .append("\"").append(escapeJson(entry.getValue())).append("\"");

                if (++count < attrsMap.size()) {
                    mapBuilder.append(",");
                }
            }
            mapBuilder.append("}");

            sb.append(",\"attributes\":").append(mapBuilder.toString());
        }

        sb.append("}");
        return sb.toString();
    }

    /**
     * Executes the HTTP POST transmission using standard HttpURLConnection.
     * Implements exponential backoff retry timings: 1s, 2s, 4s.
     */
    private boolean executePostWithRetry(String jsonPayload) {
        int retryCount = 0;
        long backoffMs = 1000;

        while (retryCount <= MAX_RETRIES) {
            HttpURLConnection urlConnection = null;
            try {
                URL url = new URL(endpointUrl);
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("POST");
                urlConnection.setRequestProperty("Content-Type", "application/json; utf-8");
                urlConnection.setRequestProperty("Accept", "application/json");
                urlConnection.setConnectTimeout(10000);
                urlConnection.setReadTimeout(10000);
                urlConnection.setDoOutput(true);

                try (OutputStream os = urlConnection.getOutputStream()) {
                    byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                    os.flush();
                }

                int responseCode = urlConnection.getResponseCode();
                if (responseCode >= 200 && responseCode < 300) {
                    return true;
                }

                Log.w(TAG, "Server responded with error status code: " + responseCode);

            } catch (Exception e) {
                Log.w(TAG, "Network attempt " + (retryCount + 1) + " encountered an exception: " + e.getMessage());
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
            }

            retryCount++;
            if (retryCount <= MAX_RETRIES) {
                try {
                    Log.d(TAG, "Retrying execution in " + backoffMs + "ms (Attempt " + retryCount + "/" + MAX_RETRIES + ")...");
                    Thread.sleep(backoffMs);
                    backoffMs *= 2;
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }

    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    /**
     * Clean down hook interface helper to stop executor allocations smoothly.
     */
    public void shutdown() {
        scheduler.shutdown();
    }

    public ScheduledExecutorService getScheduler() {
        return this.scheduler;
    }

    /**
     * Queries the system for the current battery level as a float percentage between 0.0 and 1.0.
     */
    private float getBatteryLevelPct() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                android.os.BatteryManager bm = (android.os.BatteryManager) appContext.getSystemService(Context.BATTERY_SERVICE);
                if (bm != null) {
                    int capacity = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY); // Returns 0-100
                    return capacity / 100.0f; // Convert to Float format (e.g., 0.85)
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Unable to read battery level: " + e.getMessage());
        }
        return -1.0f; // Return fallback value if reading fails
    }
}