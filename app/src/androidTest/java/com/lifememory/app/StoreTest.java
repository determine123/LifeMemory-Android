package com.lifememory.app;

import static org.junit.Assert.*;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class StoreTest {
  private Store store;
  private Context context;

  @Before
  public void setUp() {
    context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    context.deleteDatabase("integration-test.db");
    store = new Store(context, "integration-test.db");
  }

  @After
  public void tearDown() {
    store.close();
    context.deleteDatabase("integration-test.db");
  }

  @Test
  public void dateBoundaryUsesLocalTimezone() {
    TimeZone prior = TimeZone.getDefault();
    try {
      TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
      assertEquals("2026-10-13", Store.day(1791820800000L));
    } finally {
      TimeZone.setDefault(prior);
    }
  }

  @Test
  public void searchSpansDatesAndPreservesSources() {
    long now = System.currentTimeMillis();
    long a = store.add(now - 86400000, "text", "Discuss OpenClaw", "", "ready");
    long b = store.add(now, "photo", "OPENCLAW slide", "/private/photo.jpg", "ready");
    List<Store.Event> found = store.events(null, "openclaw");
    assertEquals(2, found.size());
    assertEquals(b, found.get(0).id);
    assertEquals(a, found.get(1).id);
    assertEquals(1, store.events(Store.day(now), "openclaw").size());
  }

  @Test
  public void recapDeduplicatesWithoutResettingCompletion() throws Exception {
    String day = "2026-10-13";
    JSONArray tasks = new JSONArray().put("部署测试环境").put("部署测试环境");
    store.recap(day, "第一次回顾", tasks);
    JSONArray first = store.todos(day);
    assertEquals(1, first.length());
    store.toggle(first.getJSONObject(0).getLong("id"), true);
    store.recap(day, "第二次回顾", tasks);
    assertEquals(1, store.todos(day).length());
    assertEquals(1, store.todos(day).getJSONObject(0).getInt("done"));
    assertEquals("第二次回顾", store.summary(day));
  }

  @Test
  public void failedRecapRollsBackSummaryAndTasks() throws Exception {
    store.summary("2026-10-13", "原回顾");
    try {
      store.recap("2026-10-13", "不应保存", new JSONArray().put("待办").put(new JSONObject()));
      fail("Invalid task should fail");
    } catch (JSONException expected) {
    }
    assertEquals("原回顾", store.summary("2026-10-13"));
    assertEquals(0, store.todos("2026-10-13").length());
  }

  @Test
  public void backupDatesIncludeOrphanedSummariesAndTodos() {
    store.summary("2026-10-10", "无原始事件的回顾");
    store.todo("2026-10-11", "独立待办");
    assertTrue(store.allDays().contains("2026-10-10"));
    assertTrue(store.allDays().contains("2026-10-11"));
  }

  @Test
  public void mediaExportUsesPortablePath() throws Exception {
    long id =
        store.add(System.currentTimeMillis(), "audio", "会议", "/private/media/audio-1.m4a", "ready");
    Store.Event e = store.events(null, null).get(0);
    assertEquals(id, e.id);
    assertEquals("media/audio-1.m4a", e.json().getString("media"));
  }
}
