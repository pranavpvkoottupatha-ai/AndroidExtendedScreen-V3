package com.pranav.extendedscreen;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.DisplayMetrics;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;

public class ScreenCaptureService extends Service {

    private static final String CHANNEL_ID =
            "screen_capture_channel";

    private static final int PORT = 8989;

    private MediaProjection mediaProjection;

    private VirtualDisplay virtualDisplay;

    private ImageReader imageReader;

    private HandlerThread captureThread;

    private Handler captureHandler;

    private ServerSocket serverSocket;

    private Socket clientSocket;

    private DataOutputStream outputStream;

    private volatile boolean running = false;

    private long lastFrameTime = 0;

    @Override
    public void onCreate() {

        super.onCreate();

        createNotificationChannel();

        startForeground(
                1001,
                createNotification()
        );
    }

    @Override
    public int onStartCommand(
            Intent intent,
            int flags,
            int startId) {

        if (intent == null) {
            return START_NOT_STICKY;
        }

        int resultCode =
                intent.getIntExtra(
                        "resultCode",
                        -1
                );

        Intent data =
                intent.getParcelableExtra(
                        "data"
                );

        if (resultCode != -1 && data != null) {

            startCapture(
                    resultCode,
                    data
            );
        }

        return START_NOT_STICKY;
    }

    private void startCapture(
            int resultCode,
            Intent data) {

        if (running) {
            return;
        }

        running = true;

        MediaProjectionManager manager =
                (MediaProjectionManager)
                        getSystemService(
                                MEDIA_PROJECTION_SERVICE
                        );

        mediaProjection =
                manager.getMediaProjection(
                        resultCode,
                        data
                );

        DisplayMetrics metrics =
                getResources()
                        .getDisplayMetrics();

        int width =
                metrics.widthPixels;

        int height =
                metrics.heightPixels;

        int density =
                metrics.densityDpi;

        imageReader =
                ImageReader.newInstance(
                        width,
                        height,
                        PixelFormat.RGBA_8888,
                        2
                );

        captureThread =
                new HandlerThread(
                        "ScreenCaptureThread"
                );

        captureThread.start();

        captureHandler =
                new Handler(
                        captureThread.getLooper()
                );

        imageReader.setOnImageAvailableListener(
                reader -> processImage(reader),
                captureHandler
        );

        virtualDisplay =
                mediaProjection.createVirtualDisplay(
                        "ExtendedScreen",
                        width,
                        height,
                        density,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        imageReader.getSurface(),
                        null,
                        captureHandler
                );

        startServer();
    }

    private void processImage(
            ImageReader reader) {

        long now =
                System.currentTimeMillis();

        // About 6 frames per second.
        if (now - lastFrameTime < 160) {
            return;
        }

        lastFrameTime = now;

        Image image = null;

        try {

            image = reader.acquireLatestImage();

            if (image == null) {
                return;
            }

            Image.Plane plane =
                    image.getPlanes()[0];

            ByteBuffer buffer =
                    plane.getBuffer();

            int pixelStride =
                    plane.getPixelStride();

            int rowStride =
                    plane.getRowStride();

            int width =
                    image.getWidth();

            int height =
                    image.getHeight();

            int rowPadding =
                    rowStride -
                    pixelStride * width;

            int bitmapWidth =
                    width +
                    rowPadding / pixelStride;

            Bitmap bitmap =
                    Bitmap.createBitmap(
                            bitmapWidth,
                            height,
                            Bitmap.Config.ARGB_8888
                    );

            buffer.rewind();

            bitmap.copyPixelsFromBuffer(
                    buffer
            );

            Bitmap croppedBitmap =
                    Bitmap.createBitmap(
                            bitmap,
                            0,
                            0,
                            width,
                            height
                    );

            bitmap.recycle();

            ByteArrayOutputStream jpegStream =
                    new ByteArrayOutputStream();

            croppedBitmap.compress(
                    Bitmap.CompressFormat.JPEG,
                    55,
                    jpegStream
            );

            croppedBitmap.recycle();

            byte[] frame =
                    jpegStream.toByteArray();

            sendFrame(frame);

        } catch (Exception e) {

            e.printStackTrace();

        } finally {

            if (image != null) {
                image.close();
            }
        }
    }

    private synchronized void sendFrame(
            byte[] frame) {

        if (outputStream == null) {
            return;
        }

        try {

            outputStream.writeInt(
                    frame.length
            );

            outputStream.write(
                    frame
            );

            outputStream.flush();

        } catch (Exception e) {

            try {

                if (clientSocket != null) {
                    clientSocket.close();
                }

            } catch (Exception ignored) {
            }

            clientSocket = null;
            outputStream = null;
        }
    }

    private void startServer() {

        new Thread(() -> {

            try {

                serverSocket =
                        new ServerSocket(PORT);

                while (running) {

                    Socket socket =
                            serverSocket.accept();

                    synchronized (this) {

                        if (clientSocket != null) {

                            try {
                                clientSocket.close();
                            } catch (Exception ignored) {
                            }
                        }

                        clientSocket =
                                socket;

                        outputStream =
                                new DataOutputStream(
                                        socket.getOutputStream()
                                );
                    }
                }

            } catch (Exception e) {

                e.printStackTrace();
            }

        }).start();
    }

    private Notification createNotification() {

        Notification.Builder builder;

        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O) {

            builder =
                    new Notification.Builder(
                            this,
                            CHANNEL_ID
                    );

        } else {

            builder =
                    new Notification.Builder(
                            this
                    );
        }

        return builder
                .setContentTitle(
                        "Extended Screen"
                )
                .setContentText(
                        "Screen sharing is active"
                )
                .setSmallIcon(
                        android.R.drawable.ic_menu_camera
                )
                .build();
    }

    private void createNotificationChannel() {

        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O) {

            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL_ID,
                            "Screen Capture",
                            NotificationManager
                                    .IMPORTANCE_LOW
                    );

            NotificationManager manager =
                    getSystemService(
                            NotificationManager.class
                    );

            manager.createNotificationChannel(
                    channel
            );
        }
    }

    @Override
    public void onDestroy() {

        running = false;

        try {

            if (serverSocket != null) {
                serverSocket.close();
            }

        } catch (Exception ignored) {
        }

        try {

            if (clientSocket != null) {
                clientSocket.close();
            }

        } catch (Exception ignored) {
        }

        if (virtualDisplay != null) {
            virtualDisplay.release();
        }

        if (imageReader != null) {
            imageReader.close();
        }

        if (mediaProjection != null) {
            mediaProjection.stop();
        }

        if (captureThread != null) {
            captureThread.quitSafely();
        }

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
              }
