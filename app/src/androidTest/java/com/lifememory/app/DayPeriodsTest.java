package com.lifememory.app;

import static org.junit.Assert.*;

import android.app.Instrumentation;
import android.content.*;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.view.*;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.time.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class DayPeriodsTest {
  Context c;

  @Before
  public void setup() {
    Context base = InstrumentationRegistry.getInstrumentation().getTargetContext();
    base.deleteDatabase("period-test.db");
    c =
        new ContextWrapper(base) {
          public Context getApplicationContext() {
            return this;
          }

          public SharedPreferences getSharedPreferences(String name, int mode) {
            return base.getSharedPreferences("period-test-" + name, mode);
          }

          public SQLiteDatabase openOrCreateDatabase(
              String name, int mode, SQLiteDatabase.CursorFactory f, DatabaseErrorHandler e) {
            return base.openOrCreateDatabase("period-test.db", mode, f, e);
          }
        };
    c.getSharedPreferences("ai", 0).edit().clear().commit();
    c.getSharedPreferences("period-recaps", 0).edit().clear().commit();
  }

  @After
  public void cleanup() {
    InstrumentationRegistry.getInstrumentation()
        .getTargetContext()
        .deleteDatabase("period-test.db");
    c.getSharedPreferences("period-recaps", 0).edit().clear().commit();
  }

  Store.Event event(int hour, int minute, long id) {
    long t =
        LocalDate.of(2099, 2, 1)
            .atTime(hour, minute)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli();
    return new Store.Event(id, t, "text", "source-" + id, "", "ready");
  }

  @Test
  public void boundariesAssignEachRecordExactlyOnce() {
    List<Store.Event> all =
        Arrays.asList(
            event(0, 0, 1),
            event(10, 59, 2),
            event(11, 0, 3),
            event(13, 59, 4),
            event(14, 0, 5),
            event(17, 59, 6),
            event(18, 0, 7),
            event(23, 59, 8));
    Set<Long> ids = new HashSet<>();
    for (int i = 0; i < 4; i++) {
      List<Store.Event> bucket = DayPeriods.select(all, i);
      assertEquals(2, bucket.size());
      for (Store.Event e : bucket) assertTrue(ids.add(e.id));
    }
    assertEquals(8, ids.size());
  }

  @Test
  public void midnightBelongsToPreviousEveningForScheduledRecap() {
    ZonedDateTime now = LocalDate.of(2099, 2, 2).atStartOfDay(ZoneId.systemDefault());
    assertEquals(LocalDate.of(2099, 2, 1), DayPeriodWorker.latestDay(now, 3));
    assertEquals(24 * 60 * 60 * 1000L, DayPeriodWorker.delay(now, 3));
    assertEquals(11 * 60 * 60 * 1000L, DayPeriodWorker.delay(now, 0));
  }

  @Test
  public void delayedMorningJobUsesMostRecentlyFinishedMorning() {
    ZonedDateTime now = LocalDate.of(2099, 2, 2).atTime(15, 0).atZone(ZoneId.systemDefault());
    assertEquals(LocalDate.of(2099, 2, 2), DayPeriodWorker.latestDay(now, 0));
    assertEquals(LocalDate.of(2099, 2, 1), DayPeriodWorker.latestDay(now, 2));
  }

  @Test
  public void localRecapsAreSeparateAndRefreshAfterSourceEdits() throws Exception {
    try (Store db = new Store(c)) {
      for (int i = 0; i < 4; i++) {
        Store.Event e = event(new int[] {9, 12, 16, 21}[i], 0, i + 1);
        db.add(e.time, e.kind, e.text, e.path, e.status);
      }
      DayPeriods.all(c, "2099-02-01", true);
      for (int i = 0; i < 4; i++) {
        org.json.JSONObject saved = DayPeriods.saved(c, "2099-02-01", i);
        assertEquals(1, saved.getInt("record_count"));
        assertTrue(
            saved.getString("summary").codePointCount(0, saved.getString("summary").length())
                <= 50);
        assertTrue(DayPeriods.fresh(c, saved, DayPeriods.select(db.events("2099-02-01", null), i)));
      }
      Store.Event e = DayPeriods.select(db.events("2099-02-01", null), 1).get(0);
      db.update(e.id, "edited lunch", "ready");
      assertFalse(
          DayPeriods.fresh(
              c,
              DayPeriods.saved(c, "2099-02-01", 1),
              DayPeriods.select(db.events("2099-02-01", null), 1)));
      DayPeriods.generate(c, "2099-02-01", 1, false);
      assertTrue(
          DayPeriods.fresh(
              c,
              DayPeriods.saved(c, "2099-02-01", 1),
              DayPeriods.select(db.events("2099-02-01", null), 1)));
    }
  }

  boolean contains(View v, String text) {
    if (v instanceof TextView && ((TextView) v).getText().toString().contains(text)) return true;
    if (v instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++)
        if (contains(((ViewGroup) v).getChildAt(i), text)) return true;
    return false;
  }

  @Test
  public void homepageShowsFourPeriodsAndHidesMinuteRecords() {
    Instrumentation instrument = InstrumentationRegistry.getInstrumentation();
    Context base = instrument.getTargetContext();
    long id;
    try (Store db = new Store(base)) {
      Store.Event e = event(9, 1, 1);
      id = db.add(e.time, "audio", "MINUTE_SOURCE_SHOULD_BE_HIDDEN", "", "pending");
    }
    MainActivity a =
        (MainActivity)
            instrument.startActivitySync(
                new Intent(base, MainActivity.class)
                    .putExtra("day", "2099-02-01")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    try {
      instrument.runOnMainSync(
          () -> {
            a.selected = "2099-02-01";

            a.render();
            for (String range : DayPeriods.RANGE) assertTrue(contains(a.body, range));
            assertFalse(contains(a.body, "MINUTE_SOURCE_SHOULD_BE_HIDDEN"));
          });
    } finally {
      instrument.runOnMainSync(a::finish);
      try (Store db = new Store(base)) {
        db.delete(db.find(id));
      }
    }
  }
}
