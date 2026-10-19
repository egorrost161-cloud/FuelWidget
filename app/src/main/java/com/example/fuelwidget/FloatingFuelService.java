package com.example.fuelwidget;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.car.Car;
import android.car.hardware.CarSensorEvent;
import android.car.hardware.CarSensorManager;
import android.content.ComponentCallbacks;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import java.util.Set;

public class FloatingFuelService extends Service {

    public static final String ACTION_REFRESH = "com.example.fuelwidget.ACTION_REFRESH";

    private static final long UPDATE_INTERVAL = 5 * 60 * 1000L;
    private static final long VISIBILITY_CHECK_INTERVAL = 1500L;

    private WindowManager windowManager;
    private View floatingView;
    private WindowManager.LayoutParams params;
    private TextView fuelText;
    private ImageView iconView;

    private SettingsManager settings;
    private Car mCar;
    private CarSensorManager mSensorManager;
    private UsageStatsManager usageStatsManager;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private float touchStartX, touchStartY;
    private int startX, startY;
    private boolean isDragging = false;

    private float lastFuel = -1f;
    private boolean lastLow = false;

    private final ComponentCallbacks configCallback = new ComponentCallbacks() {
        @Override
        public void onConfigurationChanged(Configuration newConfig) {
            updateFuelText(lastFuel, lastLow);
        }

        @Override
        public void onLowMemory() {}
    };

    private final Runnable updateTask = new Runnable() {
        public void run() { readFuel(); handler.postDelayed(this, UPDATE_INTERVAL); }
    };

    private final Runnable visibilityTask = new Runnable() {
        public void run() { checkActiveApp(); handler.postDelayed(this, VISIBILITY_CHECK_INTERVAL); }
    };

