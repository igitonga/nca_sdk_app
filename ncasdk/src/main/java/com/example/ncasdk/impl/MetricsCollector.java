package com.example.ncasdk.impl;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.provider.Settings;

import com.example.ncasdk.http.FlushManager;
import com.example.ncasdk.model.MetricEvent;

import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
public final class MetricsCollector {
    private static volatile MetricsCollector instance;
    private final Context appContext;
    private final UUID sessionId;
    private final String deviceId;
    private FlushManager flushManager;

    // Thread-safe, lock-free queue ideal for high-throughput SDK metric logging
    private final ConcurrentLinkedQueue<MetricEvent> eventQueue;

    // Private constructor to enforce singleton usage pattern
    private MetricsCollector(Context context) {
        this.appContext = context.getApplicationContext();
        this.eventQueue = new ConcurrentLinkedQueue<>();
        this.sessionId = UUID.randomUUID(); // Initialize unique session identifier
        this.deviceId = fetchDeviceId();    // Securely cache device ID upon startup
    }

    /**
     * Double-Checked Locking implementation for thread-safe lazy initialization.
     */
    public static MetricsCollector getInstance(Context context) {
        if (instance == null) {
            synchronized (MetricsCollector.class) {
                if (instance == null) {
                    instance = new MetricsCollector(context);
                }
            }
        }
        return instance;
    }

    public void setFlushManager(FlushManager flushManager) {
        this.flushManager = flushManager;
    }

    public String getActiveNetworkType() {
        try {
            ConnectivityManager cm = (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return "UNKNOWN";

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network activeNetwork = cm.getActiveNetwork();
                NetworkCapabilities capabilities = cm.getNetworkCapabilities(activeNetwork);
                if (capabilities == null) return "DISCONNECTED";

                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "WIFI";
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return "CELLULAR";
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return "ETHERNET";
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return "VPN";
            } else {
                // Backward-compatible fallback for older Android hardware devices
                android.net.NetworkInfo info = cm.getActiveNetworkInfo();
                if (info == null || !info.isConnected()) return "DISCONNECTED";
                if (info.getType() == ConnectivityManager.TYPE_WIFI) return "WIFI";
                if (info.getType() == ConnectivityManager.TYPE_MOBILE) return "CELLULAR";
                if (info.getType() == ConnectivityManager.TYPE_ETHERNET) return "ETHERNET";
            }
        } catch (Exception e) {
            // Shield SDK execution from permission security denials
            return "PERMISSION_RESTRICED";
        }
        return "OTHER";
    }

    public void trackAppStart(long ms) {
        MetricEvent event = createBaseEvent("app_start_time_ms", (double) ms, "ms")
                .build();
        enqueue(event);
    }

    public void trackScreenView(String name, long ms) {
        MetricEvent event = createBaseEvent("screen_view", (double) ms, "ms")
                .addAttribute("screen_name", name)
                .build();
        enqueue(event);
    }

    public void trackHttpRequest(int statusCode, long latencyMs) {
        MetricEvent event = createBaseEvent("http_request", (double) latencyMs, "ms")
                .addAttribute("status_code", String.valueOf(statusCode))
                .addAttribute("network_success", String.valueOf(statusCode >= 200 && statusCode < 300))
                .build();
        enqueue(event);
    }

    public void trackCrash(Throwable t) {
        MetricEvent event = createBaseEvent("crash_count", 1.0, "count")
                .addAttribute("exception_class", t.getClass().getName())
                .addAttribute("exception_message", t.getMessage() != null ? t.getMessage() : "No message")
                .build();
        enqueue(event);
    }

    public void trackCustomEvent(String key, String value) {
        MetricEvent event = createBaseEvent("custom_event", 1.0, "count")
                .addAttribute(key, value)
                .build();
        enqueue(event);
    }

    /**
     * Expose the queue so your background sync scheduler or worker can poll items out.
     */
    public ConcurrentLinkedQueue<MetricEvent> getEventQueue() {
        return eventQueue;
    }

    // --- Private Internal Helpers ---

    /**
     * Helper method to initialize a pre-configured builder populated with common metadata.
     */
    private MetricEvent.Builder createBaseEvent(String eventType, double value, String unit) {
        return new MetricEvent.Builder()
                .eventType(eventType)
                .value(value)
                .unit(unit)
                .timestamp(System.currentTimeMillis())
                .sessionId(this.sessionId)
                .deviceId(this.deviceId)
                .addAttribute("network_type", getActiveNetworkType())
                .addAttribute("os_architecture", System.getProperty("os.arch"))
                .addAttribute("android_version", String.valueOf(android.os.Build.VERSION.SDK_INT));
    }

    private void enqueue(MetricEvent event) {
        eventQueue.offer(event);
        // Optional hook: notify background thread executor or pipeline to run a batch sync check
    }

    /**
     * Fetches a reliable unique hardware identifier fallback across device configurations.
     */
    private String fetchDeviceId() {
        try {
            String androidId = Settings.Secure.getString(appContext.getContentResolver(), Settings.Secure.ANDROID_ID);
            if (androidId != null && !androidId.trim().isEmpty()) {
                return androidId;
            }
        } catch (Exception e) {
            // Fallback safety logic
        }
        return "fallback_" + android.os.Build.BOARD.length() % 10 + android.os.Build.BRAND.length() % 10;
    }
}
