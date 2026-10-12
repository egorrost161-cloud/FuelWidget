package com.example.fuelwidget;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
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
    private Set<String> selected = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_list);

        settings = new SettingsManager(this);
        selected = new HashSet<>(settings.getHiddenApps());

        ListView listView = findViewById(R.id.list_apps);
        Button btnSave = findViewById(R.id.btn_save_apps);

        loadApps();

        final AppAdapter adapter = new AppAdapter();
        listView.setAdapter(adapter);

        listView.setOnItemClickListener((p, v, pos, id) -> {
            AppInfo app = apps.get(pos);
            if (selected.contains(app.packageName)) selected.remove(app.packageName);
            else selected.add(app.packageName);
            adapter.notifyDataSetChanged();
        });

        btnSave.setOnClickListener(v -> {
            settings.setHiddenApps(selected);
            Toast.makeText(this, "Сохранено: " + selected.size(), Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolved = pm.queryIntentActivities(mainIntent, 0);

        for (ResolveInfo ri : resolved) {
            ApplicationInfo ai = ri.activityInfo.applicationInfo;
            if (ai.packageName.equals(getPackageName())) continue;

            AppInfo info = new AppInfo();
            info.packageName = ai.packageName;
            info.appName = pm.getApplicationLabel(ai).toString();
            info.icon = ai.loadIcon(pm);
            apps.add(info);
        }

        Collections.sort(apps, new Comparator<AppInfo>() {
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
        public int getCount() { return apps.size(); }
        public Object getItem(int p) { return apps.get(p); }
        public long getItemId(int p) { return p; }

        public View getView(int pos, View cv, ViewGroup parent) {
            if (cv == null) cv = LayoutInflater.from(AppListActivity.this)
                    .inflate(R.layout.item_app, parent, false);

            AppInfo app = apps.get(pos);
            ImageView icon = cv.findViewById(R.id.app_icon);
            TextView name = cv.findViewById(R.id.app_name);
            CheckBox cb = cv.findViewById(R.id.app_checkbox);

            icon.setImageDrawable(app.icon);
            name.setText(app.appName);
            cb.setChecked(selected.contains(app.packageName));
            return cv;
        }
    }
}
