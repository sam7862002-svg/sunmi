package com.sampos.sunmi;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.sunmi.peripheral.printer.InnerPrinterCallback;
import com.sunmi.peripheral.printer.InnerPrinterManager;
import com.sunmi.peripheral.printer.SunmiPrinterService;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private SunmiPrinterService sunmiPrinterService;

    private static final String POS_URL = "file:///android_asset/sunmi.html";

    private final InnerPrinterCallback innerPrinterCallback = new InnerPrinterCallback() {
        @Override
        protected void onConnected(SunmiPrinterService service) {
            sunmiPrinterService = service;
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "SUNMI Printer Connected!", Toast.LENGTH_SHORT).show());
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

        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 101);
            }
        } catch (Throwable ignored) {}

        try {
            InnerPrinterManager.getInstance().bindService(this, innerPrinterCallback);
        } catch (Throwable t) {
            t.printStackTrace();
        }

        try {
            webView = new WebView(this);
            setContentView(webView);

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
                public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                    if (request != null && request.isForMainFrame()) {
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Page Error: " + error.getDescription(), Toast.LENGTH_LONG).show());
                    }
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
            });

            webView.loadUrl(POS_URL);
        } catch (Throwable e) {
            Toast.makeText(this, "Startup Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
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
