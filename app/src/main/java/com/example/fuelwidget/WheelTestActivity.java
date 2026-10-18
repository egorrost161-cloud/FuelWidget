package com.example.fuelwidget;

import android.car.Car;
import android.car.hardware.CarSensorEvent;
import android.car.hardware.CarSensorManager;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class WheelTestActivity extends AppCompatActivity {

    private TextView resultText;
    private Car mCar;
    private CarSensorManager mSensorManager;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // Список ID для проверки (наиболее вероятные кандидаты для руля)
    private final int[] testIds = {
            0x11600309, // Часто используется для WHEEL_ANGLE
            0x1160030A,
            0x1160030B,
            0x1160030C,
            0x11400400, // Gear
            0x11600307  // Fuel (для сравнения, не должен меняться от руля)
    };

    private final ServiceConnection carConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            try {
                mSensorManager = (CarSensorManager) mCar.getCarManager(Car.SENSOR_SERVICE);
                startUpdating();
            } catch (Exception e) {
                resultText.setText("Ошибка подключения: " + e.getMessage());
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mSensorManager = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wheel_test);

        resultText = findViewById(R.id.wheel_result);
        resultText.setTextSize(16f); // Чуть меньше, чтобы влезло больше строк

        mCar = Car.createCar(this, carConnection);
        if (mCar != null) mCar.connect();
    }

    private void startUpdating() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (mSensorManager != null) {
                    updateValues();
                }
                // Обновляем каждые 500 мс (0.5 сек)
                handler.postDelayed(this, 500);
            }
        });
    }

    private void updateValues() {
        StringBuilder sb = new StringBuilder();
        sb.append("Крутите руль и смотрите, что меняется:\n\n");

        for (int id : testIds) {
            String hexId = "0x" + Integer.toHexString(id).toUpperCase();
            try {
                CarSensorEvent event = mSensorManager.getLatestSensorEvent(id);
                if (event != null && event.floatValues != null && event.floatValues.length > 0) {
                    float val = event.floatValues[0];
                    // Если значение изменилось от предыдущего, можно было бы подсветить,
                    // но пока просто выводим.
                    sb.append(hexId).append(" : ").append(val).append("\n");
                } else {
                    sb.append(hexId).append(" : null\n");
                }
            } catch (SecurityException e) {
                sb.append(hexId).append(" : SECURITY\n");
            } catch (Exception e) {
                sb.append(hexId).append(" : ERR\n");
            }
        }

        resultText.setText(sb.toString());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (mCar != null && mCar.isConnected()) {
            mCar.disconnect();
        }
    }
}
