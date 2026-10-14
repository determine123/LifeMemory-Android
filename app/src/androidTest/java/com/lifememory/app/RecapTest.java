package com.lifememory.app;

import static org.junit.Assert.*;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class RecapTest {
  @Test
  public void summaryBoundsUnicodeAndRemovesReferences() {
    StringBuilder input = new StringBuilder("今天完成开发[记录 #123]。");
    for (int i = 0; i < 20; i++) input.append("讨论明天计划😀");
    String result = RecapEngine.compact(input.toString());
    assertTrue(result.codePointCount(0, result.length()) <= 50);
    assertFalse(result.contains("记录 #"));
    assertFalse(Character.isHighSurrogate(result.charAt(result.length() - 1)));
    assertEquals("今天休息。", RecapEngine.compact(" 今天休息。 "));
  }

  @Test
  public void localOverviewDistinguishesPendingAudioAndKeepsRecords() {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    context.deleteDatabase("recap-test.db");
    try (Store db = new Store(context, "recap-test.db")) {
      long now = System.currentTimeMillis();
      db.add(now, "audio", "录音待转写", "", "pending");
      db.add(now, "audio", "完成项目讨论", "", "ready");
      db.add(now, "photo", "白板", "", "ready");
      String summary = RecapEngine.localSummary(db.events(Store.day(now), null));
      assertTrue(summary.contains("2段录音"));
      assertTrue(summary.contains("部分录音待转写"));
      assertTrue(summary.codePointCount(0, summary.length()) <= 50);
      assertTrue(RecapEngine.evidence(db.events(Store.day(now), null), 300).contains("完成项目讨论"));
    } finally {
      context.deleteDatabase("recap-test.db");
    }
  }
}
