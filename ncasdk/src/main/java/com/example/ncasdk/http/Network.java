package com.example.ncasdk.http;

import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
public class Network {
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final String apiKey = "your_test_api_key_xyz123";
    private final String API_URL = "http://10.0.2.2:8000";
    private static final String TAG = "NetworkEngine";

    public void sendMetricToApi(
            final int appTokenId,
            final String eventType,
            final String sessionId,
            final String deviceId,
            final String value,
            final String unit,
            final String attributesJson
    ) {
        // Dispatch the task to our background thread instantly
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection urlConnection = null;
                try {
                    // 1. Set up the connection layout
                    URL url = new URL(API_URL);
                    urlConnection = (HttpURLConnection) url.openConnection();
                    urlConnection.setRequestMethod("POST");
                    urlConnection.setRequestProperty("Content-Type", "application/json; utf-8");
                    urlConnection.setRequestProperty("Accept", "application/json");
                    // Note: Removed Bearer token since app_token_id is in the body,
                    // but keep it if your backend requires dual authentication!
                    urlConnection.setRequestProperty("Authorization", "Bearer " + apiKey);
                    urlConnection.setDoOutput(true);

                    // 2. Structure the exact JSON body payload manually
                    StringBuilder jsonBuilder = new StringBuilder();
                    jsonBuilder.append("{");
                    jsonBuilder.append("\"app_token_id\":").append(appTokenId).append(",");
                    jsonBuilder.append("\"event_type\":\"").append(escapeJson(eventType)).append("\",");
                    jsonBuilder.append("\"session_id\":\"").append(escapeJson(sessionId)).append("\",");
                    jsonBuilder.append("\"device_id\":\"").append(escapeJson(deviceId)).append("\"");

                    // Append optional value if present
                    if (value != null) {
                        jsonBuilder.append(",\"value\":\"").append(escapeJson(value)).append("\"");
                    }

                    // Append optional unit if present
                    if (unit != null) {
                        jsonBuilder.append(",\"value\":\"").append(escapeJson(unit)).append("\"");
                    }

                    // Append optional freeform attributes object if present
                    if (attributesJson != null && !attributesJson.trim().isEmpty()) {
                        jsonBuilder.append(",\"attributes\":").append(attributesJson);
                    }
                    jsonBuilder.append("}");

                    String finalPayload = jsonBuilder.toString();

                    // 3. Write the payload out to the API stream
                    try (OutputStream os = urlConnection.getOutputStream()) {
                        byte[] input = finalPayload.getBytes(StandardCharsets.UTF_8);
                        os.write(input, 0, input.length);
                    }

                    // 4. Check the API response status code
                    int responseCode = urlConnection.getResponseCode();
                    if (responseCode >= 200 && responseCode < 300) {
                        android.util.Log.d("MyMetricsSDK-Net", "Successfully synced structured metric to server.");
                    } else {
                        android.util.Log.e("MyMetricsSDK-Net", "Failed to sync. Server responded with code: " + responseCode);
                    }

                } catch (Exception e) {
                    android.util.Log.e("MyMetricsSDK-Net", "Network error occurred while syncing metrics", e);
                } finally {
                    if (urlConnection != null) {
                        urlConnection.disconnect();
                    }
                }
            }
        });
    }

    public boolean registerAppTokenOnBackend(String appToken) {
        if (appToken == null || appToken.trim().isEmpty()) {
            Log.e(TAG, "Token registration aborted: Provided appToken is empty or null.");
            return false;
        }

        HttpURLConnection urlConnection = null;
        // Adjust this endpoint to match your authentication or registration server layout
        String registerUrl = API_URL + "/app-token";

        try {
            URL url = new URL(registerUrl);
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("POST");
            urlConnection.setRequestProperty("Content-Type", "application/json; utf-8");
            urlConnection.setRequestProperty("Accept", "application/json");
            urlConnection.setConnectTimeout(8000); // 8-second network connection limit
            urlConnection.setReadTimeout(8000);    // 8-second socket data stream read limit
            urlConnection.setDoOutput(true);

            // Construct native JSON without heavy external parsing dependencies
            String jsonPayload = "{\"label\":\"" + escapeJson(appToken) + "\"}";

            // Stream the JSON text payload down to the server pipe
            try (OutputStream os = urlConnection.getOutputStream()) {
                byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
                os.flush();
            }

            int responseCode = urlConnection.getResponseCode();
            Log.d(TAG, "Token validation network response status code received: " + responseCode);


            return (responseCode >= 200 && responseCode < 300);

        } catch (Exception e) {
            Log.w(TAG, "Unable to register app token on backend (Device may be offline): " + e.getMessage());
            return false;
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
    }

    /**
     * Basic utility to escape special characters inside JSON strings safely.
     */
    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
