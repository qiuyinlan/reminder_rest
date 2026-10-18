package io.iftech.healthreminder;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AppPickerActivity extends Activity {
    private final Set<String> selected = new HashSet<>();
    private LinearLayout list;
    private boolean blockMode;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        blockMode = "block".equals(getIntent().getStringExtra("mode"));
        selected.addAll(blockMode ? ReminderSettings.blockPackages(this) : ReminderSettings.whitelist(this));
        render();
    }

    private void render() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(10));
        root.setBackgroundColor(0xfff4f8f1);
        setContentView(root);
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(blockMode ? "选择限制应用" : "白名单应用", 23, 0xff23342a, true);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, dp(42), 1));
        TextView done = text("完成", 15, 0xff277049, true);
        titleRow.addView(done);
        done.setOnClickListener(v -> {
            if (blockMode) {
                ReminderSettings.prefs(this).edit()
                        .putStringSet("block_packages", new HashSet<>(selected))
                        .putBoolean("block_selection_configured", true)
                        .apply();
            } else {
                ReminderSettings.saveWhitelist(this, selected);
            }
            finish();
        });
        root.addView(titleRow);
        TextView description = text(blockMode
                ? "选中的应用只在设定时段内拦截；白名单中的应用始终放行。"
                : "这些应用不计入屏幕使用统计，也不会触发提醒。", 13, 0xff65776a, false);
        description.setPadding(0, dp(4), 0, dp(12));
        root.addView(description);
        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(8), dp(5), dp(8), dp(5));
        list.setBackground(background(Color.WHITE, 16));
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        addApps();
    }

    private void addApps() {
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        PackageManager pm = getPackageManager();
        List<ResolveInfo> results = pm.queryIntentActivities(launcher, 0);
        ArrayList<ResolveInfo> apps = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        for (ResolveInfo info : results) {
            String pkg = info.activityInfo.packageName;
            if (!pkg.equals(getPackageName()) && seen.add(pkg)) apps.add(info);
        }
        // Some Android builds hide launcher activities from queryIntentActivities;
        // use installed user applications as a visible fallback.
        if (apps.isEmpty()) {
            for (ApplicationInfo info : pm.getInstalledApplications(PackageManager.GET_META_DATA)) {
                if (info.packageName.equals(getPackageName()) || (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
                Intent launch = pm.getLaunchIntentForPackage(info.packageName);
                if (launch == null || !seen.add(info.packageName)) continue;
                ResolveInfo resolved = pm.resolveActivity(launch, 0);
                if (resolved != null) apps.add(resolved);
            }
        }
        Collections.sort(apps, Comparator.comparing(info -> info.loadLabel(pm).toString(), String.CASE_INSENSITIVE_ORDER));
        if (blockMode && !hasSavedBlockSelection()) {
            for (ResolveInfo app : apps) selected.add(app.activityInfo.packageName);
        }
        for (ResolveInfo app : apps) {
            String pkg = app.activityInfo.packageName;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(6), dp(8), dp(6), dp(8));
            ImageView icon = new ImageView(this);
            icon.setImageDrawable(app.loadIcon(pm));
            row.addView(icon, new LinearLayout.LayoutParams(dp(38), dp(38)));
            TextView name = text(app.loadLabel(pm).toString(), 15, 0xff23342a, true);
            name.setPadding(dp(10), 0, dp(4), 0);
            row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            CheckBox check = new CheckBox(this);
            check.setButtonTintList(android.content.res.ColorStateList.valueOf(0xff277049));
            check.setChecked(selected.contains(pkg));
            check.setOnCheckedChangeListener((button, value) -> {
                if (value) selected.add(pkg); else selected.remove(pkg);
            });
            row.addView(check);
            row.setOnClickListener(v -> check.setChecked(!check.isChecked()));
            list.addView(row, new LinearLayout.LayoutParams(-1, -2));
            View divider = new View(this);
            divider.setBackgroundColor(0xffe9eee8);
            list.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        }
        if (apps.isEmpty()) {
            TextView empty = text("没有找到可启动的应用", 14, 0xff65776a, false);
            empty.setGravity(Gravity.CENTER);
            list.addView(empty, new LinearLayout.LayoutParams(-1, dp(100)));
        }
    }

    private boolean hasSavedBlockSelection() {
        android.content.SharedPreferences prefs = ReminderSettings.prefs(this);
        return prefs.getBoolean("block_selection_configured", false) || prefs.contains("block_packages");
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private GradientDrawable background(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
