package io.iftech.healthreminder;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/** 选择日期范围 / 月份 / 全部，把身体记录导出成 CSV。 */
public final class BodyExportActivity extends Activity {
    private static final int REQUEST_EXPORT = 4610;
    private static final String MODE_RANGE = "range";
    private static final String MODE_MONTH = "month";
    private static final String MODE_ALL = "all";

    private int GREEN = UiPalette.ACCENT;
    private int MINT_LIGHT = UiPalette.SOFT;
    private int MINT_PALE = UiPalette.PALE;
    private int INK = UiPalette.INK;
    private int MUTED = UiPalette.MUTED;
    private int BORDER = UiPalette.BORDER;

    private String mode = MODE_RANGE;
    private final Calendar rangeStart = Calendar.getInstance();
    private final Calendar rangeEnd = Calendar.getInstance();
    private final Calendar month = Calendar.getInstance();

    private String pendingCsv;
    private String pendingName;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        rangeStart.set(Calendar.DAY_OF_MONTH, 1);
        month.set(Calendar.DAY_OF_MONTH, 1);
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        AppUpdateManager.resumePendingInstall(this);
    }

    private void render() {
        UiPalette.applyWindow(getWindow());

        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackground(UiPalette.page());

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(10), dp(20), dp(8));
        TextView back = text("‹", 29, INK, false);
        back.setGravity(Gravity.CENTER);
        back.setBackground(round(Color.WHITE, 14));
        back.setContentDescription("返回");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setPadding(dp(12), 0, 0, 0);
        heading.addView(text("导出身体数据", 22, INK, true));
        heading.addView(text("CSV", 12, MUTED, false));
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        screen.addView(header, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(8), dp(20), dp(26));
        scroll.addView(page, new ScrollView.LayoutParams(-1, -2));
        screen.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(screen);

        page.addView(scopeCard(), new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(-1, -2);
        summaryParams.topMargin = dp(12);
        page.addView(summaryCard(), summaryParams);

        TextView export = text("导出 CSV 文件", 16, Color.WHITE, true);
        export.setGravity(Gravity.CENTER);
        export.setBackground(round(GREEN, 15));
        export.setContentDescription("导出 CSV 文件");
        export.setOnClickListener(v -> export());
        LinearLayout.LayoutParams exportParams = new LinearLayout.LayoutParams(-1, dp(50));
        exportParams.topMargin = dp(18);
        page.addView(export, exportParams);

        TextView note = text("导出范围包含每日身体记录和经期记录。文件保存位置由系统的文件选择器决定，不会上传到任何服务器。",
                12, MUTED, false);
        note.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(-1, -2);
        noteParams.topMargin = dp(12);
        page.addView(note, noteParams);
    }

    private LinearLayout scopeCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        card.setBackground(round(Color.WHITE, 17));
        card.setElevation(dp(1));

        card.addView(text("导出范围", 16, INK, true));

        LinearLayout group = new LinearLayout(this);
        group.setPadding(dp(4), dp(4), dp(4), dp(4));
        group.setBackground(round(MINT_PALE, 13));
        String[][] options = {{MODE_RANGE, "日期范围"}, {MODE_MONTH, "月份"}, {MODE_ALL, "全部"}};
        for (int index = 0; index < options.length; index++) {
            final String value = options[index][0];
            boolean selected = value.equals(mode);
            TextView chip = text(options[index][1], 13, selected ? Color.WHITE : INK, true);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(0, dp(8), 0, dp(8));
            GradientDrawable background = new GradientDrawable();
            background.setColor(selected ? GREEN : Color.TRANSPARENT);
            background.setCornerRadius(dp(10));
            chip.setBackground(background);
            chip.setOnClickListener(v -> {
                mode = value;
                render();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
            if (index > 0) params.leftMargin = dp(3);
            group.addView(chip, params);
        }
        LinearLayout.LayoutParams groupParams = new LinearLayout.LayoutParams(-1, -2);
        groupParams.topMargin = dp(11);
        card.addView(group, groupParams);

        card.addView(modeControls(), new LinearLayout.LayoutParams(-1, -2));
        return card;
    }

    private LinearLayout modeControls() {
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(13);

        if (MODE_RANGE.equals(mode)) {
            controls.addView(dateChipRow("开始", rangeStart, picked -> {
                rangeStart.set(picked.get(Calendar.YEAR), picked.get(Calendar.MONTH),
                        picked.get(Calendar.DAY_OF_MONTH));
                if (rangeStart.after(rangeEnd)) {
                    rangeEnd.set(rangeStart.get(Calendar.YEAR), rangeStart.get(Calendar.MONTH),
                            rangeStart.get(Calendar.DAY_OF_MONTH));
                }
                render();
            }), new LinearLayout.LayoutParams(-1, -2));
            controls.addView(dateChipRow("结束", rangeEnd, picked -> {
                rangeEnd.set(picked.get(Calendar.YEAR), picked.get(Calendar.MONTH),
                        picked.get(Calendar.DAY_OF_MONTH));
                if (rangeEnd.before(rangeStart)) {
                    rangeStart.set(rangeEnd.get(Calendar.YEAR), rangeEnd.get(Calendar.MONTH),
                            rangeEnd.get(Calendar.DAY_OF_MONTH));
                }
                render();
            }), new LinearLayout.LayoutParams(-1, -2));
        } else if (MODE_MONTH.equals(mode)) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(text("月份", 14, INK, true), new LinearLayout.LayoutParams(0, -2, 1));
            TextView chip = text(monthLabel(), 13, GREEN, true);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(12), dp(7), dp(12), dp(7));
            chip.setBackground(round(MINT_LIGHT, 10));
            chip.setOnClickListener(v -> WheelPicker.month(dialogContext(), "选择导出月份", month, picked -> {
                month.set(picked.get(Calendar.YEAR), picked.get(Calendar.MONTH), 1);
                render();
            }).show());
            row.addView(chip);
            controls.addView(row);
        } else {
        }
        controls.setLayoutParams(params);
        return controls;
    }

    private interface OnDayPicked { void onPicked(Calendar value); }

    private LinearLayout dateChipRow(String label, Calendar value, final OnDayPicked onPicked) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, 0);
        TextView caption = text(label, 14, INK, true);
        row.addView(caption, new LinearLayout.LayoutParams(0, -2, 1));
        TextView chip = text(new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(value.getTime()) + "  ›",
                13, GREEN, true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(12), dp(7), dp(12), dp(7));
        chip.setBackground(round(MINT_LIGHT, 10));
        chip.setOnClickListener(v -> WheelPicker.date(dialogContext(), "选择" + label + "日期", value, picked -> {
            onPicked.onPicked(picked);
        }).show());
        row.addView(chip);
        return row;
    }

    private LinearLayout summaryCard() {
        BodyDataExporter.Snapshot snapshot = snapshot();
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(round(MINT_LIGHT, 17));
        card.setElevation(dp(1));

        card.addView(text("将导出", 13, MUTED, true));
        TextView count = text(snapshot.notes + " 条身体记录 · " + snapshot.periods + " 段经期记录",
                17, INK, true);
        count.setPadding(0, dp(5), 0, 0);
        card.addView(count);
        TextView range = text(snapshot.empty() ? "这个范围里还没有数据。" : "范围：" + scopeLabel(),
                12, MUTED, false);
        range.setPadding(0, dp(6), 0, 0);
        card.addView(range);
        return card;
    }

    private String scopeLabel() {
        if (MODE_ALL.equals(mode)) return "全部记录";
        if (MODE_MONTH.equals(mode)) return monthLabel();
        return PeriodLog.dayKey(rangeStart) + " 至 " + PeriodLog.dayKey(rangeEnd);
    }

    private BodyDataExporter.Snapshot snapshot() {
        if (MODE_ALL.equals(mode)) return BodyDataExporter.collect(this, null, null);
        if (MODE_MONTH.equals(mode)) {
            Calendar from = (Calendar) month.clone();
            Calendar to = (Calendar) month.clone();
            to.set(Calendar.DAY_OF_MONTH, to.getActualMaximum(Calendar.DAY_OF_MONTH));
            return BodyDataExporter.collect(this, PeriodLog.dayKey(from), PeriodLog.dayKey(to));
        }
        return BodyDataExporter.collect(this, PeriodLog.dayKey(rangeStart), PeriodLog.dayKey(rangeEnd));
    }

    private void export() {
        BodyDataExporter.Snapshot snapshot = snapshot();
        if (snapshot.empty()) {
            Toast.makeText(this, "所选范围里还没有身体数据", Toast.LENGTH_SHORT).show();
            return;
        }
        pendingCsv = "\uFEFF" + BodyDataExporter.csv(snapshot);
        pendingName = BodyDataExporter.fileName(mode,
                MODE_MONTH.equals(mode) ? monthLabel().replace("年", "-").replace("月", "") : null,
                PeriodLog.dayKey(rangeStart), PeriodLog.dayKey(rangeEnd));

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("text/csv")
                .putExtra(Intent.EXTRA_TITLE, pendingName);
        startActivityForResult(intent, REQUEST_EXPORT);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_EXPORT || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null || pendingCsv == null) return;
        try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
            if (out == null) throw new IllegalStateException("无法写入所选文件");
            out.write(pendingCsv.getBytes(StandardCharsets.UTF_8));
            out.flush();
            Toast.makeText(this, "已导出 " + pendingName, Toast.LENGTH_LONG).show();
        } catch (Exception error) {
            Toast.makeText(this, "导出失败：" + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String monthLabel() {
        return new SimpleDateFormat("yyyy年M月", Locale.CHINA).format(month.getTime());
    }

    private Context dialogContext() {
        return new ContextThemeWrapper(this, R.style.ItemsDialogTheme);
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
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(UiPalette.surface(color));
        shape.setCornerRadius(dp(radius));
        if (color == Color.WHITE) shape.setStroke(dp(1), BORDER);
        return shape;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
