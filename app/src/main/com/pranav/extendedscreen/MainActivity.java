package com.pranav.extendedscreen;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.widget.Button;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        Button hostButton = findViewById(R.id.hostButton);
        Button displayButton = findViewById(R.id.displayButton);

        hostButton.setOnClickListener(v -> {
            Intent intent = new Intent(
                    MainActivity.this,
                    HostActivity.class
            );
            startActivity(intent);
        });

        displayButton.setOnClickListener(v -> {
            Intent intent = new Intent(
                    MainActivity.this,
                    DisplayActivity.class
            );
            startActivity(intent);
        });
    }
}
