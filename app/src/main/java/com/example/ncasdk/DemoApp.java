package com.example.ncasdk;

import android.app.Application;

public class DemoApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        Metrics.init(
                this,
                "https://api.yourbackend.com/v1/metrics", // Your metrics backend endpoint URL
                "test_api_key_xyz123"
        );
    }
}
