package com.lifememory.app;

import static org.junit.Assert.*;
import java.util.Arrays;
import org.junit.Test;

public class SpeechChunksTest {
  @Test public void prefersPauseBeforeThirtySeconds() {
    float[] audio = new float[65 * SpeechChunks.RATE];
    Arrays.fill(audio, 0.1f);
    Arrays.fill(audio, 28 * SpeechChunks.RATE, 29 * SpeechChunks.RATE, 0f);
    int end = SpeechChunks.nextEnd(audio, 0, audio.length);
    assertTrue(end > 28 * SpeechChunks.RATE && end < 29 * SpeechChunks.RATE);
  }

  @Test public void continuousSpeechUsesMaximumAndPreservesTail() {
    float[] audio = new float[65 * SpeechChunks.RATE + 13];
    Arrays.fill(audio, 0.02f);
    assertEquals(SpeechChunks.MAX, SpeechChunks.nextEnd(audio, 0, audio.length));
    int start = 0, count = 0;
    while (start < audio.length) {
      int end = SpeechChunks.nextEnd(audio, start, audio.length);
      assertTrue(end > start && end - start <= SpeechChunks.MAX);
      count += end - start;
      start = end;
    }
    assertEquals(audio.length, count);
  }

  @Test public void quietVoiceIsNeverClassifiedAsDigitalSilence() {
    float[] audio = new float[16000];
    assertTrue(SpeechChunks.digitalSilence(audio, 0, audio.length));
    audio[8000] = 0.000001f;
    assertFalse(SpeechChunks.digitalSilence(audio, 0, audio.length));
  }

  @Test public void speakerTurnBoundsArePreserved() {
    float[] audio = new float[SpeechChunks.RATE * 90];
    assertEquals(230000, SpeechChunks.nextEnd(audio, 5000, 230000));
    assertTrue(SpeechChunks.nextEnd(audio, 7000, audio.length) <= 7000 + SpeechChunks.MAX);
  }

  @Test public void invalidIntervalsFailExplicitly() {
    try {
      SpeechChunks.nextEnd(new float[4], 4, 4);
      fail("Expected invalid interval rejection");
    } catch (IllegalArgumentException expected) { }
  }
}
