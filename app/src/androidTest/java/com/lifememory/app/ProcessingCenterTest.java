package com.lifememory.app;

import static org.junit.Assert.*;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ProcessingCenterTest {
  @Test
  public void failedRetryWithExistingTranscriptRemainsVisible() throws Exception {
    Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
    c.deleteDatabase("processing-test.db");
    try (Store db = new Store(c, "processing-test.db")) {
      long now = System.currentTimeMillis();
      long id = db.add(now, "audio", "已保存的原转写", "", "ready");
      db.metadata(
          id, new JSONObject().put("segments", new org.json.JSONArray()).put("asr_error", "服务不可用"));
      assertEquals("需处理", ProcessingCenter.state(db.find(id)));
      db.metadata(id, new JSONObject().put("segments", new org.json.JSONArray()));
      assertEquals("已完成", ProcessingCenter.state(db.find(id)));
      db.status(id, "processing");
      assertEquals("转写中", ProcessingCenter.state(db.find(id)));
      db.add(now, "audio", "等待", "", "pending");
      db.add(now, "text", "文字不进入音频统计", "", "ready");
      assertArrayEquals(
          new int[] {2, 0, 0}, ProcessingCenter.counts(db.events(Store.day(now), null)));
      assertEquals("已保存的原转写", db.find(id).text);
    } finally {
      c.deleteDatabase("processing-test.db");
    }
  }
}
