package com.example.fuelwidget;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.car.Car;
import android.car.hardware.CarSensorEvent;
import android.car.hardware.CarSensorManager;
import android.content.ComponentName;
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

import java.util.List;
import java.util.Set;

public class FloatingFuelService extends Service {

    private static final long UPDATE_INTERVAL = 5 * 60 * 1000L;   // 5 минут
    private static final long VISIBILITY_CHECK_INTERVAL = 2000L; // 2 секунды

    private WindowManager windowManager;
    private View floatingView;
    private WindowManager.LayoutParams params;
    private TextView fuelText;
    private ImageView iconView;

    private SettingsManager settings;

    private Car mCar;
    private CarSensorManager mSensorManager;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private float touchStartX, touchStartY;
    private int startX, startY;
    private boolean isDragging = false;
    private long touchStartTime = 0;

    private float lastFuel = -1f;
    private boolean lastLow = false;

    // === Задачи по таймеру ===

    private final Runnable updateTask = new Runnable() {
        @Override
        public void run() {
            readFuel();
            handler.postDelayed(this, UPDATE_INTERVAL);
        }
    };

    private final Runnable visibilityTask = new Runnable() {
        @Override
        public void run() {
            checkActiveApp();
            handler.postDelayed(this, VISIBILITY_CHECK_INTERVAL);
        }
    };

    // === Подключение к Car ===

    private final ServiceConnection carConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            try {
                mSensorManager = (CarSensorManager) mCar.getCarManager(Car.SENSOR_SERVICE);
                readFuel();
            } catch (Exception e) {
                showError();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mSensorManager = null;
        }
    };

    // === Жизненный цикл сервиса ===

    @Override
    public void onCreate() {
        super.onCreate();
        settings = new SettingsManager(this);

        try {
            createNotificationChannel();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification notification = new Notification.Builder(this, "fuel_channel")
                        .setContentTitle("Fuel")
                        .setContentText("Отслеживание топлива")
                        .setSmallIcon(android.R.drawable.ic_menu_info_details)
                        .build();
                startForeground(1, notification);
            }
        } catch (Exception ignored) {
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createFloatingView();
        connectCar();

        handler.post(updateTask);
        handler.post(visibilityTask);
    }

    // === Создание плавающего окна ===

    private void createFloatingView() {
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_fuel, null);
        fuelText = floatingView.findViewById(R.id.fuel_text);
        iconView = floatingView.findViewById(R.id.fuel_icon);

        // Тонкий шрифт как у системных иконок
        fuelText.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));

        int type;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            type = WindowManager.LayoutParams.TYPE_PHONE;
        }

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
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        touchStartX = event.getRawX();
                        touchStartY = event.getRawY();
                        startX = params.x;
                        startY = params.y;
                        touchStartTime = System.currentTimeMillis();
                        isDragging = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - touchStartX;
                        float dy = event.getRawY() - touchStartY;
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            isDragging = true;
                        }
                        if (isDragging) {
                            params.x = startX + (int) dx;
                            params.y = startY + (int) dy;
                            windowManager.updateViewLayout(floatingView, params);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        long pressDuration = System.currentTimeMillis() - touchStartTime;
                        if (!isDragging && pressDuration > 800) {
                            // долгое нажатие — размер НЕ меняем, теперь всё в настройках
                        } else if (isDragging) {
                            settings.setPosition(params.x, params.y);
                        }
                        return true;
                }
                return false;
            }
        });

        windowManager.addView(floatingView, params);
    }

    // === Применение размеров (окна, шрифта, иконки) ===

    private void applySizes() {
        if (fuelText == null || iconView == null) return;

        float density = getResources().getDisplayMetrics().density;

        // Размер шрифта
        float fontSp = settings.getFontSize();
        fuelText.setTextSize(fontSp);

        // Размер иконки
        int iconPx = (int) (settings.getIconSize() * density);
        ViewGroup.LayoutParams lp = iconView.getLayoutParams();
        lp.width = iconPx;
        lp.height = iconPx;
        iconView.setLayoutParams(lp);
    }

    // === Подключение к Car ===

    private void connectCar() {
        try {
            mCar = Car.createCar(this, carConnection);
            if (mCar != null) {
                mCar.connect();
            }
        } catch (Exception e) {
            showError();
        }
    }

    // === Чтение топлива ===

    private void readFuel() {
        if (mSensorManager == null) return;
        try {
            CarSensorEvent event = mSensorManager.getLatestSensorEvent(0x11600307);
            if (event != null && event.floatValues != null && event.floatValues.length > 0) {
                float fuel = event.floatValues[0];
                boolean low = false;
                if (event.intValues != null && event.intValues.length > 0) {
                    low = event.intValues[0] == 1;
                }
                lastFuel = fuel;
                lastLow = low;
                updateFuelText(fuel, low);
                return;
            }
            showError();
        } catch (Exception e) {
            showError();
        }
    }

    // === Обновление текста ===

    private void updateFuelText(float fuel, boolean low) {
        if (fuelText == null) return;
        handler.post(new Runnable() {
            @Override
            public void run() {
                int liters = (int) fuel;
                fuelText.setText(liters + " " + getLiterWord(liters));

                int color;
                if (low) {
                    color = Color.RED;
                } else {
                    color = isNightMode() ? Color.WHITE : Color.BLACK;
                }

                fuelText.setTextColor(color);
                if (iconView != null) {
                    iconView.setColorFilter(color);
                }
            }
        });
    }

    private void showError() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (fuelText != null) {
                    fuelText.setText("-- литров");
                    int color = isNightMode() ? Color.WHITE : Color.BLACK;
                    fuelText.setTextColor(color);
                    if (iconView != null) {
                        iconView.setColorFilter(color);
                    }
                }
            }
        });
    }

    // === Склонение слова «литр» ===

    private String getLiterWord(int n) {
        int mod10 = n % 10;
        int mod100 = n % 100;
        if (mod10 == 1 && mod100 != 11) {
            return "литр";
        } else if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) {
            return "литра";
        } else {
            return "литров";
        }
    }

    // === Определение ночного режима ===

    private boolean isNightMode() {
        int nightModeFlags = getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES;
    }

    // === Скрытие окна в выбранных приложениях ===

    private void checkActiveApp() {
        try {
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
            if (tasks == null || tasks.isEmpty()) return;

            String topPackage = tasks.get(0).topActivity.getPackageName();
            Set<String> hiddenApps = settings.getHiddenApps();

            if (hiddenApps.contains(topPackage)) {
                hideWindow();
            } else {
                showWindow();
            }
        } catch (Exception ignored) {
            // если нет доступа — окно остаётся видимым
        }
    }

    private void hideWindow() {
        if (floatingView != null && floatingView.getVisibility() != View.GONE) {
            floatingView.setVisibility(View.GONE);
        }
    }

    private void showWindow() {
        if (floatingView != null && floatingView.getVisibility() != View.VISIBLE) {
            floatingView.setVisibility(View.VISIBLE);
        }
    }

    // === Канал уведомления ===

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "fuel_channel",
                    "Fuel Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    // === Служебные методы ===

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // При каждом запуске сервиса (например, из настроек) — перечитываем настройки
        if (floatingView != null && windowManager != null) {
            applySizes();
            updateFuelText(lastFuel, lastLow);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
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
    public IBinder onBind(Intent intent) {
        return null;
    }
}
