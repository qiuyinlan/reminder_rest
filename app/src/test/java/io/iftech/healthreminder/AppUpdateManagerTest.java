package io.iftech.healthreminder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.json.JSONException;
import org.junit.Test;

public final class AppUpdateManagerTest {
    @Test public void automaticCheckIntervalIsOneDay() {
        assertEquals(24L * 60L * 60L * 1000L, AppUpdateManager.CHECK_INTERVAL_MS);
    }

    @Test public void parsesHttpsUpdateManifest() throws Exception {
        AppUpdateManager.UpdateInfo info = AppUpdateManager.parseManifest(
                "{\"versionCode\":2,\"versionName\":\"0.2.0\","
                        + "\"apkUrl\":\"https://updates.example/app.apk\",\"sha256\":\""
                        + "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\","
                        + "\"notes\":\"Bug fixes\"}");
        assertEquals(2, info.versionCode);
        assertEquals("0.2.0", info.versionName);
        assertEquals("https://updates.example/app.apk", info.apkUrl);
        assertEquals(64, info.sha256.length());
        assertEquals("Bug fixes", info.notes);
    }

    @Test public void acceptsSnakeCaseApkUrl() throws Exception {
        AppUpdateManager.UpdateInfo info = AppUpdateManager.parseManifest(
                "{\"versionCode\":4,\"versionName\":\"1.0\","
                        + "\"apk_url\":\"https://updates.example/app.apk\",\"sha256\":\""
                        + "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\"}");
        assertEquals("https://updates.example/app.apk", info.apkUrl);
    }

    @Test public void rejectsInsecureOrUnverifiableUpdates() {
        String hash = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        assertThrows(JSONException.class, () -> AppUpdateManager.parseManifest(
                "{\"versionCode\":2,\"versionName\":\"0.2\",\"apkUrl\":\"http://updates.example/app.apk\",\"sha256\":\"" + hash + "\"}"));
        assertThrows(JSONException.class, () -> AppUpdateManager.parseManifest(
                "{\"versionCode\":2,\"versionName\":\"0.2\",\"apkUrl\":\"https://updates.example/app.apk\",\"sha256\":\"bad\"}"));
    }
}
