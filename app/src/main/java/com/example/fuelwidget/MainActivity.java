package com.example.fuelwidget;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private static final int OVERLAY_PERMISSION_REQ = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Перехватываем все ошибки и показываем в Toast
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable e) {
                final String error = e.getClass().getSimpleName() + ": " + e.getMessage();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "КРАШ: " + error, Toast.LENGTH_LONG).show();
                    }
                });
                try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
                android.os.Process.killProcess(android.os.Process.myPid());
            }
        });

        Toast.makeText(this, "Fuel: onCreate", Toast.LENGTH_SHORT).show();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Нужно разрешение на оверлей", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())
                );
                startActivityForResult(intent, OVERLAY_PERMISSION_REQ);
            } else {
                Toast.makeText(this, "Разрешение есть, запускаем сервис", Toast.LENGTH_SHORT).show();
                startFloatingService();
            }
        } else {
            startFloatingService();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OVERLAY_PERMISSION_REQ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, "Разрешение выдано, запускаем сервис", Toast.LENGTH_SHORT).show();
                    startFloatingService();
                } else {
                    Toast.makeText(this, "Разрешение не выдано", Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    private void startFloatingService() {
        try {
            Intent serviceIntent = new Intent(this, FloatingFuelService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            Toast.makeText(this, "Сервис запущен", Toast.LENGTH_SHORT).show();
        } catch (Throwable e) {
            Toast.makeText(this, "Ошибка запуска: " + e.getClass().getSimpleName() + " " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }
}
