package io.iftech.healthreminder;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/**
 * 壁纸缩略/预览视图：
 * - 内置壁纸直接用与桌面相同的绘制逻辑，缩略图与最终效果一致；
 * - 用户图片由调用方异步解码后传入位图，本视图只负责居中裁剪绘制。
 */
final class WallpaperThumbView extends View {
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint checkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private final Path clipPath = new Path();
    private String builtinId = WallpaperCatalog.DEFAULT;
    private Bitmap bitmap;
    private boolean selected;
    private float cornerRadius = -1f;

    WallpaperThumbView(Context context) {
        super(context);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(dp(2.5f));
        checkPaint.setColor(0xffffffff);
        checkPaint.setTextAlign(Paint.Align.CENTER);
        checkPaint.setTextSize(dp(11));
    }

    void setBuiltinId(String id) {
        this.builtinId = WallpaperCatalog.isBuiltIn(id) ? id : WallpaperCatalog.DEFAULT;
        invalidate();
    }

    void setBitmap(Bitmap value) {
        this.bitmap = value;
        invalidate();
    }

    void setChosen(boolean value) {
        this.selected = value;
        invalidate();
    }

    void setCornerRadius(float radiusPx) {
        this.cornerRadius = radiusPx;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        if (width <= 0 || height <= 0) return;
        float radius = cornerRadius >= 0 ? cornerRadius : dp(16);
        bounds.set(0, 0, width, height);
        clipPath.rewind();
        clipPath.addRoundRect(bounds, radius, radius, Path.Direction.CW);
        int save = canvas.save();
        canvas.clipPath(clipPath);
        if (bitmap != null && !bitmap.isRecycled()) {
            WallpaperCatalog.drawBitmapFill(canvas, bitmap, (int) width, (int) height);
        } else {
            WallpaperCatalog.render(getContext(), canvas, (int) width, (int) height, builtinId);
        }
        canvas.restoreToCount(save);
        if (!selected) return;
        strokePaint.setColor(UiPalette.ACCENT);
        float inset = strokePaint.getStrokeWidth() / 2f;
        canvas.drawRoundRect(new RectF(inset, inset, width - inset, height - inset),
                radius, radius, strokePaint);
        float cx = width - dp(16);
        float cy = dp(16);
        badgePaint.setColor(UiPalette.ACCENT);
        canvas.drawCircle(cx, cy, dp(10), badgePaint);
        Paint.FontMetrics metrics = checkPaint.getFontMetrics();
        canvas.drawText("✓", cx, cy - (metrics.ascent + metrics.descent) / 2f, checkPaint);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
