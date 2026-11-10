package io.iftech.healthreminder;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** 从另一台设备导出的 CSV 恢复身体记录与经期记录。 */
public final class BodyImportActivity extends Activity {
    private static final int REQUEST_PICK = 4620;

    private int GREEN = UiPalette.ACCENT;
    private int MINT_LIGHT = UiPalette.SOFT;
    private int MINT_PALE = UiPalette.PALE;
    private int INK = UiPalette.INK;
    private int MUTED = UiPalette.MUTED;
    private int BORDER = UiPalette.BORDER;
    private int WARN = UiPalette.WARN;

    private BodyDataImporter.Snapshot snapshot;
    private String pickedName = "";
    private String error = "";
    private int importedNotes = -1;
    private int importedPeriods = -1;

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
        heading.addView(text("导入身体数据", 22, INK, true));
        heading.addView(text("从另一台设备迁移过来", 12, MUTED, false));
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

        TextView pick = text(snapshot == null ? "选择 CSV 文件" : "重新选择文件", 16, Color.WHITE, true);
        pick.setGravity(Gravity.CENTER);
        pick.setBackground(round(GREEN, 15));
        pick.setContentDescription("选择要导入的 CSV 文件");
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

        if (snapshot != null && !snapshot.empty()) {
            page.addView(previewCard(), cardParams());
            page.addView(actionButtons(), cardParams());
        } else if (snapshot != null) {
            TextView empty = text("没有可导入的记录", 12, MUTED, false);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.topMargin = dp(12);
            page.addView(empty, params);
        }

        if (importedNotes >= 0) {
            LinearLayout done = new LinearLayout(this);
            done.setOrientation(LinearLayout.VERTICAL);
            done.setPadding(dp(16), dp(14), dp(16), dp(14));
            done.setBackground(round(MINT_LIGHT, 17));
            done.addView(text("导入完成", 14, INK, true));
            TextView detail = text("已写入 " + importedNotes + " 条身体记录、"
                    + importedPeriods + " 段经期记录。", 12, MUTED, false);
            detail.setPadding(0, dp(5), 0, 0);
            done.addView(detail);
            page.addView(done, cardParams());
        }
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(12);
        return params;
    }

    private LinearLayout introCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        card.setBackground(round(Color.WHITE, 17));
        card.setElevation(dp(1));
        card.addView(text("迁移说明", 16, INK, true));
        TextView body = text("选择在另一台设备上「导出身体数据」得到的 CSV（列：日期、类型、详情），"
                + "本机会恢复其中的每日身体记录和经期记录。\n同一天的身体记录会被文件里的内容覆盖；"
                + "经期记录按开始日期合并。", 12, MUTED, false);
        body.setLineSpacing(dp(4), 1f);
        body.setPadding(0, dp(8), 0, 0);
        card.addView(body);
        return card;
    }

    private LinearLayout previewCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(round(MINT_LIGHT, 17));
        card.setElevation(dp(1));
        card.addView(text("文件内容", 13, MUTED, true));
        TextView counts = text(snapshot.notes.size() + " 条身体记录 · "
                + snapshot.periods.size() + " 段经期记录", 17, INK, true);
        counts.setPadding(0, dp(5), 0, 0);
        card.addView(counts);
        String first = snapshot.firstDay();
        String last = snapshot.lastDay();
        if (first != null && last != null) {
            TextView range = text("范围：" + first + " 至 " + last, 12, MUTED, false);
            range.setPadding(0, dp(6), 0, 0);
            card.addView(range);
        }
        if (snapshot.skipped > 0) {
            TextView skipped = text("有 " + snapshot.skipped + " 行无法识别，会被跳过。", 12, WARN, false);
            skipped.setPadding(0, dp(6), 0, 0);
            card.addView(skipped);
        }
        return card;
    }

    private LinearLayout actionButtons() {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);

        TextView merge = text("合并导入", 15, Color.WHITE, true);
        merge.setGravity(Gravity.CENTER);
        merge.setBackground(round(GREEN, 14));
        merge.setOnClickListener(v -> apply(false));
        column.addView(merge, new LinearLayout.LayoutParams(-1, dp(48)));

        TextView replace = text("清空本机后导入", 14, WARN, true);
        replace.setGravity(Gravity.CENTER);
        replace.setBackground(outline(Color.WHITE, BORDER, 14));
        replace.setOnClickListener(v -> confirmReplace());
        LinearLayout.LayoutParams replaceParams = new LinearLayout.LayoutParams(-1, dp(46));
        replaceParams.topMargin = dp(9);
        column.addView(replace, replaceParams);

        TextView hint = text("「清空后导入」会删除本机所有身体记录和经期记录，再写入文件内容。",
                12, MUTED, false);
        hint.setLineSpacing(dp(3), 1f);
        hint.setPadding(dp(2), dp(9), dp(2), 0);
        column.addView(hint);
        return column;
    }

    private void confirmReplace() {
        new AlertDialog.Builder(dialogContext())
                .setTitle("清空本机身体数据？")
                .setMessage("本机已有的每日身体记录和经期记录都会被删除，然后用这份文件替换。此操作无法撤销。")
                .setNegativeButton("取消", null)
                .setPositiveButton("清空并导入", (dialog, which) -> apply(true))
                .show();
    }

    private void apply(boolean clearFirst) {
        if (snapshot == null || snapshot.empty()) return;
        BodyDataImporter.apply(this, snapshot, clearFirst);
        importedNotes = snapshot.notes.size();
        importedPeriods = snapshot.periods.size();
        snapshot = null;
        pickedName = "";
        error = "";
        Toast.makeText(this, "导入完成", Toast.LENGTH_SHORT).show();
        render();
    }

    private void pickFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*");
        startActivityForResult(intent, REQUEST_PICK);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PICK || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        pickedName = displayName(uri);
        error = "";
        importedNotes = -1;
        importedPeriods = -1;
        try {
            snapshot = BodyDataImporter.parse(readText(uri));
            if (snapshot.empty()) error = "文件里没有识别到身体记录或经期记录，请确认选的是「导出身体数据」生成的 CSV。";
        } catch (Exception failure) {
            snapshot = null;
            error = "读取失败：" + failure.getMessage();
        }
        render();
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (name != null && !name.isEmpty()) return name;
            }
        } catch (Exception ignored) {
            // 某些文件提供方不支持查询，退回文件名占位。
        }
        return "所选文件";
    }

    private String readText(Uri uri) throws Exception {
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IllegalStateException("无法打开所选文件");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) > 0) output.write(buffer, 0, read);
            return output.toString(StandardCharsets.UTF_8.name());
        }
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

    private GradientDrawable outline(int fillColor, int strokeColor, int radius) {
        GradientDrawable shape = round(fillColor, radius);
        shape.setStroke(dp(1), strokeColor);
        return shape;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
