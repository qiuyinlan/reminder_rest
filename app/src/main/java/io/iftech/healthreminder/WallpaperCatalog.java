package io.iftech.healthreminder;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.net.Uri;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 保活壁纸图库：
 * - 内置壁纸由 Canvas 代码按比例绘制，同一套绘制逻辑同时用于桌面壁纸与设置页缩略图；
 * - 用户图片通过系统文件选择器（SAF）授权读取，在本机解码后居中裁剪铺满。
 */
final class WallpaperCatalog {
    static final String SOURCE_BUILTIN = "builtin";
    static final String SOURCE_USER = "user";
    static final String DEFAULT = "default";

    /** 用户图片最多解码到约 300 万像素，避免大照片在壁纸进程里占满内存。 */
    private static final long MAX_USER_IMAGE_PIXELS = 3_000_000L;

    static final class Wallpaper {
        final String id;
        final String label;

        Wallpaper(String id, String label) {
            this.id = id;
            this.label = label;
        }
    }

    private static final Wallpaper[] BUILT_INS = {
            new Wallpaper(DEFAULT, "默认表情"),
            new Wallpaper("morning", "晨光"),
            new Wallpaper("dusk", "暮色"),
            new Wallpaper("starry", "星夜"),
            new Wallpaper("mascot", "新芽"),
            new Wallpaper("reading", "阅读")
    };

    /** 内置壁纸里的角色插画只解码一次，缩略图与桌面共用。 */
    private static final Map<Integer, WeakReference<Drawable>> ART_CACHE = new HashMap<>();

    private WallpaperCatalog() { }

    static Wallpaper[] builtIns() {
        return BUILT_INS.clone();
    }

    static boolean isBuiltIn(String id) {
        return find(id) != null;
    }

    static String label(String id) {
        Wallpaper found = find(id);
        return found == null ? BUILT_INS[0].label : found.label;
    }

    private static Wallpaper find(String id) {
        if (id == null) return null;
        for (Wallpaper wallpaper : BUILT_INS) if (wallpaper.id.equals(id)) return wallpaper;
        return null;
    }

    /** 在任意尺寸的画布上绘制一张内置壁纸（width/height 为像素）。 */
    static void render(Context context, Canvas canvas, int width, int height, String id) {
        if (canvas == null || width <= 0 || height <= 0) return;
        String safe = find(id) == null ? DEFAULT : id;
        switch (safe) {
            case "morning":
                drawMorning(canvas, width, height);
                break;
            case "dusk":
                drawDusk(canvas, width, height);
                break;
            case "starry":
                drawStarry(canvas, width, height);
                break;
            case "mascot":
                drawArtScene(context, canvas, width, height, R.drawable.home_mascot,
                        0xffeefaf3, 0xffd7f2e6);
                break;
            case "reading":
                drawArtScene(context, canvas, width, height, R.drawable.companion_reading,
                        0xfff4fbf8, 0xffe0f1f4);
                break;
            default:
                drawDefault(canvas, width, height);
                break;
        }
    }

    private static void drawDefault(Canvas canvas, int width, int height) {
        fillGradient(canvas, width, height, 0xffeffaf4, 0xffd9f3e7);
        drawFace(canvas, width * 0.5f, height * 0.42f, Math.min(width, height) * 0.19f,
                new Paint(Paint.ANTI_ALIAS_FLAG));
    }

