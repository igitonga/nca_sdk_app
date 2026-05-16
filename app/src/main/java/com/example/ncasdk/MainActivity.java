package com.example.ncasdk;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button testButton1 = findViewById(R.id.btn_test_metric);
        testButton1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Metrics.recordEvent("user_pressed_test_button");
            }
        });

        Button testButton2 = findViewById(R.id.btn_test_unhandled_exception);
        testButton2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                throw new RuntimeException("NCASDK Debug Verification: Simulated Application Crash");
            }
        });
    }
}
