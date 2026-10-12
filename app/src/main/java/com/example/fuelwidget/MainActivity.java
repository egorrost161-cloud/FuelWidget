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
    private TextView txtWindowSize;
    private TextView txtFontSize;
    private TextView txtIconSize;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        settings = new SettingsManager(this);

        txtWindowSize = findViewById(R.id.txt_window_size);
        txtFontSize = findViewById(R.id.txt_font_size);
        txtIconSize = findViewById(R.id.txt_icon_size);

        updateSizeLabels();

        findViewById(R.id.btn_window_minus).setOnClickListener(v -> {
            int size = settings.getWindowSize();
            if (size > SettingsManager.MIN_WINDOW_SIZE) {
                settings.setWindowSize(size - 1);
                updateSizeLabels();
                applySettings();
            }
        });

        findViewById(R.id.btn_window_plus).setOnClickListener(v -> {
            int size = settings.getWindowSize();
            if (size < SettingsManager.MAX_WINDOW_SIZE) {
                settings.setWindowSize(size + 1);
                updateSizeLabels();
                applySettings();
            }
        });

        findViewById(R.id.btn_font_minus).setOnClickListener(v -> {
            int size = settings.getFontSize();
            if (size > SettingsManager.MIN_FONT_SIZE) {
                settings.setFontSize(size - 1);
                updateSizeLabels();
                applySettings();
            }
        });

        findViewById(R.id.btn_font_plus).setOnClickListener(v -> {
            int size = settings.getFontSize();
            if (size < SettingsManager.MAX_FONT_SIZE) {
                settings.setFontSize(size + 1);
                updateSizeLabels();
                applySettings();
            }
        });

        findViewById(R.id.btn_icon_minus).setOnClickListener(v -> {
            int size = settings.getIconSize();
            if (size > SettingsManager.MIN_ICON_SIZE) {
                settings.setIconSize(size - 1);
                updateSizeLabels();
                applySettings();
            }
        });

        findViewById(R.id.btn_icon_plus).setOnClickListener(v -> {
            int size = settings.getIconSize();
            if (size < SettingsManager.MAX_ICON_SIZE) {
                settings.setIconSize(size + 1);
                updateSizeLabels();
                applySettings();
            }
        });

        findViewById(R.id.btn_app_list).setOnClickListener(v -> {
            startActivity(new Intent(this, AppListActivity.class));
        });

        findViewById(R.id.btn_reset).setOnClickListener(v -> {
            settings.resetAll();
            updateSizeLabels();
            applySettings();
            Toast.makeText(this, "Настройки сброшены", Toast.LENGTH_SHORT).show();
        });

        checkOverlayPermission();
    }

    private void updateSizeLabels() {
        txtWindowSize.setText(settings.getWindowSize() + " dp");
        txtFontSize.setText(settings.getFontSize() + " sp");
        txtIconSize.setText(settings.getIconSize() + " dp");
    }

    private void applySettings() {
        Intent serviceIntent = new Intent(this, FloatingFuelService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())
                );
                startActivityForResult(intent, OVERLAY_PERMISSION_REQ);
            } else {
                applySettings();
            }
        } else {
            applySettings();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OVERLAY_PERMISSION_REQ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(this)) {
                    applySettings();
                } else {
                    Toast.makeText(this, "Разрешение не выдано", Toast.LENGTH_LONG).show();
                }
            }
        }
    }
}
