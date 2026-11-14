package com.sampos.sunmi;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.view.Gravity;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class MainActivity extends AppCompatActivity {

    private FrameLayout rootLayout;
    private LinearLayout menuLayout;
    private TextView statusTxt;
    private WebView webView;
    private SunmiPrinterService sunmiPrinterService;

    private final InnerPrinterCallback innerPrinterCallback = new InnerPrinterCallback() {
        @Override
        protected void onConnected(SunmiPrinterService service) {
            sunmiPrinterService = service;
            runOnUiThread(() -> {
                if (statusTxt != null) statusTxt.setText("✅ SUNMI Printer Connected!");
            });
        }

        @Override
        protected void onDisconnected() {
            sunmiPrinterService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        rootLayout = new FrameLayout(this);
        rootLayout.setBackgroundColor(Color.parseColor("#0f172a"));

        menuLayout = new LinearLayout(this);
        menuLayout.setOrientation(LinearLayout.VERTICAL);
        menuLayout.setGravity(Gravity.CENTER);
        menuLayout.setPadding(40, 40, 40, 40);

        TextView title = new TextView(this);
        title.setText("SAMPOS SUNMI V2s");
        title.setTextColor(Color.parseColor("#38bdf8"));
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        menuLayout.addView(title);

        statusTxt = new TextView(this);
        statusTxt.setText("Connecting Printer...");
        statusTxt.setTextColor(Color.WHITE);
        statusTxt.setTextSize(15);
        statusTxt.setGravity(Gravity.CENTER);
        statusTxt.setPadding(0, 20, 0, 40);
        menuLayout.addView(statusTxt);

        Button btnOpenPos = new Button(this);
        btnOpenPos.setText("🚀 OPEN POS SCREEN");
        btnOpenPos.setOnClickListener(v -> launchWebViewSafely());
        menuLayout.addView(btnOpenPos);

        Button btnTestPrint = new Button(this);
        btnTestPrint.setText("🖨️ TEST SHELF TAG PRINT");
        btnTestPrint.setOnClickListener(v -> {
            new SunmiJsBridge().printShelfTag("SAMPLE ITEM 500G", "15.00 SAR", "628100123456", "ITM-01");
        });
        menuLayout.addView(btnTestPrint);

        rootLayout.addView(menuLayout);
        setContentView(rootLayout);

        try {
            InnerPrinterManager.getInstance().bindService(this, innerPrinterCallback);
        } catch (Throwable t) {
            statusTxt.setText("Printer Error: " + t.getMessage());
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void launchWebViewSafely() {
        try {
            statusTxt.setText("Loading POS...");
            webView = new WebView(this);
            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setDatabaseEnabled(true);
            settings.setAllowFileAccess(true);
            settings.setMediaPlaybackRequiresUserGesture(false);

            webView.addJavascriptInterface(new SunmiJsBridge(), "sunmiPrinter");
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                    runOnUiThread(() -> {
                        rootLayout.removeView(webView);
                        webView = null;
                        menuLayout.setVisibility(View.VISIBLE);
                        statusTxt.setText("❌ Android 7.1 WebView Crashed! Use Chrome Mode.");
                    });
                    return true;
                }
            });
            webView.setWebChromeClient(new WebChromeClient());

            String html = readAssetHtml("sunmi.html");
            menuLayout.setVisibility(View.GONE);
            rootLayout.addView(webView);
            webView.loadDataWithBaseURL("http://localhost/", html, "text/html", "UTF-8", null);
        } catch (Throwable e) {
            menuLayout.setVisibility(View.VISIBLE);
            statusTxt.setText("Error: " + e.getMessage());
        }
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
            return "<html><body><h3>Asset Error: " + e.getMessage() + "</h3></body></html>";
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
                sunmiPrinterService.setFontSize(20, null);
                sunmiPrinterService.printText("================================\n", null);
                sunmiPrinterService.setFontSize(24, null);
                sunmiPrinterService.printText(itemName + "\n", null);
                sunmiPrinterService.setFontSize(38, null);
                sunmiPrinterService.printText(priceWithVat + "\n", null);
                sunmiPrinterService.setFontSize(18, null);
                sunmiPrinterService.printText("(Incl. 15% VAT)\n", null);
                if (barcodeData != null && !barcodeData.trim().isEmpty()) {
                    sunmiPrinterService.printBarCode(barcodeData.trim(), 8, 55, 2, 2, null);
                }
                if (itemCode != null && !itemCode.trim().isEmpty()) {
                    sunmiPrinterService.setFontSize(18, null);
                    sunmiPrinterService.printText("Code: " + itemCode.trim() + "\n", null);
                }
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
