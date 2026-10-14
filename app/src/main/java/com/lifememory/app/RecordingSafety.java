package com.lifememory.app;

import android.content.*;
import android.media.MediaMetadataRetriever;
import java.io.*;
import org.json.*;

/**
 * A durable checkpoint survives process death; recovery never promises to repair an unfinished MP4.
 */
final class RecordingSafety {
  static final long RESERVE_BYTES = 16L * 1024 * 1024;

  static boolean enoughSpace(long available) {
    return available > RESERVE_BYTES;
  }

  static void checkpoint(Context c, File file, long start) throws IOException {
    if (!c.getSharedPreferences("capture", 0)
        .edit()
        .putString("current_file", file.getAbsolutePath())
        .putLong("current_start", start)
        .commit()) throw new IOException("Unable to save recording checkpoint");
  }

  static void clear(Context c) {
    c.getSharedPreferences("capture", 0)
        .edit()
        .remove("current_file")
        .remove("current_start")
        .commit();
  }

  static long duration(File file) {
    MediaMetadataRetriever reader = new MediaMetadataRetriever();
    try {
      reader.setDataSource(file.getAbsolutePath());
      return Long.parseLong(reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
    } catch (Exception e) {
      return 0;
    } finally {
      try {
        reader.release();
      } catch (Exception ignored) {
      }
    }
  }

  static synchronized void recover(Context c) {
    if (CaptureService.active) return;
    SharedPreferences p = c.getSharedPreferences("capture", 0);
    String path = p.getString("current_file", "");
    if (!path.isEmpty()) {
      try (Store db = new Store(c)) {
        recover(c, db, path, p.getLong("current_start", 0));
      } catch (Exception e) {
        // Keep the checkpoint so a temporary storage/database failure can be retried.
        p.edit()
            .putString(
                "error",
                I18n.english(c)
                    ? "Recording recovery failed. Original file retained; reopen to retry."
                    : "录音恢复失败，原文件已保留，请重新打开应用重试。")
            .apply();
        return;
      }
    }
    clear(c);
    p.edit().putString("state", "stopped").apply();
  }

  static void recover(Context c, Store db, String path, long start) throws Exception {
    File file = new File(path), dir = new File(c.getFilesDir(), "media");
    if (!file.getCanonicalPath().startsWith(dir.getCanonicalPath() + File.separator)) return;
    if (!file.isFile() || file.length() == 0) return;
    if (start <= 0) start = file.lastModified();
    if (db.anchor(start) != null) return;
    long duration = duration(file);
    boolean playable = duration > 0;
    String text =
        I18n.english(c)
            ? (playable
                ? "Interrupted recording recovered"
                : "Interrupted recording is incomplete; original file retained")
            : (playable ? "中断录音已恢复" : "中断录音不完整，原文件已保留");
    long id = db.add(start, "audio", text, file.getAbsolutePath(), playable ? "pending" : "error");
    JSONObject meta = new JSONObject().put("interrupted", true).put("duration_ms", duration);
    if (!playable) meta.put("asr_error", text);
    db.metadata(id, meta);
  }
}
