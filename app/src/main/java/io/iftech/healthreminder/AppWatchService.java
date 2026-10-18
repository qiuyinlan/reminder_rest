package io.iftech.healthreminder;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

import java.util.Set;

public final class AppWatchService extends AccessibilityService {
    private String lastBlocked;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        long now = System.currentTimeMillis();
        if (!ReminderSettings.blockEnabled(this)
                || !ReminderSettings.isWithinWindow(this, now)
                || ReminderSettings.prefs(this).getLong("block_bypass_until", 0) > now) return;
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence source = event.getPackageName();
        if (source == null) return;
        String packageName = source.toString();
        if (packageName.equals(getPackageName())) return;
        Set<String> whitelist = ReminderSettings.whitelist(this);
        Set<String> blocked = ReminderSettings.blockPackages(this);
        if (!blocked.contains(packageName) || whitelist.contains(packageName)) {
            lastBlocked = null;
            return;
        }
        if (packageName.equals(lastBlocked)) return;
        lastBlocked = packageName;
        Intent overlay = new Intent(this, BlockActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("blocked_package", packageName);
        try {
            startActivity(overlay);
        } catch (RuntimeException ignored) {
            lastBlocked = null;
        }
    }

    @Override
    public void onInterrupt() { }

}
