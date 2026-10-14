package com.lifememory.app;

import android.app.*;
import android.content.*;
import android.media.MediaRecorder;
import android.os.*;
import java.io.*;
import org.json.*;

public class CaptureService extends Service {
  public static volatile boolean active = false, paused = false;
  public static volatile long sessionStarted = 0;
  private MediaRecorder recorder;
  private File file;
  private long started;
  private int peak;
  private Store db;
  private PowerManager.WakeLock wake;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Runnable rotate =
      new Runnable() {
        public void run() {
          finishSegment();
          if (active && !paused) {
            try {
              begin();
              handler.postDelayed(this, segmentMs());
            } catch (Exception e) {
              fail(e);
            }
          }
        }
      };
  private final Runnable tick =
      new Runnable() {
        public void run() {
          if (!active) return;
          if (recorder != null)
            try {
              peak = Math.max(peak, recorder.getMaxAmplitude());
            } catch (Exception ignored) {
            }
          getSharedPreferences("capture", 0)
              .edit()
              .putLong("heartbeat", System.currentTimeMillis())
              .apply();
          getSystemService(NotificationManager.class).notify(7, notification());
          handler.postDelayed(this, 2000);
        }
      };

  public void onCreate() {
    super.onCreate();
    db = new Store(this);
    getSystemService(NotificationManager.class)
        .createNotificationChannel(
            new NotificationChannel(
                "capture", I18n.t(this, "生活记录 · 麦克风"), NotificationManager.IMPORTANCE_LOW));
  }

  private int segmentMs() {
    int seconds = getSharedPreferences("life", 0).getInt("segment_seconds", 60);
    return Math.max(30, Math.min(300, seconds)) * 1000;
  }

  public int onStartCommand(Intent intent, int flags, int id) {
    String action = intent == null ? "start" : intent.getAction();
    if ("stop".equals(action)) {
      AutoCapture.manualStop(this);
      stopSelf();
      return START_NOT_STICKY;
    }
    if ("auto_config".equals(action) && active && wake != null && !paused) {
      if (AutoCapture.armed(this)) wake.acquire();
      else wake.acquire(12 * 60 * 60 * 1000L);
      return START_NOT_STICKY;
    }
    if ("mark".equals(action) && active) {
      mark();
      return START_NOT_STICKY;
    }
    if ("pause".equals(action) && active) {
      togglePause();
      return START_NOT_STICKY;
    }
    if (active) return START_NOT_STICKY;
    try {
      sessionStarted = System.currentTimeMillis();
      paused = false;
      active = true;
      startForeground(7, notification());
      wake =
          ((PowerManager) getSystemService(POWER_SERVICE))
              .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LifeMemory:capture");
      wake.setReferenceCounted(false);
      if (AutoCapture.armed(this)) wake.acquire();
      else wake.acquire(12 * 60 * 60 * 1000L);
      begin();
      handler.postDelayed(rotate, segmentMs());
      handler.post(tick);
      handler.postDelayed(
          () -> {
            if (!AutoCapture.armed(this)) stopSelf();
          },
          12 * 60 * 60 * 1000L);
      state();
    } catch (Exception e) {
      fail(e);
    }
    return START_NOT_STICKY;
  }

  private Notification notification() {
    PendingIntent open =
        PendingIntent.getActivity(
            this, 2, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
    Notification.Builder b =
        new Notification.Builder(this, "capture")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(paused ? I18n.t(this, "拾光 · 已暂停") : I18n.t(this, "拾光 · 录音中"))
            .setContentText(
                (I18n.english(this) ? "Saved on-device · " : "本机保存 · ")
                    + (System.currentTimeMillis() - sessionStarted) / 60000
                    + I18n.t(this, " 分钟 · 转写队列 ")
                    + SpeechJobs.pendingCount())
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true);
    String[] actions = {"pause", "mark", "stop"};
    String[] labels = {
      paused ? (I18n.english(this) ? "Resume" : "继续") : I18n.t(this, "暂停"),
      I18n.t(this, "标记此刻"),
      I18n.t(this, "停止")
    };
    for (int i = 0; i < 3; i++)
      b.addAction(
          android.R.drawable.ic_media_pause,
          labels[i],
          PendingIntent.getService(
              this,
              10 + i,
              new Intent(this, CaptureService.class).setAction(actions[i]),
              PendingIntent.FLAG_IMMUTABLE));
    return b.build();
  }

