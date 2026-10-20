package io.iftech.healthreminder;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

public final class WellnessWallpaperService extends WallpaperService {
    @Override public Engine onCreateEngine() { return new WellnessEngine(); }

    private final class WellnessEngine extends Engine {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int width;
        private int height;
        private boolean visible;

        @Override public void onSurfaceChanged(SurfaceHolder holder, int format, int w, int h) {
            super.onSurfaceChanged(holder, format, w, h);
            width = w;
            height = h;
            drawWallpaper();
        }

        @Override public void onVisibilityChanged(boolean isVisible) {
            visible = isVisible;
            if (visible) drawWallpaper();
        }

        @Override public void onSurfaceDestroyed(SurfaceHolder holder) {
            visible = false;
            super.onSurfaceDestroyed(holder);
        }

        private void drawWallpaper() {
            if (!visible || width <= 0 || height <= 0) return;
            SurfaceHolder holder = getSurfaceHolder();
            Canvas canvas = null;
            try {
                canvas = holder.lockCanvas();
                if (canvas == null) return;
                canvas.drawColor(0xfff2f8f6);
                float faceRadius = Math.min(width, height) * .19f;
                float cx = width * .5f;
                float cy = height * .39f;
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xffe5f4ec);
                canvas.drawCircle(cx, cy, faceRadius * 1.22f, paint);
                paint.setColor(0xffffdfa8);
                canvas.drawCircle(cx, cy, faceRadius, paint);
                paint.setColor(0xff4b5350);
                canvas.drawCircle(cx - faceRadius * .36f, cy - faceRadius * .27f, faceRadius * .065f, paint);
                canvas.drawCircle(cx + faceRadius * .36f, cy - faceRadius * .27f, faceRadius * .065f, paint);
                paint.setColor(0xffee9c83);
                canvas.drawOval(new RectF(cx - faceRadius * .67f, cy - faceRadius * .02f,
                                cx - faceRadius * .34f, cy + faceRadius * .12f), paint);
                canvas.drawOval(new RectF(cx + faceRadius * .34f, cy - faceRadius * .02f,
                                cx + faceRadius * .67f, cy + faceRadius * .12f), paint);
                paint.setColor(0xff9b584d);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(faceRadius * .055f);
                paint.setStrokeCap(Paint.Cap.ROUND);
                Path mouth = new Path();
                mouth.moveTo(cx - faceRadius * .28f, cy + faceRadius * .42f);
                mouth.quadTo(cx, cy + faceRadius * .14f, cx + faceRadius * .28f, cy + faceRadius * .42f);
                canvas.drawPath(mouth, paint);
            } finally {
                if (canvas != null) holder.unlockCanvasAndPost(canvas);
            }
        }
    }
}
