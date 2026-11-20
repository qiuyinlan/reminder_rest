package io.iftech.healthreminder;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** 全部数据备份：把 App 里的所有记录和设置打包成一个 JSON 文件。 */
public final class BodyExportActivity extends Activity {
    private static final int REQUEST_EXPORT = 4610;

    private int GREEN = UiPalette.ACCENT;
    private int MINT_LIGHT = UiPalette.SOFT;
    private int MINT_PALE = UiPalette.PALE;
    private int INK = UiPalette.INK;
    private int MUTED = UiPalette.MUTED;
    private int BORDER = UiPalette.BORDER;

    private String pendingJson;
    private String pendingName;

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
        heading.addView(text("全部数据备份", 22, INK, true));
        heading.addView(text("一个文件带走所有记录", 12, MUTED, false));
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

        page.addView(whatCard(), new LinearLayout.LayoutParams(-1, -2));

        TextView export = text("导出备份文件", 16, Color.WHITE, true);
        export.setGravity(Gravity.CENTER);
        export.setBackground(round(GREEN, 15));
        export.setContentDescription("导出全部数据备份文件");
        export.setOnClickListener(v -> export());
        LinearLayout.LayoutParams exportParams = new LinearLayout.LayoutParams(-1, dp(50));
        exportParams.topMargin = dp(18);
        page.addView(export, exportParams);

        TextView note = text("文件存到哪儿由系统的文件选择器决定，不会上传到任何服务器。"
                        + "恢复时用同一份文件，会把本机数据全部换成备份里的内容。",
                12, MUTED, false);
        note.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(-1, -2);
        noteParams.topMargin = dp(12);
        page.addView(note, noteParams);
    }

    private LinearLayout whatCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        card.setBackground(round(Color.WHITE, 17));
        card.setElevation(dp(1));

        card.addView(text("备份包含", 16, INK, true));
        String[][] items = {
                {"提醒事项与设置", "含息屏、白名单、提醒开关"},
                {"任务与子任务", "含分类、完成记录"},
                {"复盘", "每天的每一条，带时间"},
                {"喝水", "每天每次喝的量"},
                {"心情与经期", "记录和统计"},
                {"身体记录", "每天那句身体情况"},
                {"白板、想法、专注记录", "现在页和悬浮窗里的内容"}};
        for (String[] item : items) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(8), 0, 0);
            TextView name = text(item[0], 14, INK, true);
            row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            TextView detail = text(item[1], 11, MUTED, false);
            row.addView(detail);
            card.addView(row);
        }

        TextView count = text("共 " + AppBackup.prefCount(this) + " 组数据", 12, MUTED, false);
        count.setPadding(0, dp(12), 0, 0);
        card.addView(count);
        return card;
    }

    private void export() {
        try {
            pendingJson = AppBackup.exportJson(this);
            pendingName = AppBackup.fileName();
        } catch (Exception error) {
            Toast.makeText(this, "打包失败：" + error.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json")
                .putExtra(Intent.EXTRA_TITLE, pendingName);
        startActivityForResult(intent, REQUEST_EXPORT);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_EXPORT || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null || pendingJson == null) return;
        try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
            if (out == null) throw new IllegalStateException("无法写入所选文件");
            out.write(pendingJson.getBytes(StandardCharsets.UTF_8));
            out.flush();
            Toast.makeText(this, "已导出 " + pendingName, Toast.LENGTH_LONG).show();
        } catch (Exception error) {
            Toast.makeText(this, "导出失败：" + error.getMessage(), Toast.LENGTH_LONG).show();
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