  private void begin() throws Exception {
    File dir = new File(getFilesDir(), "media");
    dir.mkdirs();
    started = System.currentTimeMillis();
    peak = 0;
    file = new File(dir, "audio-" + started + ".m4a");
    recorder = new MediaRecorder();
    recorder.setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION);
    recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
    recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
    recorder.setAudioSamplingRate(16000);
    recorder.setAudioEncodingBitRate(32000);
    recorder.setOutputFile(file.getAbsolutePath());
    recorder.prepare();
    recorder.start();
    getSharedPreferences("capture", 0)
        .edit()
        .putString("current_file", file.getAbsolutePath())
        .putLong("current_start", started)
        .apply();
  }

  private void finishSegment() {
    if (recorder == null) return;
    boolean ok = true;
    try {
      recorder.stop();
    } catch (Exception e) {
      ok = false;
    } finally {
      recorder.release();
      recorder = null;
    }
    if (!ok) {
      file.delete();
      clearCurrent();
      return;
    }
    long duration = System.currentTimeMillis() - started;
    boolean discard =
        getSharedPreferences("life", 0).getBoolean("discard_silence", false) && peak < 300;
    long id =
        db.add(
            started,
            "audio",
            discard ? "安静片段 · 原音频未保留" : "录音片段 · 等待转写",
            discard ? "" : file.getAbsolutePath(),
            discard ? "silence" : "pending");
    try {
      db.metadata(id, new JSONObject().put("duration_ms", duration).put("peak", peak));
    } catch (JSONException ignored) {
    }
    if (discard) file.delete();
    clearCurrent();
    getSharedPreferences("capture", 0)
        .edit()
        .putLong("last_saved", System.currentTimeMillis())
        .apply();
    SpeechJobs.refresh(this);
    if (!discard && SpeechJobs.available(this)) SpeechJobs.submit(this, id);
  }

  private void clearCurrent() {
    getSharedPreferences("capture", 0)
        .edit()
        .remove("current_file")
        .remove("current_start")
        .apply();
  }

  private void togglePause() {
    try {
      handler.removeCallbacks(rotate);
      if (!paused) {
        finishSegment();
        paused = true;
        if (wake != null && wake.isHeld()) wake.release();
      } else {
        if (AutoCapture.armed(this)) wake.acquire();
        else wake.acquire(12 * 60 * 60 * 1000L);
        begin();
        paused = false;
        handler.postDelayed(rotate, segmentMs());
      }
      state();
    } catch (Exception e) {
      fail(e);
    }
  }

  private void mark() {
    if (paused) {
      SpeechJobs.refresh(this);
      return;
    }
    long id = db.add(System.currentTimeMillis(), "bookmark", I18n.t(this, "重要时刻"), "", "ready");
    try {
      db.metadata(
          id,
          new JSONObject()
              .put("anchor_time", started)
              .put("offset_ms", System.currentTimeMillis() - started));
      db.pin(id, true);
    } catch (JSONException ignored) {
    }
    SpeechJobs.refresh(this);
  }

  private void state() {
    getSharedPreferences("capture", 0)
        .edit()
        .putString("state", active ? (paused ? "paused" : "recording") : "stopped")
        .putLong("session_started", sessionStarted)
        .apply();
    SpeechJobs.refresh(this);
  }

  private void fail(Exception e) {
    getSharedPreferences("capture", 0)
        .edit()
        .putString("error", e.getMessage() == null ? e.toString() : e.getMessage())
        .apply();
    stopSelf();
    SpeechJobs.refresh(this);
  }

  public static void recover(Context c) {
    if (active) return;
    android.content.SharedPreferences p = c.getSharedPreferences("capture", 0);
    String path = p.getString("current_file", "");
    if (!path.isEmpty())
      try (Store db = new Store(c)) {
        File f = new File(path);
        File dir = new File(c.getFilesDir(), "media");
        if (f.getCanonicalPath().startsWith(dir.getCanonicalPath() + File.separator)
            && f.isFile()
            && f.length() > 0
            && db.anchor(p.getLong("current_start", 0)) == null) {
          long id =
              db.add(
                  p.getLong("current_start", f.lastModified()),
                  "audio",
                  "录音曾中断 · 已找回原文件，可尝试回听或转写",
                  path,
                  "pending");
          db.metadata(id, new JSONObject().put("interrupted", true));
        }
      } catch (Exception ignored) {
      }
    p.edit().putString("state", "stopped").remove("current_file").remove("current_start").apply();
  }

  public void onDestroy() {
    handler.removeCallbacksAndMessages(null);
    active = false;
    paused = false;
    finishSegment();
    if (wake != null && wake.isHeld()) wake.release();
    state();
    stopForeground(STOP_FOREGROUND_REMOVE);
    super.onDestroy();
  }

  public IBinder onBind(Intent intent) {
    return null;
  }
}
