package io.iftech.healthreminder;

import java.util.Calendar;
import java.util.List;

final class ReminderScheduleMath {
    private ReminderScheduleMath() { }

    static long nextTime(List<Integer> timePointsSeconds, long nowMillis, boolean tomorrowOnly) {
        if (timePointsSeconds == null || timePointsSeconds.isEmpty()) return 0L;
        Calendar base = Calendar.getInstance();
        base.setTimeInMillis(nowMillis);
        long nextToday = Long.MAX_VALUE;
        if (!tomorrowOnly) {
            for (int point : timePointsSeconds) {
                if (point < 0 || point >= 24 * 60 * 60) continue;
                Calendar candidate = (Calendar) base.clone();
                candidate.set(Calendar.HOUR_OF_DAY, point / 3600);
                candidate.set(Calendar.MINUTE, point / 60 % 60);
                candidate.set(Calendar.SECOND, point % 60);
                candidate.set(Calendar.MILLISECOND, 0);
                long candidateTime = candidate.getTimeInMillis();
                if (candidateTime > nowMillis) nextToday = Math.min(nextToday, candidateTime);
            }
            if (nextToday != Long.MAX_VALUE) return nextToday;
        }
        int firstPoint = Integer.MAX_VALUE;
        for (int point : timePointsSeconds) {
            if (point >= 0 && point < 24 * 60 * 60) firstPoint = Math.min(firstPoint, point);
        }
        if (firstPoint == Integer.MAX_VALUE) return 0L;
        base.add(Calendar.DAY_OF_YEAR, 1);
        base.set(Calendar.HOUR_OF_DAY, firstPoint / 3600);
        base.set(Calendar.MINUTE, firstPoint / 60 % 60);
        base.set(Calendar.SECOND, firstPoint % 60);
        base.set(Calendar.MILLISECOND, 0);
        return base.getTimeInMillis();
    }
}
