package com.sampos.sunmi;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Bundle;
import android.os.RemoteException;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.webkit.WebViewAssetLoader;

import com.sunmi.peripheral.printer.InnerPrinterCallback;
import com.sunmi.peripheral.printer.InnerPrinterManager;
import com.sunmi.peripheral.printer.SunmiPrinterService;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private TextView debugBar;
    private SunmiPrinterService sunmiPrinterService;

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

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.WHITE);

        debugBar = new TextView(this);
        debugBar.setBackgroundColor(Color.parseColor("#222222"));
        debugBar.setTextColor(Color.YELLOW);
        debugBar.setTextSize(12);
        debugBar.setPadding(16, 8, 16, 8);
        debugBar.setText("Loading POS...");
        layout.addView(debugBar);

        webView = new WebView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);
        webView.setLayoutParams(params);
        layout.addView(webView);

        setContentView(layout);

        try {
            InnerPrinterManager.getInstance().bindService(this, innerPrinterCallback);
        } catch (Throwable ignored) {}

        final WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        webView.addJavascriptInterface(new SunmiJsBridge(), "sunmiPrinter");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                debugBar.setText("Loaded: " + view.getTitle());
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
                if (cm.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                    runOnUiThread(() -> debugBar.setText("JS Error (Line " + cm.lineNumber() + "): " + cm.message()));
                }
                return true;
            }
        });

        webView.loadUrl("https://appassets.androidplatform.net/assets/sunmi.html");
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
