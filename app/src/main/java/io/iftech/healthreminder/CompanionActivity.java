package io.iftech.healthreminder;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.text.InputType;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

public final class CompanionActivity extends Activity {
    private int ACCENT = UiPalette.ACCENT;
    private int MINT_LIGHT = UiPalette.SOFT;
    private int MINT_PALE = UiPalette.PALE;
    private int INK = UiPalette.INK;
    private int MUTED = UiPalette.MUTED;
    private int PAPER = UiPalette.PAGE;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView currentTime;
    private TextView timer;
    private TextView musicButton;
    private boolean focusMode;
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
        UiPalette.applyWindow(getWindow());
        boolean existingSession = CompanionAudioService.startedAt(this) > 0L;
        focusMode = existingSession ? CompanionAudioService.isFocusSession(this)
                : getIntent().getBooleanExtra(CompanionAudioService.EXTRA_FOCUS_MODE, false);
        if (focusMode) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (focusMode) hideSystemBars();
        startService(new Intent(this, CompanionAudioService.class)
                .setAction(CompanionAudioService.ACTION_START)
                .putExtra(CompanionAudioService.EXTRA_FOCUS_MODE, focusMode));
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
        if (hasFocus && focusMode) hideSystemBars();
    }

    @Override protected void onDestroy() {
        if (breatheX != null) breatheX.cancel();
        if (breatheY != null) breatheY.cancel();
        super.onDestroy();
    }

    private void render() {
        FrameLayout root = new FrameLayout(this);
        root.setBackground(UiPalette.page());

        LinearLayout content = new LinearLayout(this);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(8), dp(24), dp(28));
        FrameLayout.LayoutParams contentParams = new FrameLayout.LayoutParams(-1, -1);
        root.addView(content, contentParams);

        TextView title = text(focusMode ? "专注陪伴" : "轻松陪伴", 22, INK, true);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setMinHeight(dp(48));
        content.addView(title, new LinearLayout.LayoutParams(-1, dp(48)));
        title.setTextColor(INK);
        TextView subtitle = text(focusMode ? "留一点安静时间，把注意力放回手边。"
                : "不用做什么，先陪自己呼吸一会儿。", 15, MUTED, false);
        subtitle.setPadding(0, dp(8), 0, dp(24));
        content.addView(subtitle);
        subtitle.setTextColor(MUTED);

        ImageView face = new ImageView(this);
        face.setImageResource(focusMode ? R.drawable.companion_reading : R.drawable.beans_relax);
        face.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout faceStage = new FrameLayout(this);
        faceStage.setBackground(round(MINT_LIGHT, 18));
        FrameLayout.LayoutParams faceParams = new FrameLayout.LayoutParams(dp(216), dp(231), Gravity.CENTER);
        faceStage.addView(face, faceParams);
        LinearLayout.LayoutParams faceStageParams = new LinearLayout.LayoutParams(dp(240), dp(240));
        faceStageParams.topMargin = dp(12);
        content.addView(faceStage, faceStageParams);
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

        TextView breatheHint = text(focusMode ? "专注进行中" : "吸气 · 呼气", 15, INK, true);
        breatheHint.setGravity(Gravity.CENTER);
        breatheHint.setPadding(dp(16), dp(8), dp(16), dp(8));
        breatheHint.setBackground(round(MINT_LIGHT, 14));
        LinearLayout.LayoutParams breatheHintParams = new LinearLayout.LayoutParams(-2, -2);
        breatheHintParams.topMargin = dp(16);
        breatheHintParams.bottomMargin = dp(12);
        content.addView(breatheHint, breatheHintParams);

        LinearLayout clockPanel = new LinearLayout(this);
        clockPanel.setOrientation(LinearLayout.VERTICAL);
        clockPanel.setGravity(Gravity.CENTER);
        clockPanel.setPadding(dp(24), dp(14), dp(24), dp(14));
        clockPanel.setBackground(round(Color.WHITE, 18));
        clockPanel.setElevation(dp(1));
        TextView clockLabel = text("现在", 12, MUTED, false);
        clockPanel.addView(clockLabel);
        clockLabel.setTextColor(MUTED);
        currentTime = text("--:--:--", 46, INK, true);
        currentTime.setTextColor(INK);
        currentTime.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        currentTime.setGravity(Gravity.CENTER);
        clockPanel.addView(currentTime);
        timer = text((focusMode ? "已专注 " : "已陪伴 ") + "00:00:00", 13, MUTED, true);
        timer.setGravity(Gravity.CENTER);
        timer.setTextColor(MUTED);
        timer.setPadding(0, dp(3), 0, 0);
        clockPanel.addView(timer);
        LinearLayout.LayoutParams clockParams = new LinearLayout.LayoutParams(-1, -2);
        clockParams.setMargins(dp(22), dp(4), dp(22), 0);
        content.addView(clockPanel, clockParams);

        TextView soundLabel = text("环境音", 13, MUTED, true);
        soundLabel.setPadding(dp(4), dp(14), 0, 0);
        content.addView(soundLabel);
        soundLabel.setTextColor(MUTED);
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
        end.setGravity(Gravity.CENTER);
        end.setBackground(round(ACCENT, 16));
        end.setElevation(dp(1));
        end.setOnClickListener(v -> endSession());
        LinearLayout.LayoutParams endParams = new LinearLayout.LayoutParams(-1, dp(54));
        endParams.bottomMargin = dp(8);
        content.addView(end, endParams);

        musicButton = text("♫", 22, MUTED, true);
        musicButton.setGravity(Gravity.CENTER);
        musicButton.setContentDescription("播放" + CompanionAudioService.soundName(
                CompanionAudioService.selectedSound(this)));
        musicButton.setBackground(round(Color.WHITE, 16));
        musicButton.setOnClickListener(v -> {
            boolean play = !CompanionAudioService.isMusicPlaying(this);
            CompanionAudioService.setMusicPlaying(this, play);
            updateMusicButton();
            startService(new Intent(this, CompanionAudioService.class)
                    .setAction(CompanionAudioService.ACTION_SET_MUSIC)
                    .putExtra(CompanionAudioService.EXTRA_MUSIC_PLAYING, play));
        });
        FrameLayout.LayoutParams musicParams = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP | Gravity.RIGHT);
        musicParams.setMargins(0, dp(8), dp(24), 0);
        root.addView(musicButton, musicParams);
        setContentView(root);
    }

    private void addSoundOption(LinearLayout row, String label, String id) {
        TextView option = text(label, 14, MUTED, true);
        option.setGravity(Gravity.CENTER);
        option.setMinWidth(dp(72));
        option.setPadding(dp(14), 0, dp(14), 0);
        option.setBackground(round(Color.WHITE, 12));
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
            soundOptions.get(i).setTextColor(active ? ACCENT : MUTED);
            soundOptions.get(i).setBackground(round(active ? MINT_LIGHT : Color.WHITE, 12));
        }
    }

    private void updateMusicButton() {
        if (musicButton == null) return;
        boolean playing = CompanionAudioService.isMusicPlaying(this);
        musicButton.setText("♫");
        musicButton.setTextColor(playing ? ACCENT : MUTED);
        musicButton.setBackground(round(playing ? MINT_LIGHT : MINT_PALE, 24));
        String sound = CompanionAudioService.soundName(CompanionAudioService.selectedSound(this));
        musicButton.setContentDescription((playing ? "暂停" : "播放") + sound);
    }

    private void endSession() {
        EditText note = new EditText(this);
        note.setSingleLine(false);
        note.setMinLines(2);
        note.setMaxLines(4);
        note.setTextSize(15);
        note.setTextColor(INK);
        note.setHintTextColor(MUTED);
        note.setHint("例如：整理了桌面、读了几页书");
        note.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        note.setGravity(Gravity.TOP | Gravity.START);
        note.setPadding(dp(14), dp(12), dp(14), dp(12));
        note.setBackground(UiPalette.inputBackground(this, 0xfff7fffa, 12));
        LinearLayout noteContainer = new LinearLayout(this);
        noteContainer.setOrientation(LinearLayout.VERTICAL);
        noteContainer.setPadding(dp(24), 0, dp(24), dp(8));
        noteContainer.addView(note, new LinearLayout.LayoutParams(-1, -2));

        AlertDialog confirmDialog = new AlertDialog.Builder(dialogContext())
                .setTitle("结束陪伴")
                .setMessage("记一下刚才做了什么吧？（选填，不记也可以）")
                .setView(noteContainer)
                .setNegativeButton("继续陪伴", null)
                .setPositiveButton("退出陪伴", (dialog, which) -> stopCompanionSession(note.getText().toString().trim()))
                .create();
        showStyledDialog(confirmDialog);
    }

    private void stopCompanionSession(String note) {
        startService(new Intent(this, CompanionAudioService.class)
                .setAction(CompanionAudioService.ACTION_STOP)
                .putExtra(CompanionAudioService.EXTRA_SESSION_NOTE, note));
        finish();
        overridePendingTransition(0, 0);
    }

    private String formatDuration(long seconds) {
        return (focusMode ? "已专注 " : "已陪伴 ") + String.format(Locale.getDefault(), "%02d:%02d:%02d", seconds / 3600,
                (seconds % 3600) / 60, seconds % 60);
    }

    private void hideSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override public void onBackPressed() {
        if (focusMode) {
            return;
        }
        super.onBackPressed();
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
        shape.setColor(UiPalette.surface(color));
        shape.setCornerRadius(dp(radius));
        if (color == Color.WHITE) shape.setStroke(dp(1), UiPalette.BORDER);
        return shape;
    }

    private Context dialogContext() {
        return new ContextThemeWrapper(this, R.style.ItemsDialogTheme);
    }

    private void showStyledDialog(AlertDialog dialog) {
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(round(Color.WHITE, 16));
            window.setDimAmount(0.34f);
            window.setLayout(Math.min(getResources().getDisplayMetrics().widthPixels - dp(36), dp(480)),
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        TextView message = dialog.findViewById(android.R.id.message);
        if (message != null) {
            message.setTextColor(MUTED);
            message.setLineSpacing(dp(2), 1f);
        }
        for (int buttonId : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE,
                AlertDialog.BUTTON_NEUTRAL}) {
            android.widget.Button button = dialog.getButton(buttonId);
            if (button == null) continue;
            button.setAllCaps(false);
            button.setTextSize(14);
            button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            button.setTextColor(buttonId == AlertDialog.BUTTON_POSITIVE ? ACCENT : MUTED);
        }
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
