package io.iftech.healthreminder;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** 恢复全部数据：选一个备份文件，把本机数据整体换成备份里的。 */
public final class BodyImportActivity extends Activity {
    private static final int REQUEST_PICK = 4620;

    private int GREEN = UiPalette.ACCENT;
    private int MINT_LIGHT = UiPalette.SOFT;
    private int MINT_PALE = UiPalette.PALE;
    private int INK = UiPalette.INK;
    private int MUTED = UiPalette.MUTED;
    private int BORDER = UiPalette.BORDER;
    private int WARN = UiPalette.WARN;

    private String pickedName = "";
    private String error = "";
    private String content;
    private AppBackup.Summary summary;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
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
        heading.addView(text("恢复全部数据", 22, INK, true));
        heading.addView(text("用备份文件还原", 12, MUTED, false));
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

        page.addView(introCard(), new LinearLayout.LayoutParams(-1, -2));

        TextView pick = text(summary == null ? "选择备份文件" : "重新选择文件", 16, Color.WHITE, true);
        pick.setGravity(Gravity.CENTER);
        pick.setBackground(round(GREEN, 15));
        pick.setContentDescription("选择要恢复的备份文件");
        pick.setOnClickListener(v -> pickFile());
        LinearLayout.LayoutParams pickParams = new LinearLayout.LayoutParams(-1, dp(50));
        pickParams.topMargin = dp(16);
        page.addView(pick, pickParams);

        if (!pickedName.isEmpty()) {
            TextView file = text("已选择：" + pickedName, 12, MUTED, false);
            file.setPadding(dp(2), dp(9), dp(2), 0);
            page.addView(file);
        }

        if (!error.isEmpty()) {
            TextView message = text(error, 12, WARN, false);
            message.setLineSpacing(dp(3), 1f);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.topMargin = dp(10);
            page.addView(message, params);
        }

        if (summary != null) {
            page.addView(previewCard(), cardParams());
            TextView restore = text("恢复并覆盖本机数据", 16, Color.WHITE, true);
            restore.setGravity(Gravity.CENTER);
            restore.setBackground(round(WARN, 15));
            restore.setContentDescription("恢复并覆盖本机数据");
            restore.setOnClickListener(v -> confirmRestore());
            LinearLayout.LayoutParams restoreParams = new LinearLayout.LayoutParams(-1, dp(50));
            restoreParams.topMargin = dp(14);
            page.addView(restore, restoreParams);
        }
    }

    private LinearLayout introCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        card.setBackground(round(MINT_LIGHT, 17));
        card.setElevation(dp(1));
        card.addView(text("会用备份里的内容覆盖本机", 15, INK, true));
        TextView detail = text("提醒事项、任务、复盘、喝水、心情、经期、身体记录和设置都会换成备份里的。"
                + "本机现在的这些数据不会保留，建议先导出一份备份。", 12, MUTED, false);
        detail.setLineSpacing(dp(3), 1f);
        detail.setPadding(0, dp(7), 0, 0);
        card.addView(detail);
        return card;
    }

    private LinearLayout previewCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(round(Color.WHITE, 17));
        card.setElevation(dp(1));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(14);
        card.setLayoutParams(params);

        card.addView(text("这个备份里", 13, MUTED, true));
        TextView main = text(summary.prefs + " 组数据", 17, INK, true);
        main.setPadding(0, dp(5), 0, 0);
        card.addView(main);
        if (!summary.createdAt.isEmpty()) {
            TextView when = text("备份时间：" + summary.createdAt, 12, MUTED, false);
            when.setPadding(0, dp(6), 0, 0);
            card.addView(when);
        }
        return card;
    }

    private LinearLayout.LayoutParams cardParams() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    private void pickFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "text/plain", "*/*"});
        startActivityForResult(intent, REQUEST_PICK);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PICK || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        pickedName = displayName(uri);
        error = "";
        summary = null;
        content = null;
        try {
            content = readAll(uri);
            summary = AppBackup.inspect(content);
        } catch (Exception problem) {
            error = "读不了这个文件：" + problem.getMessage();
        }
        render();
    }

    private void confirmRestore() {
        if (content == null) return;
        new AlertDialog.Builder(this)
                .setTitle("覆盖本机数据？")
                .setMessage("本机现在的提醒、任务、复盘、喝水、心情、经期和身体记录都会换成备份里的，不能撤销。")
                .setNegativeButton("取消", null)
                .setPositiveButton("恢复", (dialog, which) -> restore())
                .show();
    }

    private void restore() {
        try {
            int count = AppBackup.restore(this, content);
            Toast.makeText(this, "已恢复 " + count + " 组数据，正在重新打开", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        } catch (Exception problem) {
            error = "恢复失败：" + problem.getMessage();
            render();
        }
    }

    private String displayName(Uri uri) {
        try (android.database.Cursor cursor = getContentResolver()
                .query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (name != null && !name.isEmpty()) return name;
            }
        } catch (Exception ignored) { }
        String path = uri.getLastPathSegment();
        return path == null ? "备份文件" : path;
    }

    private String readAll(Uri uri) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IllegalStateException("打不开文件");
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = in.read(chunk)) > 0) buffer.write(chunk, 0, read);
            return buffer.toString(StandardCharsets.UTF_8.name());
        }
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
