package com.lifememory.app;

import static org.junit.Assert.*;

import android.content.*;
import android.media.*;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.nio.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class OfflineSpeechIntegrationTest {
  @Test
  public void chineseAudioSurvivesAacEncodingAndLocalTranscription() throws Exception {
    Context base = InstrumentationRegistry.getInstrumentation().getTargetContext();
    File fixtures = new File(base.getFilesDir(), "test-fixtures"),
        model = new File(fixtures, "model.zip"),
        wav = new File(fixtures, "speech.wav");
    Assume.assumeTrue(
        "Real ASR requires model.zip and speech.wav in files/test-fixtures",
        model.isFile() && wav.isFile());
    File work = new File(base.getCacheDir(), "real-asr-integration");
    work.mkdirs();
    Context isolated =
        new ContextWrapper(base) {
          public File getFilesDir() {
            return work;
          }

          public android.content.SharedPreferences getSharedPreferences(String name, int mode) {
            return base.getSharedPreferences("test-asr-" + name, mode);
          }
        };
    File encoded = new File(work, "sample.m4a");
    try {
      OfflineAsr.install(isolated, Uri.fromFile(model));
      encode(wav, encoded);
      Transcript result = OfflineAsr.transcribe(isolated, encoded);
      String compact = result.text.replace(" ", "").replace("\n", "");
      JSONObject report =
          new JSONObject()
              .put("source", "Mandarin Windows TTS; not live microphone speech")
              .put("expected", "今天下午三点，我们讨论安卓应用的开发计划。明天需要测试录音功能，并整理会议记录。请记住这两件事情。")
              .put("recognized", result.text)
              .put("duration_ms", result.durationMs)
              .put("segments", result.segments)
              .put("engine", result.engine);
      try (OutputStream out =
          new FileOutputStream(new File(base.getFilesDir(), "real-asr-result.json"))) {
        out.write(report.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
      }
      assertTrue("No recognized speech: " + result.text, !compact.isEmpty());
      assertTrue(
          "Expected topic absent: " + result.text,
          compact.contains("录音") || compact.contains("会议"));
      assertTrue(result.durationMs > 10000);
      assertTrue(result.segments.length() > 0);
      long previous = -1;
      for (int i = 0; i < result.segments.length(); i++) {
        JSONObject segment = result.segments.getJSONObject(i);
        long start = segment.getLong("start_ms"), end = segment.getLong("end_ms");
        assertTrue(start >= previous);
        assertTrue(end >= start);
        assertTrue(end <= result.durationMs + 500);
        previous = end;
      }
    } finally {
      isolated.getSharedPreferences("life", 0).edit().clear().commit();
      remove(work);
    }
  }

  private static void remove(File file) {
    File[] files = file.listFiles();
    if (files != null) for (File child : files) remove(child);
    file.delete();
  }

  private static void encode(File wav, File m4a) throws Exception {
    byte[] file;
    try (InputStream in = new FileInputStream(wav);
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      MainActivity.copy(in, out);
      file = out.toByteArray();
    }
    ByteBuffer header = ByteBuffer.wrap(file).order(ByteOrder.LITTLE_ENDIAN);
    int begin = -1, size = 0;
    for (int p = 12; p + 8 <= file.length; ) {
      int n = header.getInt(p + 4);
      if (file[p] == 'd' && file[p + 1] == 'a' && file[p + 2] == 't' && file[p + 3] == 'a') {
        begin = p + 8;
        size = n;
        break;
      }
      p += 8 + n + (n & 1);
    }
    if (begin < 0) throw new IOException("WAV data chunk missing");
    MediaCodec codec = MediaCodec.createEncoderByType("audio/mp4a-latm");
    MediaFormat format = MediaFormat.createAudioFormat("audio/mp4a-latm", 16000, 1);
    format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
    format.setInteger(MediaFormat.KEY_BIT_RATE, 32000);
    format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 4096);
    codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
    MediaMuxer muxer =
        new MediaMuxer(m4a.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
    boolean muxerStarted = false;
    try {
      codec.start();
      int consumed = 0, track = -1;
      boolean inputDone = false, outputDone = false;
      MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
      long deadline = android.os.SystemClock.elapsedRealtime() + 60000;
      while (!outputDone) {
        if (android.os.SystemClock.elapsedRealtime() > deadline)
          throw new IOException("AAC fixture encoding timeout");
        if (!inputDone) {
          int index = codec.dequeueInputBuffer(10000);
          if (index >= 0) {
            ByteBuffer input = codec.getInputBuffer(index);
            input.clear();
            int n = Math.min(size - consumed, input.remaining());
            long pts = consumed / 2L * 1000000 / 16000;
            if (n == 0) {
              codec.queueInputBuffer(index, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
              inputDone = true;
            } else {
              input.put(file, begin + consumed, n);
              codec.queueInputBuffer(index, 0, n, pts, 0);
              consumed += n;
            }
          }
        }
        int index = codec.dequeueOutputBuffer(info, 10000);
        if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
          track = muxer.addTrack(codec.getOutputFormat());
          muxer.start();
          muxerStarted = true;
        } else if (index >= 0) {
          if (info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
            ByteBuffer output = codec.getOutputBuffer(index);
            output.position(info.offset);
            output.limit(info.offset + info.size);
            muxer.writeSampleData(track, output, info);
          }
          outputDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
          codec.releaseOutputBuffer(index, false);
        }
      }
    } finally {
      codec.stop();
      codec.release();
      if (muxerStarted) muxer.stop();
      muxer.release();
    }
  }
}
