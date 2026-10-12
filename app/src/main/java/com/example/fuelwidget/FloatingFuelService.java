package com.example.fuelwidget;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.car.Car;
import android.car.hardware.CarSensorEvent;
import android.car.hardware.CarSensorManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

public class FloatingFuelService extends Service {

    private static final String PREFS = "FuelWidgetPrefs";
    private static final String KEY_X = "pos_x";
    private static final String KEY_Y = "pos_y";
    private static final String KEY_SIZE = "size_index";

    private WindowManager windowManager;
    private View floatingView;
    private WindowManager.LayoutParams params;
    private TextView fuelText;
    private TextView iconText;

    private Car mCar;
    private CarSensorManager mSensorManager;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final long UPDATE_INTERVAL = 5 * 60 * 1000L; // 5 минут

    // Размеры окна: маленький / средний / большой
    private final int[] SIZES = {50, 70, 100}; // в dp
    private int sizeIndex = 1;

    private float touchStartX, touchStartY;
    private int startX, startY;
    private boolean isDragging = false;
    private long touchStartTime = 0;

    private final Runnable updateTask = new Runnable() {
        @Override
        public void run() {
            readFuel();
            handler.postDelayed(this, UPDATE_INTERVAL);
        }
    };

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

    @Override
    public void onCreate() {
        super.onCreate();

        // Foreground service — обязательно для Android 8+
        createNotificationChannel();
        Notification notification = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notification = new Notification.Builder(this, "fuel_channel")
                    .setContentTitle("Fuel")
                    .setContentText("Отслеживание топлива")
                    .setSmallIcon(android.R.drawable.ic_menu_info_details)
                    .build();
            startForeground(1, notification);
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createFloatingView();
        connectCar();

        handler.post(updateTask);
    }

    private void createFloatingView() {
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_fuel, null);
        fuelText = floatingView.findViewById(R.id.fuel_text);
        iconText = floatingView.findViewById(R.id.fuel_icon);

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        sizeIndex = prefs.getInt(KEY_SIZE, 1);

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
        params.x = prefs.getInt(KEY_X, 100);
        params.y = prefs.getInt(KEY_Y, 100);

        applySize();

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
                            // Долгое нажатие — меняем размер
                            changeSize();
                        } else if (isDragging) {
                            // Сохраняем позицию
                            SharedPreferences.Editor editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
                            editor.putInt(KEY_X, params.x);
                            editor.putInt(KEY_Y, params.y);
                            editor.apply();
                        }
                        return true;
                }
                return false;
            }
        });

        windowManager.addView(floatingView, params);
    }

    private void applySize() {
        if (fuelText == null) return;
        int sizePx = (int) (SIZES[sizeIndex] * getResources().getDisplayMetrics().density);
        fuelText.setTextSize(sizePx / getResources().getDisplayMetrics().density / 2);
    }

    private void changeSize() {
        sizeIndex = (sizeIndex + 1) % SIZES.length;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(KEY_SIZE, sizeIndex).apply();
        applySize();
        Toast.makeText(this, "Размер: " + (sizeIndex + 1), Toast.LENGTH_SHORT).show();
    }

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

    private void readFuel() {
        if (mSensorManager == null) return;
        try {
            CarSensorEvent event = mSensorManager.getLatestSensorEvent(0x11600307);
            if (event != null) {
                CarSensorEvent.FuelLevelData data = event.getFuelLevelData(null);
                if (data != null) {
                    float fuel = data.level;
                    boolean low = event.getFuelLevelLowData(null) != null
                            && event.getFuelLevelLowData(null).lowFuel;
                    updateFuelText(fuel, low);
                    return;
                }
            }
            showError();
        } catch (Exception e) {
            showError();
        }
    }

    private void updateFuelText(float fuel, boolean low) {
        if (fuelText == null) return;
        handler.post(() -> {
            fuelText.setText(String.format("%.0f Л", fuel));
            int color = low ? Color.RED : Color.BLACK;
            fuelText.setTextColor(color);
            if (iconText != null) iconText.setTextColor(color);
        });
    }

    private void showError() {
        handler.post(() -> {
            if (fuelText != null) {
                fuelText.setText("--");
                fuelText.setTextColor(Color.BLACK);
            }
        });
    }

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

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(updateTask);
        if (floatingView != null && windowManager != null) {
            windowManager.removeView(floatingView);
        }
        if (mSensorManager != null) {
            try { mSensorManager.unregisterListener(null); } catch (Exception ignored) {}
        }
        if (mCar != null && mCar.isConnected()) mCar.disconnect();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
