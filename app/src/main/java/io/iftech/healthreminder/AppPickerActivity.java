package io.iftech.healthreminder;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ApplicationInfo;
import android.content.res.ColorStateList;
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
    private int GREEN = UiPalette.ACCENT;
    private int MINT_LIGHT = UiPalette.SOFT;
    private int MINT_PALE = UiPalette.PALE;
    private int INK = UiPalette.INK;
    private int MUTED = UiPalette.MUTED;
    private int PAPER = UiPalette.PAGE;
    private int LINE = UiPalette.BORDER;

    private final Set<String> selected = new HashSet<>();
    private LinearLayout list;
    private TextView selectionCount;
    private int visibleSelectionCount;
    private boolean blockMode;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        UiPalette.applyWindow(getWindow());
        blockMode = "block".equals(getIntent().getStringExtra("mode"));
        selected.addAll(blockMode ? ReminderSettings.blockPackages(this) : ReminderSettings.whitelist(this));
        render();
    }

    private void render() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(19), dp(10), dp(19), dp(14));
        root.setBackground(UiPalette.page());
        setContentView(root);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("‹", 29, INK, false);
        back.setGravity(Gravity.CENTER);
        back.setBackground(round(Color.WHITE, 14));
        back.setContentDescription("返回");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(42), dp(42)));
        TextView title = text(blockMode ? "限制应用" : "白名单应用", 23, INK, true);
        title.setPadding(dp(12), 0, 0, 0);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(header);

        LinearLayout intro = new LinearLayout(this);
        intro.setGravity(Gravity.CENTER_VERTICAL);
        intro.setPadding(dp(15), dp(14), dp(15), dp(14));
        intro.setBackground(round(MINT_LIGHT, 17));
        intro.setElevation(dp(1));
        LinearLayout.LayoutParams introParams = new LinearLayout.LayoutParams(-1, -2);
        introParams.topMargin = dp(18);
        root.addView(intro, introParams);

        TextView introIcon = text(blockMode ? "拦" : "免", 16, GREEN, true);
        introIcon.setGravity(Gravity.CENTER);
        introIcon.setBackground(round(Color.WHITE, 13));
        LinearLayout.LayoutParams introIconParams = new LinearLayout.LayoutParams(dp(42), dp(42));
        introIconParams.rightMargin = dp(12);
        intro.addView(introIcon, introIconParams);

        LinearLayout introCopy = new LinearLayout(this);
        introCopy.setOrientation(LinearLayout.VERTICAL);
        introCopy.addView(text(blockMode ? "只在设定时段内拦截" : "所选应用始终放行", 15, INK, true));
        TextView description = text(blockMode
                        ? "白名单中的应用仍会始终放行。"
                        : "所选应用不计入屏幕使用统计，也不会触发提醒。",
                12, MUTED, false);
        description.setPadding(0, dp(4), 0, 0);
        introCopy.addView(description);
        intro.addView(introCopy, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout listHeader = new LinearLayout(this);
        listHeader.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams listHeaderParams = new LinearLayout.LayoutParams(-1, -2);
        listHeaderParams.topMargin = dp(21);
        listHeaderParams.bottomMargin = dp(9);
        root.addView(listHeader, listHeaderParams);
        listHeader.addView(text("应用列表", 14, INK, true),
                new LinearLayout.LayoutParams(0, -2, 1));
        selectionCount = text("已选 0 个", 11, GREEN, true);
        selectionCount.setGravity(Gravity.CENTER);
        selectionCount.setPadding(dp(10), dp(6), dp(10), dp(6));
        selectionCount.setBackground(round(MINT_PALE, 12));
        listHeader.addView(selectionCount);

        ScrollView listScroll = new ScrollView(this);
        listScroll.setFillViewport(true);
        listScroll.setClipToPadding(false);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(8), dp(4), dp(8), dp(4));
        list.setBackground(round(Color.WHITE, 17));
        list.setElevation(dp(1));
        listScroll.addView(list, new ScrollView.LayoutParams(-1, -2));
        root.addView(listScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        addApps();

        TextView done = text("完成", 15, Color.WHITE, true);
        done.setGravity(Gravity.CENTER);
        done.setBackground(solid(GREEN, 16));
        done.setElevation(dp(1));
        done.setOnClickListener(v -> saveAndClose());
        LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(-1, dp(50));
        doneParams.topMargin = dp(12);
        root.addView(done, doneParams);
    }

    private void saveAndClose() {
        if (blockMode) {
            ReminderSettings.prefs(this).edit()
                    .putStringSet("block_packages", new HashSet<>(selected))
                    .putBoolean("block_selection_configured", true)
                    .apply();
        } else {
            ReminderSettings.saveWhitelist(this, selected);
        }
        finish();
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
        Collections.sort(apps, Comparator.comparing(
                info -> info.loadLabel(pm).toString(), String.CASE_INSENSITIVE_ORDER));

        if (blockMode && !hasSavedBlockSelection()) {
            for (ResolveInfo app : apps) selected.add(app.activityInfo.packageName);
        }
        visibleSelectionCount = 0;
        for (ResolveInfo app : apps) {
            if (selected.contains(app.activityInfo.packageName)) visibleSelectionCount++;
        }
        updateSelectionCount();

        if (apps.isEmpty()) {
            TextView empty = text("没有找到可启动的应用", 14, MUTED, false);
            empty.setGravity(Gravity.CENTER);
            list.addView(empty, new LinearLayout.LayoutParams(-1, dp(120)));
            return;
        }

        for (ResolveInfo app : apps) {
            String pkg = app.activityInfo.packageName;
            String appName = app.loadLabel(pm).toString();
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(60));
            row.setPadding(dp(8), dp(7), dp(8), dp(7));

            ImageView icon = new ImageView(this);
            icon.setImageDrawable(app.loadIcon(pm));
            row.addView(icon, new LinearLayout.LayoutParams(dp(38), dp(38)));

            TextView name = text(appName, 14, INK, true);
            name.setPadding(dp(10), 0, dp(4), 0);
            row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));

            CheckBox check = new CheckBox(this);
            check.setButtonTintList(new ColorStateList(
                    new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                    new int[]{GREEN, UiPalette.DISABLED}));
            check.setChecked(selected.contains(pkg));
            check.setOnCheckedChangeListener((button, value) -> {
                if (value) {
                    selected.add(pkg);
                    visibleSelectionCount++;
                } else {
                    selected.remove(pkg);
                    visibleSelectionCount--;
                }
                row.setContentDescription(appName + (value ? "，已选择" : "，未选择"));
                updateSelectionCount();
            });
            row.addView(check);
            row.setContentDescription(appName + (check.isChecked() ? "，已选择" : "，未选择"));
            row.setOnClickListener(v -> check.setChecked(!check.isChecked()));
            list.addView(row, new LinearLayout.LayoutParams(-1, -2));

            View divider = new View(this);
            divider.setBackgroundColor(LINE);
            LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, dp(1));
            dividerParams.leftMargin = dp(46);
            list.addView(divider, dividerParams);
        }
    }

    private void updateSelectionCount() {
        if (selectionCount != null) selectionCount.setText("已选 " + visibleSelectionCount + " 个");
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

    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(UiPalette.surface(color));
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(1), UiPalette.BORDER);
        return drawable;
    }

    private GradientDrawable solid(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(UiPalette.surface(color));
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
