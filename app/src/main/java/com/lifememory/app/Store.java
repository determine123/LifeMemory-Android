package com.lifememory.app;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.text.SimpleDateFormat;
import java.util.*;
import org.json.*;

public class Store extends SQLiteOpenHelper {
  public Store(Context c) {
    this(c, "memory.db");
  }

  Store(Context c, String name) {
    super(c, name, null, 2);
  }

  public void onCreate(SQLiteDatabase d) {
    d.execSQL(
        "CREATE TABLE events(id INTEGER PRIMARY KEY, time INTEGER NOT NULL, kind TEXT NOT NULL,"
            + " text TEXT NOT NULL, path TEXT NOT NULL, status TEXT NOT NULL, meta TEXT NOT NULL"
            + " DEFAULT '{}', pinned INTEGER NOT NULL DEFAULT 0)");
    d.execSQL(
        "CREATE TABLE summaries(day TEXT PRIMARY KEY, text TEXT NOT NULL, fingerprint TEXT NOT NULL"
            + " DEFAULT '', updated INTEGER NOT NULL DEFAULT 0)");
    d.execSQL(
        "CREATE TABLE todos(id INTEGER PRIMARY KEY, day TEXT NOT NULL, text TEXT NOT NULL, done"
            + " INTEGER NOT NULL DEFAULT 0)");
    d.execSQL("CREATE INDEX events_time ON events(time)");
  }

  public void onUpgrade(SQLiteDatabase d, int old, int next) {
    if (old < 2) {
      d.execSQL("ALTER TABLE events ADD COLUMN meta TEXT NOT NULL DEFAULT '{}'");
      d.execSQL("ALTER TABLE events ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0");
      d.execSQL("ALTER TABLE summaries ADD COLUMN fingerprint TEXT NOT NULL DEFAULT ''");
      d.execSQL("ALTER TABLE summaries ADD COLUMN updated INTEGER NOT NULL DEFAULT 0");
      d.execSQL("CREATE INDEX IF NOT EXISTS events_time ON events(time)");
    }
  }

  public synchronized long add(long time, String kind, String text, String path, String status) {
    ContentValues v = new ContentValues();
    v.put("time", time);
    v.put("kind", kind);
    v.put("text", text);
    v.put("path", path);
    v.put("status", status);
    return getWritableDatabase().insertOrThrow("events", null, v);
  }

  public synchronized void update(long id, String text, String status) {
    ContentValues v = new ContentValues();
    v.put("text", text);
    v.put("status", status);
    Event event = find(id);
    if (event != null) {
      JSONObject meta = event.metadata();
      meta.remove("segments");
      v.put("meta", meta.toString());
    }
    getWritableDatabase().update("events", v, "id=?", new String[] {"" + id});
  }

