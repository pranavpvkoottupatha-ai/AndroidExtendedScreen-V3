package com.pranav.extendedscreen;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;

public class DisplayActivity extends Activity {

    private EditText ipInput;
    private EditText portInput;
    private TextView statusText;
    private ImageView screenImage;

    private volatile boolean connected = false;
    private Socket socket;
    private Thread receiverThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_display);

        ipInput = findViewById(R.id.ipInput);
        portInput = findViewById(R.id.portInput);
        statusText = findViewById(R.id.displayStatus);
        screenImage = findViewById(R.id.screenImage);

        Button connectButton =
                findViewById(R.id.connectButton);

        Button disconnectButton =
                findViewById(R.id.disconnectButton);

        connectButton.setOnClickListener(v -> connect());

        disconnectButton.setOnClickListener(v -> disconnect());
    }

    private void connect() {

        if (connected) {
            return;
        }

        String host =
                ipInput.getText().toString().trim();

        String portText =
                portInput.getText().toString().trim();

        if (host.isEmpty()) {
            statusText.setText("Enter host IP");
            return;
        }

        int port;

        try {
            port = Integer.parseInt(portText);
        } catch (Exception e) {
            statusText.setText("Invalid port");
            return;
        }

        statusText.setText("Connecting...");

        receiverThread = new Thread(
                () -> receiveLoop(host, port)
        );

        receiverThread.start();
    }

    private void receiveLoop(
            String host,
            int port) {

        try {

            socket =
                    new Socket(host, port);

            connected = true;

            runOnUiThread(() ->
                    statusText.setText("Connected")
            );

            DataInputStream input =
                    new DataInputStream(
                            new BufferedInputStream(
                                    socket.getInputStream()
                            )
                    );

            while (connected) {

                int length =
                        input.readInt();

                if (length <= 0 ||
                        length > 2_000_000) {

                    throw new IOException(
                            "Invalid frame size"
                    );
                }

                byte[] frame =
                        new byte[length];

                input.readFully(frame);

                Bitmap bitmap =
                        BitmapFactory.decodeByteArray(
                                frame,
                                0,
                                frame.length
                        );

                if (bitmap != null) {

                    runOnUiThread(() ->
                            screenImage.setImageBitmap(bitmap)
                    );
                }
            }

        } catch (Exception e) {

            runOnUiThread(() ->
                    statusText.setText(
                            "Connection lost"
                    )
            );

        } finally {

            closeSocket();

            connected = false;
        }
    }

    private void disconnect() {

        connected = false;

        closeSocket();

        statusText.setText(
                "Disconnected"
        );
    }

    private void closeSocket() {

        try {

            if (socket != null) {
                socket.close();
            }

        } catch (Exception ignored) {
        }

        socket = null;
    }

    @Override
    protected void onDestroy() {

        disconnect();

        super.onDestroy();
    }
}
