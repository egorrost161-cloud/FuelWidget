    @Override
    public void onCreate() {
        super.onCreate();
        Toast.makeText(this, "Сервис: onCreate", Toast.LENGTH_SHORT).show();

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
            Toast.makeText(this, "Сервис: startForeground OK", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Сервис: ошибка startForeground: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        try {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            Toast.makeText(this, "Сервис: windowManager OK", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Сервис: ошибка windowManager: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        try {
            createFloatingView();
            Toast.makeText(this, "Сервис: окно добавлено", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Сервис: ошибка окна: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        try {
            connectCar();
            Toast.makeText(this, "Сервис: connectCar вызван", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Сервис: ошибка Car: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        handler.post(updateTask);
        Toast.makeText(this, "Сервис: создан полностью", Toast.LENGTH_SHORT).show();
    }
