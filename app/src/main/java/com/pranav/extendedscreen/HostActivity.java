package com.pranav.extendedscreen;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.text.format.Formatter;
import android.widget.Button;
import android.widget.TextView;

public class HostActivity extends Activity {

    private static final int SCREEN_CAPTURE_REQUEST = 1001;

    private TextView statusText;
    private TextView ipText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_host);

        statusText = findViewById(R.id.statusText);
        ipText = findViewById(R.id.ipText);

        Button startButton =
                findViewById(R.id.startButton);

        Button stopButton =
                findViewById(R.id.stopButton);

        String ipAddress = getWifiIpAddress();

        ipText.setText(
                "IP: " + ipAddress + "\nPort: 8989"
        );

        startButton.setOnClickListener(v -> {

            Intent captureIntent =
                    ((android.media.projection.MediaProjectionManager)
                            getSystemService(
                                    Context.MEDIA_PROJECTION_SERVICE
                            ))
                            .createScreenCaptureIntent();

            startActivityForResult(
                    captureIntent,
                    SCREEN_CAPTURE_REQUEST
            );
        });

        stopButton.setOnClickListener(v -> {

            Intent stopIntent =
                    new Intent(
                            HostActivity.this,
                            ScreenCaptureService.class
                    );

            stopService(stopIntent);

            statusText.setText(
                    "Screen sharing stopped"
            );
        });
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (requestCode == SCREEN_CAPTURE_REQUEST) {

            if (resultCode == RESULT_OK && data != null) {

                Intent serviceIntent =
                        new Intent(
                                this,
                                ScreenCaptureService.class
                        );

                serviceIntent.putExtra(
                        "resultCode",
                        resultCode
                );

                serviceIntent.putExtra(
                        "data",
                        data
                );

                startForegroundService(
                        serviceIntent
                );

                statusText.setText(
                        "Screen sharing started"
                );

            } else {

                statusText.setText(
                        "Screen capture permission denied"
                );
            }
        }
    }

    private String getWifiIpAddress() {

        WifiManager wifiManager =
                (WifiManager)
                        getApplicationContext()
                                .getSystemService(
                                        Context.WIFI_SERVICE
                                );

        int ip =
                wifiManager
                        .getConnectionInfo()
                        .getIpAddress();

        return Formatter.formatIpAddress(ip);
    }
}
