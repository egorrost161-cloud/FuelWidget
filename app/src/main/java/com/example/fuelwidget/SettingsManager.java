package com.example.fuelwidget;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

public class SettingsManager {

    private static final String PREFS_NAME = "FuelWidgetSettings";

    private static final String KEY_WINDOW_SIZE = "window_size";
    private static final String KEY_FONT_SIZE = "font_size";
    private static final String KEY_ICON_SIZE = "icon_size";
    private static final String KEY_HIDDEN_APPS = "hidden_apps";
    private static final String KEY_POS_X = "pos_x";
    private static final String KEY_POS_Y = "pos_y";

    public static final int DEFAULT_WINDOW_SIZE = 42;
    public static final int DEFAULT_FONT_SIZE = 16;
    public static final int DEFAULT_ICON_SIZE = 20;

    public static final int MIN_WINDOW_SIZE = 20;
    public static final int MAX_WINDOW_SIZE = 100;
    public static final int MIN_FONT_SIZE = 10;
    public static final int MAX_FONT_SIZE = 40;
    public static final int MIN_ICON_SIZE = 10;
    public static final int MAX_ICON_SIZE = 60;

    private final SharedPreferences prefs;

    public SettingsManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public int getWindowSize() {
        return prefs.getInt(KEY_WINDOW_SIZE, DEFAULT_WINDOW_SIZE);
    }

    public void setWindowSize(int size) {
        prefs.edit().putInt(KEY_WINDOW_SIZE, size).apply();
    }

    public int getFontSize() {
        return prefs.getInt(KEY_FONT_SIZE, DEFAULT_FONT_SIZE);
    }

    public void setFontSize(int size) {
        prefs.edit().putInt(KEY_FONT_SIZE, size).apply();
    }

    public int getIconSize() {
        return prefs.getInt(KEY_ICON_SIZE, DEFAULT_ICON_SIZE);
    }

    public void setIconSize(int size) {
        prefs.edit().putInt(KEY_ICON_SIZE, size).apply();
    }

    public Set<String> getHiddenApps() {
        return new HashSet<>(prefs.getStringSet(KEY_HIDDEN_APPS, new HashSet<String>()));
    }

    public void setHiddenApps(Set<String> apps) {
        prefs.edit().putStringSet(KEY_HIDDEN_APPS, new HashSet<>(apps)).apply();
    }

    public void addHiddenApp(String packageName) {
        Set<String> apps = getHiddenApps();
        apps.add(packageName);
        setHiddenApps(apps);
    }

    public void removeHiddenApp(String packageName) {
        Set<String> apps = getHiddenApps();
        apps.remove(packageName);
        setHiddenApps(apps);
    }

    public boolean isAppHidden(String packageName) {
        return getHiddenApps().contains(packageName);
    }

    public int getPosX() {
        return prefs.getInt(KEY_POS_X, 100);
    }

    public int getPosY() {
        return prefs.getInt(KEY_POS_Y, 100);
    }

    public void setPosition(int x, int y) {
        prefs.edit().putInt(KEY_POS_X, x).putInt(KEY_POS_Y, y).apply();
    }

    public void resetAll() {
        prefs.edit().clear().apply();
    }
}
