package io.iftech.healthreminder;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ContextThemeWrapper;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class StatsActivity extends Activity {
    private int GREEN = UiPalette.ACCENT;
    private int MINT = UiPalette.ACCENT;
    private int MINT_LIGHT = UiPalette.SOFT;
    private int MINT_PALE = UiPalette.PALE;
    private int INK = UiPalette.INK;
    private int MUTED = UiPalette.MUTED;
    private int PAPER = UiPalette.PAGE;
    private int WARN = UiPalette.WARN;
    private int BORDER = UiPalette.BORDER;
    private int DISABLED = UiPalette.DISABLED;
    private static final int REQUEST_WRITE_IMAGE = 4128;
    private Calendar month = Calendar.getInstance();
    private Calendar selected = Calendar.getInstance();
    private static final String FILTER_ALL = "all";
    private static final String FILTER_COMPANION = "companion_all";
    private String filter = FILTER_ALL;
    private LinearLayout content;
    private TextView monthLabel;
    private float touchX;
    private float touchY;
    private long lastMonthLabelTap;
    private Bitmap previewBitmap;
    private boolean previewVisible;
    private boolean pendingShareAfterPermission;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        month.set(Calendar.DAY_OF_MONTH, 1);
        render();
    }

    private void render() {
        UiPalette.applyWindow(getWindow());
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackground(UiPalette.page());

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(20), dp(10), dp(20), dp(16));
        header.setBackground(headerBackground());
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("每日记录", 25, INK, true);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        ImageButton saveImage = new ImageButton(this);
        saveImage.setImageResource(android.R.drawable.ic_menu_share);
        saveImage.setColorFilter(GREEN);
        saveImage.setContentDescription("保存今日分享图");
        saveImage.setBackground(outline(Color.WHITE, BORDER, 12));
        lift(saveImage);
        saveImage.setOnClickListener(v -> openSharePreview());
        titleRow.addView(saveImage, new LinearLayout.LayoutParams(dp(42), dp(42)));
        header.addView(titleRow);
        TextView help = text("看看自己每天照顾身体的时刻", 13, MUTED, false);
        help.setPadding(0, dp(3), 0, 0);
        header.addView(help);
        screen.addView(header, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(14), dp(16), dp(20));
        scroll.addView(content);
        screen.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(screen);

        LinearLayout calendar = new LinearLayout(this);
        calendar.setOrientation(LinearLayout.VERTICAL);
        calendar.setPadding(dp(14), dp(12), dp(14), dp(12));
        calendar.setBackground(round(Color.WHITE, 18));
        lift(calendar);
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView previous = text("‹", 28, GREEN, true);
        TextView next = text("›", 28, GREEN, true);
        monthLabel = text("", 17, INK, true);
        previous.setGravity(Gravity.CENTER);
        next.setGravity(Gravity.CENTER);
        previous.setBackground(round(MINT_PALE, 12));
        next.setBackground(round(MINT_PALE, 12));
        previous.setOnClickListener(v -> shiftMonth(-1));
        next.setOnClickListener(v -> shiftMonth(1));
        monthLabel.setOnClickListener(v -> {
            long now = android.os.SystemClock.elapsedRealtime();
            if (now - lastMonthLabelTap < 450) {
                Calendar today = Calendar.getInstance();
                selected = (Calendar) today.clone();
                month = (Calendar) today.clone();
                month.set(Calendar.DAY_OF_MONTH, 1);
                render();
            }
            lastMonthLabelTap = now;
        });
        head.addView(previous, new LinearLayout.LayoutParams(dp(42), dp(40)));
        head.addView(monthLabel, new LinearLayout.LayoutParams(0, -2, 1));
        head.addView(next, new LinearLayout.LayoutParams(dp(42), dp(40)));
        calendar.addView(head);
        String[] week = {"日", "一", "二", "三", "四", "五", "六"};
        LinearLayout weekRow = new LinearLayout(this);
        for (String day : week) {
            TextView cell = text(day, 12, MUTED, false);
            cell.setGravity(Gravity.CENTER);
            weekRow.addView(cell, new LinearLayout.LayoutParams(0, dp(30), 1));
        }
        calendar.addView(weekRow);
        buildDays(calendar);
        content.addView(calendar);

        TextView dayHeading = text(DateFormat.getDateInstance(DateFormat.FULL).format(selected.getTime()), 16, INK, true);
        dayHeading.setPadding(dp(2), dp(18), dp(2), dp(9));
        content.addView(dayHeading);
        int[] totals = totalsForDay(selected);
        LinearLayout summary = new LinearLayout(this);
        summary.setGravity(Gravity.CENTER);
        summary.setPadding(dp(8), dp(12), dp(8), dp(12));
        summary.setBackground(outline(Color.WHITE, BORDER, 16));
        lift(summary);
        summary.addView(summaryMetric("完成", totals[0], GREEN), new LinearLayout.LayoutParams(0, dp(46), 1));
        summary.addView(summaryDivider(), new LinearLayout.LayoutParams(dp(1), dp(32)));
        summary.addView(summaryMetric("未完成", totals[1], WARN), new LinearLayout.LayoutParams(0, dp(46), 1));
        summary.addView(summaryDivider(), new LinearLayout.LayoutParams(dp(1), dp(32)));
        summary.addView(summaryMetric("陪伴", totals[2], UiPalette.COMPANION), new LinearLayout.LayoutParams(0, dp(46), 1));
        content.addView(summary);
        LinearLayout pills = new LinearLayout(this);
        pills.setPadding(dp(5), dp(5), dp(5), dp(5));
        pills.setBackground(round(UiPalette.SOFT, 14));
        TextView all = pill("全部", FILTER_ALL.equals(filter));
        TextView done = pill("做了", ReminderRecord.KIND_DONE.equals(filter));
        TextView missed = pill("没做", ReminderRecord.KIND_MISSED.equals(filter));
        TextView companion = pill("陪伴", FILTER_COMPANION.equals(filter));
        all.setOnClickListener(v -> { filter = FILTER_ALL; render(); });
        done.setOnClickListener(v -> { filter = ReminderRecord.KIND_DONE; render(); });
        missed.setOnClickListener(v -> { filter = ReminderRecord.KIND_MISSED; render(); });
        companion.setOnClickListener(v -> { filter = FILTER_COMPANION; render(); });
        pills.addView(all, new LinearLayout.LayoutParams(0, dp(42), 1));
        pills.addView(done, new LinearLayout.LayoutParams(0, dp(42), 1));
        pills.addView(missed, new LinearLayout.LayoutParams(0, dp(42), 1));
        pills.addView(companion, new LinearLayout.LayoutParams(0, dp(42), 1));
        LinearLayout.LayoutParams pillsParams = new LinearLayout.LayoutParams(-1, -2);
        pillsParams.topMargin = dp(10);
        pillsParams.bottomMargin = dp(8);
        content.addView(pills, pillsParams);
        showTimeline();
        addNavigation(screen);
    }

    private void openSharePreview() {
        if (previewBitmap != null && !previewBitmap.isRecycled()) previewBitmap.recycle();
        previewBitmap = renderTodayShareImage();
        previewVisible = true;

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackground(UiPalette.page());
        FrameLayout header = new FrameLayout(this);
        header.setBackground(headerBackground());
        TextView back = text("‹", 30, GREEN, true);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("返回统计");
        back.setOnClickListener(v -> closeSharePreview());
        FrameLayout.LayoutParams backParams = new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.LEFT | Gravity.CENTER_VERTICAL);
        header.addView(back, backParams);
        TextView previewTitle = text("今日分享图", 17, INK, true);
        previewTitle.setGravity(Gravity.CENTER);
        header.addView(previewTitle, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(-1, dp(56));
        page.addView(header, headerParams);

        ImageView image = new ImageView(this);
        image.setImageBitmap(previewBitmap);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(-1, 0, 1);
        imageParams.setMargins(dp(12), dp(4), dp(12), dp(4));
        page.addView(image, imageParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setPadding(dp(6), dp(6), dp(6), dp(6));
        actions.setBackground(round(Color.WHITE, 18));
        lift(actions);
        actions.setGravity(Gravity.CENTER);
        Button save = previewActionButton("保存图片", android.R.drawable.ic_menu_save,
                MINT_LIGHT, GREEN, v -> saveOrSharePreview(false));
        Button share = previewActionButton("分享", android.R.drawable.ic_menu_share,
                MINT, Color.WHITE, v -> saveOrSharePreview(true));
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(0, dp(54), 1);
        actionParams.setMargins(dp(6), 0, dp(6), 0);
        actions.addView(save, actionParams);
        LinearLayout.LayoutParams shareParams = new LinearLayout.LayoutParams(0, dp(54), 1);
        shareParams.setMargins(dp(6), 0, dp(6), 0);
        actions.addView(share, shareParams);
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(-1, dp(70));
        footerParams.setMargins(dp(14), 0, dp(14), dp(12));
        page.addView(actions, footerParams);
        setContentView(page);
    }

    private Button previewActionButton(String label, int iconResource, int backgroundColor,
                                       int textColor, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(textColor);
        button.setTextSize(15);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setAllCaps(false);
        Drawable icon = getDrawable(iconResource);
        if (icon != null) {
            icon = icon.mutate();
            icon.setTint(textColor);
            button.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null);
            button.setCompoundDrawablePadding(dp(8));
        }
        button.setBackground(backgroundColor == MINT ? round(backgroundColor, 16)
                : outline(backgroundColor, BORDER, 16));
        lift(button);
        button.setOnClickListener(listener);
        return button;
    }

    private void saveOrSharePreview(boolean share) {
        if (previewBitmap == null || previewBitmap.isRecycled()) return;
        pendingShareAfterPermission = share;
        if (Build.VERSION.SDK_INT < 29
                && checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_IMAGE);
            return;
        }
        persistAndSharePreview(share);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_WRITE_IMAGE) return;
        if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            persistAndSharePreview(pendingShareAfterPermission);
        } else {
            Toast.makeText(this, "需要存储权限才能保存图片", Toast.LENGTH_SHORT).show();
        }
    }

    private void persistAndSharePreview(boolean share) {
        Uri uri;
        String filename = "好好休息_今日记录_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA)
                .format(new Date()) + ".png";
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, filename);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                values.put(MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/好好休息");
                values.put(MediaStore.Images.Media.IS_PENDING, 1);
                uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new java.io.IOException("无法创建图片记录");
                try (OutputStream output = getContentResolver().openOutputStream(uri)) {
                    if (output == null || !previewBitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                        throw new java.io.IOException("图片写入失败");
                    }
                } catch (Exception error) {
                    getContentResolver().delete(uri, null, null);
                    throw error;
                }
                ContentValues published = new ContentValues();
                published.put(MediaStore.Images.Media.IS_PENDING, 0);
                getContentResolver().update(uri, published, null, null);
            } else {
                String saved = MediaStore.Images.Media.insertImage(getContentResolver(), previewBitmap,
                        filename, "好好休息今日记录");
                if (saved == null) throw new java.io.IOException("图片写入失败");
                uri = Uri.parse(saved);
            }
            if (share) {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("image/png");
                send.putExtra(Intent.EXTRA_STREAM, uri);
                send.setClipData(ClipData.newUri(getContentResolver(), "今日记录", uri));
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(send, "分享今日记录"));
            } else {
                Toast.makeText(this, "已保存到图片/好好休息", Toast.LENGTH_LONG).show();
            }
        } catch (Exception error) {
            Toast.makeText(this, share ? "保存或分享失败，请稍后重试" : "图片保存失败，请稍后重试",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void closeSharePreview() {
        previewVisible = false;
        if (previewBitmap != null && !previewBitmap.isRecycled()) previewBitmap.recycle();
        previewBitmap = null;
        render();
    }

    @Override public void onBackPressed() {
        if (previewVisible) closeSharePreview();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (previewBitmap != null && !previewBitmap.isRecycled()) previewBitmap.recycle();
        super.onDestroy();
    }

    private Bitmap renderTodayShareImage() {
        Calendar today = Calendar.getInstance();
        LinkedHashMap<String, ActionSummary> actions = new LinkedHashMap<>();
        int totalReminders = 0;
        int totalRecords = 0;
        int done = 0;
        for (ReminderRecord record : ReminderRecord.all(this)) {
            if (!sameDay(record.startedAt, today)
                    || (!ReminderRecord.KIND_DONE.equals(record.kind)
                    && !ReminderRecord.KIND_MISSED.equals(record.kind))) continue;
            ActionSummary summary = actions.get(record.action);
            if (summary == null) {
                summary = new ActionSummary();
                actions.put(record.action, summary);
            }
            totalRecords++;
            if (ReminderRecord.KIND_DONE.equals(record.kind)) {
                summary.completed++;
                done++;
            }
            if (!"提前完成".equals(record.reason)) {
                summary.reminders++;
                totalReminders++;
            }
        }

        int mood = totalRecords == 0 ? 1 : done * 3 > totalRecords * 2 ? 2
                : done * 3 > totalRecords ? 1 : 0;
        String note = totalRecords == 0 ? "今天也记得喝水、活动身体，给自己一点照顾。"
                : mood == 2 ? "今天很棒，继续保持，好好爱护身体。"
                : mood == 1 ? "不错，明天继续保持，好好爱护身体。"
                : "今天对身体有些懈怠啦，明天继续加油，好好爱护身体。";
        int width = 1080;
        int listTop = 770;
        int footerTop = Math.max(900, listTop + actions.size() * 82 + 28);
        int height = footerTop + 190;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(PAPER);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(MINT);
        canvas.drawRect(0, 0, width, 16, paint);

        drawShareText(canvas, paint, "好好休息", 64, 118, 58, INK, true);
        drawShareText(canvas, paint, "今日身体照顾记录", 66, 164, 26, MUTED, false);
        String date = new SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(today.getTime());
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextSize(27);
        paint.setColor(MINT);
        canvas.drawText(date, width - 64 - paint.measureText(date), 118, paint);

        RectF totalCard = new RectF(60, 220, 1020, 402);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(UiPalette.SURFACE);
        canvas.drawRoundRect(totalCard, 30, 30, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2);
        paint.setColor(BORDER);
        canvas.drawRoundRect(totalCard, 30, 30, paint);
        paint.setStyle(Paint.Style.FILL);
        drawShareText(canvas, paint, "今天提醒了我", 98, 280, 30, MUTED, false);
        drawShareText(canvas, paint, String.valueOf(totalReminders), 98, 374, 82, MINT, true);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextSize(82);
        float countEnd = 98 + paint.measureText(String.valueOf(totalReminders));
        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextSize(31);
        paint.setColor(INK);
        canvas.drawText("次", countEnd + 12, 373, paint);

        drawShareFace(canvas, 540, 520, 72, mood);
        String rate = totalRecords == 0 ? "今天暂无提醒记录" : "完成 " + done + " / " + totalRecords + " 次";
        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextSize(24);
        paint.setColor(MUTED);
        canvas.drawText(rate, (width - paint.measureText(rate)) / 2f, 650, paint);

        drawShareText(canvas, paint, "事项记录", 66, 744, 30, INK, true);
        int row = listTop;
        for (Map.Entry<String, ActionSummary> entry : actions.entrySet()) {
            RectF rowCard = new RectF(60, row, 1020, row + 66);
            canvas.drawRoundRect(rowCard, 18, 18, whitePaint());
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2);
            paint.setColor(BORDER);
            canvas.drawRoundRect(rowCard, 18, 18, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(categoryColor(entry.getKey()));
            canvas.drawRoundRect(new RectF(82, row + 21, 96, row + 45), 6, 6, paint);
            drawShareText(canvas, paint, fitShareText(paint, entry.getKey(), 390, 28, true),
                    116, row + 42, 28, INK, true);
            String detail = "提醒 " + entry.getValue().reminders + " 次  ·  做了 "
                    + entry.getValue().completed + " 次";
            paint.setTypeface(Typeface.DEFAULT);
            paint.setTextSize(23);
            paint.setColor(MUTED);
            canvas.drawText(detail, 986 - paint.measureText(detail), row + 42, paint);
            row += 82;
        }

        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(BORDER);
        paint.setStrokeWidth(2);
        canvas.drawLine(64, footerTop, 1016, footerTop, paint);
        drawShareText(canvas, paint, "好好休息寄语", 66, footerTop + 53, 26, MINT, true);
        drawShareText(canvas, paint, note, 66, footerTop + 105, 28, INK, false);
        drawShareText(canvas, paint, "好好爱护身体，从照顾自己的小事开始。", 66, footerTop + 151,
                23, MUTED, false);
        return bitmap;
    }

    private void drawShareFace(Canvas canvas, float centerX, float centerY, float radius, int mood) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(MINT);
        canvas.drawCircle(centerX, centerY, radius + 12, paint);
        paint.setColor(0xffffd85c);
        canvas.drawCircle(centerX, centerY, radius, paint);
        paint.setColor(0xfff39a83);
        canvas.drawOval(new RectF(centerX - 49, centerY + 13, centerX - 25, centerY + 25), paint);
        canvas.drawOval(new RectF(centerX + 25, centerY + 13, centerX + 49, centerY + 25), paint);
        paint.setColor(INK);
        if (mood == 2) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(5);
            paint.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawArc(new RectF(centerX - 48, centerY - 30, centerX - 20, centerY - 6), 20, 140, false, paint);
            canvas.drawArc(new RectF(centerX + 20, centerY - 30, centerX + 48, centerY - 6), 20, 140, false, paint);
        } else {
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(centerX - 33, centerY - 18, 5, paint);
            canvas.drawCircle(centerX + 33, centerY - 18, 5, paint);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(6);
        paint.setStrokeCap(Paint.Cap.ROUND);
        if (mood == 1) {
            canvas.drawLine(centerX - 19, centerY + 34, centerX + 19, centerY + 34, paint);
        } else {
            RectF mouth = mood == 0
                    ? new RectF(centerX - 25, centerY + 18, centerX + 25, centerY + 53)
                    : new RectF(centerX - 26, centerY + 7, centerX + 26, centerY + 47);
            canvas.drawArc(mouth, mood == 0 ? 200 : 20, 140, false, paint);
        }
    }

    private Paint whitePaint() {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(UiPalette.SURFACE);
        return paint;
    }

    private void drawShareText(Canvas canvas, Paint paint, String value, float x, float y,
                               float size, int color, boolean bold) {
        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        paint.setTextSize(size);
        paint.setColor(color);
        canvas.drawText(value, x, y, paint);
    }

    private String fitShareText(Paint paint, String value, float maxWidth, float size, boolean bold) {
        paint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        paint.setTextSize(size);
        String result = value;
        while (result.length() > 1 && paint.measureText(result + "…") > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result.equals(value) ? result : result + "…";
    }

    private static final class ActionSummary {
        int reminders;
        int completed;
    }

    private void shiftMonth(int amount) { month.add(Calendar.MONTH, amount); render(); }

    private void buildDays(LinearLayout calendar) {
        monthLabel.setText(new SimpleDateFormat("yyyy年 M月", Locale.CHINA).format(month.getTime()));
        Calendar cursor = (Calendar) month.clone();
        int offset = cursor.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY;
        cursor.add(Calendar.DAY_OF_MONTH, -offset);
        for (int week = 0; week < 6; week++) {
            LinearLayout row = new LinearLayout(this);
            for (int day = 0; day < 7; day++) {
                Calendar cellDate = (Calendar) cursor.clone();
                TextView cell = text(String.valueOf(cellDate.get(Calendar.DAY_OF_MONTH)), 13,
                        cellDate.get(Calendar.MONTH) == month.get(Calendar.MONTH) ? INK : DISABLED, false);
                cell.setGravity(Gravity.CENTER);
                boolean today = sameDay(cellDate, Calendar.getInstance());
                boolean isSelected = sameDay(cellDate, selected);
                if (today || isSelected) {
                    GradientDrawable marker = new GradientDrawable();
                    marker.setShape(GradientDrawable.OVAL);
                    marker.setColor(isSelected ? MINT : Color.TRANSPARENT);
                    if (!isSelected && today) marker.setStroke(dp(1), MINT);
                    cell.setBackground(marker);
                    if (isSelected) cell.setTextColor(Color.WHITE);
                }
                cell.setLayoutParams(new LinearLayout.LayoutParams(dp(34), dp(34)));
                LinearLayout wrapper = new LinearLayout(this);
                wrapper.setGravity(Gravity.CENTER);
                wrapper.addView(cell);
                wrapper.setOnClickListener(v -> { selected = cellDate; render(); });
                row.addView(wrapper, new LinearLayout.LayoutParams(0, dp(40), 1));
                cursor.add(Calendar.DAY_OF_MONTH, 1);
            }
            calendar.addView(row);
        }
    }

    private void showTimeline() {
        List<ReminderRecord> records = new ArrayList<>();
        for (ReminderRecord record : ReminderRecord.all(this)) {
            boolean visibleKind = ReminderRecord.KIND_DONE.equals(record.kind)
                    || ReminderRecord.KIND_MISSED.equals(record.kind)
                    || ReminderRecord.KIND_COMPANION.equals(record.kind)
                    || ReminderRecord.KIND_FOCUS.equals(record.kind);
            boolean isCompanion = ReminderRecord.KIND_COMPANION.equals(record.kind)
                    || ReminderRecord.KIND_FOCUS.equals(record.kind);
            if (sameDay(record.startedAt, selected) && (FILTER_ALL.equals(filter) ? visibleKind
                    : FILTER_COMPANION.equals(filter) ? isCompanion : record.kind.equals(filter))) records.add(record);
        }
        Collections.sort(records, Comparator.comparingLong(record -> record.startedAt));
        for (int index = 0; index < records.size(); index++) {
            ReminderRecord record = records.get(index);
            int color = categoryColor(record.action);
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.HORIZONTAL);
            TimelineRail rail = new TimelineRail(color, index == records.size() - 1);
            item.addView(rail, new LinearLayout.LayoutParams(dp(22), -1));

            LinearLayout details = new LinearLayout(this);
            details.setOrientation(LinearLayout.VERTICAL);
            details.setPadding(dp(15), dp(13), dp(15), dp(12));
            details.setBackground(pressable(round(Color.WHITE, 14)));
            lift(details);
            LinearLayout titleRow = new LinearLayout(this);
            titleRow.setGravity(Gravity.CENTER_VERTICAL);
            View category = new View(this);
            category.setBackground(round(color, 5));
            titleRow.addView(category, new LinearLayout.LayoutParams(dp(10), dp(10)));
            TextView action = text(record.action, 15, INK, true);
            LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(0, -2, 1);
            actionParams.leftMargin = dp(9);
            titleRow.addView(action, actionParams);
            details.addView(titleRow);

            String detail;
            if (ReminderRecord.KIND_COMPANION.equals(record.kind)) {
                long elapsed = Math.max(0, record.endedAt - record.startedAt) / 1000;
                detail = "陪伴了 " + duration(elapsed);
            } else if (ReminderRecord.KIND_FOCUS.equals(record.kind)) {
                long elapsed = Math.max(0, record.endedAt - record.startedAt) / 1000;
                detail = "专注陪伴了 " + duration(elapsed);
            } else if (ReminderRecord.KIND_DONE.equals(record.kind)) {
                long elapsed = Math.max(0, record.endedAt - record.startedAt) / 1000;
                detail = "完成" + ("提前完成".equals(record.reason) ? " · 提前完成" : " · 用时 " + duration(elapsed));
            } else {
                detail = "没做";
            }
            TextView state = text(detail, 12, MUTED, false);
            state.setPadding(dp(20), dp(6), 0, dp(2));
            details.addView(state);
            if (ReminderRecord.KIND_MISSED.equals(record.kind) && !record.reason.isEmpty()) {
                TextView note = text("原因：" + record.reason, 12, MUTED, false);
                note.setPadding(dp(20), dp(5), 0, 0);
                details.addView(note);
            }
            if (ReminderRecord.KIND_DONE.equals(record.kind) && !record.reason.isEmpty()
                    && !"提前完成".equals(record.reason)) {
                TextView note = text(record.reason, 13, INK, false);
                note.setPadding(dp(20), dp(5), 0, 0);
                details.addView(note);
            }
            if ((ReminderRecord.KIND_COMPANION.equals(record.kind) || ReminderRecord.KIND_FOCUS.equals(record.kind))
                    && record.reason != null && !record.reason.trim().isEmpty()) {
                TextView note = text(record.reason.trim(), 12, MUTED, false);
                note.setPadding(dp(20), dp(5), 0, 0);
                details.addView(note);
            }
            String timestamp = time(record.startedAt);
            if ((ReminderRecord.KIND_DONE.equals(record.kind) || ReminderRecord.KIND_COMPANION.equals(record.kind)
                    || ReminderRecord.KIND_FOCUS.equals(record.kind)) && record.endedAt > record.startedAt) {
                timestamp += " - " + time(record.endedAt);
            }
            TextView time = text(timestamp, 11, color, true);
            time.setPadding(dp(9), dp(4), dp(9), dp(4));
            time.setBackground(round(categoryTint(record.action), 10));
            LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(-2, -2);
            timeParams.leftMargin = dp(18);
            timeParams.topMargin = dp(7);
            details.addView(time, timeParams);

            LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(0, -2, 1);
            detailsParams.bottomMargin = dp(9);
            item.addView(details, detailsParams);
            // 长按记录 → 编辑 / 删除这条时间记录。挂在卡片本身，
            // 也让整行都响应该手势（点按仍然只是滑动列表）。
            View.OnLongClickListener openActions = v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                showRecordActions(record);
                return true;
            };
            item.setOnLongClickListener(openActions);
            details.setOnLongClickListener(openActions);
            content.addView(item, new LinearLayout.LayoutParams(-1, -2));
        }
        if (records.isEmpty()) {
            String message = FILTER_ALL.equals(filter) ? "这一天还没有记录"
                    : FILTER_COMPANION.equals(filter) ? "这一天还没有陪伴记录"
                    : ReminderRecord.KIND_DONE.equals(filter) ? "这一天还没有完成记录" : "这一天没有未完成记录";
            TextView empty = text(message, 13, MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(16), dp(32), dp(16), dp(32));
            empty.setBackground(round(Color.WHITE, 16));
            lift(empty);
            LinearLayout.LayoutParams emptyParams = new LinearLayout.LayoutParams(-1, -2);
            emptyParams.topMargin = dp(6);
            content.addView(empty, emptyParams);
        }
    }

    /** 长按记录后的操作面板：两行卡片式操作，比系统列表更清楚也更顺手。 */
    private void showRecordActions(ReminderRecord record) {
        LinearLayout board = new LinearLayout(this);
        board.setOrientation(LinearLayout.VERTICAL);
        board.setPadding(dp(16), 0, dp(16), dp(4));

        TextView summary = text(recordSummary(record), 12, MUTED, false);
        summary.setPadding(dp(2), dp(2), 0, dp(6));
        board.addView(summary);

        AlertDialog dialog = new AlertDialog.Builder(dialogContext())
                .setTitle(record.action)
                .setView(board)
                .setNegativeButton("取消", null)
                .create();
        board.addView(recordActionRow("编辑时间记录", "改名称、开始 / 结束时间或备注", false,
                () -> { dialog.dismiss(); editRecord(record); }));
        board.addView(recordActionRow("删除这条记录", "删除后无法恢复", true,
                () -> { dialog.dismiss(); confirmDeleteRecord(record); }));
        showStyledDialog(dialog);
    }

    private View recordActionRow(String title, String detail, boolean danger, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(14), dp(11), dp(14), dp(11));
        row.setBackground(outline(danger ? UiPalette.WARN_SOFT : UiPalette.PALE, BORDER, 14));
        row.addView(text(title, 15, danger ? WARN : INK, true));
        TextView sub = text(detail, 11, MUTED, false);
        sub.setPadding(0, dp(3), 0, 0);
        sub.setLetterSpacing(0.02f);
        row.addView(sub);
        row.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(8);
        row.setLayoutParams(params);
        return row;
    }

    private void confirmDeleteRecord(ReminderRecord record) {
        showStyledDialog(new AlertDialog.Builder(dialogContext())
                .setTitle("删除记录")
                .setMessage("删除「" + record.action + "」这条记录？删除后无法恢复。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    ReminderRecord.delete(this, record.id);
                    render();
                })
                .create());
    }

    /** 记录的一行摘要，用于操作面板顶部。 */
    private String recordSummary(ReminderRecord record) {
        String kind = ReminderRecord.KIND_DONE.equals(record.kind) ? "做了"
                : ReminderRecord.KIND_MISSED.equals(record.kind) ? "没做"
                : ReminderRecord.KIND_FOCUS.equals(record.kind) ? "专注陪伴" : "陪伴";
        String range = time(record.startedAt);
        if (record.endedAt > record.startedAt) range += " - " + time(record.endedAt);
        return kind + " · " + shortDate(record.startedAt) + " " + range;
    }

    private String shortDate(long timestamp) {
        return new SimpleDateFormat("M月d日", Locale.CHINA).format(new Date(timestamp));
    }

    private void editRecord(ReminderRecord record) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(4), dp(18), dp(8));

        TextView nameLabel = text("记录名称", 13, MUTED, true);
        form.addView(nameLabel);
        EditText action = new EditText(dialogContext());
        action.setSingleLine(true);
        action.setHint("记录名称");
        action.setText(record.action);
        action.setTextColor(INK);
        action.setHintTextColor(MUTED);
        action.setPadding(dp(13), dp(10), dp(13), dp(10));
        action.setMinHeight(dp(48));
        action.setBackground(UiPalette.inputBackground(this, MINT_PALE, 12));
        form.addView(action);

        // 只有「做了 / 没做」的记录才允许改类型；陪伴类记录保持原样。
        final boolean kindEditable = ReminderRecord.KIND_DONE.equals(record.kind)
                || ReminderRecord.KIND_MISSED.equals(record.kind);
        final String[] kind = {record.kind};
        if (kindEditable) {
            TextView kindLabel = text("记录类型", 13, MUTED, true);
            LinearLayout.LayoutParams kindLabelParams = new LinearLayout.LayoutParams(-1, -2);
            kindLabelParams.topMargin = dp(12);
            form.addView(kindLabel, kindLabelParams);
            LinearLayout kinds = new LinearLayout(this);
            kinds.setOrientation(LinearLayout.HORIZONTAL);
            kinds.setPadding(dp(4), dp(4), dp(4), dp(4));
            kinds.setBackground(round(MINT_PALE, 12));
            TextView doneOption = recordKindOption("做了", ReminderRecord.KIND_DONE.equals(kind[0]));
            TextView missedOption = recordKindOption("没做", ReminderRecord.KIND_MISSED.equals(kind[0]));
            kinds.addView(doneOption, new LinearLayout.LayoutParams(0, dp(42), 1));
            kinds.addView(missedOption, new LinearLayout.LayoutParams(0, dp(42), 1));
            Runnable refreshKind = () -> {
                setRecordKindSelected(doneOption, ReminderRecord.KIND_DONE.equals(kind[0]));
                setRecordKindSelected(missedOption, ReminderRecord.KIND_MISSED.equals(kind[0]));
            };
            doneOption.setOnClickListener(v -> { kind[0] = ReminderRecord.KIND_DONE; refreshKind.run(); });
            missedOption.setOnClickListener(v -> { kind[0] = ReminderRecord.KIND_MISSED; refreshKind.run(); });
            LinearLayout.LayoutParams kindsParams = new LinearLayout.LayoutParams(-1, -2);
            kindsParams.topMargin = dp(4);
            form.addView(kinds, kindsParams);
        }

        long[] startedAt = {record.startedAt};
        long[] endedAt = {record.endedAt};
        TextView timeLabel = text("时间", 13, MUTED, true);
        LinearLayout.LayoutParams timeLabelParams = new LinearLayout.LayoutParams(-1, -2);
        timeLabelParams.topMargin = dp(12);
        form.addView(timeLabel, timeLabelParams);
        TextView durationHint = text("", 12, MUTED, false);
        final Runnable refreshDuration = () -> updateDurationHint(durationHint, startedAt[0], endedAt[0]);
        LinearLayout startRow = recordTimeRow("开始时间", startedAt, refreshDuration);
        LinearLayout endRow = recordTimeRow("结束时间", endedAt, refreshDuration);
        form.addView(startRow);
        form.addView(endRow);
        durationHint.setPadding(dp(2), dp(8), 0, 0);
        form.addView(durationHint);
        refreshDuration.run();

        TextView reasonLabel = text("备注", 13, MUTED, true);
        LinearLayout.LayoutParams reasonLabelParams = new LinearLayout.LayoutParams(-1, -2);
        reasonLabelParams.topMargin = dp(12);
        form.addView(reasonLabel, reasonLabelParams);
        EditText reason = new EditText(dialogContext());
        reason.setSingleLine(true);
        reason.setHint("选填，例如：临时有事、做完的感受");
        // “提前完成”是内部标记，不展示给用户编辑。
        reason.setText("提前完成".equals(record.reason) ? "" : record.reason);
        reason.setTextColor(INK);
        reason.setHintTextColor(MUTED);
        reason.setPadding(dp(13), dp(10), dp(13), dp(10));
        reason.setMinHeight(dp(48));
        reason.setBackground(UiPalette.inputBackground(this, MINT_PALE, 12));
        form.addView(reason);

        AlertDialog dialog = new AlertDialog.Builder(dialogContext())
                .setTitle("编辑记录")
                .setView(form)
                .setNegativeButton("取消", null)
                .setNeutralButton("删除", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    String title = action.getText().toString().trim();
                    if (title.isEmpty()) {
                        Toast.makeText(this, "记录名称不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (endedAt[0] < startedAt[0]) {
                        Toast.makeText(this, "结束时间不能早于开始时间", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String note = reason.getText().toString().trim();
                    if (note.isEmpty() && "提前完成".equals(record.reason)) note = "提前完成";
                    ReminderRecord.update(this, new ReminderRecord(record.id, title, kind[0],
                            startedAt[0], endedAt[0], note));
                    dialog.dismiss();
                    render();
                }));
        dialog.setButton(AlertDialog.BUTTON_NEUTRAL, "删除", (confirm, which) ->
                showStyledDialog(new AlertDialog.Builder(dialogContext())
                        .setMessage("删除这条记录？")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("删除", (deleteDialog, selected) -> {
                            ReminderRecord.delete(this, record.id);
                            dialog.dismiss();
                            render();
                        })
                        .create()));
        showStyledDialog(dialog);
    }

    /** 编辑记录里的「做了 / 没做」分段控件。 */
    private TextView recordKindOption(String label, boolean selected) {
        TextView option = text(label, 14, selected ? Color.WHITE : INK, true);
        option.setGravity(Gravity.CENTER);
        setRecordKindSelected(option, selected);
        return option;
    }

    private void setRecordKindSelected(TextView option, boolean selected) {
        option.setTextColor(selected ? Color.WHITE : INK);
        if (selected) {
            GradientDrawable background = round(MINT, 10);
            option.setBackground(background);
            lift(option);
        } else {
            option.setBackground(round(UiPalette.SOFT, 10));
            option.setElevation(0f);
        }
    }

    /**
     * 时间字段：左侧是时间（点一下用「时/分/秒」滚轮改），右侧是日期胶囊（点一下用「年/月/日」滚轮改）。
     * 两个选择都和霸屏页「临时有事」里的滚轮一个样式。
     */
    private LinearLayout recordTimeRow(String label, final long[] timestamp, final Runnable onChanged) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(13), dp(9), dp(10), dp(9));
        row.setBackground(outline(MINT_PALE, BORDER, 12));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);
        TextView caption = text(label, 11, MUTED, false);
        final TextView value = text(time(timestamp[0]), 17, INK, true);
        value.setPadding(0, dp(2), 0, 0);
        copy.addView(caption);
        copy.addView(value);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));

        final TextView dateChip = text(shortDate(timestamp[0]) + "  ›", 12, GREEN, true);
        dateChip.setGravity(Gravity.CENTER);
        dateChip.setPadding(dp(10), dp(6), dp(10), dp(6));
        dateChip.setBackground(round(MINT_LIGHT, 10));
        row.addView(dateChip);

        final Runnable refresh = () -> {
            value.setText(time(timestamp[0]));
            dateChip.setText(shortDate(timestamp[0]) + "  ›");
            if (onChanged != null) onChanged.run();
        };
        row.setOnClickListener(v -> {
            Calendar current = Calendar.getInstance();
            current.setTimeInMillis(timestamp[0]);
            int secondsOfDay = current.get(Calendar.HOUR_OF_DAY) * 3600
                    + current.get(Calendar.MINUTE) * 60 + current.get(Calendar.SECOND);
            WheelPicker.time(dialogContext(), "选择" + label, "确定", secondsOfDay, true, picked -> {
                Calendar updated = Calendar.getInstance();
                updated.setTimeInMillis(timestamp[0]);
                updated.set(Calendar.HOUR_OF_DAY, picked / 3600);
                updated.set(Calendar.MINUTE, picked / 60 % 60);
                updated.set(Calendar.SECOND, picked % 60);
                timestamp[0] = updated.getTimeInMillis();
                refresh.run();
            }).show();
        });
        dateChip.setOnClickListener(v -> {
            Calendar current = Calendar.getInstance();
            current.setTimeInMillis(timestamp[0]);
            WheelPicker.date(dialogContext(), "选择日期", current, picked -> {
                Calendar updated = Calendar.getInstance();
                updated.setTimeInMillis(timestamp[0]);
                updated.set(picked.get(Calendar.YEAR), picked.get(Calendar.MONTH), picked.get(Calendar.DAY_OF_MONTH));
                timestamp[0] = updated.getTimeInMillis();
                refresh.run();
            }).show();
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(7);
        row.setLayoutParams(params);
        return row;
    }

    private void updateDurationHint(TextView hint, long startedAt, long endedAt) {
        if (endedAt <= startedAt) {
            hint.setText("");
            return;
        }
        hint.setText("这段记录持续 " + duration((endedAt - startedAt) / 1000));
    }

    private String recordDateTime(long timestamp) {
        return new SimpleDateFormat("yyyy年M月d日 HH:mm:ss", Locale.CHINA).format(new Date(timestamp));
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) { touchX = event.getX(); touchY = event.getY(); }
        boolean handled = super.dispatchTouchEvent(event);
        if (event.getAction() == MotionEvent.ACTION_UP) {
            float dx = event.getX() - touchX;
            float dy = event.getY() - touchY;
            if (Math.abs(dx) > dp(70) && Math.abs(dx) > Math.abs(dy) && touchY < dp(430)) {
                shiftMonth(dx > 0 ? -1 : 1);
                return true;
            }
        }
        return handled;
    }

    private int[] totalsForDay(Calendar day) {
        int[] totals = new int[3];
        for (ReminderRecord record : ReminderRecord.all(this)) {
            if (!sameDay(record.startedAt, day)) continue;
            int index = ReminderRecord.KIND_DONE.equals(record.kind) ? 0
                    : ReminderRecord.KIND_MISSED.equals(record.kind) ? 1
                    : ReminderRecord.KIND_COMPANION.equals(record.kind) || ReminderRecord.KIND_FOCUS.equals(record.kind) ? 2 : -1;
            if (index >= 0) totals[index]++;
        }
        return totals;
    }

    /** 分类颜色（浅底可读的深色；深绿主题用亮色系）。事项自选过颜色时以自选色为准。 */
    private int categoryColor(String action) {
        return UiPalette.categoryInk(recordColorKind(action));
    }

    /** 分类浅色底（对应 iconTint），用于时间标签等胶囊。 */
    private int categoryTint(String action) {
        return UiPalette.categoryFill(recordColorKind(action));
    }

    /** 记录对应的类目色：优先取同名事项的自选颜色，找不到就按名称自动归类。 */
    private int recordColorKind(String action) {
        if (action != null && !action.isEmpty()) {
            for (ReminderSettings.Rule rule : ReminderSettings.rules(this)) {
                if (action.equals(rule.title)) return UiPalette.colorKind(rule.color, rule.title);
            }
        }
        return UiPalette.categoryKind(action);
    }

    private final class TimelineRail extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int color;
        private final boolean last;

        TimelineRail(int color, boolean last) {
            super(StatsActivity.this);
            this.color = color;
            this.last = last;
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float center = getWidth() / 2f;
            paint.setColor(BORDER);
            paint.setStrokeWidth(dp(2));
            canvas.drawLine(center, dp(12), center, last ? dp(12) : getHeight(), paint);
            paint.setColor(color);
            canvas.drawCircle(center, dp(13), dp(5), paint);
        }
    }

    private void addNavigation(LinearLayout screen) {
        LinearLayout nav = BottomNavigationBar.create(this, BottomNavigationBar.STATS,
                () -> { finish(); overridePendingTransition(0, 0); },
                () -> { startActivity(new Intent(this, ItemsActivity.class)); overridePendingTransition(0, 0); finish(); },
                () -> { startActivity(new Intent(this, CompanionLandingActivity.class)); overridePendingTransition(0, 0); finish(); },
                null);
        LinearLayout.LayoutParams navParams = new LinearLayout.LayoutParams(-1, dp(64));
        navParams.leftMargin = dp(14);
        navParams.rightMargin = dp(14);
        navParams.bottomMargin = dp(10);
        screen.addView(nav, navParams);
    }

    private LinearLayout summaryMetric(String label, int value, int accent) {
        LinearLayout metric = new LinearLayout(this);
        metric.setOrientation(LinearLayout.HORIZONTAL);
        metric.setGravity(Gravity.CENTER);
        TextView count = text(String.valueOf(value), 22, accent, true);
        TextView caption = text(label, 13, MUTED, true);
        LinearLayout.LayoutParams captionParams = new LinearLayout.LayoutParams(-2, -2);
        captionParams.leftMargin = dp(6);
        metric.addView(count);
        metric.addView(caption, captionParams);
        return metric;
    }

    private View summaryDivider() {
        View divider = new View(this);
        divider.setBackgroundColor(BORDER);
        return divider;
    }

    private TextView pill(String label, boolean selected) {
        TextView view = text(label, 14, selected ? Color.WHITE : INK, true);
        view.setGravity(Gravity.CENTER);
        view.setClickable(true);
        view.setFocusable(true);
        view.setSelected(selected);
        view.setContentDescription(label + (selected ? "，已选中" : "，筛选记录"));
        if (selected) {
            view.setBackground(round(MINT, 12));
            lift(view);
        } else {
            GradientDrawable shape = round(UiPalette.SOFT, 10);
            view.setBackground(new RippleDrawable(ColorStateList.valueOf(MINT_LIGHT), shape, shape));
        }
        return view;
    }

    private Context dialogContext() {
        return new ContextThemeWrapper(this, R.style.ItemsDialogTheme);
    }

    private void showStyledDialog(AlertDialog dialog) {
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(round(Color.WHITE, 20));
            window.setDimAmount(0.34f);
            window.setLayout(Math.min(getResources().getDisplayMetrics().widthPixels - dp(36), dp(480)),
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        int titleId = getResources().getIdentifier("alertTitle", "id", "android");
        TextView title = titleId == 0 ? null : dialog.findViewById(titleId);
        if (title != null) {
            title.setTextColor(INK);
            title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        TextView message = dialog.findViewById(android.R.id.message);
        if (message != null) message.setTextColor(MUTED);
        for (int buttonId : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE,
                AlertDialog.BUTTON_NEUTRAL}) {
            Button button = dialog.getButton(buttonId);
            if (button == null) continue;
            button.setAllCaps(false);
            button.setTextSize(14);
            button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            button.setTextColor(buttonId == AlertDialog.BUTTON_POSITIVE ? MINT : MUTED);
        }
        if (dialog.getListView() != null) dialog.getListView().setBackgroundColor(UiPalette.SURFACE);
    }

    private boolean sameDay(long time, Calendar day) {
        Calendar value = Calendar.getInstance();
        value.setTimeInMillis(time);
        return value.get(Calendar.YEAR) == day.get(Calendar.YEAR)
                && value.get(Calendar.DAY_OF_YEAR) == day.get(Calendar.DAY_OF_YEAR);
    }

    private boolean sameDay(Calendar left, Calendar right) { return sameDay(left.getTimeInMillis(), right); }
    private String time(long value) { return new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(value)); }
    private String duration(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainder = seconds % 60;
        return hours > 0 ? String.format(Locale.getDefault(), "%d小时%02d分", hours, minutes)
                : minutes > 0 ? String.format(Locale.getDefault(), "%d分%02d秒", minutes, remainder)
                : remainder + "秒";
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return view;
    }
    private GradientDrawable round(int color, int radius) {
        GradientDrawable shape = new GradientDrawable(); shape.setColor(UiPalette.surface(color)); shape.setCornerRadius(dp(radius)); if (color == Color.WHITE) shape.setStroke(dp(1), UiPalette.BORDER); return shape;
    }

    /** 卡片按下时的水波纹反馈，长按记录时能立刻看到响应。 */
    private Drawable pressable(GradientDrawable content) {
        if (Build.VERSION.SDK_INT < 21) return content;
        Drawable mask = content.getConstantState() == null
                ? content : content.getConstantState().newDrawable().mutate();
        return new RippleDrawable(ColorStateList.valueOf((0x2a << 24) | (UiPalette.ACCENT & 0xffffff)),
                content, mask);
    }
    private GradientDrawable outline(int fillColor, int strokeColor, int radius) {
        GradientDrawable shape = round(fillColor, radius);
        shape.setStroke(dp(1), strokeColor);
        return shape;
    }
    /** 头部渐变：浅绿主题为薄荷白，深绿主题为深绿玻璃。 */
    private GradientDrawable headerBackground() {
        return new GradientDrawable(GradientDrawable.Orientation.TL_BR, UiPalette.headGradient());
    }
    /** 白色卡片轻微浮起。 */
    private void lift(View view) {
        if (Build.VERSION.SDK_INT >= 21) view.setElevation(dp(1));
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
