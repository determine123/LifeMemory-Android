package com.lifememory.app;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import java.io.*;
import java.util.UUID;
import org.json.JSONObject;

final class AudioImport {
  static long save(Context c, Uri uri) throws Exception {
    File dir = new File(c.getFilesDir(), "media");
    dir.mkdirs();
    String mime = c.getContentResolver().getType(uri);
    String extension = ".audio";
    if ("audio/mpeg".equals(mime)) extension = ".mp3";
    else if ("audio/wav".equals(mime) || "audio/x-wav".equals(mime)) extension = ".wav";
    else if ("audio/mp4".equals(mime) || "audio/x-m4a".equals(mime)) extension = ".m4a";
    else if ("audio/ogg".equals(mime)) extension = ".ogg";
    else if ("audio/flac".equals(mime)) extension = ".flac";
    File file = new File(dir, "imported-" + UUID.randomUUID() + extension);
    long id = -1;
    try {
      try (InputStream in = c.getContentResolver().openInputStream(uri);
          OutputStream out = new FileOutputStream(file)) {
        if (in == null) throw new IOException("Cannot read audio / 无法读取音频");
        byte[] buf = new byte[8192];
        long total = 0;
        int count;
        while ((count = in.read(buf)) != -1) {
          total += count;
          if (total > 256L * 1024 * 1024) throw new IOException("Audio exceeds 256 MB / 音频超过256MB");
          out.write(buf, 0, count);
        }
      }
      long duration;
      MediaMetadataRetriever metadata = new MediaMetadataRetriever();
      try {
        metadata.setDataSource(file.getAbsolutePath());
        String ms = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
        duration = ms == null ? 0 : Long.parseLong(ms);
      } finally {
        metadata.release();
      }
      if (duration <= 0) throw new IOException("Unsupported or empty audio / 不支持或空音频");
      try (Store db = new Store(c)) {
        id =
            db.add(
                System.currentTimeMillis(),
                "audio",
                I18n.english(c) ? "Imported audio · Pending transcription" : "导入录音 · 等待转写",
                file.getAbsolutePath(),
                "pending");
        db.metadata(
            id, new JSONObject().put("duration_ms", duration).put("source", "external-recorder"));
      }
      if (SpeechJobs.available(c)) SpeechJobs.submit(c, id);
      return id;
    } catch (Exception error) {
      if (id < 0) file.delete();
      throw error;
    }
  }
}
