package io.iftech.healthreminder;

import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 「保活壁纸」动态壁纸服务：设为壁纸后系统更不容易回收本应用。
 * 壁纸内容可在设置页选择——应用内置壁纸，或用户相册里的图片；
 * 用户图片解码、居中裁剪后直接绘制，不再强依赖静态绘制。
 */
public final class WellnessWallpaperService extends WallpaperService {

    static boolean isApplied(Context context) {
        WallpaperInfo info = WallpaperManager.getInstance(context).getWallpaperInfo();
        return info != null && context.getPackageName().equals(info.getPackageName());
    }

    static Intent systemPreviewIntent(Context context) {
        ComponentName component = new ComponentName(context, WellnessWallpaperService.class);
        return new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component);
    }

    @Override public Engine onCreateEngine() {
        return new WellnessEngine();
    }

    private final class WellnessEngine extends Engine {
        private final Handler mainHandler = new Handler(Looper.getMainLooper());
        private final ExecutorService decoder = Executors.newSingleThreadExecutor();
        private final SharedPreferences.OnSharedPreferenceChangeListener prefsListener =
                (preferences, key) -> {
                    if (key != null && key.startsWith("wallpaper_")) {
                        mainHandler.post(this::renderSurface);
                    }
                };
        private Bitmap userBitmap;
        private String loadedUserKey = "";
        private String loadingUserKey = "";
        private int width;
        private int height;
        private boolean surfaceReady;
        private boolean visible;

        @Override public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            ReminderSettings.prefs(context()).registerOnSharedPreferenceChangeListener(prefsListener);
        }

        @Override public void onDestroy() {
            ReminderSettings.prefs(context()).unregisterOnSharedPreferenceChangeListener(prefsListener);
            decoder.shutdownNow();
            super.onDestroy();
        }

        @Override public void onSurfaceCreated(SurfaceHolder holder) {
            super.onSurfaceCreated(holder);
            surfaceReady = true;
        }

        @Override public void onSurfaceChanged(SurfaceHolder holder, int format, int w, int h) {
            super.onSurfaceChanged(holder, format, w, h);
            width = w;
            height = h;
            renderSurface();
        }

        @Override public void onSurfaceRedrawNeeded(SurfaceHolder holder) {
            super.onSurfaceRedrawNeeded(holder);
            renderSurface();
        }

        @Override public void onSurfaceDestroyed(SurfaceHolder holder) {
            surfaceReady = false;
            super.onSurfaceDestroyed(holder);
        }

        @Override public void onVisibilityChanged(boolean isVisible) {
            visible = isVisible;
            if (visible) renderSurface();
        }

        private Context context() {
            return WellnessWallpaperService.this;
        }

        private boolean usingUserImage() {
            return WallpaperCatalog.SOURCE_USER.equals(ReminderSettings.wallpaperSource(context()));
        }

        private void renderSurface() {
            if (!visible || !surfaceReady || width <= 0 || height <= 0) return;
            boolean user = usingUserImage();
            if (user) ensureUserBitmap();
            SurfaceHolder holder = getSurfaceHolder();
            Canvas canvas = null;
            try {
                canvas = holder.lockCanvas();
                if (canvas == null) return;
                if (user && userBitmap != null && !userBitmap.isRecycled()) {
                    WallpaperCatalog.drawBitmapFill(canvas, userBitmap, width, height);
                } else {
                    String builtin = user ? WallpaperCatalog.DEFAULT
                            : ReminderSettings.wallpaperBuiltinId(context());
                    WallpaperCatalog.render(context(), canvas, width, height, builtin);
                }
            } finally {
                if (canvas != null) holder.unlockCanvasAndPost(canvas);
            }
        }

        private void ensureUserBitmap() {
            String uri = ReminderSettings.wallpaperUserUri(context());
            if (uri == null || uri.isEmpty()) return;
            String key = uri + "@" + width + "x" + height;
            if (key.equals(loadedUserKey) || key.equals(loadingUserKey)) return;
            loadingUserKey = key;
            final Context appContext = context();
            final Uri target = Uri.parse(uri);
            decoder.submit(() -> {
                Bitmap bitmap = WallpaperCatalog.decodeUserImage(appContext, target);
                mainHandler.post(() -> {
                    if (!key.equals(loadingUserKey)) return;
                    loadingUserKey = "";
                    loadedUserKey = key;
                    if (userBitmap != null && userBitmap != bitmap) userBitmap.recycle();
                    userBitmap = bitmap;
                    renderSurface();
                });
            });
        }
    }
}
