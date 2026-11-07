package io.iftech.healthreminder;

import android.content.Context;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析 `BodyDataExporter` 导出的 CSV（日期,类型,详情），
 * 用于把另一台设备的身体记录和经期记录搬到本机。
 */
final class BodyDataImporter {
    private static final Pattern DAY = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final String TYPE_NOTE = "身体记录";
    private static final String TYPE_PERIOD = "经期";

    static final class Snapshot {
        final List<Map.Entry<String, String>> notes = new ArrayList<>();
        final List<PeriodLog.Range> periods = new ArrayList<>();
        int skipped;

        boolean empty() {
            return notes.isEmpty() && periods.isEmpty();
        }

        String firstDay() {
            String first = null;
            for (Map.Entry<String, String> note : notes) {
                if (first == null || note.getKey().compareTo(first) < 0) first = note.getKey();
            }
            for (PeriodLog.Range range : periods) {
                if (first == null || range.startKey.compareTo(first) < 0) first = range.startKey;
            }
            return first;
        }

        String lastDay() {
            String last = null;
            for (Map.Entry<String, String> note : notes) {
                if (last == null || note.getKey().compareTo(last) > 0) last = note.getKey();
            }
            for (PeriodLog.Range range : periods) {
                if (last == null || range.startKey.compareTo(last) > 0) last = range.startKey;
            }
            return last;
        }
    }

    private BodyDataImporter() { }

    static Snapshot parse(String raw) {
        Snapshot snapshot = new Snapshot();
        if (raw == null) return snapshot;
        String text = raw.startsWith("\uFEFF") ? raw.substring(1) : raw;
        List<List<String>> rows = parseRows(text);

        Map<String, String> notes = new LinkedHashMap<>();
        Map<String, PeriodLog.Range> periods = new LinkedHashMap<>();
        boolean firstRow = true;
        for (List<String> row : rows) {
            if (row.isEmpty()) continue;
            if (row.size() == 1 && row.get(0).trim().isEmpty()) continue;
            String date = row.size() > 0 ? row.get(0).trim() : "";
            String type = row.size() > 1 ? row.get(1).trim() : "";
            String detail = row.size() > 2 ? row.get(2) : "";
            if (firstRow) {
                firstRow = false;
                if (date.contains("日期") || type.contains("类型")) continue;
            }
            if (!PeriodLog.isValidDay(date)) {
                snapshot.skipped++;
                continue;
            }
            if (TYPE_NOTE.equals(type)) {
                if (detail.trim().isEmpty()) {
                    snapshot.skipped++;
                    continue;
                }
                notes.put(date, detail);
            } else if (TYPE_PERIOD.equals(type)) {
                Matcher matcher = DAY.matcher(detail);
                List<String> days = new ArrayList<>();
                while (matcher.find()) days.add(matcher.group());
                if (days.isEmpty()) {
                    snapshot.skipped++;
                    continue;
                }
                String start = days.get(0);
                String end;
                if (detail.contains("进行中") || detail.contains("至今")) end = "";
                else if (days.size() > 1) end = days.get(1);
                else end = start;
                if (!PeriodLog.isValidDay(start) || (!end.isEmpty() && end.compareTo(start) < 0)) {
                    snapshot.skipped++;
                    continue;
                }
                periods.put(start, new PeriodLog.Range(start, end));
            } else {
                snapshot.skipped++;
            }
        }
        snapshot.notes.addAll(notes.entrySet());
        snapshot.periods.addAll(periods.values());
        return snapshot;
    }

    /** clearFirst=true 会先清空本机身体记录再导入。 */
    static void apply(Context context, Snapshot snapshot, boolean clearFirst) {
        if (clearFirst) {
            DailyBodyNote.clear(context);
            PeriodLog.clear(context);
        }
        for (Map.Entry<String, String> note : snapshot.notes) {
            DailyBodyNote.save(context, note.getKey(), note.getValue());
        }
        for (PeriodLog.Range range : snapshot.periods) {
            PeriodLog.put(context, range.startKey, range.endKey);
        }
    }

    /** RFC4180 简化版：支持引号包裹、引号内逗号/换行、双引号转义。 */
    private static List<List<String>> parseRows(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            if (quoted) {
                if (value == '"') {
                    if (index + 1 < text.length() && text.charAt(index + 1) == '"') {
                        field.append('"');
                        index++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(value);
                }
                continue;
            }
            if (value == '"') {
                quoted = true;
            } else if (value == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (value == '\n') {
                row.add(field.toString());
                field.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else if (value != '\r') {
                field.append(value);
            }
        }
        if (field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }
}
