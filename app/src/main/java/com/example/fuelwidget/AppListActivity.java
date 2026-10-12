package com.example.fuelwidget;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AppListActivity extends AppCompatActivity {

    private SettingsManager settings;
    private List<AppInfo> apps = new ArrayList<>();
    private Set<String> selectedPackages = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_list);

        settings = new SettingsManager(this);
        selectedPackages = new HashSet<>(settings.getHiddenApps());

        ListView listView = findViewById(R.id.list_apps);
        Button btnSave = findViewById(R.id.btn_save_apps);

        loadApps();

        final AppAdapter adapter = new AppAdapter();
        listView.setAdapter(adapter);

        listView.setOnItemClickListener((parent, view, position, id) -> {
            AppInfo app = apps.get(position);
            if (selectedPackages.contains(app.packageName)) {
                selectedPackages.remove(app.packageName);
            } else {
                selectedPackages.add(app.packageName);
            }
            adapter.notifyDataSetChanged();
        });

        btnSave.setOnClickListener(v -> {
            settings.setHiddenApps(selectedPackages);
            Toast.makeText(this, "Сохранено: " + selectedPackages.size() + " приложений",
                    Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> installedApps = pm.getInstalledApplications(0);

        for (ApplicationInfo appInfo : installedApps) {
            AppInfo info = new AppInfo();
            info.packageName = appInfo.packageName;
            info.appName = pm.getApplicationLabel(appInfo).toString();
            info.icon = appInfo.loadIcon(pm);
            apps.add(info);
        }

        Collections.sort(apps, new Comparator<AppInfo>() {
            @Override
            public int compare(AppInfo a, AppInfo b) {
                return a.appName.compareToIgnoreCase(b.appName);
            }
        });
    }

    private static class AppInfo {
        String packageName;
        String appName;
        android.graphics.drawable.Drawable icon;
    }

    private class AppAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return apps.size();
        }

        @Override
        public Object getItem(int position) {
            return apps.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(AppListActivity.this)
                        .inflate(R.layout.item_app, parent, false);
            }

            AppInfo app = apps.get(position);

            ImageView icon = convertView.findViewById(R.id.app_icon);
            TextView name = convertView.findViewById(R.id.app_name);
            CheckBox checkBox = convertView.findViewById(R.id.app_checkbox);

            icon.setImageDrawable(app.icon);
            name.setText(app.appName);
            checkBox.setChecked(selectedPackages.contains(app.packageName));

            return convertView;
        }
    }
}
