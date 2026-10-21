package io.iftech.healthreminder;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * 「选择图标」弹窗：顶部按分类切换（默认 / 符号 / 内置图标包 5 个分类），下方是图标网格，
 * 点一下即选用并关闭。返回值沿用 ReminderSettings.Rule.icon 的字符串约定：
 * 空串=默认（取标题首字）、单个符号=文字徽标、"img:图标id"=内置图片图标。
 */
final class ItemIconPicker {
    interface OnIconPicked {
        void onPicked(String value);
    }

    private static final String TAB_DEFAULT = "默认";
    private static final String TAB_SYMBOL = "符号";
    private static final int COLUMNS = 4;
    private static final int CELL_HEIGHT_DP = 84;

    private static final String[] SYMBOLS = {
            "💧", "👁", "🚶", "🧘", "🌿", "⏰", "☕", "🎧",
            "📖", "🌙", "⭐", "🍵", "🌸", "🎯", "💤", "🌞"
    };

    private ItemIconPicker() { }

    /** 单个单元格。 */
    private static final class Cell {
        final String value;
        final String label;
        final String text;

        Cell(String value, String label, String text) {
            this.value = value;
            this.label = label;
            this.text = text;
        }
    }

    /**
     * 构建选择弹窗（未 show）。调用方负责 show 以及自己的弹窗样式。
     *
     * @param context      弹窗上下文（可传主题包装后的 Context）
     * @param currentValue 当前图标值
     * @param onPick       选中回调（拿到新的图标值）
     */
    static AlertDialog build(final Context context, final String currentValue, final OnIconPicked onPick) {
        final String[] tabs = new String[ItemIconCatalog.CATEGORIES.length + 2];
        tabs[0] = TAB_DEFAULT;
        tabs[1] = TAB_SYMBOL;
        System.arraycopy(ItemIconCatalog.CATEGORIES, 0, tabs, 2, ItemIconCatalog.CATEGORIES.length);
        final int[] active = {initialTab(currentValue)};

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(px(context, 10), px(context, 6), px(context, 10), px(context, 2));

        final LinearLayout tabRow = new LinearLayout(context);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        HorizontalScrollView tabScroll = new HorizontalScrollView(context);
        tabScroll.setHorizontalScrollBarEnabled(false);
        tabScroll.setClipToPadding(false);
        tabScroll.addView(tabRow);
        root.addView(tabScroll, new LinearLayout.LayoutParams(-1, -2));

        final FrameLayout content = new FrameLayout(context);
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(-1, -2);
        contentParams.topMargin = px(context, 10);
        root.addView(content, contentParams);

        final AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("选择图标")
                .setView(root)
                .setNegativeButton("取消", null)
                .create();

        final Runnable[] render = new Runnable[1];
        render[0] = () -> {
            tabRow.removeAllViews();
            for (int i = 0; i < tabs.length; i++) {
                final int index = i;
                TextView chip = chip(context, tabs[i], index == active[0]);
                chip.setOnClickListener(v -> {
                    if (active[0] == index) return;
                    active[0] = index;
                    render[0].run();
                });
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
                params.rightMargin = px(context, 6);
                tabRow.addView(chip, params);
            }
            content.removeAllViews();
            List<Cell> cells = cells(active[0]);
            content.addView(grid(context, cells, currentValue, onPick, dialog),
                    new FrameLayout.LayoutParams(-1, -2));
        };
        render[0].run();

        dialog.setOnShowListener(ignored -> styleDialog(context, dialog));
        return dialog;
    }

    /** 图标值对应的说明文字，例如「默认 · 取标题首字」「图标包 · 冥想打坐」「符号 💧」。 */
    static String describe(String value) {
        if (value == null || value.isEmpty()) return "默认 · 取标题首字";
        if (ItemIconCatalog.isImageIcon(value)) {
            String label = ItemIconCatalog.label(value);
            return label.isEmpty() ? "内置图标" : "图标包 · " + label;
        }
        return "符号 " + value;
    }

    private static int initialTab(String currentValue) {
        if (ItemIconCatalog.isImageIcon(currentValue)) {
            ItemIconCatalog.Icon icon = ItemIconCatalog.find(ItemIconCatalog.idOf(currentValue));
            if (icon != null) {
                for (int i = 0; i < ItemIconCatalog.CATEGORIES.length; i++) {
                    if (ItemIconCatalog.CATEGORIES[i].equals(icon.category)) return i + 2;
                }
            }
            return 2;
        }
        return currentValue == null || currentValue.isEmpty() ? 0 : 1;
    }

