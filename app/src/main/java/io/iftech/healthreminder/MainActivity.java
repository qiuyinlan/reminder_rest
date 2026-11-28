package io.iftech.healthreminder;

import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Service;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.ComponentName;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.accessibility.AccessibilityManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.app.NotificationManagerCompat;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class MainActivity extends androidx.activity.ComponentActivity {
    private int GREEN = UiPalette.ACCENT;
    private int INK = UiPalette.INK;
    private int MUTED = UiPalette.MUTED;
    private int PAPER = UiPalette.PAGE;
    private int MINT_LIGHT = UiPalette.SOFT;
    private int MINT_PALE = UiPalette.PALE;
    private int LINE = UiPalette.BORDER;
    private LinearLayout body;
    private ScrollView scroll;
    private boolean reopenPermissionGuide;
    private TextView completedMetric;
    private ProgressBar todayProgress;
    /** 首页游戏区只播一次波浪入场，回页面不重复闹。 */
    private boolean gameZoneAnimated;
    private static final String ONBOARDING_PREFS = "welcome_letter";
    private static final String KEY_WELCOME_SEEN = "seen_v1";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        boolean newItem = getIntent().getBooleanExtra("new_item", false);
        int editId = getIntent().getIntExtra("edit_id", -1);
        if (newItem || editId >= 0) {
            Intent editor = new Intent(this, ItemsActivity.class).putExtra("return_to_home", true);
            if (newItem) editor.putExtra("new_item", true);
            if (editId >= 0) editor.putExtra("edit_id", editId);
            startActivity(editor);
            finish();
            return;
        }
        UiPalette.applyWindow(getWindow());
        ReminderScheduler.update(this);
        if (getSharedPreferences(ONBOARDING_PREFS, MODE_PRIVATE).getBoolean(KEY_WELCOME_SEEN, false)) {
            enterHome();
        } else {
            showWelcomeLetter();
        }
        CrashLog.promptIfAny(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        AppUpdateManager.resumePendingInstall(this);
        if (body != null) {
            ReminderScheduler.reconcileAfterScreenOn(this);
            render();
            if (reopenPermissionGuide) {
                reopenPermissionGuide = false;
                showPermissionChecklist();
            }
        }
    }

    private void render() {
        int previousScrollY = scroll == null ? 0 : scroll.getScrollY();
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackground(UiPalette.page());
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackground(UiPalette.page());
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(14), dp(20), dp(24));
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        screen.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(screen);
        ScrollView currentScroll = scroll;
        currentScroll.post(() -> currentScroll.scrollTo(0, previousScrollY));

        DailyQuest.ensureDay(this);
        TextView date = label(new java.text.SimpleDateFormat("M\u6708d\u65e5  E",
                java.util.Locale.CHINA).format(Calendar.getInstance().getTime()), 11, MUTED, false);
        date.setLetterSpacing(0.14f);
        date.setPadding(dp(2), 0, 0, dp(4));
        body.addView(date);
        LinearLayout top = row();
        boolean dayAllDone = DailyQuest.allQuestsDone(this) && DailyQuest.todayActions(this) > 0;
        TextView brand = label(dayAllDone ? "休息一下" : "今天", 26, INK, true);
        brand.setLetterSpacing(0.01f);
        top.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        TextView moodButton = label("心情", 12, INK, true);
        moodButton.setGravity(Gravity.CENTER);
        moodButton.setLetterSpacing(0.06f);
        moodButton.setPadding(dp(12), dp(7), dp(12), dp(7));
        moodButton.setBackground(outline(UiPalette.SOFT, UiPalette.BORDER, 18));
        moodButton.setElevation(dp(1));
        moodButton.setContentDescription("记一下现在的心情");
        moodButton.setOnClickListener(v -> MoodDialogs.showToday(this, this::render));
        LinearLayout.LayoutParams moodParams = new LinearLayout.LayoutParams(-2, -2);
        moodParams.rightMargin = dp(8);
        top.addView(moodButton, moodParams);
        TextView settingsButton = label("设置", 12, INK, true);
        settingsButton.setGravity(Gravity.CENTER);
        settingsButton.setLetterSpacing(0.06f);
        settingsButton.setPadding(dp(12), dp(7), dp(12), dp(7));
        settingsButton.setBackground(outline(UiPalette.SOFT, UiPalette.BORDER, 18));
        settingsButton.setElevation(dp(1));
        settingsButton.setContentDescription("设置");
        settingsButton.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        top.addView(settingsButton);
        body.addView(top);

        // 游戏化激励卡片：与预览页同一套 Compose 布局
        if (GameSettings.isEnabled(this)) {
            android.view.View questView = DewQuestHost.create(this,
                    () -> startActivity(new Intent(this, TaskActivity.class)),
                    this::render);
            LinearLayout.LayoutParams questParams = full();
            questParams.topMargin = dp(10);
            body.addView(questView, questParams);
        }

        // 现在页记下的想法、每日复盘规则都不属于普通提醒事项
        List<ReminderSettings.Rule> rules = ReminderSettings.listedRules(this);
        addDailyBodyNote();

        LinearLayout sectionHead = row();
        sectionHead.setPadding(0, dp(22), 0, dp(10));
        TextView sectionTitle = label("提醒事项", 18, INK, true);
        sectionTitle.setLetterSpacing(0.02f);
        sectionHead.addView(sectionTitle, new LinearLayout.LayoutParams(0, -2, 1));
        TextView manageItems = label("管理", 12, GREEN, true);
        manageItems.setGravity(Gravity.CENTER);
        manageItems.setLetterSpacing(0.04f);
        manageItems.setPadding(dp(13), dp(7), dp(13), dp(7));
        manageItems.setBackground(round(UiPalette.SOFT, 14));
        manageItems.setOnClickListener(v -> startActivity(new Intent(this, ItemsActivity.class)));
        sectionHead.addView(manageItems);
        body.addView(sectionHead);
        LinearLayout schedule = new LinearLayout(this);
        schedule.setOrientation(LinearLayout.VERTICAL);
        schedule.setPadding(0, 0, 0, 0);
        schedule.setBackgroundColor(Color.TRANSPARENT);
        boolean addedBuiltIn = false;
        for (ReminderSettings.Rule rule : rules) {
            addRuleRow(schedule, rule);
            addedBuiltIn = true;
        }
        if (!addedBuiltIn) addEmptyMessage(schedule, "还没有提醒事项，请到事项页添加。");
        body.addView(schedule, full());

        // 提醒运行状态放最下面，不抢首页视觉
        addTodayStatusCard(rules);

        LinearLayout nav = BottomNavigationBar.create(this, BottomNavigationBar.HOME, null,
                () -> { startActivity(new Intent(this, TaskActivity.class)); overridePendingTransition(0, 0); },
                () -> { startActivity(new Intent(this, ItemsActivity.class)); overridePendingTransition(0, 0); },
                () -> { startActivity(new Intent(this, CompanionLandingActivity.class)); overridePendingTransition(0, 0); },
                () -> { startActivity(new Intent(this, StatsActivity.class)); overridePendingTransition(0, 0); });
        LinearLayout.LayoutParams navParams = new LinearLayout.LayoutParams(-1, dp(64));
        navParams.leftMargin = dp(14);
        navParams.rightMargin = dp(14);
        navParams.bottomMargin = dp(10);
        screen.addView(nav, navParams);
    }

    /** 身体记录：默认折叠成一行入口，点开才出现输入框；经期已移出首页。 */
    private void addDailyBodyNote() {
        LinearLayout card = columnCard();
        card.setPadding(dp(18), dp(14), dp(18), dp(14));

        LinearLayout head = row();
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(0, dp(2), 0, dp(2));
        head.addView(label("\u4eca\u5929\u8eab\u4f53\u600e\u4e48\u6837", 14, INK, true),
                new LinearLayout.LayoutParams(0, -2, 1));

        int water = WaterLog.enabled(this) ? WaterLog.todayTotal(this) : 0;

        // 水滴：独立按钮，点一下直接记喝水
        if (WaterLog.enabled(this)) {
            LinearLayout waterBtn = new LinearLayout(this);
            waterBtn.setGravity(Gravity.CENTER_VERTICAL);
            waterBtn.setPadding(dp(9), dp(6), dp(9), dp(6));
            waterBtn.setBackground(round(UiPalette.PALE, 12));
            waterBtn.setContentDescription("\u8bb0\u4e00\u6b21\u559d\u6c34");
            waterBtn.addView(label("\uD83D\uDCA7", 13, INK, false));
            if (water > 0) {
                waterBtn.addView(label(" " + water + "ml", 11, MUTED, false));
            }
            waterBtn.setOnClickListener(v -> WaterDialogs.showAmount(this, this::render));
            LinearLayout.LayoutParams waterParams = new LinearLayout.LayoutParams(-2, -2);
            waterParams.rightMargin = dp(12);
            head.addView(waterBtn, waterParams);
        }

        final TextView toggle = label("\u8bb0\u4e00\u53e5 \u203a", 12, GREEN, true);
        toggle.setPadding(dp(2), dp(6), dp(2), dp(6));
        head.addView(toggle);
        card.addView(head, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout expand = new LinearLayout(this);
        expand.setOrientation(LinearLayout.VERTICAL);
        expand.setVisibility(View.GONE);
        card.addView(expand, new LinearLayout.LayoutParams(-1, -2));

        EditText note = new EditText(this);
        note.setTextSize(14);
        note.setTextColor(INK);
        note.setHintTextColor(MUTED);
        note.setGravity(Gravity.TOP | Gravity.LEFT);
        note.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        note.setMinLines(2);
        note.setMaxLines(6);
        note.setPadding(dp(12), dp(10), dp(12), dp(10));
        note.setBackground(round(UiPalette.PALE, 12));
        note.setHint("睡得好不好、精神、酸痛…随便写一句");
        note.setContentDescription("\u6bcf\u65e5\u8eab\u4f53\u60c5\u51b5");
        Calendar noteDay = Calendar.getInstance();
        note.setText(DailyBodyNote.read(this, noteDay));
        note.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                DailyBodyNote.save(MainActivity.this, noteDay, s.toString());
            }
            @Override public void afterTextChanged(android.text.Editable s) { }
        });
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(-1, -2);
        noteParams.topMargin = dp(10);
        expand.addView(note, noteParams);

        if (WaterLog.enabled(this)) {
            LinearLayout waterRow = row();
            waterRow.setPadding(0, dp(10), 0, 0);
            TextView cup = label("\uD83D\uDCA7 \u8bb0\u4e00\u676f", 12, INK, true);
            cup.setPadding(dp(12), dp(7), dp(12), dp(7));
            cup.setBackground(round(UiPalette.PALE, 12));
            cup.setOnClickListener(v -> WaterDialogs.showAmount(this, this::render));
            waterRow.addView(cup);
            expand.addView(waterRow, new LinearLayout.LayoutParams(-1, -2));
        }

        View.OnClickListener flip = v -> {
            // 先收键盘并清焦点，否则第一次点击会被输入法吃掉
            View focused = getCurrentFocus();
            if (focused != null) {
                focused.clearFocus();
                android.view.inputmethod.InputMethodManager imm =
                        (android.view.inputmethod.InputMethodManager)
                                getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                }
            }
            boolean show = expand.getVisibility() != View.VISIBLE;
            expand.setVisibility(show ? View.VISIBLE : View.GONE);
            toggle.setText(show ? "\u6536\u8d77 \u2304" : "\u8bb0\u4e00\u53e5 \u203a");
            if (show) FormKit.focus(note);
        };
        toggle.setOnClickListener(flip);
        head.setOnClickListener(flip);

        LinearLayout.LayoutParams params = full();
        params.topMargin = dp(10);
        body.addView(card, params);
    }

    private void addGamificationCard() {
        DailyQuest.ensureDay(this);
        long dayStart = GameProgress.dayStart(System.currentTimeMillis());

        int completed = 0;
        int total = 0;
        for (Habits.Habit habit : Habits.all(this)) {
            if (habit.isScheduledForToday()) {
                total++;
                if (Habits.isCompletedToday(this, habit.id, dayStart)) completed++;
            }
        }
        for (ReminderSettings.Rule rule : ReminderSettings.listedRules(this)) {
            if (rule.isTaskModule() && rule.enabled) {
                total++;
                if (ReminderRecord.completedOn(this, rule.module, rule.title, dayStart)) completed++;
            }
        }

        int streak = GameProgress.getGlobalStreak(this);
        int level = DailyQuest.level(this);
        int sproutLevel = GameSettings.levelIndex(streak);

        LinearLayout questCard = columnCard();
        questCard.setPadding(dp(13), dp(10), dp(13), dp(12));

        // ---------- 英雄区：极淡绿白渐变底 ----------
        LinearLayout heroRow = new LinearLayout(this);
        heroRow.setGravity(Gravity.CENTER_VERTICAL);
        heroRow.setPadding(dp(8), dp(6), dp(10), dp(6));
        GradientDrawable heroBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xfff2faf5, 0xffe6f4ec});
        heroBg.setCornerRadius(dp(16));
        heroRow.setBackground(heroBg);

        HomeMascotView mascot = new HomeMascotView(this);
        heroRow.addView(mascot, new LinearLayout.LayoutParams(dp(72), dp(72)));

        GameHeroView status = new GameHeroView(this);
        status.setData(completed, total, streak, sproutLevel);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(0, dp(84), 1);
        statusParams.leftMargin = dp(2);
        heroRow.addView(status, statusParams);

        android.widget.FrameLayout heroWrap = new android.widget.FrameLayout(this);
        heroWrap.addView(heroRow, new android.widget.FrameLayout.LayoutParams(-1, -2));
        final CelebrationView heroFx = new CelebrationView(this);
        heroWrap.addView(heroFx, new android.widget.FrameLayout.LayoutParams(-1, dp(84)));
        questCard.addView(heroWrap, new LinearLayout.LayoutParams(-1, -2));

        // 完成数变多 → 豆芽弹跳 + 星星
        final String dayKey = new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
                .format(new java.util.Date());
        android.content.SharedPreferences fxPrefs = getSharedPreferences("game_fx", MODE_PRIVATE);
        boolean sameDay = dayKey.equals(fxPrefs.getString("last_day", ""));
        int prevCompleted = sameDay ? fxPrefs.getInt("last_completed", -1) : -1;
        if (prevCompleted >= 0 && completed > prevCompleted) {
            heroWrap.post(() -> {
                mascot.bounce();
                heroFx.burst(0xff6fe3a8);
                SoundKit.tick(this);
            });
        }
        fxPrefs.edit().putString("last_day", dayKey).putInt("last_completed", completed).apply();

        // ---------- 标题行 ----------
        LinearLayout head = row();
        LinearLayout.LayoutParams headParams = new LinearLayout.LayoutParams(-1, -2);
        headParams.topMargin = dp(10);
        headParams.leftMargin = dp(4);
        questCard.addView(head, headParams);
        TextView sectionLabel = label("今日任务", 12, UiPalette.MUTED, true);
        sectionLabel.setLetterSpacing(0.12f);
        head.addView(sectionLabel, new LinearLayout.LayoutParams(0, -2, 1));
        TextView lvBadge = label("Lv." + level, 10, UiPalette.ACCENT, true);
        lvBadge.setPadding(dp(8), dp(3), dp(8), dp(3));
        lvBadge.setBackground(round(UiPalette.PALE, 9));
        head.addView(lvBadge);

        // ---------- 任务行：可点果冻豆圆圈 ----------
        int index = 0;
        for (final DailyQuest.Quest quest : DailyQuest.quests()) {
            int progress = Math.min(DailyQuest.questProgress(this, quest), quest.target);
            final boolean done = progress >= quest.target;

            android.widget.FrameLayout rowWrap = new android.widget.FrameLayout(this);
            LinearLayout.LayoutParams wrapParams = new LinearLayout.LayoutParams(-1, -2);
            wrapParams.topMargin = dp(9);
            questCard.addView(rowWrap, wrapParams);

            LinearLayout rowBody = new LinearLayout(this);
            rowBody.setOrientation(LinearLayout.VERTICAL);

            LinearLayout topLine = new LinearLayout(this);
            topLine.setGravity(Gravity.CENTER_VERTICAL);
            rowBody.addView(topLine, new LinearLayout.LayoutParams(-1, -2));

            // 跳转按钮（点了去任务页完成，不是就地勾选）
            final TextView bean = jumpButton(done);
            topLine.addView(bean, new LinearLayout.LayoutParams(dp(24), dp(24)));

            final TextView questTitle = label(quest.title, 15, done ? UiPalette.MUTED : INK, done);
            questTitle.setPadding(dp(10), 0, 0, 0);
            if (done) {
                questTitle.setPaintFlags(questTitle.getPaintFlags()
                        | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            }
            topLine.addView(questTitle, new LinearLayout.LayoutParams(0, -2, 1));

            final TextView state = label(done ? "已完成" : progress + "/" + quest.target,
                    13, done ? Color.WHITE : UiPalette.ACCENT, true);
            if (done) {
                state.setPadding(dp(9), dp(3), dp(9), dp(3));
                state.setBackground(greenPill(10));
            }
            topLine.addView(state);

            // 行底细分段进度条（最少一格，完成就亮）
            if (quest.target >= 1) {
                SegmentBarView bar = new SegmentBarView(this);
                bar.setSegments(quest.target, progress, 0xff4caf7d, 0xffddede3);
                LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(-1, dp(4));
                barParams.topMargin = dp(4);
                barParams.leftMargin = dp(32);
                rowBody.addView(bar, barParams);
            }

            rowWrap.addView(rowBody, new android.widget.FrameLayout.LayoutParams(-1, -2));
            final CelebrationView rowFx = new CelebrationView(this);
            rowWrap.addView(rowFx, new android.widget.FrameLayout.LayoutParams(-1, dp(56)));

            // 点按钮：弹一下 → 去任务页
            bean.setOnClickListener(v -> {
                AnimKit.pop(bean, 1.3f);
                rowFx.burst(0xff6fe3a8);
                SoundKit.tick(this);
                bean.postDelayed(
                        () -> startActivity(new Intent(this, TaskActivity.class)), 260);
            });
            index++;
        }

        // ---------- 宝箱进度：🎁 + 分段条 ----------
        final int questTotal = DailyQuest.quests().size();
        final int questDone = DailyQuest.doneQuestCount(this);
        final boolean chestFull = questDone >= questTotal;

        LinearLayout chestRow = new LinearLayout(this);
        chestRow.setGravity(Gravity.CENTER_VERTICAL);
        chestRow.setBackground(round(UiPalette.PALE, 12));
        chestRow.setPadding(dp(10), dp(9), dp(10), dp(9));
        LinearLayout.LayoutParams chestRowParams = new LinearLayout.LayoutParams(-1, -2);
        chestRowParams.topMargin = dp(12);
        questCard.addView(chestRow, chestRowParams);

        final ChestIconView gift = new ChestIconView(this);
        gift.setUnlocked(chestFull);
        chestRow.addView(gift, new LinearLayout.LayoutParams(dp(26), dp(26)));

        SegmentBarView chestBar = new SegmentBarView(this);
        chestBar.setSegments(questTotal, questDone, 0xff4caf7d, 0xffddede3);
        LinearLayout.LayoutParams chestBarParams = new LinearLayout.LayoutParams(0, dp(6), 1);
        chestBarParams.leftMargin = dp(10);
        chestBarParams.rightMargin = dp(10);
        chestRow.addView(chestBar, chestBarParams);

        chestRow.addView(label(chestFull ? "可以领奖啦" : "还剩 " + (questTotal - questDone) + " 个",
                11, chestFull ? GREEN : MUTED, chestFull));

        if (chestFull && DailyQuest.bonusAvailable(this)) {
            gift.postDelayed(() -> {
                AnimKit.shake(gift);
                AnimKit.pop(gift, 1.3f);
            }, 300);
        }

        // ---------- 领取按钮 ----------
        if (DailyQuest.bonusAvailable(this)) {
            TextView claim = label("领取完成奖励  +40 经验", 14, Color.WHITE, true);
            claim.setGravity(Gravity.CENTER);
            claim.setPadding(dp(12), dp(12), dp(12), dp(12));
            claim.setBackground(greenPill(16));
            claim.setElevation(dp(3));
            claim.setOnClickListener(v -> {
                DailyQuest.claimAllBonus(this);
                List<DailyQuest.Reward> gained = DailyQuest.advanceSprint(this);
                String chestText = null;
                for (DailyQuest.Reward reward : gained) {
                    if (reward.text.contains("宝箱")) { chestText = reward.text; break; }
                }
                render();
                if (chestText != null) {
                    String name = chestText.contains("金") ? "金"
                            : (chestText.contains("银") ? "银" : "铜");
                    ChestDialog.show(MainActivity.this, name, chestText);
                } else {
                    SoundKit.levelUp(this);
                    Toast.makeText(MainActivity.this, "已领取，冲刺模式开启", Toast.LENGTH_SHORT).show();
                }
            });
            AnimKit.press(claim);
            LinearLayout.LayoutParams claimParams = new LinearLayout.LayoutParams(-1, -2);
            claimParams.topMargin = dp(10);
            questCard.addView(claim, claimParams);

        } else if (!DailyQuest.allQuestsDone(this)) {
            ReminderSettings.Rule recommend = pickRecommendation();
            if (recommend != null) {
                LinearLayout tip = new LinearLayout(this);
                tip.setGravity(Gravity.CENTER_VERTICAL);
                tip.setPadding(dp(12), dp(7), dp(7), dp(7));
                tip.setBackground(round(UiPalette.PALE, 14));
                LinearLayout.LayoutParams tipParams = new LinearLayout.LayoutParams(-1, -2);
                tipParams.topMargin = dp(8);
                questCard.addView(tip, tipParams);

                TextView tipText = label("推荐  " + recommend.title, 12, INK, false);
                tipText.setSingleLine(true);
                tipText.setEllipsize(android.text.TextUtils.TruncateAt.END);
                tip.addView(tipText, new LinearLayout.LayoutParams(0, -2, 1));

                TextView go = label("去做", 12, Color.WHITE, true);
                go.setGravity(Gravity.CENTER);
                go.setPadding(dp(13), dp(6), dp(13), dp(6));
                go.setBackground(greenPill(12));
                go.setOnClickListener(v -> startActivity(new Intent(this, TaskActivity.class)));
                AnimKit.press(go);
                tip.addView(go);
            }
        }

        // ---------- 冲刺 ----------
        if (DailyQuest.sprintUnlocked(this)) {
            LinearLayout sprint = new LinearLayout(this);
            sprint.setGravity(Gravity.CENTER_VERTICAL);
            sprint.setPadding(dp(12), dp(9), dp(12), dp(9));
            sprint.setBackground(round(UiPalette.PALE, 12));
            LinearLayout.LayoutParams sprintParams = new LinearLayout.LayoutParams(-1, -2);
            sprintParams.topMargin = dp(8);
            questCard.addView(sprint, sprintParams);

            sprint.addView(label("\u26A1 冲刺 " + DailyQuest.todayActions(this) + " 次",
                    12, UiPalette.ACCENT, true));
            TextView sprintRight = label("", 11, MUTED, false);
            int next = DailyQuest.nextSprintTarget(this);
            sprintRight.setText(next > 0
                    ? "再 " + Math.max(0, next - DailyQuest.todayActions(this)) + " 次到下一档"
                    : "今日冲满");
            sprintRight.setGravity(Gravity.END);
            sprint.addView(sprintRight, new LinearLayout.LayoutParams(0, -2, 1));
        }

        LinearLayout.LayoutParams cardParams = full();
        cardParams.topMargin = dp(10);
        body.addView(questCard, cardParams);
        if (!gameZoneAnimated) AnimKit.waveIn(questCard, 0);
        gameZoneAnimated = true;
    }

    /** 任务行左侧的跳转按钮：浅绿圆底箭头，完成后变亮绿打勾。 */
    private TextView jumpButton(boolean done) {
        TextView btn = label(done ? "\u2713" : "\u203A", done ? 13 : 18,
                done ? Color.WHITE : 0xff2f8f63, true);
        btn.setGravity(Gravity.CENTER);
        if (done) {
            GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{0xff6fe3a8, 0xff3cae78});
            bg.setShape(GradientDrawable.OVAL);
            btn.setBackground(bg);
        } else {
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(0xffe4f6ec);
            bg.setStroke(dp(1), 0xffc6ead6);
            btn.setBackground(bg);
        }
        btn.setElevation(dp(2));
        btn.setContentDescription("去完成任务");
        return btn;
    }

    /** 亮绿渐变胶囊（完成标签、领取按钮共用）。 */
    private GradientDrawable greenPill(int radius) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xff6fe3a8, 0xff3cae78});
        g.setCornerRadius(dp(radius));
        return g;
    }

    /** 从今天还没做的任务里随机挑一个，给「去做」用。 */
    private ReminderSettings.Rule pickRecommendation() {
        long dayStart = GameProgress.dayStart(System.currentTimeMillis());
        List<ReminderSettings.Rule> candidates = new ArrayList<>();
        for (ReminderSettings.Rule rule : ReminderSettings.listedRules(this)) {
            if (!rule.isTaskModule() || !rule.enabled) continue;
            if (!ReminderRecord.completedOn(this, rule.module, rule.title, dayStart)) {
                candidates.add(rule);
            }
        }
        if (candidates.isEmpty()) return null;
        return candidates.get(new java.util.Random().nextInt(candidates.size()));
    }

    private void showChestRewardDialog(int milestone) {
        int chestIdx = GameSettings.chestIndex(milestone);
        String icon = GameSettings.CHEST_ICONS[Math.min(chestIdx, GameSettings.CHEST_ICONS.length - 1)];

        String[] rewards = {"专属头像框", "特殊主题色", "成就徽章"};
        String reward = rewards[Math.min(chestIdx, rewards.length - 1)];

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(icon + " 恭喜解锁宝箱！")
                .setMessage("坚持 " + milestone + " 天，获得：\n\n" + reward + "\n\n继续坚持解锁更多奖励！")
                .setPositiveButton("收下", (d, w) -> {
                    GameProgress.markChestClaimed(this, "global", milestone);
                    Toast.makeText(this, "已领取：" + reward, Toast.LENGTH_SHORT).show();
                    render();
                })
                .create();
        dialog.show();
        styleDialog(dialog);
    }

    /** 结束日点错了，从首页直接改回来。 */
    private void pickPeriodEnd(final PeriodLog.Range range) {
        Calendar current = PeriodLog.toCalendar(range.endKey);
        if (current == null) current = Calendar.getInstance();
        WheelPicker.date(this, "结束日", current, picked -> {
            if (!PeriodLog.setEnd(this, range, picked)) {
                Toast.makeText(this, "这段经期没法改到这个日期", Toast.LENGTH_SHORT).show();
                return;
            }
            PeriodLog.Range updated = PeriodLog.latest(this);
            String label = updated == null ? "" : PeriodLog.monthDay(updated.endKey);
            Toast.makeText(this, "结束日改成 " + label, Toast.LENGTH_SHORT).show();
            render();
        }).show();
    }

    private void showWelcomeLetter() {
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(UiPalette.PAGE);
        screen.setPadding(dp(18), dp(12), dp(18), dp(18));

        LinearLayout topBar = row();
        TextView space = label("", 16, INK, false);
        topBar.addView(space, new LinearLayout.LayoutParams(0, dp(44), 1));
        TextView close = label("×", 28, MUTED, false);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("关闭引导信");
        close.setBackground(round(Color.WHITE, 22));
        topBar.addView(close, new LinearLayout.LayoutParams(dp(44), dp(44)));
        close.setOnClickListener(v -> enterHome());
        screen.addView(topBar);

        ScrollView letterScroll = new ScrollView(this);
        letterScroll.setFillViewport(true);
        letterScroll.setClipToPadding(false);
        LinearLayout.LayoutParams letterScrollParams = new LinearLayout.LayoutParams(-1, 0, 1);
        letterScrollParams.topMargin = dp(8);
        letterScrollParams.bottomMargin = dp(14);
        screen.addView(letterScroll, letterScrollParams);

        LinearLayout paper = new LinearLayout(this);
        paper.setOrientation(LinearLayout.VERTICAL);
        paper.setPadding(dp(24), dp(30), dp(24), dp(30));
        GradientDrawable paperBackground = new GradientDrawable();
        paperBackground.setColor(UiPalette.surface(Color.WHITE));
        paperBackground.setCornerRadius(dp(3));
        paperBackground.setStroke(dp(1), UiPalette.isDark() ? 0xff32624f : UiPalette.BORDER);
        paper.setBackground(paperBackground);
        letterScroll.addView(paper, new ScrollView.LayoutParams(-1, -2));

        TextView title = label("写给刚来这里的你", 23, INK, true);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(24));
        paper.addView(title, full());

        addLetterParagraph(paper, "你好，我是「休息豆芽」的开发者，很高兴见到你。", true);
        addLetterParagraph(paper, "我做这个 App，是因为我发现有时候明明累了，却还是停不下来，忘了歇一会儿。", false);
        addLetterParagraph(paper, "学习停不下来，工作停不下来，玩还是停不下来。", false);
        addLetterParagraph(paper, "身体支撑着我们的一举一动，但是我们却没有腾出时间，让它休息休息。", false);
        addLetterParagraph(paper, "身体是脆弱的，过度的劳累会让它不堪重负。\n但身体又是强大的，我们好好对它，它也会积极地自愈。", true);
        addLetterParagraph(paper, "所以，我想做一个APP，让可爱灵动的豆芽，强制提醒你去休息，好好爱护自己的身体。", false);
        addLetterParagraph(paper, "你可以按自己的情况安排提醒，豆芽会担起责任，好好监督你，让你百忙之余也不要忘了身体。", false);
        addLetterParagraph(paper, "累了，也可以来这里听听声音，让豆芽陪你待一会儿。", false);
        addLetterParagraph(paper, "谢谢你愿意试试「好好歇会」。", false);
        addLetterParagraph(paper, "希望它能陪你一起，爱护你的身体，\n平时记得做想做的事，\n也要记得照顾好自己。", false);

        TextView signature = label("— 「休息豆芽」开发者", 14, MUTED, true);
        signature.setGravity(Gravity.RIGHT);
        signature.setPadding(0, dp(8), 0, 0);
        paper.addView(signature, full());

        TextView begin = label("开始使用", 15, UiPalette.ACCENT, true);
        begin.setGravity(Gravity.CENTER);
        begin.setBackground(round(UiPalette.SOFT, 18));
        begin.setContentDescription("开始使用好好歇会");
        screen.addView(begin, new LinearLayout.LayoutParams(-1, dp(48)));
        begin.setOnClickListener(v -> enterHome());
        setContentView(screen);
    }

    private void addLetterParagraph(LinearLayout paper, String content, boolean emphasize) {
        TextView paragraph = label(content, 15, emphasize ? INK : MUTED, emphasize);
        paragraph.setLineSpacing(dp(4), 1f);
        LinearLayout.LayoutParams params = full();
        params.bottomMargin = dp(17);
        paper.addView(paragraph, params);
    }

    private void enterHome() {
        getSharedPreferences(ONBOARDING_PREFS, MODE_PRIVATE).edit()
                .putBoolean(KEY_WELCOME_SEEN, true).apply();
        render();
        AppUpdateManager.check(this, false);
    }

    /** 提醒运行状态：降级成一张轻量白卡，只留小图标 + 一行小字 + 开关。 */
    private void addTodayStatusCard(List<ReminderSettings.Rule> rules) {
        boolean remindersOn = ReminderSettings.enabled(this);
        int completed = 0;
        int target = 0;
        int remainingReminders = 0;
        long now = System.currentTimeMillis();
        long dayStart = ReminderRecord.dayStart(now);
        if (remindersOn) {
            for (ReminderSettings.Rule rule : rules) {
                if (!rule.enabled) continue;
                completed += ReminderSettings.completedToday(this, rule.id);
                target += countTodaySlots(rule, dayStart);
                remainingReminders += countRemainingReminders(rule, now, dayStart);
            }
        }

        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(12), dp(12), dp(12));
        card.setBackground(round(UiPalette.PALE, 20));
        card.setElevation(dp(1));

        MiniGlyphView dot = new MiniGlyphView(this);
        dot.setSproutLevel(remindersOn ? 2 : 0,
                remindersOn ? UiPalette.ACCENT : UiPalette.MUTED);
        card.addView(dot, new LinearLayout.LayoutParams(dp(20), dp(20)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(10), 0, dp(8), 0);
        copy.addView(label(remindersOn ? "提醒运行中" : "提醒已暂停", 13, INK, true));

        if (remindersOn && target > 0) {
            TextView sub = label("今日 " + completed + "/" + target + " 次 · 待完成 "
                    + remainingReminders + " 次", 11, 0xff6b8a79, false);
            sub.setPadding(0, dp(3), 0, 0);
            copy.addView(sub);
        }
        card.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));

        Switch enabled = new Switch(this);
        styleSwitch(enabled);
        enabled.setScaleX(0.85f);
        enabled.setScaleY(0.85f);
        enabled.setChecked(ReminderSettings.enabled(this));
        enabled.setContentDescription(ReminderSettings.enabled(this) ? "暂停今日提醒" : "开启今日提醒");
        enabled.setOnCheckedChangeListener((button, checked) -> {
            ReminderSettings.setEnabled(this, checked);
            if (checked && Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 91);
            }
            Toast.makeText(this, checked ? "提醒已开启" : "提醒已暂停", Toast.LENGTH_SHORT).show();
            render();
        });
        card.addView(enabled);

        LinearLayout.LayoutParams params = full();
        params.topMargin = dp(10);
        body.addView(card, params);
    }

    /** 今天这条规则该提醒几次（不分早晚，只看今天该不该响）。 */
    private int countTodaySlots(ReminderSettings.Rule rule, long dayStart) {
        if (!rule.enabled) return 0;
        if (rule.isSubtaskModule()) return 0;
        if (ReminderSettings.MODULE_BOARD.equals(rule.module)) return 0;
        if (!appliesToday(rule, dayStart)) return 0;
        long dayEnd = dayStart + 24 * 60 * 60 * 1000L;
        if (rule.specificTimeMode) {
            int total = 0;
            for (int pointSec : rule.timePointsSeconds) {
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(dayStart);
                cal.add(Calendar.SECOND, pointSec);
                if (cal.getTimeInMillis() < dayEnd) total++;
            }
            return total;
        }
        int startSec = rule.start * 60;
        int endSec = rule.end * 60;
        if (endSec <= startSec) endSec += 24 * 60 * 60; // 跨天
        long windowStart = dayStart + startSec * 1000L;
        long windowEnd = Math.min(dayStart + endSec * 1000L, dayEnd);
        if (windowEnd <= windowStart) return 0;
        int interval = Math.max(1, rule.intervalSeconds);
        return (int) ((windowEnd - windowStart) / (interval * 1000L)) + 1;
    }

    /** 单次 / 每周规则今天到底该不该响。 */
    private boolean appliesToday(ReminderSettings.Rule rule, long dayStart) {
        long dayEnd = dayStart + 24 * 60 * 60 * 1000L;
        if (rule.isOnceSchedule()) return rule.onceAt >= dayStart && rule.onceAt < dayEnd;
        if (rule.isWeeklySchedule()) {
            return rule.matchesWeekday(Calendar.getInstance().get(Calendar.DAY_OF_WEEK));
        }
        return true;
    }

    /** 今天还剩多少次提醒：取「还没到的时间点」和「还没完成的数量」中小的那个。 */
    private int countRemainingReminders(ReminderSettings.Rule rule, long now, long dayStart) {
        if (!rule.enabled) return 0;
        // 子任务不单独排闹钟，白板不提醒，都不计入
        if (rule.isSubtaskModule()) return 0;
        if (ReminderSettings.MODULE_BOARD.equals(rule.module)) return 0;
        // 单次 / 每周：不是今天就归零
        if (rule.isOnceSchedule()) {
            if (rule.onceAt < dayStart || rule.onceAt >= dayStart + 24 * 60 * 60 * 1000L) return 0;
        } else if (rule.isWeeklySchedule()) {
            if (!rule.matchesWeekday(Calendar.getInstance().get(Calendar.DAY_OF_WEEK))) return 0;
        }
        int upcoming = 0;
        int total = countTodaySlots(rule, dayStart);
        if (total <= 0) return 0;
        long dayEnd = dayStart + 24 * 60 * 60 * 1000L;
        if (rule.specificTimeMode) {
            for (int pointSec : rule.timePointsSeconds) {
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(dayStart);
                cal.add(Calendar.SECOND, pointSec);
                long pointMillis = cal.getTimeInMillis();
                if (pointMillis >= dayEnd) continue;
                if (pointMillis > now) upcoming++;
            }
        } else {
            int startSec = rule.start * 60;
            int endSec = rule.end * 60;
            if (endSec <= startSec) endSec += 24 * 60 * 60; // 跨天
            long windowStart = dayStart + startSec * 1000L;
            long windowEnd = Math.min(dayStart + endSec * 1000L, dayEnd);
            long effectiveStart = Math.max(now, windowStart);
            if (windowEnd > effectiveStart) {
                int interval = Math.max(1, rule.intervalSeconds);
                upcoming = (int) ((windowEnd - effectiveStart) / (interval * 1000L)) + 1;
            }
        }
        int undone = Math.max(0, total - ReminderSettings.completedToday(this, rule.id));
        return Math.min(upcoming, undone);
    }

    private void addPermissionBanner() {
        boolean notification = notificationPermissionReady();
        boolean exactAlarm = exactAlarmAllowed();
        boolean fullScreen = fullScreenIntentAllowed();
        boolean usage = UsageAccess.hasAccess(this);
        boolean accessibility = isWatchServiceEnabled();
        boolean overlay = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this);
        boolean batteryExempt = isIgnoringBatteryOptimizations();
        boolean ready = notification && exactAlarm && fullScreen && overlay && batteryExempt
                && (!ReminderSettings.usageOnly(this) || usage)
                && (!ReminderSettings.blockEnabled(this) || accessibility);
        LinearLayout banner = row();
        banner.setGravity(Gravity.CENTER_VERTICAL);
        banner.setPadding(dp(14), dp(11), dp(14), dp(11));
        banner.setBackground(glassRound(16));
        banner.setElevation(dp(1));
        int statusColor = ready ? INK : UiPalette.WARN;
        TextView mark = label(ready ? "✓" : "!", 14, ready ? GREEN : statusColor, true);
        mark.setGravity(Gravity.CENTER);
        GradientDrawable markBackground = round(ready ? UiPalette.SOFT : UiPalette.WARN_SOFT, 30);
        if (!ready) markBackground.setStroke(dp(1), UiPalette.WARN_BORDER);
        mark.setBackground(markBackground);
        banner.addView(mark, new LinearLayout.LayoutParams(dp(30), dp(30)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(11), 0, dp(5), 0);
        copy.addView(label(ready ? "提醒权限已就绪" : "提醒权限待检查", 13, statusColor, true));
        TextView detail = label(ready ? "通知、闹钟与后台运行可用" : "点击检查通知、闹钟和后台设置", 11, MUTED, false);
        detail.setPadding(0, dp(3), 0, 0);
        detail.setLetterSpacing(0.02f);
        copy.addView(detail);
        banner.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        banner.addView(label("›", 18, MUTED, false));
        banner.setOnClickListener(v -> showPermissionChecklist());
        LinearLayout.LayoutParams params = full();
        params.topMargin = dp(12);
        params.bottomMargin = dp(2);
        body.addView(banner, params);
    }

    private void showPermissionChecklist() {
        LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); list.setPadding(dp(20), dp(4), dp(20), 0);
        boolean notification = notificationPermissionReady();
        AlertDialog guide = new AlertDialog.Builder(this).setTitle("完成系统配置")
                .setView(list).setNeutralButton("重新检查", null).setPositiveButton("完成", null).create();
        addPermissionRow(list, guide, "通知权限", notification, () -> {
            boolean runtimeGranted = Build.VERSION.SDK_INT < 33
                    || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
            if (Build.VERSION.SDK_INT >= 33 && !runtimeGranted) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 91);
            } else {
                openNotificationSettings();
            }
        });
        addPermissionRow(list, guide, "精确闹钟", exactAlarmAllowed(), this::openExactAlarmSettings);
        if (Build.VERSION.SDK_INT >= 34) {
            addPermissionRow(list, guide, "锁屏全屏提醒", fullScreenIntentAllowed(), this::openFullScreenSettings);
        }
        addPermissionRow(list, guide, "使用情况访问", UsageAccess.hasAccess(this), this::openUsageSettings);
        addPermissionRow(list, guide, "无障碍服务（读取当前 App 名称）", isWatchServiceEnabled(), this::openAccessibilitySettings);
        addPermissionRow(list, guide, "悬浮窗（其他 App 上显示提醒）", Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this), this::openOverlaySettings);
        addPermissionRow(list, guide, "限制 App 选择", !ReminderSettings.blockPackages(this).isEmpty(), () -> startActivity(new Intent(this, AppPickerActivity.class).putExtra("mode", "block")));
        addPermissionRow(list, guide, "后台运行（忽略电池优化）", isIgnoringBatteryOptimizations(), this::openBatteryOptimizationSettings);
        addActionRow(list, guide, "自启动管理", "", this::openAutoStartSettings);
        addActionRow(list, guide, "保活壁纸", isWellnessWallpaperSet() ? "已设为壁纸" : "", this::openKeepAliveWallpaper);
        TextView testOverlay = label("测试覆盖提醒", 15, INK, true);
        testOverlay.setPadding(0, dp(14), 0, dp(14));
        testOverlay.setOnClickListener(v -> {
            guide.dismiss();
            body.postDelayed(() -> {
                if (!ReminderOverlayWindow.show(this, ReminderSettings.nextType(this))) {
                    Toast.makeText(this, "覆盖提醒未显示，请检查悬浮窗权限并保持屏幕解锁", Toast.LENGTH_LONG).show();
                }
            }, 300);
        });
        list.addView(testOverlay);
        guide.show();
        styleDialog(guide);
        guide.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            guide.dismiss();
            render();
            body.post(this::showPermissionChecklist);
        });
    }

    private void addPermissionRow(LinearLayout parent, AlertDialog guide, String title, boolean ready, Runnable action) {
        TextView row = label((ready ? "✓ " : "○ ") + title + (ready ? "  已完成" : "  去设置"), 15, ready ? INK : UiPalette.WARN, true);
        row.setPadding(0, dp(13), 0, dp(13));
        row.setOnClickListener(v -> {
            guide.dismiss();
            reopenPermissionGuide = true;
            action.run();
        });
        parent.addView(row);
    }

    private boolean notificationPermissionReady() {
        boolean runtimeGranted = Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
        return runtimeGranted && NotificationManagerCompat.from(this).areNotificationsEnabled();
    }

    private boolean exactAlarmAllowed() {
        if (Build.VERSION.SDK_INT < 31) return true;
        android.app.AlarmManager alarms = getSystemService(android.app.AlarmManager.class);
        return alarms != null && alarms.canScheduleExactAlarms();
    }

    private boolean fullScreenIntentAllowed() {
        if (Build.VERSION.SDK_INT < 34) return true;
        android.app.NotificationManager notifications = getSystemService(android.app.NotificationManager.class);
        return notifications != null && notifications.canUseFullScreenIntent();
    }

    private void openNotificationSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
        } catch (Exception unavailable) {
            openAppDetails("无法打开通知设置");
        }
    }

    private void openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < 31) return;
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception unavailable) {
            openAppDetails("无法打开精确闹钟设置");
        }
    }

    private void openFullScreenSettings() {
        if (Build.VERSION.SDK_INT < 34) return;
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception unavailable) {
            openAppDetails("无法打开全屏提醒设置");
        }
    }

    private void addActionRow(LinearLayout parent, AlertDialog guide, String title, String detail, Runnable action) {
        LinearLayout row = row();
        row.setPadding(0, dp(12), 0, dp(12));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(label(title, 15, INK, true));
        TextView subtitle = label(detail, 12, MUTED, false);
        subtitle.setPadding(0, dp(3), 0, 0);
        copy.addView(subtitle);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(label("前往  ›", 13, INK, true));
        row.setOnClickListener(v -> {
            guide.dismiss();
            reopenPermissionGuide = true;
            action.run();
        });
        parent.addView(row);
    }

    private void openAutoStartSettings() {
        ComponentName[] candidates = {
                new ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
                new ComponentName("com.iqoo.secure", "com.iqoo.secure.safeguard.PurviewTabActivity"),
                new ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.PurviewTabActivity")
        };
        for (ComponentName candidate : candidates) {
            try {
                Intent intent = new Intent(Intent.ACTION_MAIN).setComponent(candidate);
                startActivity(intent);
                return;
            } catch (Exception ignored) { }
        }
        openAppDetails("未找到 Vivo 自启动管理页面，请在本应用信息中查找自启动权限");
    }

    private void openAppDetails(String fallbackMessage) {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception error) {
            Toast.makeText(this, fallbackMessage, Toast.LENGTH_LONG).show();
        }
    }

    private boolean isWellnessWallpaperSet() {
        return WellnessWallpaperService.isApplied(this);
    }

    private void openKeepAliveWallpaper() {
        startActivity(new Intent(this, WallpaperSettingsActivity.class));
    }

    private void openOverlaySettings() {
        if (Build.VERSION.SDK_INT < 23) return;
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception error) {
            Toast.makeText(this, "无法打开悬浮窗设置", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isIgnoringBatteryOptimizations() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
        android.os.PowerManager manager = (android.os.PowerManager) getSystemService(POWER_SERVICE);
        return manager != null && manager.isIgnoringBatteryOptimizations(getPackageName());
    }

    private void openBatteryOptimizationSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception ignored) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        }
    }

    private boolean isBuiltIn(String title) {
        for (String type : ReminderSettings.TYPES) if (type.equals(title)) return true;
        return false;
    }

    private void addEmptyMessage(LinearLayout parent, String message) {
        TextView empty = label(message, 13, MUTED, false);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(8), dp(18), dp(8), dp(18));
        empty.setOnClickListener(v -> startActivity(new Intent(this, ItemsActivity.class)));
        parent.addView(empty);
    }

    private void addRuleRow(LinearLayout parent, ReminderSettings.Rule rule) {
        int done = ReminderSettings.completedToday(this, rule.id);
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(dp(16), dp(15), dp(16), dp(15));
        item.setBackground(itemCard(rule));
        item.setElevation(dp(3));
        if (!rule.enabled) item.setAlpha(0.62f);

        LinearLayout main = row();
        ItemIconView badge = new ItemIconView(this);
        badge.setTextSize(15);
        badge.setTextColor(ruleInk(rule));
        badge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setContentDescription(rule.title);
        badge.setPadding(dp(6), dp(6), dp(6), dp(6));
        badge.bindIcon(rule.iconImage(), rule.badgeText());
        GradientDrawable badgeBackground = round(ruleTint(rule), 20);
        badgeBackground.setStroke(dp(1), ruleStroke(rule));
        badge.setBackground(badgeBackground);
        main.addView(badge, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(12), 0, dp(10), 0);
        LinearLayout titleLine = row();
        TextView title = label(rule.title, 16, INK, true);
        title.setLetterSpacing(0.01f);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titleLine.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView targetBadge = label(done >= rule.dailyTarget ? "达标" : done + " / " + rule.dailyTarget,
                10, ruleInk(rule), true);
        targetBadge.setGravity(Gravity.CENTER);
        targetBadge.setPadding(dp(8), dp(4), dp(8), dp(4));
        targetBadge.setBackground(round(ruleTint(rule), 10));
        titleLine.addView(targetBadge);
        copy.addView(titleLine);

        String nextText;
        long nextAt = 0L;
        boolean ticking = false;
        if (done >= rule.dailyTarget && !rule.remindAfterTarget) {
            nextText = "今日已完成";
        } else if (!rule.enabled || !ReminderSettings.enabled(this)) {
            nextText = "已暂停";
        } else {
            nextAt = ReminderScheduler.scheduledAt(this, rule.id);
            if (nextAt <= 0L) {
                nextAt = ReminderScheduler.nextTime(rule, System.currentTimeMillis());
            }
            ticking = true;
            nextText = "还有 " + countdownText(nextAt - System.currentTimeMillis());
        }
        // 倒计时放前面、字号更大：用户第一眼就能看到“还有几秒提醒”，
        // 时段摘要退到后面，空间不够时优先省略它。
        LinearLayout metaLine = row();
        TextView countdown = label("", 12, ticking ? GREEN : MUTED, ticking);
        countdown.setText(countdownStyled(nextText));
        countdown.setSingleLine(true);
        countdown.setLetterSpacing(0.02f);
        if (ticking) startCountdown(countdown, rule);
        metaLine.addView(countdown);
        TextView schedule = label(compactScheduleSummary(rule), 10, MUTED, false);
        schedule.setSingleLine(true);
        schedule.setLetterSpacing(0.02f);
        schedule.setGravity(Gravity.END);
        schedule.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams scheduleParams = new LinearLayout.LayoutParams(0, -2, 1);
        scheduleParams.leftMargin = dp(10);
        metaLine.addView(schedule, scheduleParams);
        LinearLayout.LayoutParams metaParams = full();
        metaParams.topMargin = dp(5);
        copy.addView(metaLine, metaParams);
        main.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));

        final ProgressBar itemProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        itemProgress.setIndeterminate(false);
        itemProgress.setMax(Math.max(1, rule.dailyTarget));
        itemProgress.setProgress(Math.min(done, Math.max(1, rule.dailyTarget)));
        itemProgress.setProgressTintList(ColorStateList.valueOf(ruleInk(rule)));
        itemProgress.setProgressBackgroundTintList(ColorStateList.valueOf(UiPalette.hairline()));

        TextView complete = new TextView(this);
        complete.setText("完成");
        complete.setTextSize(10);
        complete.setTextColor(ruleInk(rule));
        complete.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        complete.setAllCaps(false);
        complete.setGravity(Gravity.CENTER);
        complete.setLetterSpacing(0.04f);
        complete.setPadding(dp(12), dp(6), dp(12), dp(6));
        complete.setMinHeight(dp(30));
        complete.setMinWidth(dp(52));
        complete.setIncludeFontPadding(false);
        GradientDrawable completeBackground = round(ruleTint(rule), 15);
        completeBackground.setStroke(dp(1), ruleStroke(rule));
        complete.setBackground(completeBackground);
        complete.setElevation(dp(1));
        complete.setOnClickListener(v -> {
            long now = System.currentTimeMillis();
            // 任务类事项：大任务一点完成，底下子任务默认全部打勾
            if (ReminderSettings.MODULE_TASK.equals(rule.module)) {
                Subtasks.completeAll(this, rule.id, ReminderRecord.dayStart(now));
            }
            ReminderSettings.markCompleted(this, rule.id, rule.dailyTarget);
            ReminderScheduler.resetNext(this, rule.id);
            String recordId = java.util.UUID.randomUUID().toString();
            ReminderRecord.finish(this, recordId, rule.title, ReminderRecord.KIND_DONE,
                    now, now, "提前完成一次");
            ReminderScheduler.update(this);
            int updatedDone = ReminderSettings.completedToday(this, rule.id);
            targetBadge.setText(updatedDone >= rule.dailyTarget ? "达标" : updatedDone + " / " + rule.dailyTarget);
            itemProgress.setMax(Math.max(1, rule.dailyTarget));
            itemProgress.setProgress(Math.min(updatedDone, Math.max(1, rule.dailyTarget)));

            boolean remindersOn = ReminderSettings.enabled(this);
            long todayStart = ReminderRecord.dayStart(now);
            int allDone = 0;
            int allTarget = 0;
            if (remindersOn) {
                for (ReminderSettings.Rule current : ReminderSettings.listedRules(this)) {
                    if (!current.enabled) continue;
                    allDone += ReminderSettings.completedToday(this, current.id);
                    allTarget += countTodaySlots(current, todayStart);
                }
            }
            if (completedMetric != null) {
                completedMetric.setText(remindersOn && allTarget > 0
                        ? allDone + " / " + allTarget + " 次" : "—");
            }
            if (todayProgress != null && remindersOn && allTarget > 0) {
                todayProgress.setMax(Math.max(1, allTarget));
                todayProgress.setProgress(Math.min(allDone, Math.max(1, allTarget)));
            }

            boolean targetReached = updatedDone >= rule.dailyTarget;
            if (targetReached && !rule.remindAfterTarget) {
                countdown.setText(countdownStyled("今日已完成"));
                countdown.setTextColor(MUTED);
            } else {
                long refreshedNextAt = ReminderScheduler.scheduledAt(this, rule.id);
                if (refreshedNextAt <= 0L) refreshedNextAt = ReminderScheduler.nextTime(rule, now);
                countdown.setText(countdownStyled("还有 " + countdownText(refreshedNextAt - now)));
                countdown.setTextColor(targetReached ? MUTED : GREEN);
            }
        });
        main.addView(complete);
        item.addView(main);

        LinearLayout.LayoutParams itemProgressParams = new LinearLayout.LayoutParams(-1, dp(3));
        itemProgressParams.topMargin = dp(14);
        item.addView(itemProgress, itemProgressParams);

        item.setOnClickListener(v -> {
            if (rule.isReviewModule()) {
                startActivity(new Intent(this, ReviewReminderActivity.class)
                        .putExtra("rule_id", rule.id));
            } else {
                openItemEditor(rule.id);
            }
        });
        item.setOnLongClickListener(v -> { openItemEditor(rule.id); return true; });
        LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(-1, -2);
        itemParams.bottomMargin = dp(12);
        parent.addView(item, itemParams);
    }

    private String compactScheduleSummary(ReminderSettings.Rule rule) {
        if (!rule.specificTimeMode) {
            return clock(rule.start) + "–" + clock(rule.end) + " · " + intervalText(rule.intervalSeconds);
        }
        StringBuilder summary = new StringBuilder("每天 ");
        int shown = Math.min(2, rule.timePointsSeconds.size());
        for (int i = 0; i < shown; i++) {
            if (i > 0) summary.append("、");
            int point = rule.timePointsSeconds.get(i);
            if (point % 60 == 0) {
                summary.append(String.format(java.util.Locale.getDefault(), "%02d:%02d",
                        point / 3600, point / 60 % 60));
            } else {
                summary.append(String.format(java.util.Locale.getDefault(), "%02d:%02d:%02d",
                        point / 3600, point / 60 % 60, point % 60));
            }
        }
        if (rule.timePointsSeconds.size() > shown) summary.append(" +").append(rule.timePointsSeconds.size() - shown);
        return summary.toString();
    }

    private String scheduleSummary(ReminderSettings.Rule rule) {
        if (!rule.specificTimeMode) {
            return clock(rule.start) + " - " + clock(rule.end) + "  ·  " + intervalText(rule.intervalSeconds);
        }
        StringBuilder summary = new StringBuilder("每天 ");
        for (int i = 0; i < rule.timePointsSeconds.size(); i++) {
            if (i > 0) summary.append("、");
            int point = rule.timePointsSeconds.get(i);
            summary.append(String.format(java.util.Locale.getDefault(), "%02d:%02d:%02d",
                    point / 3600, point / 60 % 60, point % 60));
        }
        return summary.toString();
    }

    private void openItemEditor(int ruleId) {
        startActivity(new Intent(this, ItemsActivity.class)
                .putExtra("edit_id", ruleId)
                .putExtra("return_to_home", true));
    }

    /** 剩余时间文案：始终精确到秒，首页第一眼就能看到还有多久提醒。 */
    private String countdownText(long remainingMillis) {
        long seconds = Math.max(0, (remainingMillis + 999L) / 1000L);
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainder = seconds % 60;
        if (hours > 0) {
            return hours + "小时" + minutes + "分" + remainder + "秒";
        }
        if (minutes > 0) {
            return minutes + "分" + remainder + "秒";
        }
        return remainder + "秒";
    }

    /** 倒计时里的数字放大一档并加深颜色，单位文字保持小号。 */
    private CharSequence countdownStyled(String text) {
        SpannableStringBuilder builder = new SpannableStringBuilder(text);
        int numberColor = UiPalette.COUNTDOWN_INK;
        int index = 0;
        while (index < builder.length()) {
            if (!Character.isDigit(builder.charAt(index))) {
                index++;
                continue;
            }
            int start = index;
            while (index < builder.length() && Character.isDigit(builder.charAt(index))) index++;
            builder.setSpan(new RelativeSizeSpan(1.22f), start, index, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            builder.setSpan(new ForegroundColorSpan(numberColor), start, index, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            builder.setSpan(new StyleSpan(Typeface.BOLD), start, index, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return builder;
    }

    private void startCountdown(TextView view, ReminderSettings.Rule rule) {
        Runnable ticker = new Runnable() {
            @Override public void run() {
                if (!view.isAttachedToWindow()) return;
                long now = System.currentTimeMillis();
                int done = ReminderSettings.completedToday(MainActivity.this, rule.id);
                boolean targetReached = done >= rule.dailyTarget && !rule.remindAfterTarget;
                if (targetReached) {
                    view.setText(countdownStyled("今日已完成"));
                    view.setTextColor(MUTED);
                } else {
                    long nextAt = ReminderScheduler.scheduledAt(MainActivity.this, rule.id);
                    if (nextAt <= 0L) nextAt = ReminderScheduler.nextTime(rule, now);
                    if (nextAt <= now) {
                        ReminderScheduler.reconcileAfterScreenOn(MainActivity.this);
                        nextAt = ReminderScheduler.scheduledAt(MainActivity.this, rule.id);
                    }
                    view.setText(countdownStyled("还有 " + countdownText(nextAt - now)));
                    view.setTextColor(GREEN);
                }
                view.postDelayed(this, 1000);
            }
        };
        view.post(ticker);
    }

    /** 事项最终使用的颜色（自选优先，否则按标题自动归类）。 */
    private int ruleKind(ReminderSettings.Rule rule) {
        return UiPalette.colorKind(rule.color, rule.title);
    }

    private int ruleInk(ReminderSettings.Rule rule) {
        return UiPalette.categoryInk(ruleKind(rule));
    }

    private int ruleTint(ReminderSettings.Rule rule) {
        return UiPalette.categoryFill(ruleKind(rule));
    }

    private int ruleStroke(ReminderSettings.Rule rule) {
        return UiPalette.categoryStroke(ruleKind(rule));
    }

    private int iconColor(String title) {
        return UiPalette.categoryInk(UiPalette.categoryKind(title));
    }

    private void editRule(ReminderSettings.Rule existing) {
        final int id = existing == null ? ReminderSettings.nextRuleId(this) : existing.id;
        final int[] start = {existing == null ? 9 * 60 : existing.start};
        final int[] end = {existing == null ? 18 * 60 : existing.end};
        final int[] interval = {existing == null ? 30 * 60 : existing.intervalSeconds};
        final int[] target = {existing == null ? 3 : existing.dailyTarget};
        final boolean[] remindAfterTarget = {existing != null && existing.remindAfterTarget};
        final boolean[] active = {existing == null || existing.enabled};
        LinearLayout form = new LinearLayout(this);
        form.setPadding(dp(20), dp(8), dp(20), 0);
        form.setOrientation(LinearLayout.VERTICAL);
        EditText name = new EditText(this);
        name.setSingleLine(true);
        name.setTextColor(INK);
        name.setHintTextColor(MUTED);
        name.setHint("事项名称，例如：起来活动一下");
        name.setText(existing == null ? "" : existing.title);
        name.setBackground(UiPalette.inputBackground(this, MINT_PALE, 10));
        form.addView(name);

        final String[] iconValue = {existing == null ? "" : existing.icon};
        LinearLayout iconRow = row();
        iconRow.setPadding(0, dp(10), 0, 0);
        iconRow.addView(label("图标", 14, INK, true), new LinearLayout.LayoutParams(0, -2, 1));
        final ItemIconView iconPreview = new ItemIconView(this);
        iconPreview.setTextSize(17);
        iconPreview.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        iconPreview.setTextColor(INK);
        iconPreview.setGravity(Gravity.CENTER);
        iconPreview.setPadding(dp(7), dp(7), dp(7), dp(7));
        LinearLayout iconChoice = new LinearLayout(this);
        iconChoice.setGravity(Gravity.CENTER_VERTICAL);
        iconChoice.setPadding(dp(5), dp(4), dp(12), dp(4));
        iconChoice.setBackground(round(Color.WHITE, 12));
        iconChoice.addView(iconPreview, new LinearLayout.LayoutParams(dp(40), dp(40)));
        final TextView iconName = label("", 13, MUTED, false);
        iconName.setPadding(dp(10), 0, 0, 0);
        iconChoice.addView(iconName);
        iconRow.addView(iconChoice);
        form.addView(iconRow);
        final Runnable refreshIcon = () -> {
            String value = iconValue[0];
            iconPreview.bindIcon(value, value.isEmpty() ? "首" : value);
            iconPreview.setTextColor(INK);
            iconName.setText(ItemIconPicker.describe(value) + "  ›");
        };
        refreshIcon.run();
        iconChoice.setOnClickListener(v -> ItemIconPicker.build(this, iconValue[0], picked -> {
            iconValue[0] = picked;
            refreshIcon.run();
        }).show());
        TextView fromButton = timeField("开始时间", start[0]);
        TextView toButton = timeField("结束时间", end[0]);
        Button intervalButton = button("提醒间隔  " + intervalText(interval[0]), () -> {});
        fromButton.setOnClickListener(v -> new TimePickerDialog(this, (view, h, m) -> { start[0] = h * 60 + m; fromButton.setText("开始时间\n" + clock(start[0])); }, start[0] / 60, start[0] % 60, true).show());
        toButton.setOnClickListener(v -> new TimePickerDialog(this, (view, h, m) -> { end[0] = h * 60 + m; toButton.setText("结束时间\n" + clock(end[0])); }, end[0] / 60, end[0] % 60, true).show());
        int[] values = {5, 10, 15, 20, 30, 45, 60, 90};
        String[] labels = new String[values.length];
        for (int i = 0; i < values.length; i++) labels[i] = "每 " + values[i] + " 分钟";
        java.util.Set<String> savedIntervals = ReminderSettings.prefs(this)
                .getStringSet("custom_item_intervals_seconds", java.util.Collections.emptySet());
        ArrayList<Integer> customIntervals = new ArrayList<>();
        for (String value : savedIntervals) {
            try { customIntervals.add(Integer.parseInt(value)); } catch (NumberFormatException ignored) { }
        }
        customIntervals.sort(Integer::compareTo);
        String[] intervalOptions = new String[customIntervals.size() + labels.length + 1];
        for (int i = 0; i < customIntervals.size(); i++) intervalOptions[i] = "自定义 · " + intervalText(customIntervals.get(i));
        intervalOptions[customIntervals.size()] = "新增自定义时间…";
        System.arraycopy(labels, 0, intervalOptions, customIntervals.size() + 1, labels.length);
        intervalButton.setOnClickListener(v -> FormKit.options(this, "提醒间隔", intervalOptions, -1, which -> {
            if (which < customIntervals.size()) {
                interval[0] = customIntervals.get(which);
                intervalButton.setText("提醒间隔  " + intervalText(interval[0]));
            } else if (which == customIntervals.size()) {
                chooseCustomInterval(interval, intervalButton);
            } else {
                interval[0] = values[which - customIntervals.size() - 1] * 60;
                intervalButton.setText("提醒间隔  " + intervalText(interval[0]));
            }
        }));
        Switch enabledSwitch = new Switch(this);
        enabledSwitch.setText("启用此事项");
        enabledSwitch.setChecked(active[0]);
        enabledSwitch.setOnCheckedChangeListener((button, checked) -> active[0] = checked);
        form.addView(fromButton);
        form.addView(toButton);
        form.addView(intervalButton);
        final Button[] targetButtonHolder = new Button[1];
        Button targetButton = button("每日目标  " + target[0] + " 次", () -> {
            final EditText input = new EditText(this);
            input.setInputType(2);
            input.setText(String.valueOf(target[0]));
            input.setTextColor(INK);
            input.setHintTextColor(MUTED);
            input.setPadding(dp(14), dp(10), dp(14), dp(10));
            input.setBackground(UiPalette.inputBackground(this, MINT_PALE, 10));
            AlertDialog targetDialog = new AlertDialog.Builder(this)
                    .setTitle("每天想完成几次")
                    .setView(input)
                    .setNegativeButton("取消", null)
                    .setPositiveButton("确定", (d, w) -> {
                        try {
                            target[0] = Math.max(1, Integer.parseInt(input.getText().toString()));
                            targetButtonHolder[0].setText("每日目标  " + target[0] + " 次");
                        } catch (Exception ignored) { }
                    })
                    .create();
            targetDialog.show();
            styleDialog(targetDialog);
        });
        targetButtonHolder[0] = targetButton;
        form.addView(targetButton);
        TextView afterTargetLabel = label("达标后继续", 14, INK, true);
        afterTargetLabel.setPadding(0, dp(8), 0, dp(2));
        form.addView(afterTargetLabel);
        RadioGroup afterTargetOptions = new RadioGroup(this);
        afterTargetOptions.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton stopAfterTarget = new RadioButton(this);
        stopAfterTarget.setId(1);
        stopAfterTarget.setText("否，停止提醒");
        RadioButton keepAfterTarget = new RadioButton(this);
        keepAfterTarget.setId(2);
        keepAfterTarget.setText("是，继续提醒");
        afterTargetOptions.addView(stopAfterTarget, new RadioGroup.LayoutParams(0, -2, 1));
        afterTargetOptions.addView(keepAfterTarget, new RadioGroup.LayoutParams(0, -2, 1));
        afterTargetOptions.check(remindAfterTarget[0] ? 2 : 1);
        afterTargetOptions.setOnCheckedChangeListener((group, checkedId) -> remindAfterTarget[0] = checkedId == 2);
        form.addView(afterTargetOptions);
        form.addView(enabledSwitch);
        AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle(existing == null ? "添加提醒事项" : "编辑提醒事项")
                .setView(form)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (d, w) -> {
                    String title = name.getText().toString().trim();
                    if (title.isEmpty()) { Toast.makeText(this, "请输入事项名称", Toast.LENGTH_SHORT).show(); return; }
                    ArrayList<ReminderSettings.Rule> updated = new ArrayList<>(ReminderSettings.rules(this));
                    ReminderSettings.Rule saved = ReminderSettings.Rule.withIntervalSeconds(id, title, start[0], end[0], interval[0], target[0], remindAfterTarget[0], active[0])
                            .withReminderMessage(existing == null ? "" : existing.reminderMessage)
                            .withIcon(iconValue[0])
                            .withColor(existing == null ? ReminderSettings.Rule.COLOR_AUTO : existing.color)
                            .withOverlayArtwork(existing == null ? ReminderArtworkCatalog.DEFAULT : existing.overlayArtwork);
                    int replaceIndex = -1;
                    for (int i = 0; i < updated.size(); i++) {
                        if (updated.get(i).id == id) { replaceIndex = i; break; }
                    }
                    if (replaceIndex >= 0) updated.set(replaceIndex, saved);
                    else updated.add(saved);
                    saveRules(updated);
                });
        if (existing != null) builder.setNeutralButton("删除", (d, w) -> {
            ArrayList<ReminderSettings.Rule> updated = new ArrayList<>(ReminderSettings.rules(this));
            updated.removeIf(rule -> rule.id == id);
            saveRules(updated);
        });
        AlertDialog dialog = builder.create();
        dialog.show();
        styleDialog(dialog);
    }

    private void chooseCustomInterval(int[] interval, Button intervalButton) {
        LinearLayout fields = row();
        fields.setGravity(Gravity.CENTER);
        fields.setOrientation(LinearLayout.HORIZONTAL);
        fields.setPadding(dp(18), dp(12), dp(18), dp(4));
        NumberPicker hours = new NumberPicker(this);
        hours.setMinValue(0);
        hours.setMaxValue(23);
        hours.setValue(interval[0] / 3600);
        hours.setWrapSelectorWheel(false);
        NumberPicker minutes = new NumberPicker(this);
        minutes.setMinValue(0);
        minutes.setMaxValue(59);
        minutes.setValue(interval[0] / 60 % 60);
        NumberPicker seconds = new NumberPicker(this);
        seconds.setMinValue(0);
        seconds.setMaxValue(59);
        seconds.setWrapSelectorWheel(false);
        seconds.setValue(interval[0] % 60);
        minutes.setWrapSelectorWheel(false);
        addIntervalWheel(fields, hours, "小时");
        addIntervalWheel(fields, minutes, "分钟");
        addIntervalWheel(fields, seconds, "秒");

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("自定义提醒间隔")
                .setView(fields)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int totalSeconds = hours.getValue() * 3600 + minutes.getValue() * 60 + seconds.getValue();
            if (totalSeconds == 0) {
                Toast.makeText(this, "提醒间隔至少为 1 秒", Toast.LENGTH_SHORT).show();
                return;
            }
            interval[0] = totalSeconds;
            java.util.Set<String> saved = new java.util.HashSet<>(ReminderSettings.prefs(this)
                    .getStringSet("custom_item_intervals_seconds", java.util.Collections.emptySet()));
            saved.add(Integer.toString(totalSeconds));
            ReminderSettings.prefs(this).edit().putStringSet("custom_item_intervals_seconds", saved).commit();
            intervalButton.setText("提醒间隔  " + intervalText(totalSeconds));
            dialog.dismiss();
        }));
        dialog.show();
    }

    private String intervalText(int seconds) {
        int hours = seconds / 3600;
        int minutes = seconds / 60 % 60;
        int remainder = seconds % 60;
        StringBuilder result = new StringBuilder("每 ");
        if (hours > 0) result.append(hours).append(" 小时 ");
        if (minutes > 0) result.append(minutes).append(" 分钟 ");
        if (remainder > 0 || (hours == 0 && minutes == 0)) result.append(remainder).append(" 秒");
        return result.toString().trim();
    }

    private void addIntervalWheel(LinearLayout parent, NumberPicker picker, String unit) {
        LinearLayout column = new LinearLayout(this);
        column.setGravity(Gravity.CENTER);
        column.setOrientation(LinearLayout.VERTICAL);
        column.addView(picker, new LinearLayout.LayoutParams(dp(76), dp(112)));
        TextView label = label(unit, 13, MUTED, true);
        label.setGravity(Gravity.CENTER);
        column.addView(label, new LinearLayout.LayoutParams(-1, dp(24)));
        parent.addView(column, new LinearLayout.LayoutParams(0, -2, 1));
    }

    private TextView timeField(String title, int minute) {
        TextView field = label(title + "\n" + clock(minute), 15, INK, true);
        field.setGravity(Gravity.CENTER);
        field.setPadding(dp(10), dp(12), dp(10), dp(12));
        field.setBackground(round(MINT_PALE, 10));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(7);
        field.setLayoutParams(params);
        return field;
    }

    private void saveRule(ReminderSettings.Rule changed) {
        ArrayList<ReminderSettings.Rule> updated = new ArrayList<>(ReminderSettings.rules(this));
        for (int i = 0; i < updated.size(); i++) if (updated.get(i).id == changed.id) updated.set(i, changed);
        saveRules(updated);
    }

    private void saveRules(List<ReminderSettings.Rule> updated) {
        ReminderSettings.saveRules(this, updated);
        render();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != 22) return;
        boolean enable = ReminderSettings.prefs(this).getBoolean("enable_block_after_picker", false);
        ReminderSettings.prefs(this).edit().remove("enable_block_after_picker").apply();
        if (enable && !ReminderSettings.blockPackages(this).isEmpty()) {
            ReminderSettings.prefs(this).edit().putBoolean("block_enabled", true).apply();
            if (!isWatchServiceEnabled()) openAccessibilitySettings();
        }
        render();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 91) {
            render();
            if (reopenPermissionGuide) {
                reopenPermissionGuide = false;
                showPermissionChecklist();
            }
        }
    }

    private void openUsageSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        } catch (Exception error) {
            Toast.makeText(this, "无法打开使用情况访问设置", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isWatchServiceEnabled() {
        AccessibilityManager manager = (AccessibilityManager) getSystemService(Service.ACCESSIBILITY_SERVICE);
        if (manager == null) return false;
        for (AccessibilityServiceInfo info : manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            if (info.getResolveInfo() == null) continue;
            ServiceInfo serviceInfo = info.getResolveInfo().serviceInfo;
            if (serviceInfo != null && getPackageName().equals(serviceInfo.packageName)
                    && AppWatchService.class.getName().equals(serviceInfo.name)) return true;
        }
        return false;
    }

    private void openAccessibilitySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (Exception error) {
            Toast.makeText(this, "无法打开无障碍设置", Toast.LENGTH_SHORT).show();
        }
    }

    static String clock(int minute) {
        return String.format(java.util.Locale.getDefault(), "%02d:%02d", minute / 60, minute % 60);
    }

    private void section(String title) {
        TextView heading = label(title, 18, INK, true);
        LinearLayout.LayoutParams params = full();
        params.topMargin = dp(20);
        params.bottomMargin = dp(9);
        body.addView(heading, params);
    }

    private LinearLayout columnCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(12), dp(16), dp(12));
        card.setBackground(softGreenCard(20));
        card.setElevation(dp(4));
        card.setTranslationZ(dp(2));
        return card;
    }

    /** 带绿调软阴影的绿卡：大扩散、低透明度，看起来像浮在软垫上。 */
    private GradientDrawable softGreenCard(int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(UiPalette.PALE);
        shape.setCornerRadius(dp(radius));
        shape.setStroke(dp(1), UiPalette.BORDER);
        return shape;
    }

    /** 带类目色淡染的玻璃卡片。 */
    private GradientDrawable itemCard(ReminderSettings.Rule rule) {
        // 复盘卡：绿卡 + 细描边，不用粉红色块
        if (rule.isReviewModule()) {
            GradientDrawable white = new GradientDrawable();
            white.setColor(UiPalette.PALE);
            white.setCornerRadius(dp(22));
            white.setStroke(dp(1), UiPalette.BORDER);
            return white;
        }
        int tint = ruleTint(rule);
        boolean dark = UiPalette.isDark();
        int base = dark ? 0xff0a251c : UiPalette.PALE;
        int[] colors = dark
                ? new int[]{UiPalette.blend(base, tint, 0.90f), UiPalette.blend(base, tint, 0.55f), UiPalette.blend(base, tint, 0.30f)}
                : new int[]{UiPalette.blend(base, tint, 0.88f), UiPalette.blend(base, tint, 0.55f), UiPalette.blend(base, tint, 0.22f)};
        GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        background.setCornerRadius(dp(22));
        background.setStroke(dp(1), UiPalette.hairline());
        return background;
    }

    /** 玻璃质感的白色卡片：极淡渐变 + 极细描边 + 大圆角。 */
    private GradientDrawable glassRound(int radius) {
        GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                UiPalette.glassGradient());
        background.setCornerRadius(dp(radius));
        background.setStroke(dp(1), UiPalette.hairline());
        return background;
    }

    /** 首页主状态卡：带主题色柔光的玻璃卡片。 */
    private GradientDrawable heroCard() {
        GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                UiPalette.heroGradient());
        background.setCornerRadius(dp(26));
        background.setStroke(dp(1), UiPalette.hairline());
        return background;
    }

    private int iconBorder(String title) {
        return UiPalette.categoryStroke(UiPalette.categoryKind(title));
    }
    private GradientDrawable outline(int fillColor, int strokeColor, int radius) {
        GradientDrawable background = round(fillColor, radius);
        background.setStroke(dp(1), strokeColor);
        return background;
    }

    private void addSettingRow(LinearLayout parent, String title, String value, String hint, Runnable action) {
        LinearLayout row = row();
        row.setPadding(dp(2), dp(9), dp(2), dp(9));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(label(title, 15, INK, true));
        TextView detail = label(hint, 11, MUTED, false);
        detail.setPadding(0, dp(3), 0, 0);
        copy.addView(detail);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        TextView val = label(value + "  ›", 14, INK, true);
        val.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        row.addView(val, new LinearLayout.LayoutParams(-2, -1));
        row.setOnClickListener(v -> action.run());
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private void addDivider(LinearLayout parent) {
        View divider = new View(this);
        divider.setBackgroundColor(LINE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(1));
        params.topMargin = dp(12);
        params.bottomMargin = dp(2);
        parent.addView(divider, params);
    }

    private void styleSwitch(Switch toggle) {
        int[][] states = new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{}
        };
        toggle.setThumbTintList(new ColorStateList(states, new int[]{Color.WHITE, Color.WHITE}));
        toggle.setTrackTintList(new ColorStateList(states, new int[]{GREEN, UiPalette.DISABLED}));
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private TextView label(String text, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private Button button(String text, Runnable action) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(INK);
        button.setAllCaps(false);
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(UiPalette.surface(color));
        drawable.setCornerRadius(dp(radius));
        if (color == Color.WHITE) drawable.setStroke(dp(1), UiPalette.BORDER);
        return drawable;
    }

    private void styleDialog(AlertDialog dialog) {
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(round(Color.WHITE, 18));
            dialog.getWindow().setDimAmount(0.34f);
        }
        int titleId = getResources().getIdentifier("alertTitle", "id", "android");
        TextView title = titleId == 0 ? null : dialog.findViewById(titleId);
        if (title != null) {
            title.setTextColor(INK);
            title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        TextView message = dialog.findViewById(android.R.id.message);
        if (message != null) message.setTextColor(MUTED);
        for (int buttonId : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE,
                AlertDialog.BUTTON_NEUTRAL}) {
            Button action = dialog.getButton(buttonId);
            if (action == null) continue;
            action.setAllCaps(false);
            action.setTextColor(buttonId == AlertDialog.BUTTON_POSITIVE ? INK : MUTED);
            action.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        FormKit.focusFirstInput(dialog);
    }

    private LinearLayout.LayoutParams full() { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
