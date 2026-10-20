package com.example.fuelwidget;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private static final int OVERLAY_PERMISSION_REQ = 1001;
    private static final int USAGE_PERMISSION_REQ = 1002;

    private SettingsManager settings;
    private TextView txtWindowSize, txtFontSize, txtIconSize, txtFontWeight, txtTheme, txtLock;
    private LinearLayout layoutPermissions, layoutSettings, layoutActions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        settings = new SettingsManager(this);

        layoutPermissions = findViewById(R.id.layout_permissions);
        layoutSettings = findViewById(R.id.layout_settings);
        layoutActions = findViewById(R.id.layout_actions);

        txtWindowSize = findViewById(R.id.txt_window_size);
        txtFontSize = findViewById(R.id.txt_font_size);
        txtIconSize = findViewById(R.id.txt_icon_size);
        txtFontWeight = findViewById(R.id.txt_font_weight);
        txtTheme = findViewById(R.id.txt_theme);
        txtLock = findViewById(R.id.txt_lock);

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
        findViewById(R.id.btn_lock_minus).setOnClickListener(v -> setLock(false));
        findViewById(R.id.btn_lock_plus).setOnClickListener(v -> setLock(true));

        findViewById(R.id.btn_app_list).setOnClickListener(v ->
                startActivity(new Intent(this, AppListActivity.class)));

        findViewById(R.id.btn_reset).setOnClickListener(v -> {
            settings.resetAll();
            updateLabels();
            applySettings();
            Toast.makeText(this, "Сброшено", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btn_perm_overlay).setOnClickListener(v -> {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(i, OVERLAY_PERMISSION_REQ);
        });

        findViewById(R.id.btn_perm_usage).setOnClickListener(v -> {
            startActivityForResult(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), USAGE_PERMISSION_REQ);
        });

        findViewById(R.id.btn_perm_carinfo).setOnClickListener(v -> {
            // Это системное разрешение, его нельзя выдать через всплывающее окно. 
            // Открываем карточку приложения, где пользователь найдет нужный пункт.
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            Toast.makeText(this, "Найдите 'Car information' и включите", Toast.LENGTH_LONG).show();
        });

        findViewById(R.id.btn_continue).setOnClickListener(v -> checkAllPermissions());

        // Проверяем разрешения при каждом запуске
        checkAllPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Отправляем сигнал сервису на обновление (если он уже запущен)
        applySettings();
    }

    private void checkAllPermissions() {
        boolean hasOverlay = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            hasOverlay = Settings.canDrawOverlays(this);
        }
        boolean hasUsage = hasUsageStatsPermission();

        if (hasOverlay && hasUsage) {
            layoutPermissions.setVisibility(View.GONE);
            layoutSettings.setVisibility(View.VISIBLE);
            layoutActions.setVisibility(View.VISIBLE);
            applySettings(); // Запускаем виджет, если всё ок
        } else {
            layoutPermissions.setVisibility(View.VISIBLE);
            layoutSettings.setVisibility(View.GONE);
            layoutActions.setVisibility(View.GONE);
        }
    }

    private boolean hasUsageStatsPermission() {
        try {
            AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
            if (appOps == null) return false;
            int mode = appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    getPackageName()
            );
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
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

    private void setLock(boolean locked) {
        settings.setLocked(locked);
        updateLabels();
        applySettings();
    }

    private void updateLabels() {
        txtWindowSize.setText(settings.getWindowSize() + " dp");
        txtFontSize.setText(settings.getFontSize() + " sp");
        txtIconSize.setText(settings.getIconSize() + " dp");
        txtFontWeight.setText(String.valueOf(settings.getFontWeight()));
        txtTheme.setText(themeName(settings.getTheme()));
        txtLock.setText(settings.isLocked() ? "Заблокирован" : "Разблокирован");
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
        if (!Settings.canDrawOverlays(this)) return; // Защита
        try {
            Intent i = new Intent(this, FloatingFuelService.class);
            i.setAction(FloatingFuelService.ACTION_REFRESH);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i);
            else startService(i);
        } catch (Exception ignored) {}
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        checkAllPermissions();
    }
}