    private static void drawMorning(Canvas canvas, int width, int height) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new LinearGradient(0, 0, width * 0.4f, height,
                new int[]{0xfffbfcf2, 0xfffdf6e6, 0xfff2f8ee}, null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, paint);
        paint.setShader(null);
        drawSun(canvas, width * 0.8f, height * 0.16f, Math.min(width, height) * 0.13f,
                0xfffff1cd, paint);
        drawFace(canvas, width * 0.5f, height * 0.6f, Math.min(width, height) * 0.1f, paint);
    }

    private static void drawDusk(Canvas canvas, int width, int height) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new LinearGradient(0, 0, width * 0.35f, height,
                new int[]{0xfffce9dc, 0xfff3d9e4, 0xffdccbea}, null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, paint);
        paint.setShader(null);
        drawSun(canvas, width * 0.5f, height * 0.62f, Math.min(width, height) * 0.15f,
                0xffffd9a8, paint);
        drawHills(canvas, width, height, 0x807a6a9c, paint);
    }

    private static void drawStarry(Canvas canvas, int width, int height) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new LinearGradient(0, 0, width * 0.2f, height,
                new int[]{0xff163a2b, 0xff0a2518, 0xff03110b}, null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, paint);
        paint.setShader(null);
        Random random = new Random(11);
        float scale = Math.min(width, height) / 720f;
        for (int i = 0; i < 80; i++) {
            float x = random.nextFloat() * width;
            float y = random.nextFloat() * height * 0.82f;
            float radius = Math.max(0.8f, (0.6f + random.nextFloat() * 2.2f) * scale);
            int alpha = 0x30 + random.nextInt(0xc0);
            paint.setColor((alpha << 24) | 0xf2f8f3);
            canvas.drawCircle(x, y, radius, paint);
        }
        drawMoon(canvas, width * 0.78f, height * 0.15f, Math.min(width, height) * 0.08f, paint);
        drawHills(canvas, width, height, 0xb003110b, paint);
    }

    private static void drawArtScene(Context context, Canvas canvas, int width, int height,
                                     int drawableId, int fromColor, int toColor) {
        fillGradient(canvas, width, height, fromColor, toColor);
        Drawable art = artDrawable(context, drawableId);
        if (art == null) return;
        int intrinsicWidth = Math.max(1, art.getIntrinsicWidth());
        int intrinsicHeight = Math.max(1, art.getIntrinsicHeight());
        float scale = Math.min(width * 0.66f / intrinsicWidth, height * 0.62f / intrinsicHeight);
        float drawWidth = intrinsicWidth * scale;
        float drawHeight = intrinsicHeight * scale;
        float left = (width - drawWidth) / 2f;
        float top = height * 0.46f - drawHeight / 2f;
        art.setBounds(Math.round(left), Math.round(top),
                Math.round(left + drawWidth), Math.round(top + drawHeight));
        art.draw(canvas);
    }

    private static Drawable artDrawable(Context context, int drawableId) {
        WeakReference<Drawable> cached = ART_CACHE.get(drawableId);
        Drawable drawable = cached == null ? null : cached.get();
        if (drawable == null) {
            drawable = androidx.core.content.ContextCompat.getDrawable(context, drawableId);
            if (drawable != null) ART_CACHE.put(drawableId, new WeakReference<>(drawable));
        }
        return drawable;
    }

    private static void fillGradient(Canvas canvas, int width, int height, int from, int to) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new LinearGradient(0, 0, width, height, from, to, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, paint);
    }

    private static void drawSun(Canvas canvas, float cx, float cy, float radius, int color,
                                Paint paint) {
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new RadialGradient(cx, cy, radius * 2.2f,
                new int[]{color, color & 0x00ffffff}, null, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, radius * 2.2f, paint);
        paint.setShader(null);
        paint.setColor(color);
        canvas.drawCircle(cx, cy, radius, paint);
    }

    private static void drawMoon(Canvas canvas, float cx, float cy, float radius, Paint paint) {
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new RadialGradient(cx, cy, radius * 2.4f,
                new int[]{0x66e9f7ef, 0x00e9f7ef}, null, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, radius * 2.4f, paint);
        paint.setShader(null);
        paint.setColor(0xffe9f7ef);
        canvas.drawCircle(cx, cy, radius, paint);
        paint.setColor(0x33a8cbb8);
        canvas.drawCircle(cx - radius * 0.3f, cy - radius * 0.35f, radius * 0.16f, paint);
        canvas.drawCircle(cx + radius * 0.25f, cy + radius * 0.2f, radius * 0.22f, paint);
    }

    private static void drawHills(Canvas canvas, int width, int height, int color, Paint paint) {
        Path path = new Path();
        float base = height * 0.86f;
        path.moveTo(0, height);
        path.lineTo(0, base);
        path.quadTo(width * 0.25f, base - height * 0.09f, width * 0.5f, base - height * 0.01f);
        path.quadTo(width * 0.75f, base - height * 0.12f, width, base - height * 0.02f);
        path.lineTo(width, height);
        path.close();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        canvas.drawPath(path, paint);
    }

    private static void drawFace(Canvas canvas, float cx, float cy, float radius, Paint paint) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xffe5f4ec);
        canvas.drawCircle(cx, cy, radius * 1.22f, paint);
        paint.setColor(0xffffdfa8);
        canvas.drawCircle(cx, cy, radius, paint);
        paint.setColor(0xff4b5350);
        canvas.drawCircle(cx - radius * .36f, cy - radius * .27f, radius * .065f, paint);
        canvas.drawCircle(cx + radius * .36f, cy - radius * .27f, radius * .065f, paint);
        paint.setColor(0xffee9c83);
        canvas.drawOval(new RectF(cx - radius * .67f, cy - radius * .02f,
                cx - radius * .34f, cy + radius * .12f), paint);
        canvas.drawOval(new RectF(cx + radius * .34f, cy - radius * .02f,
                cx + radius * .67f, cy + radius * .12f), paint);
        paint.setColor(0xff9b584d);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(radius * .055f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        Path mouth = new Path();
        mouth.moveTo(cx - radius * .28f, cy + radius * .42f);
        mouth.quadTo(cx, cy + radius * .14f, cx + radius * .28f, cy + radius * .42f);
        canvas.drawPath(mouth, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeCap(Paint.Cap.BUTT);
    }

    /** 居中裁剪铺满画布，保证缩略图与桌面都不留黑边。 */
    static void drawBitmapFill(Canvas canvas, Bitmap bitmap, int width, int height) {
        if (canvas == null || bitmap == null || bitmap.isRecycled() || width <= 0 || height <= 0) {
            return;
        }
        float scale = Math.max(width / (float) bitmap.getWidth(),
                height / (float) bitmap.getHeight());
        float drawWidth = bitmap.getWidth() * scale;
        float drawHeight = bitmap.getHeight() * scale;
        float left = (width - drawWidth) / 2f;
        float top = (height - drawHeight) / 2f;
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(bitmap, null, new RectF(left, top, left + drawWidth, top + drawHeight),
                paint);
    }

    /** 按需缩小解码用户图片；内容 URI 读取失败（授权被撤销等）时返回 null。 */
    static Bitmap decodeUserImage(Context context, Uri uri) {
        if (context == null || uri == null) return null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream stream = context.getContentResolver().openInputStream(uri)) {
                if (stream == null) return null;
                BitmapFactory.decodeStream(stream, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            int sample = 1;
            while ((long) (bounds.outWidth / (sample * 2))
                    * (bounds.outHeight / (sample * 2)) > MAX_USER_IMAGE_PIXELS) {
                sample *= 2;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            try (InputStream stream = context.getContentResolver().openInputStream(uri)) {
                return stream == null ? null : BitmapFactory.decodeStream(stream, null, options);
            }
        } catch (Exception error) {
            return null;
        }
    }
}
