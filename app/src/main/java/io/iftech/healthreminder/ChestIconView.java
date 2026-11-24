package io.iftech.healthreminder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/**
 * 宝箱图形：不用 emoji，直接画。
 * unlocked = 金色 + 发光；否则灰绿。
 */
final class ChestIconView extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF body = new RectF();
    private final RectF lid = new RectF();
    private final RectF lock = new RectF();
    private boolean unlocked;

    ChestIconView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        fill.setStyle(Paint.Style.FILL);
        glow.setStyle(Paint.Style.FILL);
    }

    void setUnlocked(boolean unlocked) {
        this.unlocked = unlocked;
        invalidate();
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        float pad = w * 0.04f;
        int main = unlocked ? 0xffffc96b : UiPalette.MUTED;
        int dark = unlocked ? 0xffe0a13f : UiPalette.DISABLED;

        // 解锁时外面垫一层淡金光晕
        if (unlocked) {
            glow.setColor(0x40ffc96b);
            RectF halo = new RectF(-pad * 2, -pad * 2, w + pad * 2, h + pad * 2);
            canvas.drawRoundRect(halo, w * 0.28f, w * 0.28f, glow);
        }

        // 箱体
        body.set(w * 0.10f, h * 0.44f, w * 0.90f, h * 0.90f);
        fill.setColor(dark);
        canvas.drawRoundRect(body, w * 0.10f, w * 0.10f, fill);

        // 箱体正面亮面
        RectF face = new RectF(w * 0.14f, h * 0.50f, w * 0.86f, h * 0.86f);
        fill.setColor(main);
        canvas.drawRoundRect(face, w * 0.08f, w * 0.08f, fill);

        // 箱盖
        lid.set(w * 0.06f, h * 0.22f, w * 0.94f, h * 0.48f);
        fill.setColor(main);
        canvas.drawRoundRect(lid, w * 0.12f, w * 0.12f, fill);

        // 盖顶高光
        RectF lidHighlight = new RectF(w * 0.12f, h * 0.26f, w * 0.88f, h * 0.33f);
        fill.setColor(0x66ffffff);
        canvas.drawRoundRect(lidHighlight, w * 0.05f, w * 0.05f, fill);

        // 锁扣
        lock.set(w * 0.43f, h * 0.40f, w * 0.57f, h * 0.62f);
        fill.setColor(0xffffffff);
        canvas.drawRoundRect(lock, w * 0.03f, w * 0.03f, fill);

        // 锁扣上的小孔
        fill.setColor(unlocked ? 0xffe0a13f : UiPalette.DISABLED);
        canvas.drawCircle(w * 0.50f, h * 0.50f, dp(1.2f), fill);

        // 底部阴影线
        fill.setColor(0x22000000);
        RectF shadow = new RectF(w * 0.14f, h * 0.88f, w * 0.86f, h * 0.94f);
        canvas.drawRoundRect(shadow, w * 0.05f, w * 0.05f, fill);
    }
}
