package io.iftech.healthreminder;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

final class MembershipAccess {
    interface Decision { void onResult(boolean member); }

    private MembershipAccess() { }

    static void requirePremium(Activity activity, String reason, Decision decision) {
        MembershipRepository repository = repository(activity);
        new Thread(() -> {
            MembershipRepository.Result result = repository.refreshAccountAndEntitlement();
            boolean active = result.success && result.active;
            new Handler(Looper.getMainLooper()).post(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                if (!active) {
                    Intent intent = new Intent(activity, MembershipActivity.class)
                            .putExtra(MembershipActivity.EXTRA_REASON, reason);
                    if (!result.success) intent.putExtra(MembershipActivity.EXTRA_STATUS, result.message);
                    activity.startActivity(intent);
                }
                decision.onResult(active);
            });
        }, "membership-check").start();
    }

    static MembershipRepository repository(Activity activity) {
        return new MembershipRepository(new MembershipApiClient(BuildConfig.MEMBERSHIP_API_BASE_URL),
                new MembershipCredentialStore(activity.getApplicationContext()));
    }
}
