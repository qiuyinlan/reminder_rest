package io.iftech.healthreminder;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

final class AppUpdateManager {
    private static final String PREFS = "app_updates";
    private static final String LAST_CHECK = "last_check_at";
    private static final String PENDING_INSTALL = "pending_install_path";
    static final long CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L;
    private static final int MAX_APK_BYTES = 150 * 1024 * 1024;

    static final class UpdateInfo {
        final int versionCode;
        final String versionName;
        final String apkUrl;
        final String sha256;
        final String notes;

        UpdateInfo(int versionCode, String versionName, String apkUrl, String sha256, String notes) {
            this.versionCode = versionCode;
            this.versionName = versionName;
            this.apkUrl = apkUrl;
            this.sha256 = sha256;
            this.notes = notes;
        }
    }

    private AppUpdateManager() { }

    static UpdateInfo parseManifest(String json) throws JSONException {
        JSONObject object = new JSONObject(json);
        int versionCode = object.optInt("versionCode", -1);
        String versionName = object.optString("versionName", "").trim();
        String apkUrl = object.optString("apkUrl", object.optString("apk_url", "")).trim();
        String sha256 = object.optString("sha256", "").trim().toLowerCase(Locale.ROOT);
        String notes = object.optString("notes", "").trim();
        if (versionCode < 1 || versionName.isEmpty() || !isHttps(apkUrl) || !sha256.matches("[a-f0-9]{64}")) {
            throw new JSONException("Invalid update manifest");
        }
        return new UpdateInfo(versionCode, versionName, apkUrl, sha256, notes);
    }

