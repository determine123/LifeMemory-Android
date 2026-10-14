package com.lifememory.app;

import android.media.*;
import java.io.*;
import java.nio.*;
import java.util.Arrays;

/** Decode PCM16, downmix and average into bounded 16 kHz mono samples. */
final class PcmAudio {
  static final int RATE = 16000, MAX_SAMPLES = RATE * 600;

  static float[] read(File file) throws Exception {
    MediaExtractor x = new MediaExtractor();
    MediaCodec codec = null;
    try {
      x.setDataSource(file.getAbsolutePath());
      MediaFormat format = null;
      for (int i = 0; i < x.getTrackCount(); i++) {
        MediaFormat f = x.getTrackFormat(i);
        if (f.getString(MediaFormat.KEY_MIME).startsWith("audio/")) {
          format = f;
          x.selectTrack(i);
          break;
        }
      }
      if (format == null) throw new IOException("No audio track");
      if (format.containsKey(MediaFormat.KEY_DURATION)
          && format.getLong(MediaFormat.KEY_DURATION) > 600000000L)
        throw new IOException("Split audio into recordings of at most 10 minutes");
      int rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
          channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
      codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
      codec.configure(format, null, null, 0);
      codec.start();
      boolean inputDone = false, done = false;
      MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
      long deadline = android.os.SystemClock.elapsedRealtime() + 120000;
      float[] out = new float[8192];
      int size = 0;
      long frames = 0;
      double sum = 0;
      int accumulated = 0;
      while (!done) {
        if (android.os.SystemClock.elapsedRealtime() > deadline)
          throw new IOException("Audio decode timed out");
        if (!inputDone) {
          int i = codec.dequeueInputBuffer(10000);
          if (i >= 0) {
            ByteBuffer b = codec.getInputBuffer(i);
            int n = x.readSampleData(b, 0);
            if (n < 0) {
              codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
              inputDone = true;
            } else {
              codec.queueInputBuffer(i, 0, n, x.getSampleTime(), 0);
              x.advance();
            }
          }
        }
        int i = codec.dequeueOutputBuffer(info, 10000);
        if (i == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
          MediaFormat f = codec.getOutputFormat();
          rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE);
          channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
          if (f.containsKey(MediaFormat.KEY_PCM_ENCODING)
              && f.getInteger(MediaFormat.KEY_PCM_ENCODING) != AudioFormat.ENCODING_PCM_16BIT)
            throw new IOException("PCM16 audio required");
          if (rate < 8000 || channels < 1 || channels > 8)
            throw new IOException("Unsupported audio rate/channel count");
        } else if (i >= 0) {
          try {
            ByteBuffer b = codec.getOutputBuffer(i).order(ByteOrder.LITTLE_ENDIAN);
            b.position(info.offset);
            b.limit(info.offset + info.size);
            while (b.remaining() >= channels * 2) {
              double mono = 0;
              for (int c = 0; c < channels; c++) mono += b.getShort() / 32768.0;
              sum += mono / channels;
              accumulated++;
              frames++;
              if (frames * RATE / rate > size) {
                float value = (float) (sum / accumulated);
                while (frames * RATE / rate > size) {
                  if (size == MAX_SAMPLES) throw new IOException("Audio exceeds 10-minute limit");
                  if (size == out.length)
                    out = Arrays.copyOf(out, Math.min(MAX_SAMPLES, out.length * 2));
                  out[size++] = value;
                }
                sum = 0;
                accumulated = 0;
              }
            }
          } finally {
            codec.releaseOutputBuffer(i, false);
          }
          done = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
        }
      }
      return Arrays.copyOf(out, size);
    } finally {
      x.release();
      if (codec != null) {
        try {
          codec.stop();
        } catch (Exception ignored) {
        }
        codec.release();
      }
    }
  }
}
