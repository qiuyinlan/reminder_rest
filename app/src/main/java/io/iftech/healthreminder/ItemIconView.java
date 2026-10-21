package io.iftech.healthreminder;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.widget.TextView;

/**
 * 事项徽标视图：既能显示内置图标包里的 PNG 图标，也能回退成文字（标题首字 / 自定义符号）。
 * 背景、描边仍由调用方通过 setBackground 设置，图片会居中绘制在可用区域内。
 */
final class ItemIconView extends TextView {
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final RectF dest = new RectF();
    private Bitmap icon;

    ItemIconView(Context context) {
        super(context);
        setIncludeFontPadding(false);
    }

    /**
     * 绑定图标。
     *
     * @param value        图标存储值（"img:xxx" 为内置图片，其余按文字处理）
     * @param fallbackText 没有可用图片时显示的文字
     */
    void bindIcon(String value, String fallbackText) {
        Bitmap loaded = ItemIconCatalog.bitmap(getContext(), value);
        icon = loaded;
        setText(loaded == null ? (fallbackText == null ? "" : fallbackText) : "");
        invalidate();
    }

    /** 当前是否显示的是图片图标。 */
    boolean hasImage() {
        return icon != null && !icon.isRecycled();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (icon == null || icon.isRecycled()) return;
        int left = getPaddingLeft();
        int top = getPaddingTop();
        int width = getWidth() - left - getPaddingRight();
        int height = getHeight() - top - getPaddingBottom();
        if (width <= 0 || height <= 0) return;
        float scale = Math.min(width / (float) icon.getWidth(), height / (float) icon.getHeight());
        float drawWidth = icon.getWidth() * scale;
        float drawHeight = icon.getHeight() * scale;
        float drawLeft = left + (width - drawWidth) / 2f;
        float drawTop = top + (height - drawHeight) / 2f;
        dest.set(drawLeft, drawTop, drawLeft + drawWidth, drawTop + drawHeight);
        canvas.drawBitmap(icon, null, dest, paint);
    }
}
