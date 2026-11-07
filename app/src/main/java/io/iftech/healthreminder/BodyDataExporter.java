package io.iftech.healthreminder;

import android.content.Context;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** 把每日身体记录 + 经期记录整理成 CSV，供设置页导出。 */
final class BodyDataExporter {
    static final class Entry {
        final String date;
        final String type;
        final String detail;

        Entry(String date, String type, String detail) {
            this.date = date;
            this.type = type;
            this.detail = detail;
        }
    }

    static final class Snapshot {
        final List<Entry> entries;
        final int notes;
        final int periods;

        Snapshot(List<Entry> entries, int notes, int periods) {
            this.entries = entries;
            this.notes = notes;
            this.periods = periods;
        }

        boolean empty() {
            return entries.isEmpty();
        }
    }

    private BodyDataExporter() { }

    /** startKey / endKey 传 null 表示不限。 */
    static Snapshot collect(Context context, String startKey, String endKey) {
        List<Entry> entries = new ArrayList<>();
        int notes = 0;
        for (Map.Entry<String, String> note : DailyBodyNote.all(context)) {
            String date = note.getKey();
            if (startKey != null && date.compareTo(startKey) < 0) continue;
            if (endKey != null && date.compareTo(endKey) > 0) continue;
            entries.add(new Entry(date, "身体记录", note.getValue()));
            notes++;
        }

        int periods = 0;
        for (PeriodLog.Range range : PeriodLog.all(context)) {
            String rangeEnd = range.effectiveEndKey();
            if (startKey != null && rangeEnd.compareTo(startKey) < 0) continue;
            if (endKey != null && range.startKey.compareTo(endKey) > 0) continue;
            String detail = range.ongoing()
                    ? "开始 " + range.startKey + "，进行中（第 " + PeriodLog.days(context, range) + " 天）"
                    : "开始 " + range.startKey + "，结束 " + range.endKey
                    + "（" + PeriodLog.days(context, range) + " 天）";
            entries.add(new Entry(range.startKey, "经期", detail));
            periods++;
        }

        entries.sort(Comparator.comparing((Entry entry) -> entry.date)
                .thenComparing(entry -> entry.type));
        return new Snapshot(entries, notes, periods);
    }

    static String csv(Snapshot snapshot) {
        StringBuilder out = new StringBuilder();
        out.append("日期,类型,详情\r\n");
        for (Entry entry : snapshot.entries) {
            out.append(escape(entry.date)).append(',')
                    .append(escape(entry.type)).append(',')
                    .append(escape(entry.detail)).append("\r\n");
        }
        return out.toString();
    }

    static String fileName(String mode, String monthKey, String startKey, String endKey) {
        String today = PeriodLog.todayKey();
        if ("month".equals(mode) && monthKey != null) return "身体数据_" + monthKey + ".csv";
        if ("range".equals(mode) && startKey != null && endKey != null) {
            return "身体数据_" + startKey + "_" + endKey + ".csv";
        }
        return "身体数据_全部_" + today + ".csv";
    }

    private static String escape(String value) {
        String text = value == null ? "" : value.replace("\r\n", "\n").replace('\r', '\n');
        boolean needsQuotes = text.indexOf(',') >= 0 || text.indexOf('"') >= 0
                || text.indexOf('\n') >= 0 || text.indexOf(';') >= 0;
        if (!needsQuotes) return text;
        return '"' + text.replace("\"", "\"\"") + '"';
    }
}
