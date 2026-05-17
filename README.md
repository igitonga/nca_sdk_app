# NCASDK — Android Metrics & Crash Tracking SDK

A lightweight, asynchronous, thread-safe SDK for Android that monitors application lifecycles, intercepts unhandled exceptions, and syncs structured performance data to a remote analytics backend using exponential backoff.

---

## Project Structure

This is a multi-module Android project with two primary components:

```
├── app/                        # Demo sandbox application
│   ├── src/main/java/com/example/ncasdk/
│   │   ├── DemoApp.java        # SDK initialization entry point
│   │   └── MainActivity.java   # UI for triggering test crashes & metrics
│   └── build.gradle            # App-level dependencies (imports ncasdk)
│
└── ncasdk/                     # Core SDK library module
    ├── src/main/java/com/example/ncasdk/
    │   ├── http/               # Network logic & FlushManager
    │   ├── impl/               # MetricsCollector & core Engine
    │   ├── model/              # MetricEvent data structures
    │   └── store/              # Local cache storage (Store.java)
    └── build.gradle            # SDK-level dependencies
```

---

## Prerequisites

Before running the project, make sure you have the following installed and configured:

| Requirement | Details |
|---|---|
| **Android Studio** | Koala or newer recommended |
| **JDK** | Version 17 or higher |
| **Android Emulator** | An active AVD (required for `10.0.2.2` routing) |
| **Local Backend Server** | Running and listening on `localhost:8000` |

> **Note on `10.0.2.2`:** The Android emulator cannot reach your computer's `localhost` directly. Instead, it uses the special alias `10.0.2.2`, which maps to your machine's localhost. All backend URLs in this project use this address.

---

## Setup Instructions

### Step 1 — Configure Network Endpoints (SDK Module)

Open the following file:

```
ncasdk/src/main/java/com/example/ncasdk/http/Network.java
```

Set the URLs to point to your local backend via the emulator bridge address:

```java
private String registerUrl = "http://10.0.2.2:8000/v1/auth/register-token";
private String API_URL     = "http://10.0.2.2:8000/metric-events";
```

---

### Step 2 — Configure the Demo App

#### 2a. Allow Cleartext (HTTP) Traffic

Android blocks plain `http://` connections by default. To allow local development traffic, open:

```
app/src/main/AndroidManifest.xml
```

Add the `usesCleartextTraffic` attribute to the `<application>` tag:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <application
        android:usesCleartextTraffic="true"
        android:name=".DemoApp"
        ... >
        <!-- Activities -->
    </application>

</manifest>
```

#### 2b. Initialize the SDK

Open your custom Application class:

```
app/src/main/java/com/example/ncasdk/DemoApp.java
```

Initialize the SDK inside `onCreate()`:

```java
public class DemoApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Metrics.init(
            this,
            "http://10.0.2.2:8000/metric-events",
            "your_app_token_here"          // Replace with your actual token
        );
    }
}
```

> **Important:** Always initialize the SDK in your `Application` class, not in an `Activity`. This ensures the SDK is ready before any screen is shown and can catch crashes from the very start of the app's lifecycle.

---

## Testing & Verification

### Test 1 — Crash Interception

This test confirms that `CrashTracker` is correctly intercepting and persisting fatal errors.

1. Launch the `app` on your Android Emulator.
2. Tap the **"Test Crash"** button in `MainActivity`.
3. The app will close — the SDK intercepts the exception, saves it to local storage, then terminates cleanly.
4. **Relaunch the app.** On startup, `Metrics.init()` calls `syncHistoricalCacheLogs()`, which retrieves the saved crash event and schedules it for transmission to the backend.

### Test 2 — Monitoring Logs in Logcat

Open the **Logcat** tab in Android Studio and filter by `package:mine`, or search for these SDK-specific tags: `FlushManager`, `NetworkEngine`, `Metrics`.

A healthy data lifecycle produces the following log sequence:

```
D/Metrics:        Verifying app token on remote servers...
D/NetworkEngine:  Token validation network response status code received: 200
I/Metrics:        Token authorized successfully by the server.
D/FlushManager:   ⏰ Time trigger fired. Initiating batch flush...
D/FlushManager:   Processing 1 items from pipeline queue...
I/FlushManager:   Successfully synced batch of 1 individual events to API.
```

---

## Architecture Overview

The SDK is composed of four main components that work together in a pipeline:

```
App Event / Crash
       │
       ▼
┌─────────────────┐
│ MetricsCollector│  Thread-safe singleton; buffers events in a
│                 │  concurrent queue.
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  FlushManager   │  Background scheduler; drains the queue every
│                 │  30 seconds OR when 50 items accumulate.
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│    Network      │  Handles HTTP connections, formats JSON payloads,
│                 │  and retries failed requests using exponential backoff.
└────────┬────────┘
         │
         ▼
  Remote Analytics
      Backend


┌─────────────────┐
│  CrashTracker   │  Implements Thread.UncaughtExceptionHandler.
│                 │  Catches fatal errors, saves them to local Store
│                 │  before process death, and sends on next launch.
└─────────────────┘
```

| Component | Responsibility |
|---|---|
| `MetricsCollector` | Thread-safe singleton; receives events from trackers and buffers them in a concurrent queue |
| `FlushManager` | Drains the memory queue on a 30-second timer or at 50-item threshold; formats and ships JSON payloads |
| `Network` | Manages `HttpURLConnection` lifecycle, API token validation, and exponential backoff retries |
| `CrashTracker` | Implements `Thread.UncaughtExceptionHandler`; serializes stack traces to local `Store` before process death |

---

## Common Issues & Tips

**Connection refused on emulator?**
Confirm your backend server is running on your machine and listening on `0.0.0.0` (not just `127.0.0.1`). Also verify the port (`8000`) matches what's set in `Network.java`.

**App crashing immediately on launch?**
Check that `DemoApp` is correctly referenced in `AndroidManifest.xml` under `android:name=".DemoApp"`.

**No logs appearing in Logcat?**
Make sure your emulator is selected (not a physical device) and filter by `package:mine` rather than by a specific process name.

**Token authorization failing?**
Ensure your backend's `/v1/auth/register-token` endpoint is reachable and that `"your_app_token_here"` in `DemoApp.java` is replaced with a valid token issued by your backend.
