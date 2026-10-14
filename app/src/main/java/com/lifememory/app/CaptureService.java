package com.lifememory.app;

import android.app.*;
import android.content.*;
import android.media.*;
import android.os.*;
import java.io.*;
import java.util.List;
import org.json.*;

public class CaptureService extends Service {
  public static volatile boolean active = false, paused = false;
  public static volatile long sessionStarted = 0;
  private MediaRecorder recorder;
  private File file;
  private long started;
  private boolean stopping;
  private int peak;
  private Store db;
  private PowerManager.WakeLock wake;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Runnable rotate =
      new Runnable() {
        public void run() {
          try {
            finishSegment();
          } catch (Exception e) {
            fail(e);
            return;
          }
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
          if (recorder != null && !RecordingSafety.enoughSpace(getFilesDir().getUsableSpace())) {
            fail(
                new IOException(
                    I18n.english(CaptureService.this)
                        ? "Storage is low. Recording stopped; saved segments retained."
                        : "存储空间不足，录音已停止，已保存片段仍保留。"));
            return;
          }
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
      RecordingSafety.recover(this);
      if (!getSharedPreferences("capture", 0).getString("current_file", "").isEmpty())
        throw new IOException("Previous recording recovery is pending");
      stopping = false;
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
    if (!dir.isDirectory() && !dir.mkdirs())
      throw new IOException("Cannot create recording directory");
    if (!RecordingSafety.enoughSpace(dir.getUsableSpace()))
      throw new IOException(
          I18n.english(this)
              ? "At least 16 MB of free storage is required for recording."
              : "录音需要至少16 MB可用存储空间。");
    started = System.currentTimeMillis();
    peak = 0;
    file = new File(dir, "audio-" + started + ".m4a");
    RecordingSafety.checkpoint(this, file, started);
    recorder = new MediaRecorder();
    recorder.setOnErrorListener(
        (r, what, extra) ->
            handler.post(
                () -> {
                  if (r == recorder && !stopping)
                    fail(
                        new IOException(
                            I18n.english(this)
                                ? "Microphone recording was interrupted. Check saved audio before"
                                      + " resuming."
                                : "麦克风录音已中断，请检查保存的录音后再继续。"));
                }));
    recorder.setOnInfoListener(
        (r, what, extra) -> {
          if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED)
            handler.post(
                () -> {
                  if (r == recorder && !stopping)
                    fail(new IOException("Recording file size limit reached"));
                });
        });
    recorder.setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION);
    recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
    recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
    recorder.setAudioSamplingRate(16000);
    recorder.setAudioEncodingBitRate(32000);
    recorder.setMaxFileSize(
        Math.min(8L * 1024 * 1024, dir.getUsableSpace() - RecordingSafety.RESERVE_BYTES));
    recorder.setOutputFile(file.getAbsolutePath());
    recorder.prepare();
    recorder.start();
    if (Build.VERSION.SDK_INT >= 29) {
      final MediaRecorder current = recorder;
      recorder.registerAudioRecordingCallback(
          getMainExecutor(),
          new AudioManager.AudioRecordingCallback() {
            @Override
            public void onRecordingConfigChanged(List<AudioRecordingConfiguration> configs) {
              if (current != recorder || stopping) return;
              AudioRecordingConfiguration config = current.getActiveRecordingConfiguration();
              if (config != null && config.isClientSilenced()) {
                fail(
                    new IOException(
                        I18n.english(CaptureService.this)
                            ? "Microphone access was interrupted by a call or another app."
                                  + " Recording stopped; resume manually when available."
                            : "通话或其他应用中断了麦克风访问，录音已停止，请在麦克风可用后手动继续。"));
              }
            }
          });
    }
  }

  private void finishSegment() {
    if (recorder == null) return;
    boolean ok = true;
    try {
      recorder.stop();
    } catch (Exception e) {
      ok = false;
    } finally {
      try {
        recorder.release();
      } catch (Exception ignored) {
      }
      recorder = null;
    }
    if (!ok) {
      try {
        RecordingSafety.recover(this, db, file.getAbsolutePath(), started);
      } catch (Exception e) {
        throw new IllegalStateException("Unable to preserve interrupted recording", e);
      }
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
    RecordingSafety.clear(this);
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
    if (stopping) return;
    stopping = true;
    handler.removeCallbacksAndMessages(null);
    getSharedPreferences("capture", 0)
        .edit()
        .putString("error", e.getMessage() == null ? e.toString() : e.getMessage())
        .apply();
    stopSelf();
    SpeechJobs.refresh(this);
  }

  public static void recover(Context c) {
    RecordingSafety.recover(c);
  }

  public void onDestroy() {
    handler.removeCallbacksAndMessages(null);
    active = false;
    paused = false;
    try {
      finishSegment();
    } catch (Exception e) {
      getSharedPreferences("capture", 0)
          .edit()
          .putString("error", "Unable to save recording; original checkpoint retained")
          .apply();
    }
    if (wake != null && wake.isHeld()) wake.release();
    state();
    db.close();
    stopForeground(STOP_FOREGROUND_REMOVE);
    super.onDestroy();
  }

  public IBinder onBind(Intent intent) {
    return null;
  }
}