    static void check(Activity activity, boolean manual) {
        String manifestUrl = BuildConfig.UPDATE_MANIFEST_URL;
        if (!isHttps(manifestUrl)) {
            if (manual) toast(activity, "尚未配置更新清单地址");
            return;
        }
        long now = System.currentTimeMillis();
        android.content.SharedPreferences prefs = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE);
        if (!manual && now - prefs.getLong(LAST_CHECK, 0) < CHECK_INTERVAL_MS) return;
        if (manual) toast(activity, "正在检查更新，请稍候…");
        prefs.edit().putLong(LAST_CHECK, now).apply();
        new Thread(() -> {
            UpdateInfo update = null;
            String failure = null;
            try {
                update = parseManifest(requestText(manifestUrl));
                if (update.versionCode <= BuildConfig.VERSION_CODE) update = null;
            } catch (IOException | JSONException | IllegalStateException e) {
                failure = "检查更新失败，请检查网络后重试";
            }
            UpdateInfo found = update;
            String error = failure;
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                if (found != null) showAvailable(activity, found);
                else if (manual) toast(activity, error == null ? "当前已是最新版本" : error);
            });
        }, "app-update-check").start();
    }

    static void resumePendingInstall(Activity activity) {
        String path = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
                .getString(PENDING_INSTALL, null);
        if (path == null || path.isEmpty()) return;
        if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) return;
        activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE).edit().remove(PENDING_INSTALL).apply();
        install(activity, new File(path));
    }

    private static void showAvailable(Activity activity, UpdateInfo update) {
        String message = "版本 " + update.versionName + " (" + update.versionCode + ")";
        if (!update.notes.isEmpty()) message += "\n\n" + update.notes;
        new AlertDialog.Builder(activity)
                .setTitle("发现新版本")
                .setMessage(message)
                .setNegativeButton("稍后", null)
                .setPositiveButton("下载更新", (dialog, which) -> download(activity, update))
                .show();
    }

    private static void download(Activity activity, UpdateInfo update) {
        ProgressDialog progress = new ProgressDialog(activity);
        progress.setMessage("正在下载并验证更新…");
        progress.setCancelable(false);
        progress.show();
        new Thread(() -> {
            File apk = null;
            String failure = null;
            try {
                File parent = new File(activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "updates");
                if (!parent.exists() && !parent.mkdirs()) throw new IOException("无法创建更新目录");
                File part = new File(parent, "update.apk.part");
                apk = new File(parent, "update.apk");
                String downloadedHash = downloadAndHash(update.apkUrl, part);
                if (!MessageDigest.isEqual(downloadedHash.getBytes(StandardCharsets.US_ASCII),
                        update.sha256.getBytes(StandardCharsets.US_ASCII))) {
                    throw new IOException("更新文件校验失败");
                }
                if (!matchesPackageVersionAndSignature(activity, part, update)) {
                    throw new IOException("安装包版本、包名或签名校验失败");
                }
                if (apk.exists() && !apk.delete()) throw new IOException("无法替换旧更新包");
                if (!part.renameTo(apk)) throw new IOException("无法保存更新包");
            } catch (Exception e) {
                failure = e.getMessage() == null ? "更新下载或校验失败" : e.getMessage();
            }
            File verifiedApk = apk;
            String error = failure;
            activity.runOnUiThread(() -> {
                if (progress.isShowing()) progress.dismiss();
                if (activity.isFinishing() || activity.isDestroyed()) return;
                if (error != null) {
                    toast(activity, error);
                    return;
                }
                activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE).edit()
                        .putString(PENDING_INSTALL, verifiedApk.getAbsolutePath()).apply();
                install(activity, verifiedApk);
            });
        }, "app-update-download").start();
    }

    private static String requestText(String url) throws IOException {
        HttpURLConnection connection = openHttps(url);
        try {
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() + count > 1024 * 1024) throw new IOException("更新清单过大");
                    output.write(buffer, 0, count);
                }
                return output.toString(StandardCharsets.UTF_8.name());
            }
        } finally { connection.disconnect(); }
    }

    private static String downloadAndHash(String url, File destination) throws Exception {
        HttpURLConnection connection = openHttps(url);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        try {
            int length = connection.getContentLength();
            if (length > MAX_APK_BYTES) throw new IOException("更新包超过大小限制");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long total = 0;
            try (InputStream input = new BufferedInputStream(connection.getInputStream());
                 FileOutputStream file = new FileOutputStream(destination);
                 BufferedOutputStream output = new BufferedOutputStream(file)) {
                byte[] buffer = new byte[32 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > MAX_APK_BYTES) throw new IOException("更新包超过大小限制");
                    digest.update(buffer, 0, count);
                    output.write(buffer, 0, count);
                }
            }
            byte[] hash = digest.digest();
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) hex.append(String.format(Locale.ROOT, "%02x", b & 0xff));
            return hex.toString();
        } finally { connection.disconnect(); }
    }

    private static HttpURLConnection openHttps(String value) throws IOException {
        String current = value;
        for (int redirects = 0; redirects <= 5; redirects++) {
            if (!isHttps(current)) throw new IOException("更新地址必须使用 HTTPS");
            HttpURLConnection connection = (HttpURLConnection) new URL(current).openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("Accept", "application/json, application/vnd.android.package-archive, application/octet-stream");
            int status = connection.getResponseCode();
            if (status >= 300 && status < 400) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null || redirects == 5) throw new IOException("更新服务器重定向失败");
                current = new URL(new URL(current), location).toString();
                continue;
            }
            if (status < 200 || status >= 300) {
                connection.disconnect();
                throw new IOException("更新服务器请求失败（" + status + ")");
            }
            return connection;
        }
        throw new IOException("更新服务器重定向过多");
    }

    private static boolean matchesPackageVersionAndSignature(Activity activity, File apk, UpdateInfo expected) throws Exception {
        PackageManager manager = activity.getPackageManager();
        PackageInfo installed;
        PackageInfo candidate;
        if (Build.VERSION.SDK_INT >= 28) {
            installed = manager.getPackageInfo(activity.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
            candidate = manager.getPackageArchiveInfo(apk.getAbsolutePath(), PackageManager.GET_SIGNING_CERTIFICATES);
            if (candidate == null || !activity.getPackageName().equals(candidate.packageName)
                    || candidate.signingInfo == null || installed.signingInfo == null) return false;
            return candidate.getLongVersionCode() == expected.versionCode
                    && expected.versionName.equals(candidate.versionName)
                    && sameSigners(installed.signingInfo.getApkContentsSigners(), candidate.signingInfo.getApkContentsSigners());
        }
        installed = manager.getPackageInfo(activity.getPackageName(), PackageManager.GET_SIGNATURES);
        candidate = manager.getPackageArchiveInfo(apk.getAbsolutePath(), PackageManager.GET_SIGNATURES);
        if (candidate == null || !activity.getPackageName().equals(candidate.packageName)) return false;
        return candidate.versionCode == expected.versionCode
                && expected.versionName.equals(candidate.versionName)
                && sameSigners(installed.signatures, candidate.signatures);
    }

    private static boolean sameSigners(Signature[] installed, Signature[] candidate) throws Exception {
        if (installed == null || candidate == null || installed.length == 0 || installed.length != candidate.length) return false;
        java.util.ArrayList<String> left = new java.util.ArrayList<>();
        java.util.ArrayList<String> right = new java.util.ArrayList<>();
        for (Signature signature : installed) left.add(fingerprint(signature.toByteArray()));
        for (Signature signature : candidate) right.add(fingerprint(signature.toByteArray()));
        java.util.Collections.sort(left);
        java.util.Collections.sort(right);
        return left.equals(right);
    }

    private static String fingerprint(byte[] certificate) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(certificate);
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte b : digest) hex.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return hex.toString();
    }

    private static void install(Activity activity, File apk) {
        try {
        if (!apk.isFile()) {
            toast(activity, "更新包不存在，请重新检查并下载更新");
            return;
        }
        if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(activity, "请允许好好歇会安装此来源的应用，然后返回继续", Toast.LENGTH_LONG).show();
            Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(settings);
            return;
        }
        activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE).edit().remove(PENDING_INSTALL).apply();
        Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", apk);
        Intent intent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(intent);
        } catch (RuntimeException e) {
            toast(activity, "无法打开系统安装页面，请从 GitHub Release 手动下载安装包");
        }
    }

    private static boolean isHttps(String value) { return value != null && value.startsWith("https://"); }

    private static void toast(Activity activity, String message) {
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
    }
}
