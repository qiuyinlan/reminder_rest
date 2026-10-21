package io.iftech.healthreminder;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 保活壁纸选择页：内置壁纸（软件提供）或相册图片任选其一，
 * 再通过系统动态壁纸预览完成设置。壁纸内容只保存在本机偏好中。
 */
public final class WallpaperSettingsActivity extends Activity {
    private static final int REQUEST_PICK_IMAGE = 61;
    private int GREEN = UiPalette.ACCENT;
    private int MINT_LIGHT = UiPalette.SOFT;
    private int MINT_PALE = UiPalette.PALE;
    private int INK = UiPalette.INK;
    private int MUTED = UiPalette.MUTED;
    private int WARN = UiPalette.WARN;
    private int WARN_SOFT = UiPalette.WARN_SOFT;
    private int WARN_BORDER = UiPalette.WARN_BORDER;

    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ScrollView scroll;
    private WallpaperThumbView preview;
    private String previewUriKey;
    private Bitmap previewBitmap;
    private String loadingUriKey = "";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        UiPalette.applyWindow(getWindow());
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        render();
    }

    @Override protected void onDestroy() {
        decoder.shutdownNow();
        super.onDestroy();
    }

    private void render() {
        int savedScroll = scroll == null ? 0 : scroll.getScrollY();
        boolean applied = WellnessWallpaperService.isApplied(this);
        String source = ReminderSettings.wallpaperSource(this);
        String storedBuiltin = ReminderSettings.wallpaperBuiltinId(this);
        String builtinId = WallpaperCatalog.isBuiltIn(storedBuiltin) ? storedBuiltin : WallpaperCatalog.DEFAULT;
        String userUri = ReminderSettings.wallpaperUserUri(this);
        boolean usingUserImage = WallpaperCatalog.SOURCE_USER.equals(source) && userUri != null;

        final ScrollView created = new ScrollView(this);
        created.setFillViewport(true);
        created.setClipToPadding(false);
        created.setBackground(UiPalette.page());
        scroll = created;

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(10), dp(20), dp(30));
        page.setBackground(UiPalette.page());
        created.addView(page, new ScrollView.LayoutParams(-1, -2));
        setContentView(created);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("‹", 29, INK, false);
        back.setGravity(Gravity.CENTER);
        back.setBackground(round(Color.WHITE, 14));
        back.setContentDescription("返回");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(42), dp(42)));
        TextView title = text("保活壁纸", 25, INK, true);
        title.setPadding(dp(12), 0, 0, 0);
        header.addView(title);
        page.addView(header);

        TextView subtitle = text("选一张软件提供的壁纸，或用自己相册里的照片", 13, MUTED, false);
        subtitle.setPadding(dp(54), dp(5), 0, 0);
        page.addView(subtitle);

        LinearLayout intro = new LinearLayout(this);
        intro.setGravity(Gravity.CENTER_VERTICAL);
        intro.setPadding(dp(16), dp(15), dp(16), dp(15));
        intro.setBackground(round(MINT_LIGHT, 18));
        intro.setElevation(dp(1));
        LinearLayout.LayoutParams introParams = new LinearLayout.LayoutParams(-1, -2);
        introParams.topMargin = dp(20);
        page.addView(intro, introParams);

        TextView introIcon = text("壁", 17, GREEN, true);
        introIcon.setGravity(Gravity.CENTER);
        introIcon.setBackground(round(Color.WHITE, 14));
        LinearLayout.LayoutParams introIconParams = new LinearLayout.LayoutParams(dp(44), dp(44));
        introIconParams.rightMargin = dp(13);
        intro.addView(introIcon, introIconParams);

        LinearLayout introCopy = new LinearLayout(this);
        introCopy.setOrientation(LinearLayout.VERTICAL);
        introCopy.addView(text("把好好歇会设为动态壁纸", 16, INK, true));
        TextView introDetail = text("系统更不容易回收壁纸服务；不想用时在系统壁纸设置里换回即可。", 12, MUTED, false);
        introDetail.setPadding(0, dp(5), 0, 0);
        introCopy.addView(introDetail);
        intro.addView(introCopy, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout previewCard = new LinearLayout(this);
        previewCard.setOrientation(LinearLayout.VERTICAL);
        previewCard.setPadding(dp(10), dp(10), dp(10), dp(12));
        previewCard.setBackground(round(Color.WHITE, 20));
        previewCard.setElevation(dp(1));
        LinearLayout.LayoutParams previewCardParams = new LinearLayout.LayoutParams(-1, -2);
        previewCardParams.topMargin = dp(14);
        page.addView(previewCard, previewCardParams);

        preview = new WallpaperThumbView(this);
        preview.setCornerRadius(dp(14));
        previewCard.addView(preview, new LinearLayout.LayoutParams(-1, dp(220)));

        LinearLayout previewCaption = new LinearLayout(this);
        previewCaption.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams captionParams = new LinearLayout.LayoutParams(-1, -2);
        captionParams.topMargin = dp(10);
        previewCard.addView(previewCaption, captionParams);
        String currentLabel = usingUserImage ? "我的图片" : WallpaperCatalog.label(builtinId);
        previewCaption.addView(text("当前壁纸 · " + currentLabel, 13, INK, true),
                new LinearLayout.LayoutParams(0, -2, 1));
        TextView appliedBadge = text(applied ? "已生效" : "未生效", 11, applied ? GREEN : WARN, true);
        appliedBadge.setPadding(dp(9), dp(5), dp(9), dp(5));
        appliedBadge.setBackground(applied ? round(MINT_PALE, 12) : round(WARN_SOFT, 12, WARN_BORDER));
        previewCaption.addView(appliedBadge);

        if (usingUserImage) {
            if (previewBitmap != null && userUri.equals(previewUriKey)) {
                preview.setBitmap(previewBitmap);
            } else {
                decodePreview(userUri);
            }
        } else {
            preview.setBuiltinId(builtinId);
        }

        addSectionHeading(page, "软件提供的壁纸", "点按即可选用；已设为壁纸时桌面会立即切换。");
        WallpaperCatalog.Wallpaper[] builtIns = WallpaperCatalog.builtIns();
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < builtIns.length; i += 2) {
            LinearLayout row = new LinearLayout(this);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            if (i > 0) rowParams.topMargin = dp(6);
            rows.addView(row, rowParams);
            for (int j = i; j < Math.min(i + 2, builtIns.length); j++) {
                row.addView(builtinCell(builtIns[j],
                        !usingUserImage && builtIns[j].id.equals(builtinId)),
                        new LinearLayout.LayoutParams(0, -2, 1));
            }
        }
        page.addView(rows, new LinearLayout.LayoutParams(-1, -2));

        addSectionHeading(page, "我的图片", "从相册挑一张，只保存在本机。");
        LinearLayout userCard = new LinearLayout(this);
        userCard.setGravity(Gravity.CENTER_VERTICAL);
        userCard.setPadding(dp(15), dp(15), dp(14), dp(15));
        userCard.setBackground(round(Color.WHITE, 17));
        userCard.setElevation(dp(1));
        userCard.setContentDescription("从相册选择图片作为壁纸");
        userCard.setOnClickListener(v -> pickUserImage());
        page.addView(userCard, new LinearLayout.LayoutParams(-1, -2));

        TextView userIcon = text("图", 16, GREEN, true);
        userIcon.setGravity(Gravity.CENTER);
        userIcon.setBackground(round(MINT_PALE, 13));
        LinearLayout.LayoutParams userIconParams = new LinearLayout.LayoutParams(dp(46), dp(46));
        userIconParams.rightMargin = dp(13);
        userCard.addView(userIcon, userIconParams);

        LinearLayout userCopy = new LinearLayout(this);
        userCopy.setOrientation(LinearLayout.VERTICAL);
        userCopy.addView(text("我的图片", 16, INK, true));
        String userDetailText;
        if (userUri == null) userDetailText = "还未选择图片";
        else if (usingUserImage) userDetailText = "正在使用 · 点按可更换";
        else userDetailText = "已选择 · 点按可更换";
        TextView userDetail = text(userDetailText, 12, MUTED, false);
        userDetail.setPadding(0, dp(5), 0, 0);
        userCopy.addView(userDetail);
        userCard.addView(userCopy, new LinearLayout.LayoutParams(0, -2, 1));

        TextView choose = text(userUri == null ? "选择" : "更换", 13, GREEN, true);
        choose.setGravity(Gravity.CENTER);
        choose.setPadding(dp(14), dp(8), dp(14), dp(8));
        choose.setBackground(round(MINT_PALE, 16));
        userCard.addView(choose);

        addSectionHeading(page, "设为壁纸", "通过系统预览确认，这一步由系统完成。");
        LinearLayout statusCard = new LinearLayout(this);
        statusCard.setOrientation(LinearLayout.VERTICAL);
        statusCard.setPadding(dp(15), dp(15), dp(15), dp(15));
        statusCard.setBackground(round(Color.WHITE, 17));
        statusCard.setElevation(dp(1));
        page.addView(statusCard, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusCard.addView(statusRow, new LinearLayout.LayoutParams(-1, -2));
        TextView statusMark = text(applied ? "✓" : "!", 14, applied ? GREEN : WARN, true);
        statusMark.setGravity(Gravity.CENTER);
        statusMark.setBackground(applied ? round(MINT_PALE, 30) : round(WARN_SOFT, 30, WARN_BORDER));
        LinearLayout.LayoutParams statusMarkParams = new LinearLayout.LayoutParams(dp(30), dp(30));
        statusMarkParams.rightMargin = dp(11);
        statusRow.addView(statusMark, statusMarkParams);
        LinearLayout statusCopy = new LinearLayout(this);
        statusCopy.setOrientation(LinearLayout.VERTICAL);
        statusCopy.addView(text(applied ? "保活壁纸已生效" : "保活壁纸尚未生效", 14, INK, true));
        TextView statusDetail = text(applied ? "下拉桌面即可看到当前选择" : "打开系统预览完成确认", 12, MUTED, false);
        statusDetail.setPadding(0, dp(4), 0, 0);
        statusCopy.addView(statusDetail);
        statusRow.addView(statusCopy, new LinearLayout.LayoutParams(0, -2, 1));

        TextView open = text(applied ? "重新打开系统壁纸预览" : "打开系统壁纸预览", 15, Color.WHITE, true);
        open.setGravity(Gravity.CENTER);
        open.setPadding(dp(16), dp(13), dp(16), dp(13));
        open.setBackground(round(GREEN, 15));
        open.setContentDescription("打开系统壁纸预览，确认应用保活壁纸");
        open.setOnClickListener(v -> openSystemPreview());
        LinearLayout.LayoutParams openParams = new LinearLayout.LayoutParams(-1, -2);
        openParams.topMargin = dp(13);
        statusCard.addView(open, openParams);

        TextView footnote = text("壁纸来自本应用的动态壁纸服务：可能降低系统回收概率，但不保证应用持续运行。"
                + "部分机型还需要在系统壁纸里把「桌面」也切换到动态壁纸。", 12, MUTED, false);
        footnote.setPadding(0, dp(14), 0, 0);
        page.addView(footnote);

        final int scrollY = savedScroll;
        created.post(() -> created.scrollTo(0, scrollY));
    }

    private LinearLayout builtinCell(WallpaperCatalog.Wallpaper wallpaper, boolean selected) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setPadding(dp(4), dp(4), dp(4), dp(4));
        WallpaperThumbView thumb = new WallpaperThumbView(this);
        thumb.setBuiltinId(wallpaper.id);
        thumb.setChosen(selected);
        cell.addView(thumb, new LinearLayout.LayoutParams(-1, dp(140)));
        TextView label = text(wallpaper.label, 12, selected ? INK : MUTED, selected);
        label.setGravity(Gravity.CENTER);
        label.setPadding(0, dp(6), 0, 0);
        cell.addView(label);
        cell.setContentDescription(wallpaper.label + "壁纸" + (selected ? "，已选择" : ""));
        cell.setOnClickListener(v -> {
            ReminderSettings.setWallpaperBuiltinId(this, wallpaper.id);
            ReminderSettings.setWallpaperSource(this, WallpaperCatalog.SOURCE_BUILTIN);
            String hint = WellnessWallpaperService.isApplied(this) ? "，桌面会同步切换" : "";
            Toast.makeText(this, "已选择「" + wallpaper.label + "」壁纸" + hint, Toast.LENGTH_SHORT).show();
            render();
        });
        return cell;
    }

    private void addSectionHeading(LinearLayout page, String title, String detail) {
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(22);
        params.bottomMargin = dp(9);
        page.addView(heading, params);
        heading.addView(text(title, 15, INK, true));
        TextView caption = text(detail, 12, MUTED, false);
        caption.setPadding(0, dp(3), 0, 0);
        heading.addView(caption);
    }

    private void pickUserImage() {
        Intent open = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("image/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(open, REQUEST_PICK_IMAGE);
        } catch (Exception unavailable) {
            try {
                startActivityForResult(new Intent(Intent.ACTION_PICK,
                        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI), REQUEST_PICK_IMAGE);
            } catch (Exception ignored) {
                Toast.makeText(this, "没有可用的相册应用", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PICK_IMAGE || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception notPersistable) { }
        ReminderSettings.setWallpaperUserUri(this, uri.toString());
        ReminderSettings.setWallpaperSource(this, WallpaperCatalog.SOURCE_USER);
        Toast.makeText(this, WellnessWallpaperService.isApplied(this)
                ? "已换好这张图片，桌面会同步更新" : "已选择这张图片", Toast.LENGTH_SHORT).show();
        render();
    }

    private void openSystemPreview() {
        try {
            startActivity(WellnessWallpaperService.systemPreviewIntent(this));
        } catch (Exception unavailable) {
            try {
                startActivity(new Intent(android.app.WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER));
            } catch (Exception error) {
                Toast.makeText(this, "无法打开系统壁纸设置", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void decodePreview(String uriString) {
        if (uriString.equals(loadingUriKey)) return;
        loadingUriKey = uriString;
        final Uri uri = Uri.parse(uriString);
        decoder.submit(() -> {
            Bitmap bitmap = WallpaperCatalog.decodeUserImage(this, uri);
            mainHandler.post(() -> {
                loadingUriKey = "";
                if (bitmap == null) return;
                previewUriKey = uriString;
                previewBitmap = bitmap;
                if (preview != null && WallpaperCatalog.SOURCE_USER.equals(ReminderSettings.wallpaperSource(this))) {
                    preview.setBitmap(bitmap);
                }
            });
        });
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView result = new TextView(this);
        result.setText(value);
        result.setTextSize(size);
        result.setTextColor(color);
        if (bold) result.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return result;
    }

    private GradientDrawable round(int color, int radius) {
        return round(color, radius, UiPalette.BORDER);
    }

    private GradientDrawable round(int color, int radius, int strokeColor) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(UiPalette.surface(color));
        shape.setCornerRadius(dp(radius));
        shape.setStroke(dp(1), strokeColor);
        return shape;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
