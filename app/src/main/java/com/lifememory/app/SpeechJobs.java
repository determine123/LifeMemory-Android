package com.lifememory.app;

import android.content.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;

public class SpeechJobs {
  private static final ExecutorService queue = Executors.newSingleThreadExecutor();
  private static final Set<Long> pending = ConcurrentHashMap.newKeySet();

  public static boolean available(Context c) {
    String mode = c.getSharedPreferences("life", 0).getString("asr_mode", "local");
    return mode.equals("local")
        ? OfflineAsr.model(c) != null
        : new Ai(c).enabled()
            && !new Ai(c).deepSeek()
            && !c.getSharedPreferences("life", 0).getBoolean("asr_blocked", false);
  }

  public static int pendingCount() {
    return pending.size();
  }

  static String unavailableReason(Context c) {
    boolean en = I18n.english(c);
    if (c.getSharedPreferences("life", 0).getString("asr_mode", "local").equals("local"))
      return en ? "Import the offline Chinese model in Settings first." : "请先在设置中导入离线中文模型。";
    if (new Ai(c).deepSeek())
      return en
          ? "This DeepSeek configuration is for chat. Select on-device transcription and import the"
                + " Chinese model."
          : "当前DeepSeek配置用于聊天，请选择本机转写并导入中文模型。";
    if (c.getSharedPreferences("life", 0).getBoolean("asr_blocked", false))
      return en
          ? "Cloud queue paused after authentication failure. Correct and save the API settings,"
                + " test authentication, then retry."
          : "云端队列因鉴权失败暂停。请修正并保存API设置，测试鉴权后再重试。";
    return en
        ? "Configure this speech provider’s API URL, model and key in Settings."
        : "请在设置中配置语音服务商对应的API地址、模型和密钥。";
  }

  public static Future<?> submit(Context context, long id) {
    Context c = context.getApplicationContext();
    if (!pending.add(id)) return CompletableFuture.completedFuture(null);
    return queue.submit(
        () -> {
          try (Store db = new Store(c)) {
            Store.Event e = db.find(id);
            if (e == null) return;
            if (!available(c)) {
              db.status(id, e.metadata().has("segments") ? "ready" : "pending");
              return;
            }
            if (e.path.isEmpty() || !new File(e.path).isFile())
              throw new java.io.IOException("原始音频已不存在");
            db.status(id, "processing");
            refresh(c);
            Transcript result =
                c.getSharedPreferences("life", 0).getString("asr_mode", "local").equals("local")
                    ? OfflineAsr.transcribe(c, new File(e.path))
                    : new Ai(c).transcribeDetailed(new File(e.path));
            db.transcript(id, result);
          } catch (Exception | LinkageError error) {
            if (error instanceof ApiFailure
                && (((ApiFailure) error).status == 401 || ((ApiFailure) error).status == 403))
              c.getSharedPreferences("life", 0).edit().putBoolean("asr_blocked", true).apply();
            try (Store db = new Store(c)) {
              Store.Event e = db.find(id);
              if (e != null) {
                org.json.JSONObject meta = e.metadata();
                try {
                  meta.put(
                      "asr_error",
                      (I18n.english(c) ? "Transcription failed: " : "转写未完成：") + error.getMessage());
                  if (error instanceof ApiFailure)
                    meta.put("asr_http_status", ((ApiFailure) error).status);
                } catch (org.json.JSONException ignored) {
                }
                db.metadata(id, meta);
                db.status(id, meta.has("segments") ? "ready" : "pending");
              }
            }
          } finally {
            pending.remove(id);
            refresh(c);
          }
        });
  }

  static void recoverInterrupted(Context c) {
    try (Store db = new Store(c)) {
      for (Store.Event e : db.events(null, null))
        if (e.kind.equals("audio") && e.status.equals("processing") && !pending.contains(e.id))
          db.status(e.id, e.metadata().has("segments") ? "ready" : "pending");
    }
  }

  public static int retry(Context c) {
    int count = 0;
    try (Store db = new Store(c)) {
      for (Store.Event e : db.events(null, null))
        if (e.kind.equals("audio")
            && (e.status.equals("pending") || e.status.equals("processing"))
            && !e.path.isEmpty()) {
          submit(c, e.id);
          count++;
        }
    }
    return count;
  }

  static void refresh(Context c) {
    c.sendBroadcast(new Intent("com.lifememory.REFRESH").setPackage(c.getPackageName()));
  }
}
