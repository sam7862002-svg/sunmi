package com.sampos.sunmi;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsIntent;

import com.sunmi.peripheral.printer.InnerPrinterCallback;
import com.sunmi.peripheral.printer.InnerPrinterManager;
import com.sunmi.peripheral.printer.SunmiPrinterService;

public class MainActivity extends AppCompatActivity {

    private TextView statusTxt;
    private EditText etItemName, etPrice, etBarcode, etItemCode, etCopies, etPosUrl;
    private SunmiPrinterService sunmiPrinterService;
    private Intent pendingIntent = null;

    private static final String DEFAULT_POS_URL = "https://sam7862002-svg.github.io/bakala/sunmi.html";

    private final InnerPrinterCallback innerPrinterCallback = new InnerPrinterCallback() {
        @Override
        protected void onConnected(SunmiPrinterService service) {
            sunmiPrinterService = service;
            runOnUiThread(() -> {
                if (statusTxt != null) {
                    statusTxt.setText("✅ SUNMI Printer Connected!");
                    statusTxt.setTextColor(Color.parseColor("#4ade80"));
                }
                if (pendingIntent != null) {
                    handlePrintIntent(pendingIntent);
                    pendingIntent = null;
                }
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

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#0f172a"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 32, 32, 48);

        TextView title = new TextView(this);
        title.setText("📟 SAMPOS SUNMI V2s");
        title.setTextColor(Color.parseColor("#38bdf8"));
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        statusTxt = new TextView(this);
        statusTxt.setText("⏳ Connecting SUNMI Printer...");
        statusTxt.setTextColor(Color.YELLOW);
        statusTxt.setTextSize(14);
        statusTxt.setGravity(Gravity.CENTER);
        statusTxt.setPadding(0, 12, 0, 28);
        root.addView(statusTxt);

        Button btnOpenPos = new Button(this);
        btnOpenPos.setText("🚀 OPEN POS (CHROME MODE)");
        btnOpenPos.setBackgroundColor(Color.parseColor("#2563eb"));
        btnOpenPos.setTextColor(Color.WHITE);
        btnOpenPos.setTextSize(16);
        btnOpenPos.setPadding(20, 28, 20, 28);
        btnOpenPos.setOnClickListener(v -> openPosInChrome());
        root.addView(btnOpenPos);

        TextView tagHeader = new TextView(this);
        tagHeader.setText("\n🏷️ MANUAL SHELF TAG & BARCODE");
        tagHeader.setTextColor(Color.parseColor("#f97316"));
        tagHeader.setTextSize(15);
        tagHeader.setPadding(0, 24, 0, 12);
        root.addView(tagHeader);

        etItemName = makeInput("Item Name (ഉദാ: ALMARAI MILK 1L)");
        root.addView(etItemName);

        etPrice = makeInput("Price Incl. VAT (ഉദാ: 6.50)");
        etPrice.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        root.addView(etPrice);

        etBarcode = makeInput("Barcode Number (ഉദാ: 6281007001234)");
        root.addView(etBarcode);

        etItemCode = makeInput("Short Item Code (ഉദാ: ITM-105)");
        root.addView(etItemCode);

        etCopies = makeInput("Copies (Default: 1)");
        etCopies.setInputType(InputType.TYPE_CLASS_NUMBER);
        etCopies.setText("1");
        root.addView(etCopies);

        Button btnPrintTag = new Button(this);
        btnPrintTag.setText("🏷️ PRINT SHELF TAG");
        btnPrintTag.setBackgroundColor(Color.parseColor("#f97316"));
        btnPrintTag.setTextColor(Color.WHITE);
        btnPrintTag.setPadding(16, 24, 16, 24);
        btnPrintTag.setOnClickListener(v -> handleShelfTagPrint());
        root.addView(btnPrintTag);

        Button btnPrintBarcode = new Button(this);
        btnPrintBarcode.setText("📊 PRINT BARCODE ONLY");
        btnPrintBarcode.setBackgroundColor(Color.parseColor("#0284c7"));
        btnPrintBarcode.setTextColor(Color.WHITE);
        btnPrintBarcode.setPadding(16, 20, 16, 20);
        btnPrintBarcode.setOnClickListener(v -> handleBarcodeOnlyPrint());
        root.addView(btnPrintBarcode);

        TextView urlLbl = new TextView(this);
        urlLbl.setText("\n⚙️ POS Web URL:");
        urlLbl.setTextColor(Color.parseColor("#94a3b8"));
        urlLbl.setTextSize(12);
        root.addView(urlLbl);

        etPosUrl = makeInput("POS URL");
        String savedUrl = getSharedPreferences("sampos", MODE_PRIVATE).getString("pos_url", DEFAULT_POS_URL);
        etPosUrl.setText(savedUrl);
        root.addView(etPosUrl);

        scroll.addView(root);
        setContentView(scroll);

        try {
            InnerPrinterManager.getInstance().bindService(this, innerPrinterCallback);
        } catch (Throwable t) {
            statusTxt.setText("Printer Bind Error: " + t.getMessage());
        }

        handlePrintIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handlePrintIntent(intent);
    }

    private void handlePrintIntent(Intent intent) {
        if (intent == null || intent.getData() == null) return;
        Uri uri = intent.getData();
        if (!"sampos".equals(uri.getScheme())) return;

        if (sunmiPrinterService == null) {
            pendingIntent = intent;
            return;
        }

        if ("print".equals(uri.getHost())) {
            String text = uri.getQueryParameter("text");
            String qr = uri.getQueryParameter("qr");
            if (text != null) {
                executeDirectPrint(text, qr);
            }
        } else if ("tag".equals(uri.getHost())) {
            String name = uri.getQueryParameter("name");
            String price = uri.getQueryParameter("price");
            String barcode = uri.getQueryParameter("barcode");
            String code = uri.getQueryParameter("code");
            String mode = uri.getQueryParameter("mode");
            int copies = 1;
            try { copies = Integer.parseInt(uri.getQueryParameter("copies")); } catch (Exception ignored) {}
            if (copies < 1) copies = 1;

            for (int i = 0; i < copies; i++) {
                if ("barcode".equals(mode)) {
                    printBarcode(name != null ? name : "", barcode != null ? barcode : "");
                } else {
                    printShelfTag(name != null ? name : "ITEM", price != null ? price : "0.00 SAR",
                            barcode != null ? barcode : "", code != null ? code : "");
                }
            }
            Toast.makeText(this, "🖨️ Printed " + copies + " Tag(s)!", Toast.LENGTH_SHORT).show();
            new Handler(Looper.getMainLooper()).postDelayed(this::openPosInChrome, 500);
        }
    }

    private void executeDirectPrint(String text, String qr) {
        try {
            String cleanText = text.replaceAll("[\\x00-\\x09\\x0B-\\x1F]", "");
            sunmiPrinterService.printerInit(null);
            sunmiPrinterService.setFontSize(22, null);
            sunmiPrinterService.printText(cleanText + "\n", null);
            if (qr != null && !qr.isEmpty()) {
                sunmiPrinterService.setAlignment(1, null);
                sunmiPrinterService.printQRCode(qr, 6, 1, null);
            }
            sunmiPrinterService.lineWrap(4, null);
            Toast.makeText(this, "🖨️ Bill Printed!", Toast.LENGTH_SHORT).show();
            new Handler(Looper.getMainLooper()).postDelayed(this::openPosInChrome, 500);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private EditText makeInput(String hint) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setHintTextColor(Color.parseColor("#64748b"));
        et.setTextColor(Color.WHITE);
        et.setBackgroundColor(Color.parseColor("#1e293b"));
        et.setPadding(24, 20, 24, 20);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, 16);
        et.setLayoutParams(lp);
        return et;
    }

    private void openPosInChrome() {
        String url = etPosUrl.getText().toString().trim();
        if (url.isEmpty()) url = DEFAULT_POS_URL;
        getSharedPreferences("sampos", MODE_PRIVATE).edit().putString("pos_url", url).apply();

        try {
            CustomTabsIntent customTabsIntent = new CustomTabsIntent.Builder().build();
            customTabsIntent.launchUrl(this, Uri.parse(url));
        } catch (Exception e) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        }
    }

    private void handleShelfTagPrint() {
        String name = etItemName.getText().toString().trim();
        String price = etPrice.getText().toString().trim();
        String barcode = etBarcode.getText().toString().trim();
        String code = etItemCode.getText().toString().trim();
        int copies = 1;
        try { copies = Integer.parseInt(etCopies.getText().toString().trim()); } catch (Exception ignored) {}
        if (copies < 1) copies = 1;

        if (name.isEmpty()) name = "ITEM NAME";
        if (price.isEmpty()) price = "0.00";
        if (!price.toUpperCase().contains("SAR")) price = price + " SAR";

        for (int i = 0; i < copies; i++) {
            printShelfTag(name, price, barcode, code);
        }
        Toast.makeText(this, "Printed " + copies + " Shelf Tag(s)!", Toast.LENGTH_SHORT).show();
    }

    private void handleBarcodeOnlyPrint() {
        String name = etItemName.getText().toString().trim();
        String barcode = etBarcode.getText().toString().trim();
        if (barcode.isEmpty()) {
            Toast.makeText(this, "Enter Barcode Number!", Toast.LENGTH_SHORT).show();
            return;
        }
        int copies = 1;
        try { copies = Integer.parseInt(etCopies.getText().toString().trim()); } catch (Exception ignored) {}
        if (copies < 1) copies = 1;

        for (int i = 0; i < copies; i++) {
            printBarcode(name, barcode);
        }
        Toast.makeText(this, "Printed " + copies + " Barcode(s)!", Toast.LENGTH_SHORT).show();
    }

    private void printShelfTag(String itemName, String priceWithVat, String barcodeData, String itemCode) {
        if (sunmiPrinterService == null) {
            Toast.makeText(this, "Printer not ready!", Toast.LENGTH_SHORT).show();
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
            if (!barcodeData.isEmpty()) {
                sunmiPrinterService.printBarCode(barcodeData, 8, 55, 2, 2, null);
            }
            if (!itemCode.isEmpty()) {
                sunmiPrinterService.setFontSize(18, null);
                sunmiPrinterService.printText("Code: " + itemCode + "\n", null);
            }
            sunmiPrinterService.setFontSize(20, null);
            sunmiPrinterService.printText("================================\n", null);
            sunmiPrinterService.lineWrap(4, null);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private void printBarcode(String itemName, String barcodeData) {
        if (sunmiPrinterService == null) {
            Toast.makeText(this, "Printer not ready!", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            sunmiPrinterService.printerInit(null);
            sunmiPrinterService.setAlignment(1, null);
            if (!itemName.isEmpty()) {
                sunmiPrinterService.setFontSize(20, null);
                sunmiPrinterService.printText(itemName + "\n", null);
            }
            sunmiPrinterService.printBarCode(barcodeData, 8, 60, 2, 2, null);
            sunmiPrinterService.lineWrap(3, null);
        } catch (RemoteException e) {
            e.printStackTrace();
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