  public static String day(long t) {
    return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(t));
  }

  public List<Event> events(String day, String query) {
    List<Event> out = new ArrayList<>();
    String sql = "SELECT * FROM events";
    String[] args = null;
    if (day != null) {
      java.time.LocalDate date = java.time.LocalDate.parse(day);
      java.time.ZoneId zone = java.time.ZoneId.systemDefault();
      sql += " WHERE time>=? AND time<?";
      args =
          new String[] {
            "" + date.atStartOfDay(zone).toInstant().toEpochMilli(),
            "" + date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
          };
    }
    sql += " ORDER BY time DESC,id DESC";
    try (Cursor c = getReadableDatabase().rawQuery(sql, args)) {
      while (c.moveToNext()) {
        Event e =
            new Event(
                c.getLong(0),
                c.getLong(1),
                c.getString(2),
                c.getString(3),
                c.getString(4),
                c.getString(5),
                c.getString(6),
                c.getInt(7) == 1);
        if ((day == null || day(e.time).equals(day))
            && (query == null
                || e.text.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))))
          out.add(e);
      }
    }
    return out;
  }

  public void delete(Event e) {
    getWritableDatabase().delete("events", "id=?", new String[] {"" + e.id});
    if (!e.path.isEmpty()) new java.io.File(e.path).delete();
  }

  public String summary(String day) {
    try (Cursor c =
        getReadableDatabase()
            .rawQuery("SELECT text FROM summaries WHERE day=?", new String[] {day})) {
      return c.moveToFirst() ? c.getString(0) : "";
    }
  }

  public void summary(String day, String text) {
    ContentValues v = new ContentValues();
    v.put("day", day);
    v.put("text", text);
    v.put("fingerprint", fingerprint(events(day, null)));
    v.put("updated", System.currentTimeMillis());
    getWritableDatabase()
        .insertWithOnConflict("summaries", null, v, SQLiteDatabase.CONFLICT_REPLACE);
  }

  public void todo(String day, String text) {
    ContentValues v = new ContentValues();
    v.put("day", day);
    v.put("text", text);
    getWritableDatabase().insert("todos", null, v);
  }

  public void recap(String day, String summary, JSONArray tasks) throws JSONException {
    SQLiteDatabase d = getWritableDatabase();
    d.beginTransaction();
    try {
      summary(day, summary);
      for (int n = 0; n < tasks.length(); n++) {
        Object raw = tasks.get(n);
        if (!(raw instanceof String)) throw new JSONException("待办必须是字符串");
        String t = ((String) raw).trim();
        if (t.isEmpty()) continue;
        try (Cursor c =
            d.rawQuery("SELECT id FROM todos WHERE day=? AND text=?", new String[] {day, t})) {
          if (!c.moveToFirst()) todo(day, t);
        }
      }
      d.setTransactionSuccessful();
    } finally {
      d.endTransaction();
    }
  }

  public JSONArray todos(String day) throws JSONException {
    JSONArray a = new JSONArray();
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(
                "SELECT id,text,done FROM todos WHERE day=? ORDER BY id", new String[] {day})) {
      while (c.moveToNext())
        a.put(
            new JSONObject()
                .put("id", c.getLong(0))
                .put("text", c.getString(1))
                .put("done", c.getInt(2)));
    }
    return a;
  }

  public void toggle(long id, boolean done) {
    ContentValues v = new ContentValues();
    v.put("done", done ? 1 : 0);
    getWritableDatabase().update("todos", v, "id=?", new String[] {"" + id});
  }

  public Set<String> allDays() {
    Set<String> days = new TreeSet<>();
    for (Event e : events(null, null)) days.add(day(e.time));
    try (Cursor c =
        getReadableDatabase()
            .rawQuery("SELECT day FROM summaries UNION SELECT day FROM todos", null)) {
      while (c.moveToNext()) days.add(c.getString(0));
    }
    return days;
  }

  public Event find(long id) {
    try (Cursor c =
        getReadableDatabase().rawQuery("SELECT * FROM events WHERE id=?", new String[] {"" + id})) {
      return c.moveToFirst() ? read(c) : null;
    }
  }

  public void metadata(long id, JSONObject meta) {
    ContentValues v = new ContentValues();
    v.put("meta", meta.toString());
    getWritableDatabase().update("events", v, "id=?", new String[] {"" + id});
  }

  public void pin(long id, boolean pinned) {
    ContentValues v = new ContentValues();
    v.put("pinned", pinned ? 1 : 0);
    getWritableDatabase().update("events", v, "id=?", new String[] {"" + id});
  }

  public void status(long id, String status) {
    ContentValues v = new ContentValues();
    v.put("status", status);
    getWritableDatabase().update("events", v, "id=?", new String[] {"" + id});
  }

  public Event anchor(long time) {
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(
                "SELECT * FROM events WHERE time=? AND kind='audio' LIMIT 1",
                new String[] {"" + time})) {
      return c.moveToFirst() ? read(c) : null;
    }
  }

  private Event read(Cursor c) {
    return new Event(
        c.getLong(0),
        c.getLong(1),
        c.getString(2),
        c.getString(3),
        c.getString(4),
        c.getString(5),
        c.getString(6),
        c.getInt(7) == 1);
  }

  public synchronized void transcript(long id, Transcript result) {
    Event e = find(id);
    if (e == null) return;
    JSONObject meta = e.metadata();
    meta.remove("asr_error");
    try {
      meta.put("segments", result.segments)
          .put("asr", result.engine)
          .put(
              "duration_ms",
              result.durationMs > 0 ? result.durationMs : meta.optLong("duration_ms", 0));
    } catch (JSONException ex) {
      throw new IllegalArgumentException(ex);
    }
    ContentValues v = new ContentValues();
    v.put("text", result.text.isEmpty() ? "未识别到语音" : result.text);
    v.put("status", "ready");
    v.put("meta", meta.toString());
    getWritableDatabase().update("events", v, "id=?", new String[] {"" + id});
  }

  public static String fingerprint(List<Event> events) {
    try {
      java.security.MessageDigest hash = java.security.MessageDigest.getInstance("SHA-256");
      for (Event e : events)
        hash.update(
            (e.id + ":" + e.time + ":" + e.text + ":" + e.meta + "\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
      StringBuilder s = new StringBuilder();
      for (byte b : hash.digest()) s.append(String.format(Locale.US, "%02x", b));
      return s.toString();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public boolean summaryStale(String day) {
    try (Cursor c =
        getReadableDatabase()
            .rawQuery("SELECT fingerprint FROM summaries WHERE day=?", new String[] {day})) {
      return c.moveToFirst() && !c.getString(0).equals(fingerprint(events(day, null)));
    }
  }

  public void summarySnapshot(String day, String fingerprint) {
    ContentValues v = new ContentValues();
    v.put("fingerprint", fingerprint);
    getWritableDatabase().update("summaries", v, "day=?", new String[] {day});
  }

  public long audioBytes() {
    long size = 0;
    for (Event e : events(null, null))
      if (e.kind.equals("audio") && !e.path.isEmpty()) size += new java.io.File(e.path).length();
    return size;
  }

  public int purgeAudio(long before) {
    Set<Long> anchors = new HashSet<>();
    for (Event e : events(null, null))
      if (e.kind.equals("bookmark")) anchors.add(e.metadata().optLong("anchor_time", -1));
    int count = 0;
    for (Event e : events(null, null)) {
      if (!e.kind.equals("audio")
          || e.time >= before
          || e.pinned
          || anchors.contains(e.time)
          || e.path.isEmpty()) continue;
      java.io.File f = new java.io.File(e.path);
      if (f.exists() && !f.delete()) continue;
      ContentValues v = new ContentValues();
      v.put("path", "");
      if (e.status.equals("pending")) v.put("status", "expired");
      getWritableDatabase().update("events", v, "id=?", new String[] {"" + e.id});
      count++;
    }
    return count;
  }

  public static class Event {
    final long id, time;
    final String kind, text, path, status;
    final String meta;
    final boolean pinned;

    Event(long id, long time, String kind, String text, String path, String status) {
      this(id, time, kind, text, path, status, "{}", false);
    }

    Event(
        long id,
        long time,
        String kind,
        String text,
        String path,
        String status,
        String meta,
        boolean pinned) {
      this.id = id;
      this.time = time;
      this.kind = kind;
      this.text = text;
      this.path = path;
      this.status = status;
      this.meta = meta;
      this.pinned = pinned;
    }

    public JSONObject metadata() {
      try {
        return new JSONObject(meta);
      } catch (JSONException e) {
        return new JSONObject();
      }
    }

    public JSONObject json() throws JSONException {
      return new JSONObject()
          .put("id", id)
          .put("time", time)
          .put("kind", kind)
          .put("text", text)
          .put("media", path.isEmpty() ? "" : "media/" + new java.io.File(path).getName())
          .put("status", status)
          .put("meta", metadata())
          .put("pinned", pinned);
    }
  }
}
