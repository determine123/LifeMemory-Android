package com.lifememory.app;

import android.app.*;
import android.media.*;
import android.os.*;
import android.widget.*;
import java.io.*;

/** Player owns its callbacks and media instance, so dismissal stops playback immediately. */
final class AudioPlayer {
  static void show(MainActivity activity, String path, long offset) {
    if (path.isEmpty() || !new File(path).isFile()) {
      activity.message("原音频未保存或已清理。");
      return;
    }
    MediaPlayer player = new MediaPlayer();
    Handler handler = new Handler(Looper.getMainLooper());
    try {
      player.setDataSource(path);
      player.prepare();
      int duration = player.getDuration();
      player.seekTo((int) Math.min(Math.max(0, offset), duration));
      LinearLayout content = activity.column();
      content.setPadding(activity.dp(16), activity.dp(8), activity.dp(16), activity.dp(8));
      TextView progress = activity.text("", 14, 0xff263d34);
      content.addView(progress);
      SeekBar seek = new SeekBar(activity);
      seek.setMax(duration);
      content.addView(seek);
      Button toggle = activity.button("暂停", () -> {});
      toggle.setOnClickListener(
          v -> {
            if (player.isPlaying()) {
              player.pause();
              toggle.setText("继续播放");
            } else {
              player.start();
              toggle.setText("暂停");
            }
          });
      content.addView(toggle);
      Spinner speed = new Spinner(activity);
      speed.setAdapter(
          new ArrayAdapter<>(
              activity,
              android.R.layout.simple_spinner_dropdown_item,
              new String[] {"0.75 ×", "1 ×", "1.25 ×", "1.5 ×", "2 ×"}));
      speed.setSelection(1);
      speed.setOnItemSelectedListener(
          new android.widget.AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(android.widget.AdapterView<?> v) {}

            public void onItemSelected(
                android.widget.AdapterView<?> v, android.view.View row, int position, long id) {
              boolean playing = player.isPlaying();
              try {
                player.setPlaybackParams(
                    player
                        .getPlaybackParams()
                        .setSpeed(new float[] {.75f, 1, 1.25f, 1.5f, 2}[position]));
                if (!playing) player.pause();
              } catch (Exception ignored) {
              }
            }
          });
      content.addView(speed);
      seek.setOnSeekBarChangeListener(
          new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int value, boolean user) {
              if (user) player.seekTo(value);
            }

            public void onStartTrackingTouch(SeekBar s) {}

            public void onStopTrackingTouch(SeekBar s) {}
          });
      AlertDialog dialog =
          new AlertDialog.Builder(activity)
              .setTitle("原声回听")
              .setView(content)
              .setPositiveButton("停止", null)
              .create();
      Runnable tick =
          new Runnable() {
            public void run() {
              try {
                int position = player.getCurrentPosition();
                seek.setProgress(position);
                progress.setText(FeatureUi.clock(position) + " / " + FeatureUi.clock(duration));
                handler.postDelayed(this, 400);
              } catch (Exception ignored) {
              }
            }
          };
      dialog.setOnDismissListener(
          d -> {
            handler.removeCallbacksAndMessages(null);
            player.release();
            activity.playbackDialog = null;
          });
      player.setOnCompletionListener(
          p -> {
            toggle.setText("重新播放");
          });
      activity.playbackDialog = dialog;
      dialog.show();
      player.start();
      handler.post(tick);
    } catch (Exception e) {
      handler.removeCallbacksAndMessages(null);
      player.release();
      activity.message("回听失败：" + e.getMessage());
    }
  }
}
