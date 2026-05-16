package com.example.ncasdk.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MetricEvent {
    private final String eventType;
    private final double value;
    private final String unit;
    private final long timestamp;
    private final UUID sessionId;
    private final String deviceId;
    private final Map<String, String> attributes;

    private MetricEvent(Builder builder) {
        this.eventType = builder.eventType;
        this.value = builder.value;
        this.unit = builder.unit;
        this.timestamp = builder.timestamp;
        this.sessionId = builder.sessionId;
        this.deviceId = builder.deviceId;
        // Safeguard against mutations by wrapping it in an unmodifiable map layout
        this.attributes = builder.attributes != null
                ? Collections.unmodifiableMap(new HashMap<>(builder.attributes))
                : Collections.<String, String>emptyMap();
    }

    // --- Getters ---
    public String getEventType() {
        return eventType;
    }

    public double getValue() {
        return value;
    }

    public String getUnit() {
        return unit;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    /**
     * Converts the object data directly into a JSON formatted string layout.
     * Built natively without external reflection dependencies to keep the SDK footprint small.
     */
    public String toJson() {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"event_type\":\"").append(escapeJson(eventType)).append("\",");
        json.append("\"value\":").append(value).append(",");
        json.append("\"unit\":").append(unit != null ? "\"" + escapeJson(unit) + "\"" : "null").append(",");
        json.append("\"timestamp\":").append(timestamp).append(",");
        json.append("\"session_id\":\"").append(sessionId.toString()).append("\",");
        json.append("\"device_id\":\"").append(escapeJson(deviceId)).append("\",");

        // Append Attributes Map object representation nested inside
        json.append("\"attributes\":{");
        boolean first = true;
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            if (!first) {
                json.append(",");
            }
            json.append("\"").append(escapeJson(entry.getKey())).append("\":")
                    .append(entry.getValue() != null ? "\"" + escapeJson(entry.getValue()) + "\"" : "null");
            first = false;
        }
        json.append("}");

        json.append("}");
        return json.toString();
    }

    // Basic internal JSON escape string utility function
    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // --- Static Fluent Builder Pattern Engine ---
    public static class Builder {
        private String eventType;
        private double value;
        private String unit;
        private long timestamp = System.currentTimeMillis(); // Default to runtime execution epoch ms
        private UUID sessionId;
        private String deviceId;
        private Map<String, String> attributes = new HashMap<>();

        public Builder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder value(double value) {
            this.value = value;
            return this;
        }

        public Builder unit(String unit) {
            this.unit = unit;
            return this;
        }

        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder sessionId(UUID sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder deviceId(String deviceId) {
            this.deviceId = deviceId;
            return this;
        }

        public Builder attributes(Map<String, String> attributes) {
            if (attributes != null) {
                this.attributes = new HashMap<>(attributes);
            }
            return this;
        }

        public Builder addAttribute(String key, String value) {
            if (this.attributes == null) {
                this.attributes = new HashMap<>();
            }
            this.attributes.put(key, value);
            return this;
        }

        public MetricEvent build() {
            // Validation requirements checking before compiling
            if (eventType == null || eventType.trim().isEmpty()) {
                throw new IllegalStateException("event_type profile string cannot be blank or null");
            }
            if (sessionId == null) {
                throw new IllegalStateException("session_id requires an assignment payload configuration");
            }
            if (deviceId == null || deviceId.trim().isEmpty()) {
                throw new IllegalStateException("device_id must explicitly identify a host framework reference");
            }
            return new MetricEvent(this);
        }
    }
}
