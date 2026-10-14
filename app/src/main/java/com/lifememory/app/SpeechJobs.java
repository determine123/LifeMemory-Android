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
    return mode.equals("local") ? OfflineAsr.model(c) != null : new Ai(c).enabled();
  }

  public static int pendingCount() {
    return pending.size();
  }

  public static Future<?> submit(Context context, long id) {
    Context c = context.getApplicationContext();
    if (!pending.add(id)) return CompletableFuture.completedFuture(null);
    return queue.submit(
        () -> {
          try (Store db = new Store(c)) {
            Store.Event e = db.find(id);
            if (e == null) return;
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
            try (Store db = new Store(c)) {
              Store.Event e = db.find(id);
              if (e != null) {
                org.json.JSONObject meta = e.metadata();
                try {
                  meta.put("asr_error", "转写未完成：" + error.getMessage());
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
