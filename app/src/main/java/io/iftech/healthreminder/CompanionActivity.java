package io.iftech.healthreminder;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

public final class CompanionActivity extends Activity {
    private static final int GREEN = 0xff277049;
    private static final int PAPER = 0xffedf5eb;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView currentTime;
    private TextView timer;
    private TextView musicButton;
    /* private boolean focusMode; */
    private final java.util.ArrayList<TextView> soundOptions = new java.util.ArrayList<>();
    private ObjectAnimator breatheX;
    private ObjectAnimator breatheY;
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            long started = CompanionAudioService.startedAt(CompanionActivity.this);
            currentTime.setText(new java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    .format(System.currentTimeMillis()));
            timer.setText(formatDuration(started == 0 ? 0 : (System.currentTimeMillis() - started) / 1000));
            handler.postDelayed(this, 1000);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(PAPER);
        getWindow().setNavigationBarColor(PAPER);
        hideSystemBars();
        /*
        boolean existingSession = CompanionAudioService.startedAt(this) > 0L;
        focusMode = existingSession ? CompanionAudioService.isFocusSession(this)
                : getIntent().getBooleanExtra(CompanionAudioService.EXTRA_FOCUS_MODE, false);
        */
        startService(new Intent(this, CompanionAudioService.class)
                .setAction(CompanionAudioService.ACTION_START));
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(ticker);
        ticker.run();
        updateMusicButton();
    }

    @Override protected void onPause() {
        handler.removeCallbacks(ticker);
        super.onPause();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    @Override protected void onDestroy() {
        if (breatheX != null) breatheX.cancel();
        if (breatheY != null) breatheY.cancel();
        super.onDestroy();
    }

    private void render() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(PAPER);

        LinearLayout content = new LinearLayout(this);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(72), dp(24), dp(28));
        FrameLayout.LayoutParams contentParams = new FrameLayout.LayoutParams(-1, -1);
        root.addView(content, contentParams);

        TextView title = text("陪伴", 22, GREEN, true);
        /* TextView title = text(focusMode ? "专注" : "陪伴", 22, GREEN, true); */
        content.addView(title);
        TextView subtitle = text("不用做什么，先陪自己呼吸一会儿。", 15, 0xff617466, false);
        /* TextView subtitle = text(focusMode ? "留一点安静时间，把注意力放回手边。"
                : "不用做什么，先陪自己呼吸一会儿。", 15, 0xff617466, false); */
        subtitle.setPadding(0, dp(8), 0, dp(24));
        content.addView(subtitle);

        WellnessAnimationView face = new WellnessAnimationView(this, WellnessAnimationView.Mood.BREATHING);
        LinearLayout.LayoutParams faceParams = new LinearLayout.LayoutParams(dp(224), dp(224));
        faceParams.topMargin = dp(18);
        content.addView(face, faceParams);
        breatheX = ObjectAnimator.ofFloat(face, View.SCALE_X, 1f, 1.1f, 1f);
        breatheX.setDuration(4200);
        breatheX.setRepeatCount(ValueAnimator.INFINITE);
        breatheX.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        breatheX.start();
        breatheY = ObjectAnimator.ofFloat(face, View.SCALE_Y, 1f, 1.1f, 1f);
        breatheY.setDuration(4200);
        breatheY.setRepeatCount(ValueAnimator.INFINITE);
        breatheY.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        breatheY.start();

        TextView breatheHint = text("吸气……呼气……", 17, GREEN, true);
        /* TextView breatheHint = text(focusMode ? "专注进行中" : "吸气……呼气……", 17, GREEN, true); */
        breatheHint.setPadding(0, dp(20), 0, dp(10));
        content.addView(breatheHint);

        LinearLayout clockPanel = new LinearLayout(this);
        clockPanel.setOrientation(LinearLayout.VERTICAL);
        clockPanel.setGravity(Gravity.CENTER);
        clockPanel.setPadding(dp(24), dp(14), dp(24), dp(14));
        clockPanel.setBackground(round(Color.WHITE, 18));
        TextView clockLabel = text("现在", 12, 0xff718174, false);
        clockPanel.addView(clockLabel);
        currentTime = text("--:--:--", 48, 0xff23342a, true);
        currentTime.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        currentTime.setGravity(Gravity.CENTER);
        clockPanel.addView(currentTime);
        timer = text("已陪伴 00:00:00", 13, 0xff617466, true);
        /* timer = text((focusMode ? "已专注 " : "已陪伴 ") + "00:00:00", 13, 0xff617466, true); */
        timer.setGravity(Gravity.CENTER);
        timer.setPadding(0, dp(3), 0, 0);
        clockPanel.addView(timer);
        LinearLayout.LayoutParams clockParams = new LinearLayout.LayoutParams(-1, -2);
        clockParams.setMargins(dp(22), dp(4), dp(22), 0);
        content.addView(clockPanel, clockParams);

        TextView soundLabel = text("环境音", 13, 0xff617466, false);
        soundLabel.setPadding(0, dp(14), 0, 0);
        content.addView(soundLabel);
        HorizontalScrollView soundScroll = new HorizontalScrollView(this);
        soundScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout soundPicker = new LinearLayout(this);
        soundPicker.setGravity(Gravity.CENTER_VERTICAL);
        soundPicker.setOrientation(LinearLayout.HORIZONTAL);
        soundPicker.setPadding(0, dp(8), 0, dp(8));
        soundScroll.addView(soundPicker, new HorizontalScrollView.LayoutParams(-2, dp(56)));
        content.addView(soundScroll, new LinearLayout.LayoutParams(-1, dp(56)));
        addSoundOption(soundPicker, "溪水", "river");
        addSoundOption(soundPicker, "细雨", "rain");
        addSoundOption(soundPicker, "鸟鸣", "birds");
        addSoundOption(soundPicker, "风声", "wind");
        addSoundOption(soundPicker, "海浪", "waves");
        addSoundOption(soundPicker, "风吹树叶", "trees");
        updateSoundOptions();

        View spacer = new View(this);
        content.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1));

        TextView end = text("结束陪伴", 16, Color.WHITE, true);
        /* TextView end = text(focusMode ? "结束专注" : "结束陪伴", 16, Color.WHITE, true); */
        end.setGravity(Gravity.CENTER);
        end.setBackground(round(GREEN, 28));
        end.setOnClickListener(v -> endSession());
        LinearLayout.LayoutParams endParams = new LinearLayout.LayoutParams(-1, dp(54));
        endParams.bottomMargin = dp(8);
        content.addView(end, endParams);

        musicButton = text("♫", 22, 0xff9aa59c, true);
        musicButton.setGravity(Gravity.CENTER);
        musicButton.setContentDescription("播放" + CompanionAudioService.soundName(
                CompanionAudioService.selectedSound(this)));
        musicButton.setBackground(round(Color.WHITE, 24));
        musicButton.setOnClickListener(v -> {
            boolean play = !CompanionAudioService.isMusicPlaying(this);
            CompanionAudioService.setMusicPlaying(this, play);
            updateMusicButton();
            startService(new Intent(this, CompanionAudioService.class)
                    .setAction(CompanionAudioService.ACTION_SET_MUSIC)
                    .putExtra(CompanionAudioService.EXTRA_MUSIC_PLAYING, play));
        });
        FrameLayout.LayoutParams musicParams = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP | Gravity.RIGHT);
        musicParams.setMargins(0, dp(12), dp(16), 0);
        root.addView(musicButton, musicParams);
        setContentView(root);
    }

    private void addSoundOption(LinearLayout row, String label, String id) {
        TextView option = text(label, 14, GREEN, true);
        option.setGravity(Gravity.CENTER);
        option.setMinWidth(dp(72));
        option.setPadding(dp(14), 0, dp(14), 0);
        option.setBackground(round(Color.WHITE, 20));
        option.setOnClickListener(v -> {
            if (id.equals(CompanionAudioService.selectedSound(this))) return;
            CompanionAudioService.selectSound(this, id);
            updateSoundOptions();
            updateMusicButton();
            startService(new Intent(this, CompanionAudioService.class)
                    .setAction(CompanionAudioService.ACTION_SET_SOUND)
                    .putExtra(CompanionAudioService.EXTRA_SOUND_ID, id));
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(40));
        params.setMargins(dp(4), 0, dp(4), 0);
        row.addView(option, params);
        soundOptions.add(option);
    }

    private void updateSoundOptions() {
        String selected = CompanionAudioService.selectedSound(this);
        String[] ids = {"river", "rain", "birds", "wind", "waves", "trees"};
        for (int i = 0; i < soundOptions.size(); i++) {
            boolean active = ids[i].equals(selected);
            soundOptions.get(i).setTextColor(active ? Color.WHITE : GREEN);
            soundOptions.get(i).setBackground(round(active ? GREEN : Color.WHITE, 20));
        }
    }

    private void updateMusicButton() {
        if (musicButton == null) return;
        boolean playing = CompanionAudioService.isMusicPlaying(this);
        musicButton.setText("♫");
        musicButton.setTextColor(playing ? GREEN : 0xff9aa59c);
        musicButton.setBackground(round(playing ? 0xffe0efe1 : Color.WHITE, 24));
        String sound = CompanionAudioService.soundName(CompanionAudioService.selectedSound(this));
        musicButton.setContentDescription((playing ? "暂停" : "播放") + sound);
    }

    private void endSession() {
        startService(new Intent(this, CompanionAudioService.class).setAction(CompanionAudioService.ACTION_STOP));
        finish();
        overridePendingTransition(0, 0);
    }

    private String formatDuration(long seconds) {
        return "已陪伴 " + String.format(Locale.getDefault(), "%02d:%02d:%02d", seconds / 3600,
                (seconds % 3600) / 60, seconds % 60);
    }

    private void hideSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        return shape;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
