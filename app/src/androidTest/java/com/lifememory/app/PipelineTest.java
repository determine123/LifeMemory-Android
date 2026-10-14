package com.lifememory.app;

import static org.junit.Assert.*;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class PipelineTest {
  Context c;
  Store db;

  @Before
  public void setup() {
    c = InstrumentationRegistry.getInstrumentation().getTargetContext();
    c.deleteDatabase("pipeline-test.db");
    db = new Store(c, "pipeline-test.db");
  }

  @After
  public void cleanup() {
    for (Store.Event e : db.events(null, null)) if (!e.path.isEmpty()) new File(e.path).delete();
    db.close();
    c.deleteDatabase("pipeline-test.db");
  }

  byte[] zip(JSONObject manifest, String name, byte[] media) throws Exception {
    ByteArrayOutputStream b = new ByteArrayOutputStream();
    try (ZipOutputStream z = new ZipOutputStream(b)) {
      z.putNextEntry(new ZipEntry("records.json"));
      z.write(manifest.toString().getBytes("UTF-8"));
      z.closeEntry();
      if (name != null) {
        z.putNextEntry(new ZipEntry(name));
        z.write(media);
        z.closeEntry();
      }
    }
    return b.toByteArray();
  }

  JSONObject event(long id) throws Exception {
    return new JSONObject()
        .put("id", id)
        .put("time", System.currentTimeMillis())
        .put("kind", "audio")
        .put("text", "meeting transcript")
        .put("media", "media/test.wav")
        .put("status", "processing")
        .put("pinned", true)
        .put("meta", new JSONObject().put("topics", Topics.parse("Work, 工作")));
  }

  @Test
  public void backupMergeRemapsSourcesAndIsIdempotent() throws Exception {
    long existing = db.add(1, "text", "existing", "", "ready");
    String day = Store.day(System.currentTimeMillis());
    JSONObject detail =
        new JSONObject()
            .put("summary", "A meeting.")
            .put(
                "events",
                new JSONArray()
                    .put(
                        new JSONObject()
                            .put("text", "Meeting")
                            .put("source_ids", new JSONArray().put(99))))
            .put("todos", new JSONArray());
    JSONObject manifest =
        new JSONObject()
            .put("version", 3)
            .put("events", new JSONArray().put(event(99)))
            .put("summaries", new JSONObject().put(day, "A meeting."))
            .put("recaps", new JSONObject().put(day, detail))
            .put(
                "todos",
                new JSONObject()
                    .put(
                        day,
                        new JSONArray()
                            .put(
                                new JSONObject()
                                    .put("text", "Follow up")
                                    .put("done", 1)
                                    .put("source_ids", new JSONArray().put(99)))));
    byte[] archive = zip(manifest, "media/test.wav", new byte[] {1, 2, 3});
    assertEquals(1, BackupImport.restore(c, db, new ByteArrayInputStream(archive)));
    assertEquals(0, BackupImport.restore(c, db, new ByteArrayInputStream(archive)));
    assertNotNull(db.find(existing));
    assertEquals(2, db.events(null, null).size());
    Store.Event restored = db.events(null, null).get(0);
    assertTrue(restored.pinned);
    assertEquals("pending", restored.status);
    assertTrue(new File(restored.path).isFile());
    assertEquals(
        restored.id,
        db.recapDetails(day)
            .getJSONArray("events")
            .getJSONObject(0)
            .getJSONArray("source_ids")
            .getLong(0));
    assertEquals(1, db.todos(day).getJSONObject(0).getInt("done"));
    assertEquals(restored.id, db.todos(day).getJSONObject(0).getJSONArray("source_ids").getLong(0));
  }

  @Test
  public void missingMediaRollsBackWithoutDeletingExistingData() throws Exception {
    long id = db.add(1, "text", "keep me", "", "ready");
    JSONObject archive =
        new JSONObject().put("version", 2).put("events", new JSONArray().put(event(99)));
    try {
      BackupImport.restore(c, db, new ByteArrayInputStream(zip(archive, null, null)));
      fail("Missing attachment accepted");
    } catch (IOException expected) {
    }
    assertEquals(1, db.events(null, null).size());
    assertNotNull(db.find(id));
  }

  @Test
  public void zipTraversalIsRejected() throws Exception {
    JSONObject manifest = new JSONObject().put("version", 2).put("events", new JSONArray());
    try {
      BackupImport.restore(
          c, db, new ByteArrayInputStream(zip(manifest, "../escaped", new byte[] {1})));
      fail("Traversal accepted");
    } catch (IOException expected) {
    }
    assertTrue(db.events(null, null).isEmpty());
  }

  @Test
  public void malformedTodosRollBackCopiedMediaAndRecords() throws Exception {
    String day = Store.day(System.currentTimeMillis());
    JSONObject manifest =
        new JSONObject()
            .put("version", 2)
            .put("events", new JSONArray().put(event(99)))
            .put(
                "todos",
                new JSONObject().put(day, new JSONArray().put(new JSONObject().put("done", 1))));
    try {
      BackupImport.restore(
          c, db, new ByteArrayInputStream(zip(manifest, "media/test.wav", new byte[] {1})));
      fail("Malformed task accepted");
    } catch (JSONException expected) {
    }
    assertTrue(db.events(null, null).isEmpty());
  }

  @Test
  public void recapRejectsInventedSourcesAndPreservesCompletedTasks() throws Exception {
    long id = db.add(System.currentTimeMillis(), "text", "Meeting", "", "ready");
    JSONArray sources = new JSONArray().put(id).put(id).put(9999);
    JSONObject raw =
        new JSONObject()
            .put("summary", "A short recap")
            .put(
                "events",
                new JSONArray()
                    .put(new JSONObject().put("text", "Grounded").put("source_ids", sources))
                    .put(
                        new JSONObject()
                            .put("text", "Invented")
                            .put("source_ids", new JSONArray().put(9999))))
            .put(
                "todos",
                new JSONArray()
                    .put(new JSONObject().put("text", "Follow up").put("source_ids", sources)));
    JSONObject validated = RecapSources.validate(raw, db.events(null, null));
    assertEquals(1, validated.getJSONArray("events").length());
    assertEquals(
        1, validated.getJSONArray("events").getJSONObject(0).getJSONArray("source_ids").length());
    String day = Store.day(System.currentTimeMillis());
    db.recapDetailed(day, validated);
    db.toggle(db.todos(day).getJSONObject(0).getLong("id"), true);
    db.recapDetailed(day, validated);
    assertEquals(1, db.todos(day).getJSONObject(0).getInt("done"));
  }

  @Test
  public void topicsSupportCaseInsensitiveHistoricalRecall() throws Exception {
    long id = db.add(1, "text", "First note", "", "ready");
    db.metadata(id, new JSONObject().put("topics", Topics.parse("Work,work, 工作")));
    db.add(2, "text", "Unrelated", "", "ready");
    assertEquals(2, Topics.list(db.events(null, null)).size());
    assertEquals(id, Topics.select("WORK", db.events(null, null)).get(0).id);
    assertEquals(
        id,
        MemorySearch.select("work", db.events(null, null), java.time.LocalDate.now()).get(0).id);
  }

  @Test
  public void manualSpeakerCorrectionSurvivesMatchingRetranscription() throws Exception {
    long id = db.add(1, "audio", "original", "", "ready");
    org.json.JSONArray parts =
        new org.json.JSONArray()
            .put(
                new org.json.JSONObject()
                    .put("start_ms", 0)
                    .put("end_ms", 1000)
                    .put("text", "hello")
                    .put("speaker", "Alice")
                    .put("speaker_manual", true));
    db.metadata(id, new org.json.JSONObject().put("segments", parts));
    org.json.JSONArray newer =
        new org.json.JSONArray()
            .put(
                new org.json.JSONObject()
                    .put("start_ms", 0)
                    .put("end_ms", 1000)
                    .put("text", "hello")
                    .put("speaker", "Speaker 1"));
    db.transcript(id, new Transcript("hello", newer, "test", 1000));
    assertEquals(
        "Alice",
        db.find(id).metadata().getJSONArray("segments").getJSONObject(0).getString("speaker"));
  }

  @Test
  public void decoderDownmixesStereoAndUpsamplesEightKhz() throws Exception {
    for (int rate : new int[] {8000, 44100}) {
      int channels = rate == 8000 ? 1 : 2, size = rate * channels * 2;
      java.nio.ByteBuffer b =
          java.nio.ByteBuffer.allocate(44 + size).order(java.nio.ByteOrder.LITTLE_ENDIAN);
      b.put("RIFF".getBytes("US-ASCII"))
          .putInt(36 + size)
          .put("WAVEfmt ".getBytes("US-ASCII"))
          .putInt(16)
          .putShort((short) 1)
          .putShort((short) channels)
          .putInt(rate)
          .putInt(rate * channels * 2)
          .putShort((short) (channels * 2))
          .putShort((short) 16)
          .put("data".getBytes("US-ASCII"))
          .putInt(size);
      for (int i = 0; i < rate; i++)
        for (int channel = 0; channel < channels; channel++)
          b.putShort((short) (channel == 0 ? 1000 : 3000));
      File f = File.createTempFile("pcm-", ".wav", c.getCacheDir());
      try {
        try (FileOutputStream out = new FileOutputStream(f)) {
          out.write(b.array());
        }
        float[] pcm = PcmAudio.read(f);
        assertEquals(16000, pcm.length);
        assertEquals((channels == 1 ? 1000f : 2000f) / 32768f, pcm[8000], 0.001f);
      } finally {
        f.delete();
      }
    }
  }

  @Test
  public void versionTwoMigrationPreservesRecapAndCompletedTask() throws Exception {
    String name = "migration-v2-test.db";
    c.deleteDatabase(name);
    android.database.sqlite.SQLiteDatabase old = c.openOrCreateDatabase(name, 0, null);
    old.execSQL(
        "CREATE TABLE events(id INTEGER PRIMARY KEY,time INTEGER NOT NULL,kind TEXT NOT NULL,text"
            + " TEXT NOT NULL,path TEXT NOT NULL,status TEXT NOT NULL,meta TEXT NOT NULL DEFAULT"
            + " '{}',pinned INTEGER NOT NULL DEFAULT 0)");
    old.execSQL(
        "CREATE TABLE summaries(day TEXT PRIMARY KEY,text TEXT NOT NULL,fingerprint TEXT NOT NULL"
            + " DEFAULT '',updated INTEGER NOT NULL DEFAULT 0)");
    old.execSQL(
        "CREATE TABLE todos(id INTEGER PRIMARY KEY,day TEXT NOT NULL,text TEXT NOT NULL,done"
            + " INTEGER NOT NULL DEFAULT 0)");
    old.execSQL("INSERT INTO events VALUES(7,1,'audio','keep transcript','','ready','{}',1)");
    old.execSQL("INSERT INTO summaries VALUES('2026-10-14','keep recap','',0)");
    old.execSQL("INSERT INTO todos VALUES(5,'2026-10-14','completed',1)");
    old.setVersion(2);
    old.close();
    try (Store migrated = new Store(c, name)) {
      assertTrue(migrated.find(7).pinned);
      assertEquals("keep transcript", migrated.find(7).text);
      assertEquals("keep recap", migrated.summary("2026-10-14"));
      assertEquals(1, migrated.todos("2026-10-14").getJSONObject(0).getInt("done"));
      assertEquals(
          0, migrated.todos("2026-10-14").getJSONObject(0).getJSONArray("source_ids").length());
      assertEquals(3, migrated.getReadableDatabase().getVersion());
    } finally {
      c.deleteDatabase(name);
    }
  }

  @Test
  public void missingBookmarkSourceDoesNotPointAtAnUnrelatedRecord() throws Exception {
    long unrelated = db.add(1, "text", "unrelated", "", "ready");
    JSONObject mark =
        new JSONObject()
            .put("id", 99)
            .put("time", 2)
            .put("kind", "bookmark")
            .put("text", "mark")
            .put("media", "")
            .put(
                "meta",
                new JSONObject().put("anchor_time", 9999).put("anchor_event_id", unrelated));
    JSONObject manifest =
        new JSONObject().put("version", 3).put("events", new JSONArray().put(mark));
    assertEquals(
        1, BackupImport.restore(c, db, new ByteArrayInputStream(zip(manifest, null, null))));
    assertEquals(-1, db.events(null, null).get(0).metadata().getLong("anchor_event_id"));
  }
}
