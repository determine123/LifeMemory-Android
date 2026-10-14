package com.lifememory.app;

import android.content.*;
import android.media.*;
import android.net.Uri;
import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.zip.*;
import org.json.*;
import org.vosk.Model;
import org.vosk.Recognizer;

/**
 * Model installation and inference are serialized so model replacement cannot interrupt decoding.
 */
public class OfflineAsr {
  public static File model(Context c) {
    String path = c.getSharedPreferences("life", 0).getString("model_path", "");
    if (path.isEmpty()) return null;
    try {
      File root = new File(c.getFilesDir(), "asr-models");
      File f = new File(path);
      if (f.getCanonicalPath().startsWith(root.getCanonicalPath() + File.separator)
          && new File(f, "am/final.mdl").isFile()
          && new File(f, "conf/model.conf").isFile()) return f;
    } catch (IOException ignored) {
    }
    return null;
  }

  public static synchronized void install(Context c, Uri uri) throws Exception {
    File parent = new File(c.getFilesDir(), "asr-models");
    parent.mkdirs();
    File stage = new File(parent, UUID.randomUUID().toString());
    stage.mkdirs();
    boolean success = false;
    try {
      try (InputStream input = c.getContentResolver().openInputStream(uri)) {
        if (input == null) throw new IOException("无法读取模型");
        extract(input, stage);
      }
      File root = findRoot(stage, 0);
      if (root == null)
        throw new IOException("不是有效的 Vosk 模型 ZIP（缺少 am/final.mdl 或 conf/model.conf）");
      try (Model test = new Model(root.getAbsolutePath())) {}
      File old = model(c);
      c.getSharedPreferences("life", 0)
          .edit()
          .putString("model_path", root.getAbsolutePath())
          .putString("asr_mode", "local")
          .commit();
      success = true;
      if (old != null) {
        File oldStage = old;
        while (oldStage.getParentFile() != null && !oldStage.getParentFile().equals(parent))
          oldStage = oldStage.getParentFile();
        if (oldStage.getParentFile() != null
            && oldStage.getParentFile().equals(parent)
            && !oldStage.equals(stage)) remove(oldStage);
      }
    } finally {
      if (!success) remove(stage);
    }
  }

