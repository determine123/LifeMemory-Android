package com.lifememory.app;

import static org.junit.Assert.*;

import android.content.*;
import android.database.sqlite.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.time.*;
import java.util.*;
import java.util.zip.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class UpgradeTest {
  private Context context;
  private Store db;

  @Before
  public void setup() {
    context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    context.deleteDatabase("upgrade-test.db");
    db = new Store(context, "upgrade-test.db");
  }

  @After
  public void cleanup() {
    db.close();
    context.deleteDatabase("upgrade-test.db");
  }

  @Test
  public void migrationPreservesVersionOneData() {
    db.close();
    context.deleteDatabase("upgrade-test.db");
    SQLiteDatabase old = context.openOrCreateDatabase("upgrade-test.db", 0, null);
    old.execSQL(
        "CREATE TABLE events(id INTEGER PRIMARY KEY,time INTEGER NOT NULL,kind TEXT NOT NULL,text"
            + " TEXT NOT NULL,path TEXT NOT NULL,status TEXT NOT NULL)");
    old.execSQL("CREATE TABLE summaries(day TEXT PRIMARY KEY,text TEXT NOT NULL)");
    old.execSQL(
        "CREATE TABLE todos(id INTEGER PRIMARY KEY,day TEXT NOT NULL,text TEXT NOT NULL,done"
            + " INTEGER NOT NULL DEFAULT 0)");
    old.execSQL("INSERT INTO events VALUES(7,1791820800000,'text','旧版本记录','','ready')");
    old.execSQL("INSERT INTO summaries VALUES('2026-10-13','旧版本回顾')");
    old.setVersion(1);
    old.close();
    db = new Store(context, "upgrade-test.db");
    assertEquals("旧版本记录", db.find(7).text);
    assertFalse(db.find(7).pinned);
    assertEquals(0, db.find(7).metadata().length());
    assertEquals("旧版本回顾", db.summary("2026-10-13"));
    assertEquals(2, db.getReadableDatabase().getVersion());
  }

  @Test
  public void audioRetentionProtectsBookmarksAndPinnedSources() throws Exception {
    File first = audio("expired"), second = audio("pinned"), third = audio("marked");
    long now = System.currentTimeMillis(), old = now - 90 * 86400000L;
    long one = db.add(old, "audio", "已转写文字", first.getAbsolutePath(), "ready"),
        two = db.add(old + 1, "audio", "重要", second.getAbsolutePath(), "ready");
    db.pin(two, true);
    db.add(old + 2, "audio", "被标记", third.getAbsolutePath(), "ready");
    long bookmark = db.add(old + 3, "bookmark", "瞬间", "", "ready");
    db.metadata(bookmark, new JSONObject().put("anchor_time", old + 2).put("offset_ms", 5000));
    try {
      assertEquals(1, db.purgeAudio(now - 30 * 86400000L));
      assertFalse(first.exists());
      assertTrue(second.exists());
      assertTrue(third.exists());
      assertEquals("已转写文字", db.find(one).text);
      assertEquals("", db.find(one).path);
    } finally {
      first.delete();
      second.delete();
      third.delete();
    }
  }

  private File audio(String prefix) throws Exception {
    File f = File.createTempFile(prefix, ".m4a", context.getCacheDir());
    try (OutputStream out = new FileOutputStream(f)) {
      out.write(new byte[] {1, 2, 3});
    }
    return f;
  }

  @Test
  public void newEvidenceMakesOldSummaryStale() {
    String day = Store.day(System.currentTimeMillis());
    db.add(System.currentTimeMillis(), "text", "第一条", "", "ready");
    db.summary(day, "已整理");
    assertFalse(db.summaryStale(day));
    db.add(System.currentTimeMillis(), "text", "第二条", "", "ready");
    assertTrue(db.summaryStale(day));
  }

  @Test
  public void inFlightRecapDoesNotClaimNewlyArrivingEvidence() {
    String day = Store.day(System.currentTimeMillis());
    db.add(System.currentTimeMillis(), "text", "第一条", "", "ready");
    String snapshot = Store.fingerprint(db.events(day, null));
    db.add(System.currentTimeMillis(), "text", "录音新片段", "", "ready");
    db.summary(day, "仅整理第一条");
    db.summarySnapshot(day, snapshot);
    assertTrue(db.summaryStale(day));
  }

  @Test
  public void failedRetryCanKeepExistingTranscript() throws Exception {
    long id = db.add(System.currentTimeMillis(), "audio", "原文", "", "ready");
    JSONArray parts = new JSONArray().put(new JSONObject().put("start_ms", 2500).put("text", "原文"));
    db.transcript(id, new Transcript("原文", parts, "local", 10000));
    db.status(id, "processing");
    assertEquals("原文", db.find(id).text);
    assertEquals(1, db.find(id).metadata().getJSONArray("segments").length());
  }

  @Test
  public void cloudTimeAndSpeakerAreNormalized() throws Exception {
    Transcript t =
        Transcript.cloud(
            "{\"text\":\"会议\",\"duration\":8.5,\"segments\":[{\"start\":2.5,\"end\":7.2,\"speaker\":\"A\",\"text\":\"会议\"}]}");
    assertEquals(8500, t.durationMs);
    assertEquals(2500, t.segments.getJSONObject(0).getLong("start_ms"));
    assertEquals("A", t.segments.getJSONObject(0).getString("speaker"));
  }

  @Test
  public void offlineWordsBecomeSeekableSegments() throws Exception {
    StringBuilder text = new StringBuilder();
    JSONArray segments = new JSONArray();
    OfflineAsr.append(
        "{\"text\":\"你好"
            + " 世界\",\"result\":[{\"start\":1.1,\"end\":2.0,\"word\":\"你好\"},{\"start\":2.3,\"end\":3.0,\"word\":\"世界\"}]}",
        text,
        segments);
    assertEquals("你好 世界", text.toString());
    assertEquals(1100, segments.getJSONObject(0).getLong("start_ms"));
    assertEquals(3000, segments.getJSONObject(0).getLong("end_ms"));
  }

  @Test
  public void modelZipCannotEscapePrivateDirectory() throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
      zip.putNextEntry(new ZipEntry("../escape.txt"));
      zip.write(1);
      zip.closeEntry();
    }
    File stage = new File(context.getCacheDir(), "zip-boundary-test");
    stage.mkdirs();
    try {
      OfflineAsr.extract(new ByteArrayInputStream(bytes.toByteArray()), stage);
      fail("Zip traversal should be rejected");
    } catch (IOException expected) {
      assertTrue(expected.getMessage().contains("路径"));
    } finally {
      stage.delete();
    }
  }

  @Test
  public void recapScheduleMovesToTomorrowAfterChosenTime() {
    assertEquals(30 * 60000L, RecapWorker.delay(LocalDateTime.of(2026, 10, 13, 21, 30), 22, 0));
    assertEquals(23 * 3600000L, RecapWorker.delay(LocalDateTime.of(2026, 10, 13, 23, 0), 22, 0));
  }

  @Test
  public void recapAcceptsFenceButRejectsMalformedJson() throws Exception {
    assertEquals(
        "回顾",
        RecapEngine.parse("```json\n{\"summary\":\"回顾\",\"todos\":[]}\n```").getString("summary"));
    try {
      RecapEngine.parse("无效JSON");
      fail("Malformed JSON should fail");
    } catch (JSONException expected) {
    }
  }

  @Test
  public void nativeVoskBridgeLoadsOnAndroid() {
    org.vosk.LibVosk.vosk_set_log_level(-1);
  }
}