    private final ServiceConnection carConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName n, IBinder s) {
            try {
                mSensorManager = (CarSensorManager) mCar.getCarManager(Car.SENSOR_SERVICE);
                readFuel();
            } catch (Throwable e) { showError(); }
        }
        public void onServiceDisconnected(ComponentName n) { mSensorManager = null; }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        settings = new SettingsManager(this);

        try {
            createNotificationChannel();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification notification = new Notification.Builder(this, "fuel_channel")
                        .setContentTitle("Топливо")
                        .setContentText("Отслеживание уровня топлива")
                        .setSmallIcon(android.R.drawable.ic_menu_info_details)
                        .build();
                startForeground(1, notification);
            }
        } catch (Exception ignored) {}

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        usageStatsManager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);

        registerComponentCallbacks(configCallback);

        createFloatingView();
        connectCar();

        handler.post(updateTask);
        handler.post(visibilityTask);
    }

    private void createFloatingView() {
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_fuel, null);
        fuelText = floatingView.findViewById(R.id.fuel_text);
        iconView = floatingView.findViewById(R.id.fuel_icon);

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = settings.getPosX();
        params.y = settings.getPosY();

        applySizes();

        floatingView.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                if (settings.isLocked()) {
                    return false;
                }

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        touchStartX = event.getRawX();
                        touchStartY = event.getRawY();
                        startX = params.x;
                        startY = params.y;
                        isDragging = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - touchStartX;
                        float dy = event.getRawY() - touchStartY;
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) isDragging = true;
                        if (isDragging) {
                            params.x = startX + (int) dx;
                            params.y = startY + (int) dy;
                            windowManager.updateViewLayout(floatingView, params);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (isDragging) settings.setPosition(params.x, params.y);
                        return true;
                }
                return false;
            }
        });

        windowManager.addView(floatingView, params);
    }

    private void applySizes() {
        if (fuelText == null || iconView == null) return;

        float density = getResources().getDisplayMetrics().density;

        fuelText.setTextSize(settings.getFontSize());

        // Используем стандартный шрифт, если Roboto Flex недоступен
        try {
            Typeface tf = Typeface.create("sans-serif", settings.getFontWeight());
            fuelText.setTypeface(tf);
        } catch (Throwable ignored) {}

        int iconPx = (int) (settings.getIconSize() * density);
        ViewGroup.LayoutParams lp = iconView.getLayoutParams();
        lp.width = iconPx; lp.height = iconPx;
        iconView.setLayoutParams(lp);
    }

    private void connectCar() {
        try {
            mCar = Car.createCar(this, carConnection);
            if (mCar != null) mCar.connect();
        } catch (Throwable e) { showError(); }
    }

    private void readFuel() {
        if (mSensorManager == null) return;
        try {
            CarSensorEvent event = mSensorManager.getLatestSensorEvent(0x11600307);
            if (event != null && event.floatValues != null && event.floatValues.length > 0) {
                float fuel = event.floatValues[0];
                boolean low = event.intValues != null && event.intValues.length > 0
                        && event.intValues[0] == 1;
                lastFuel = fuel; lastLow = low;
                updateFuelText(fuel, low);
                return;
            }
            showError();
        } catch (Throwable e) { showError(); }
    }

    private void updateFuelText(float fuel, boolean low) {
        if (fuelText == null) return;
        handler.post(() -> {
            if (fuel < 0) {
                fuelText.setText("-- литров");
            } else {
                int liters = (int) fuel;
                fuelText.setText(liters + " " + getLiterWord(liters));
            }
            int color = low ? Color.RED : (isDarkText() ? Color.BLACK : Color.WHITE);
            fuelText.setTextColor(color);
            if (iconView != null) iconView.setColorFilter(color);
        });
    }

    private void showError() {
        handler.post(() -> {
            if (fuelText != null) {
                fuelText.setText("-- литров");
                int color = isDarkText() ? Color.BLACK : Color.WHITE;
                fuelText.setTextColor(color);
                if (iconView != null) iconView.setColorFilter(color);
            }
        });
    }

    private String getLiterWord(int n) {
        int m10 = n % 10, m100 = n % 100;
        if (m10 == 1 && m100 != 11) return "литр";
        if (m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14)) return "литра";
        return "литров";
    }

    private boolean isDarkText() {
        int theme = settings.getTheme();
        if (theme == SettingsManager.THEME_DAY) return true;
        if (theme == SettingsManager.THEME_NIGHT) return false;

        int m = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return m != Configuration.UI_MODE_NIGHT_YES;
    }

    private void checkActiveApp() {
        try {
            if (usageStatsManager == null) return;
            long t = System.currentTimeMillis();
            UsageEvents events = usageStatsManager.queryEvents(t - 10000, t);
            String lastPkg = null;
            UsageEvents.Event e = new UsageEvents.Event();
            while (events.hasNextEvent()) {
                events.getNextEvent(e);
                if (e.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    lastPkg = e.getPackageName();
                }
            }
            if (lastPkg == null) return;
            Set<String> hidden = settings.getHiddenApps();
            if (hidden.contains(lastPkg)) hideWindow(); else showWindow();
        } catch (Exception ignored) {}
    }

    private void hideWindow() {
        if (floatingView != null && floatingView.getVisibility() != View.GONE)
            floatingView.setVisibility(View.GONE);
    }

    private void showWindow() {
        if (floatingView != null && floatingView.getVisibility() != View.VISIBLE)
            floatingView.setVisibility(View.VISIBLE);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    "fuel_channel", "Топливо", NotificationManager.IMPORTANCE_LOW);
            NotificationManager m = getSystemService(NotificationManager.class);
            if (m != null) m.createNotificationChannel(ch);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (floatingView != null) {
            applySizes();
            updateFuelText(lastFuel, lastLow);
        }

        if (intent != null && ACTION_REFRESH.equals(intent.getAction())) {
            readFuel();
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterComponentCallbacks(configCallback);
        handler.removeCallbacks(updateTask);
        handler.removeCallbacks(visibilityTask);
        if (floatingView != null && windowManager != null) {
            try { windowManager.removeView(floatingView); } catch (Exception ignored) {}
        }
        if (mCar != null && mCar.isConnected()) {
            try { mCar.disconnect(); } catch (Exception ignored) {}
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }
}