  static void extract(InputStream input, File stage) throws IOException {
    long size = 0;
    int count = 0;
    String prefix = stage.getCanonicalPath() + File.separator;
    File archive = new File(stage, ".model-import.zip");
    try {
      byte[] buffer = new byte[8192];
      long compressed = 0;
      try (OutputStream stream = new FileOutputStream(archive)) {
        int n;
        while ((n = input.read(buffer)) != -1) {
          compressed += n;
          if (compressed > 128L * 1024 * 1024) throw new IOException("模型压缩包超过 128 MB 限制");
          stream.write(buffer, 0, n);
        }
      }
      try (ZipFile zip = new ZipFile(archive)) {
        java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
          ZipEntry e = entries.nextElement();
          if (++count > 2000) throw new IOException("模型文件过多");
          File out = new File(stage, e.getName());
          if (!out.getCanonicalPath().startsWith(prefix)
              || e.getName().contains("\\")
              || out.getCanonicalPath().equals(archive.getCanonicalPath()))
            throw new IOException("模型包含无效路径");
          if (e.isDirectory()) {
            out.mkdirs();
            continue;
          }
          out.getParentFile().mkdirs();
          CRC32 checksum = new CRC32();
          try (InputStream entry = zip.getInputStream(e);
              OutputStream stream = new FileOutputStream(out)) {
            int n;
            while ((n = entry.read(buffer)) != -1) {
              size += n;
              if (size > 512L * 1024 * 1024) throw new IOException("解压模型超过 512 MB 限制");
              stream.write(buffer, 0, n);
              checksum.update(buffer, 0, n);
            }
          }
          if (e.getCrc() != -1 && checksum.getValue() != e.getCrc())
            throw new IOException("模型文件校验失败");
        }
      }
    } finally {
      archive.delete();
    }
  }

  private static File findRoot(File f, int depth) {
    if (depth > 8) return null;
    if (new File(f, "am/final.mdl").isFile() && new File(f, "conf/model.conf").isFile()) return f;
    File[] children = f.listFiles();
    if (children != null)
      for (File child : children)
        if (child.isDirectory()) {
          File root = findRoot(child, depth + 1);
          if (root != null) return root;
        }
    return null;
  }

  private static void remove(File root) {
    File[] files = root.listFiles();
    if (files != null) for (File f : files) remove(f);
    root.delete();
  }

  public static synchronized Transcript transcribe(Context c, File file) throws Exception {
    File root = model(c);
    if (root == null) throw new IOException("请先在设置中导入离线中文模型");
    MediaExtractor extractor = new MediaExtractor();
    MediaCodec codec = null;
    StringBuilder text = new StringBuilder();
    JSONArray segments = new JSONArray();
    long duration = 0;
    try {
      extractor.setDataSource(file.getAbsolutePath());
      MediaFormat format = null;
      for (int i = 0; i < extractor.getTrackCount(); i++) {
        MediaFormat track = extractor.getTrackFormat(i);
        if (track.getString(MediaFormat.KEY_MIME).startsWith("audio/")) {
          format = track;
          extractor.selectTrack(i);
          break;
        }
      }
      if (format == null) throw new IOException("没有音频轨道");
      int rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
      if (format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) != 1)
        throw new IOException("离线转写目前只支持单声道录音");
      if (format.containsKey(MediaFormat.KEY_DURATION))
        duration = format.getLong(MediaFormat.KEY_DURATION) / 1000;
      codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
      codec.configure(format, null, null, 0);
      codec.start();
      boolean inputDone = false, outputDone = false;
      MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
      long deadline = android.os.SystemClock.elapsedRealtime() + Math.max(120000, duration * 4);
      try (Model model = new Model(root.getAbsolutePath());
          Recognizer recognizer = new Recognizer(model, (float) rate)) {
        recognizer.setWords(true);
        while (!outputDone) {
          if (android.os.SystemClock.elapsedRealtime() > deadline)
            throw new IOException("离线转写超时，请重试");
          if (!inputDone) {
            int index = codec.dequeueInputBuffer(10000);
            if (index >= 0) {
              ByteBuffer buffer = codec.getInputBuffer(index);
              int n = extractor.readSampleData(buffer, 0);
              if (n < 0) {
                codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                inputDone = true;
              } else {
                codec.queueInputBuffer(index, 0, n, extractor.getSampleTime(), 0);
                extractor.advance();
              }
            }
          }
          int index = codec.dequeueOutputBuffer(info, 10000);
          if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            MediaFormat decoded = codec.getOutputFormat();
            if (decoded.containsKey(MediaFormat.KEY_PCM_ENCODING)
                && decoded.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    != AudioFormat.ENCODING_PCM_16BIT) throw new IOException("音频解码格式不受支持");
          } else if (index >= 0) {
            try {
              if (info.size > 0) {
                ByteBuffer buffer = codec.getOutputBuffer(index);
                buffer.position(info.offset);
                buffer.limit(info.offset + info.size);
                byte[] pcm = new byte[info.size];
                buffer.get(pcm);
                if (recognizer.acceptWaveForm(pcm, pcm.length))
                  append(recognizer.getResult(), text, segments);
              }
            } finally {
              codec.releaseOutputBuffer(index, false);
            }
            outputDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
          }
        }
        append(recognizer.getFinalResult(), text, segments);
      }
      return new Transcript(text.toString().trim(), segments, "本机 Vosk", duration);
    } finally {
      extractor.release();
      if (codec != null) {
        try {
          codec.stop();
        } catch (Exception ignored) {
        }
        codec.release();
      }
    }
  }

  static void append(String json, StringBuilder text, JSONArray segments) throws JSONException {
    JSONObject j = new JSONObject(json);
    String value = j.optString("text", "").trim();
    if (value.isEmpty()) return;
    if (text.length() > 0) text.append("\n");
    text.append(value);
    JSONArray words = j.optJSONArray("result");
    long start = 0, end = 0;
    if (words != null && words.length() > 0) {
      start = Math.round(words.getJSONObject(0).optDouble("start", 0) * 1000);
      end = Math.round(words.getJSONObject(words.length() - 1).optDouble("end", 0) * 1000);
    }
    segments.put(
        new JSONObject()
            .put("start_ms", start)
            .put("end_ms", end)
            .put("text", value)
            .put("speaker", ""));
  }
}