    private static List<Cell> cells(int tab) {
        ArrayList<Cell> cells = new ArrayList<>();
        if (tab == 0) {
            cells.add(new Cell("", "默认 · 标题首字", "首"));
            return cells;
        }
        if (tab == 1) {
            for (String symbol : SYMBOLS) cells.add(new Cell(symbol, symbol, symbol));
            return cells;
        }
        String category = ItemIconCatalog.CATEGORIES[tab - 2];
        for (ItemIconCatalog.Icon icon : ItemIconCatalog.ofCategory(category)) {
            cells.add(new Cell(ItemIconCatalog.valueFor(icon.id), icon.label, ""));
        }
        return cells;
    }

    private static GridView grid(final Context context, final List<Cell> cells, final String currentValue,
                                final OnIconPicked onPick, final AlertDialog dialog) {
        final GridView grid = new GridView(context);
        grid.setNumColumns(COLUMNS);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setHorizontalSpacing(px(context, 4));
        grid.setVerticalSpacing(px(context, 4));
        grid.setSelector(new ColorDrawable(Color.TRANSPARENT));
        grid.setVerticalScrollBarEnabled(false);
        grid.setClipToPadding(false);
        grid.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return cells.size(); }
            @Override public Object getItem(int position) { return cells.get(position); }
            @Override public long getItemId(int position) { return position; }
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                Cell cell = cells.get(position);
                return cellView(context, cell, sameValue(cell.value, currentValue));
            }
        });
        grid.setOnItemClickListener((parent, view, position, id) -> {
            onPick.onPicked(cells.get(position).value);
            dialog.dismiss();
        });
        int rows = Math.max(1, (cells.size() + COLUMNS - 1) / COLUMNS);
        grid.setLayoutParams(new FrameLayout.LayoutParams(-1, rows * px(context, CELL_HEIGHT_DP)));
        return grid;
    }

    private static View cellView(Context context, Cell cell, boolean selected) {
        LinearLayout view = new LinearLayout(context);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setGravity(Gravity.CENTER_HORIZONTAL);
        view.setPadding(px(context, 4), px(context, 6), px(context, 4), px(context, 4));
        view.setBackground(selected ? selectedBackground(context) : plainBackground(context));

        ItemIconView badge = new ItemIconView(context);
        badge.setTextSize(21);
        badge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        badge.setTextColor(selected ? UiPalette.ACCENT : UiPalette.INK);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(px(context, 6), px(context, 6), px(context, 6), px(context, 6));
        badge.bindIcon(cell.value, cell.text);
        view.addView(badge, new LinearLayout.LayoutParams(px(context, 46), px(context, 46)));

        TextView label = new TextView(context);
        label.setText(cell.label);
        label.setTextSize(11);
        label.setTextColor(selected ? UiPalette.ACCENT : UiPalette.MUTED);
        label.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        label.setMaxLines(2);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER);
        label.setLineSpacing(0f, 0.95f);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, -2);
        labelParams.topMargin = px(context, 3);
        view.addView(label, labelParams);
        return view;
    }

    private static TextView chip(Context context, String text, boolean selected) {
        TextView chip = new TextView(context);
        chip.setText(text);
        chip.setTextSize(12);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(px(context, 13), px(context, 8), px(context, 13), px(context, 8));
        chip.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        chip.setTextColor(selected ? UiPalette.ACCENT : UiPalette.MUTED);
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(px(context, 13));
        background.setColor(selected ? UiPalette.SOFT : Color.TRANSPARENT);
        if (selected) background.setStroke(px(context, 1), UiPalette.BORDER);
        chip.setBackground(background);
        return chip;
    }

    private static GradientDrawable plainBackground(Context context) {
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(px(context, 13));
        background.setColor(Color.TRANSPARENT);
        return background;
    }

    private static GradientDrawable selectedBackground(Context context) {
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(px(context, 13));
        background.setColor(UiPalette.SOFT);
        background.setStroke(px(context, 1), UiPalette.ACCENT);
        return background;
    }

    private static boolean sameValue(String first, String second) {
        String a = first == null ? "" : first;
        String b = second == null ? "" : second;
        return a.equals(b);
    }

    private static void styleDialog(Context context, AlertDialog dialog) {
        Window window = dialog.getWindow();
        if (window != null) {
            GradientDrawable background = new GradientDrawable();
            background.setCornerRadius(px(context, 20));
            background.setColor(UiPalette.SURFACE);
            window.setBackgroundDrawable(background);
            window.setDimAmount(0.34f);
            int width = Math.min(context.getResources().getDisplayMetrics().widthPixels - px(context, 36),
                    px(context, 480));
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        int titleId = context.getResources().getIdentifier("alertTitle", "id", "android");
        TextView title = titleId == 0 ? null : dialog.findViewById(titleId);
        if (title != null) {
            title.setTextColor(UiPalette.INK);
            title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        android.widget.Button cancel = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (cancel != null) {
            cancel.setAllCaps(false);
            cancel.setTextSize(14);
            cancel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            cancel.setTextColor(UiPalette.MUTED);
        }
    }

    private static int px(Context context, int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }
}
