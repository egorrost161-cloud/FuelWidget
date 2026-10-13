package com.example.fuelwidget;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private static final int OVERLAY_PERMISSION_REQ = 1001;

    private SettingsManager settings;
    private TextView txtWindowSize, txtFontSize, txtIconSize, txtFontWeight, txtTheme;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        settings = new SettingsManager(this);

        txtWindowSize = findViewById(R.id.txt_window_size);
        txtFontSize = findViewById(R.id.txt_font_size);
        txtIconSize = findViewById(R.id.txt_icon_size);
        txtFontWeight = findViewById(R.id.txt_font_weight);
        txtTheme = findViewById(R.id.txt_theme);

        updateLabels();

        findViewById(R.id.btn_window_minus).setOnClickListener(v -> changeWindow(-1));
        findViewById(R.id.btn_window_plus).setOnClickListener(v -> changeWindow(+1));
        findViewById(R.id.btn_font_minus).setOnClickListener(v -> changeFont(-1));
        findViewById(R.id.btn_font_plus).setOnClickListener(v -> changeFont(+1));
        findViewById(R.id.btn_icon_minus).setOnClickListener(v -> changeIcon(-1));
        findViewById(R.id.btn_icon_plus).setOnClickListener(v -> changeIcon(+1));
        findViewById(R.id.btn_weight_minus).setOnClickListener(v -> changeWeight(-1));
        findViewById(R.id.btn_weight_plus).setOnClickListener(v -> changeWeight(+1));
        findViewById(R.id.btn_theme_minus).setOnClickListener(v -> changeTheme(-1));
        findViewById(R.id.btn_theme_plus).setOnClickListener(v -> changeTheme(+1));

        findViewById(R.id.btn_app_list).setOnClickListener(v ->
                startActivity(new Intent(this, AppListActivity.class)));

        findViewById(R.id.btn_usage_access).setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
            Toast.makeText(this, "Найдите Fuel и включите доступ", Toast.LENGTH_LONG).show();
        });

        findViewById(R.id.btn_reset).setOnClickListener(v -> {
            settings.resetAll();
            updateLabels();
            applySettings();
            Toast.makeText(this, "Сброшено", Toast.LENGTH_SHORT).show();
        });

        checkOverlay();
    }

    private void changeWindow(int d) {
        int s = settings.getWindowSize() + d;
        if (s >= SettingsManager.MIN_WINDOW_SIZE && s <= SettingsManager.MAX_WINDOW_SIZE) {
            settings.setWindowSize(s); updateLabels(); applySettings();
        }
    }

    private void changeFont(int d) {
        int s = settings.getFontSize() + d;
        if (s >= SettingsManager.MIN_FONT_SIZE && s <= SettingsManager.MAX_FONT_SIZE) {
            settings.setFontSize(s); updateLabels(); applySettings();
        }
    }

    private void changeIcon(int d) {
        int s = settings.getIconSize() + d;
        if (s >= SettingsManager.MIN_ICON_SIZE && s <= SettingsManager.MAX_ICON_SIZE) {
            settings.setIconSize(s); updateLabels(); applySettings();
        }
    }

    private void changeWeight(int d) {
        int step = SettingsManager.FONT_WEIGHT_STEP;
        int w = settings.getFontWeight() + (d * step);
        if (w < SettingsManager.MIN_FONT_WEIGHT) w = SettingsManager.MIN_FONT_WEIGHT;
        if (w > SettingsManager.MAX_FONT_WEIGHT) w = SettingsManager.MAX_FONT_WEIGHT;
        settings.setFontWeight(w); updateLabels(); applySettings();
    }

    private void changeTheme(int d) {
        int t = settings.getTheme() + d;
        if (t < SettingsManager.THEME_MIN) t = SettingsManager.THEME_MIN;
        if (t > SettingsManager.THEME_MAX) t = SettingsManager.THEME_MAX;
        settings.setTheme(t); updateLabels(); applySettings();
    }

    private void updateLabels() {
        txtWindowSize.setText(settings.getWindowSize() + " dp");
        txtFontSize.setText(settings.getFontSize() + " sp");
        txtIconSize.setText(settings.getIconSize() + " dp");
        txtFontWeight.setText(String.valueOf(settings.getFontWeight()));
        txtTheme.setText(themeName(settings.getTheme()));
    }

    private String themeName(int t) {
        switch (t) {
            case SettingsManager.THEME_AUTO: return "Авто";
            case SettingsManager.THEME_DAY: return "День";
            case SettingsManager.THEME_NIGHT: return "Ночь";
            default: return "Авто";
        }
    }

    private void applySettings() {
        Intent i = new Intent(this, FloatingFuelService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i);
        else startService(i);
    }

    private void checkOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(i, OVERLAY_PERMISSION_REQ);
            } else applySettings();
        } else applySettings();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == OVERLAY_PERMISSION_REQ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                applySettings();
            }
        }
    }
}
