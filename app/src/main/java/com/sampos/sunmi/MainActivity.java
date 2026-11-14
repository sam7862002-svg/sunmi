package com.sampos.sunmi;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
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

        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 101);
            }
        } catch (Throwable ignored) {}

        try {
            InnerPrinterManager.getInstance().bindService(this, innerPrinterCallback);
        } catch (Throwable ignored) {}

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        webView.addJavascriptInterface(new SunmiJsBridge(), "sunmiPrinter");
        webView.setWebViewClient(new WebViewClient());
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

        String html = readAssetHtml("sunmi.html");
        webView.loadDataWithBaseURL("http://localhost/", html, "text/html", "UTF-8", null);
    }

    private String readAssetHtml(String fileName) {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = getAssets().open(fileName);
             BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (Exception e) {
            return "<html><body style='background:#0f172a;color:#fff;padding:20px;'><h3>Asset Load Error</h3><p>" + e.getMessage() + "</p></body></html>";
        }
        return sb.toString();
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
        public void printShelfTag(String itemName, String priceWithVat, String barcodeData, String itemCode) {
            if (sunmiPrinterService == null) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Printer not ready!", Toast.LENGTH_SHORT).show());
                return;
            }
            try {
                sunmiPrinterService.printerInit(null);
                sunmiPrinterService.setAlignment(1, null);

                // 1. Top border
                sunmiPrinterService.setFontSize(20, null);
                sunmiPrinterService.printText("================================\n", null);

                // 2. Item Name
                sunmiPrinterService.setFontSize(24, null);
                sunmiPrinterService.printText(itemName + "\n", null);

                // 3. Price (Incl. VAT) in Large Font
                sunmiPrinterService.setFontSize(38, null);
                sunmiPrinterService.printText(priceWithVat + "\n", null);

                sunmiPrinterService.setFontSize(18, null);
                sunmiPrinterService.printText("(Incl. 15% VAT)\n", null);

                // 4. Scannable Barcode (Code128 - symbology 8) + text below (textPosition 2)
                if (barcodeData != null && !barcodeData.trim().isEmpty()) {
                    sunmiPrinterService.printBarCode(barcodeData.trim(), 8, 55, 2, 2, null);
                }

                // 5. Small item code like main POS
                if (itemCode != null && !itemCode.trim().isEmpty()) {
                    sunmiPrinterService.setFontSize(18, null);
                    sunmiPrinterService.printText("Code: " + itemCode.trim() + "\n", null);
                }

                // 6. Bottom border & feed
                sunmiPrinterService.setFontSize(20, null);
                sunmiPrinterService.printText("================================\n", null);
                sunmiPrinterService.lineWrap(4, null);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        @JavascriptInterface
        public void printBarcode(String barcodeData, int height, int width) {
            if (sunmiPrinterService == null || barcodeData == null || barcodeData.isEmpty()) return;
            try {
                sunmiPrinterService.setAlignment(1, null);
                // Symbology 8 = CODE128, textPosition 2 = below barcode
                sunmiPrinterService.printBarCode(barcodeData.trim(), 8, height > 0 ? height : 60, width > 0 ? width : 2, 2, null);
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
