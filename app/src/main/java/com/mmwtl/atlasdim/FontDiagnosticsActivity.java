package com.mmwtl.atlasdim;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Isolated, manually launched, read-only diagnostics UI. No scene controls. */
public final class FontDiagnosticsActivity extends Activity {
    private EditText host;
    private Button scan;
    private TextView report;
    private String lastReport = "";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);
        TextView info = new TextView(this);
        info.setText("Cityray Font Diagnostics — только чтение. Не управляет композицией и не запускает демоны. Подключение к QNX использует штатную конфигурацию AtlasDim.");
        layout.addView(info);
        host = new EditText(this);
        host.setSingleLine(true);
        host.setHint("QNX IP");
        host.setText(BuildConfig.QNX_HOST);
        layout.addView(host);
        scan = new Button(this);
        scan.setText("Диагностика");
        layout.addView(scan);
        Button copy = new Button(this);
        copy.setText("Копировать отчёт");
        layout.addView(copy);
        Button share = new Button(this);
        share.setText("Поделиться отчётом");
        layout.addView(share);
        ScrollView scroll = new ScrollView(this);
        report = new TextView(this);
        report.setTextIsSelectable(true);
        scroll.addView(report);
        layout.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(layout);

        scan.setOnClickListener(v -> {
            String address = host.getText().toString().trim();
            String pass = BuildConfig.QNX_PASSWORD;
            if (address.isEmpty() || pass.isEmpty()) {
                report.setText("Не задан IP QNX или отсутствует штатная конфигурация авторизации.");
                return;
            }
            Diagnostics.Input input = new Diagnostics.Input();
            input.host = address;
            input.password = pass;
            input.version = BuildConfig.VERSION_NAME;
            input.active = false;
            input.serviceRunning = false;
            input.state = "Отдельная диагностическая сборка";
            scan.setEnabled(false);
            report.setText("Диагностика выполняется…");
            new Thread(() -> {
                String result;
                try {
                    result = Diagnostics.run(input, message -> runOnUiThread(() -> report.setText(message)));
                } catch (Exception e) {
                    result = "Ошибка диагностики: " + e.getMessage();
                }
                final String output = result;
                runOnUiThread(() -> {
                    lastReport = output;
                    report.setText(output);
                    scan.setEnabled(true);
                });
            }, "Cityray-font-diagnostics").start();
        });
        copy.setOnClickListener(v -> {
            if (lastReport.isEmpty()) return;
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("Cityray diagnostics", lastReport));
        });
        share.setOnClickListener(v -> {
            if (lastReport.isEmpty()) return;
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, lastReport);
            startActivity(Intent.createChooser(intent, "Отправить отчёт"));
        });
    }
}
