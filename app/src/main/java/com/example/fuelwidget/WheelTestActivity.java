package com.example.fuelwidget;

import android.car.Car;
import android.car.hardware.CarSensorEvent;
import android.car.hardware.CarSensorManager;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class WheelTestActivity extends AppCompatActivity {

    private TextView resultText;
    private Car mCar;
    private CarSensorManager mSensorManager;

    private final ServiceConnection carConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            try {
                mSensorManager = (CarSensorManager) mCar.getCarManager(Car.SENSOR_SERVICE);
                testWheelAngle();
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

        mCar = Car.createCar(this, carConnection);
        if (mCar != null) mCar.connect();
    }

    private void testWheelAngle() {
        if (mSensorManager == null) {
            resultText.setText("SensorManager = null");
            return;
        }

        StringBuilder sb = new StringBuilder();

        // Пробуем разные ID, которые могут быть связаны с рулём
        int[] ids = {
                0x11600309, // Часто используется для WHEEL_ANGLE
                0x11400400, // GEAR (для сравнения)
                291504647   // SPEED (из старого кода)
        };

        for (int id : ids) {
            try {
                CarSensorEvent event = mSensorManager.getLatestSensorEvent(id);
                if (event != null && event.floatValues != null && event.floatValues.length > 0) {
                    sb.append("ID ").append(Integer.toHexString(id)).append(": ")
                      .append(event.floatValues[0]).append("\n");
                } else {
                    sb.append("ID ").append(Integer.toHexString(id)).append(": Нет данных (null)\n");
                }
            } catch (SecurityException e) {
                sb.append("ID ").append(Integer.toHexString(id)).append(": SecurityException (нет доступа)\n");
            } catch (Exception e) {
                sb.append("ID ").append(Integer.toHexString(id)).append(": Ошибка ").append(e.getClass().getSimpleName()).append("\n");
            }
        }

        resultText.setText(sb.toString());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCar != null && mCar.isConnected()) {
            mCar.disconnect();
        }
    }
}
