package com.sampos.sunmi;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.view.Gravity;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.sunmi.peripheral.printer.InnerPrinterCallback;
import com.sunmi.peripheral.printer.InnerPrinterManager;
import com.sunmi.peripheral.printer.SunmiPrinterService;

import java.io.InputStream;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private TextView statusText;
    private SunmiPrinterService sunmiPrinterService;

    private final InnerPrinterCallback innerPrinterCallback = new InnerPrinterCallback() {
        @Override
        protected void onConnected(SunmiPrinterService service) {
            sunmiPrinterService = service;
            runOnUiThread(() -> {
                if (statusText != null) statusText.setText("Printer: CONNECTED");
                Toast.makeText(MainActivity.this, "SUNMI Printer Connected!", Toast.LENGTH_SHORT).show();
            });
        }

        @Override
        protected void onDisconnected() {
            sunmiPrinterService = null;
        }
    };

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Safe fallback layout so app NEVER crashes on startup
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);

        LinearLayout infoBox = new LinearLayout(this);
        infoBox.setOrientation(LinearLayout.VERTICAL);
        infoBox.setGravity(Gravity.CENTER);
        infoBox.setPadding(32, 32, 32, 32);

        statusText = new TextView(this);
        statusText.setText("Starting SAMPOS...");
        statusText.setTextColor(Color.BLACK);
        statusText.setTextSize(16);
        statusText.setGravity(Gravity.CENTER);
        infoBox.addView(statusText);

        Button testBtn = new Button(this);
        testBtn.setText("TEST SUNMI PRINTER");
        testBtn.setOnClickListener(v -> {
            new SunmiJsBridge().printText("=== SAMPOS TEST PRINT ===\nSUNMI V2s Direct Print OK!\n=========================");
        });
        infoBox.addView(testBtn);

        root.addView(infoBox);
        setContentView(root);

        // 2. Bind SUNMI Printer safely
        try {
            InnerPrinterManager.getInstance().bindService(this, innerPrinterCallback);
        } catch (Throwable t) {
            statusText.setText("Printer Bind Error: " + t.getMessage());
        }

        // 3. Check if sunmi.html exists in assets
        boolean assetExists = false;
        try {
            InputStream is = getAssets().open("sunmi.html");
            is.close();
            assetExists = true;
        } catch (Throwable e) {
            statusText.setText("ERROR: 'sunmi.html' not found in assets folder!\n(" + e.getMessage() + ")");
        }

        if (!assetExists) return;

        // 4. Load WebView after short delay to prevent Android 7.1 startup crash
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                webView = new WebView(MainActivity.this);
                WebSettings settings = webView.getSettings();
                settings.setJavaScriptEnabled(true);
                settings.setDomStorageEnabled(true);
                settings.setDatabaseEnabled(true);
                settings.setAllowFileAccess(true);
                settings.setAllowFileAccessFromFileURLs(true);
                settings.setAllowUniversalAccessFromFileURLs(true);
                settings.setMediaPlaybackRequiresUserGesture(false);
                settings.setCacheMode(WebSettings.LOAD_DEFAULT);

                webView.addJavascriptInterface(new SunmiJsBridge(), "sunmiPrinter");

                webView.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageFinished(WebView view, String url) {
                        infoBox.setVisibility(View.GONE);
                    }
                });

                webView.setWebChromeClient(new WebChromeClient() {
                    @Override
                    public void onPermissionRequest(final PermissionRequest request) {
                        runOnUiThread(() -> {
                            try {
                                request.grant(request.getResources());
                            } catch (Throwable ignored) {}
                        });
                    }

                    @Override
                    public boolean onConsoleMessage(ConsoleMessage cm) {
                        return true;
                    }
                });

                root.addView(webView);
                webView.loadUrl("file:///android_asset/sunmi.html");
            } catch (Throwable e) {
                statusText.setText("WebView Crash Prevented:\n" + e.toString());
            }
        }, 500);
    }

    public class SunmiJsBridge {
        @JavascriptInterface
        public void printText(String text) {
            if (sunmiPrinterService == null) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Printer not ready!", Toast.LENGTH_SHORT).show());
                return;
            }
            try {
                String cleanText = text.replaceAll("[\\x00-\\x09\\x0B-\\x1F]", "");
                sunmiPrinterService.printerInit(null);
                sunmiPrinterService.setFontSize(22, null);
                sunmiPrinterService.printText(cleanText + "\n", null);
                sunmiPrinterService.lineWrap(3, null);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        @JavascriptInterface
        public void printQRCode(String qrData, int moduleSize, int errorLevel) {
            if (sunmiPrinterService == null || qrData == null || qrData.isEmpty()) return;
            try {
                sunmiPrinterService.setAlignment(1, null);
                sunmiPrinterService.printQRCode(qrData, moduleSize > 0 ? moduleSize : 6, errorLevel, null);
                sunmiPrinterService.lineWrap(3, null);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        @JavascriptInterface
        public void lineWrap(int lines) {
            if (sunmiPrinterService == null) return;
            try {
                sunmiPrinterService.lineWrap(lines > 0 ? lines : 3, null);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            InnerPrinterManager.getInstance().unBindService(this, innerPrinterCallback);
        } catch (Throwable ignored) {}
    }
}
