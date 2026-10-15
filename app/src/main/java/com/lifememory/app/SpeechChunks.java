package com.lifememory.app;

/** Pause-aware bounded chunks, without dropping samples or changing source offsets. */
final class SpeechChunks {
  static final int RATE = 16000, MAX = 30 * RATE;

  static int nextEnd(float[] samples, int start, int end) {
    if (start < 0 || end > samples.length || start >= end)
      throw new IllegalArgumentException("Invalid audio interval");
    int limit = Math.min(end, start + MAX);
    if (limit == end) return end;
    double sum = 0;
    for (int i = start; i < limit; i++) sum += samples[i] * (double) samples[i];
    double average = sum / (limit - start);
    int window = RATE / 5, best = limit;
    double lowest = average * 0.1;
    // Only move a boundary into a clearly quieter 200 ms window in the last 5 s.
    // Quiet speech is still decoded; this is not a neural speech detector.
    for (int at = limit - 5 * RATE; at + window <= limit; at += RATE / 50) {
      double energy = 0;
      for (int i = at; i < at + window; i++) energy += samples[i] * (double) samples[i];
      energy /= window;
      if (energy <= lowest) {
        lowest = energy;
        best = at + window / 2;
      }
    }
    return best;
  }

  static boolean digitalSilence(float[] samples, int start, int end) {
    for (int i = start; i < end; i++) if (samples[i] != 0f) return false;
    return true;
  }
}
