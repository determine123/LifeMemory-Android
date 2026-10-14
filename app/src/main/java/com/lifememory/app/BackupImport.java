package com.lifememory.app;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;

final class BackupImport {
  static int restore(Context c, Uri uri) throws Exception {
    try (Store db = new Store(c)) {
      InputStream in = c.getContentResolver().openInputStream(uri);
      if (in == null) throw new IOException("Cannot read backup");
      return restore(c, db, in);
    }
  }

  static int restore(Context c, Store db, InputStream input) throws Exception {
    File stage = new File(c.getCacheDir(), "backup-import-" + UUID.randomUUID());
    List<File> copied = new ArrayList<>();
    boolean committed = false;
    try {
      SafeZip.extract(input, stage, 1024L * 1024 * 1024);
      File manifest = new File(stage, "records.json");
      if (!manifest.isFile() || manifest.length() > 32L * 1024 * 1024)
        throw new IOException("Missing or oversized records.json");
      byte[] bytes = Files.readAllBytes(manifest.toPath());
      JSONObject archive = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
      int version = archive.getInt("version");
      if (version != 2 && version != 3) throw new IOException("Unsupported backup version");
      JSONArray events = archive.getJSONArray("events");
      if (events.length() > 20000) throw new IOException("Too many records");
      String key = hex(MessageDigest.getInstance("SHA-256").digest(bytes));
      Set<Long> seen = new HashSet<>();
      Map<Long, Long> ids = new HashMap<>();
      Map<Long, Long> audioByTime = new HashMap<>();
      // Validate the complete manifest before writing records or copying media.
      for (int i = 0; i < events.length(); i++) {
        JSONObject e = events.getJSONObject(i);
        long id = e.getLong("id");
        if (!seen.add(id) || id <= 0) throw new IOException("Invalid duplicate record ID");
        e.getLong("time");
        e.getString("kind");
        e.getString("text");
        String media = e.optString("media", "");
        if (!media.isEmpty()
            && (!media.matches("media/[^/\\\\:]+") || !new File(stage, media).isFile()))
          throw new IOException("Missing or invalid media attachment: " + media);
      }
      Map<String, Long> existingKeys = new HashMap<>();
      for (Store.Event event : db.events(null, null)) {
        String k = event.metadata().optString("backup_key");
        if (!k.isEmpty()) existingKeys.put(k, event.id);
      }
      SQLiteDatabase d = db.getWritableDatabase();
      d.beginTransaction();
      int imported = 0;
      try {
        for (int i = 0; i < events.length(); i++) {
          JSONObject e = events.getJSONObject(i);
          long old = e.getLong("id");
          String identity = key + ":" + old;
          Long existing = existingKeys.get(identity);
          if (existing != null) {
            ids.put(old, existing);
            if (e.getString("kind").equals("audio")) audioByTime.put(e.getLong("time"), existing);
            continue;
          }
          String media = e.optString("media", ""), path = "";
          if (!media.isEmpty()) {
            File dir = new File(c.getFilesDir(), "media");
            if (!dir.isDirectory() && !dir.mkdirs())
              throw new IOException("Cannot create media directory");
            File to =
                new File(dir, "restored-" + UUID.randomUUID() + "-" + new File(media).getName());
            copied.add(to);
            try (InputStream in = new FileInputStream(new File(stage, media));
                OutputStream out = new FileOutputStream(to)) {
              byte[] b = new byte[32768];
              int n;
              while ((n = in.read(b)) != -1) {
                if (dir.getUsableSpace() < 32L * 1024 * 1024)
                  throw new IOException("Not enough free storage");
                out.write(b, 0, n);
              }
            }
            path = to.getAbsolutePath();
          }
          String status = e.optString("status", "ready");
          if (status.equals("processing")) status = "pending";
          long id =
              db.add(e.getLong("time"), e.getString("kind"), e.getString("text"), path, status);
          JSONObject meta = e.optJSONObject("meta");
          if (meta == null) meta = new JSONObject();
          meta.put("backup_key", identity);
          db.metadata(id, meta);
          db.pin(id, e.optBoolean("pinned", false));
          ids.put(old, id);
          if (e.getString("kind").equals("audio")) audioByTime.put(e.getLong("time"), id);
          imported++;
        }
        for (int i = 0; i < events.length(); i++) {
          JSONObject e = events.getJSONObject(i);
          if (e.getString("kind").equals("bookmark")) {
            Store.Event restored = db.find(ids.get(e.getLong("id")));
            JSONObject meta = restored.metadata();
            Long anchor = audioByTime.get(meta.optLong("anchor_time", -1));
            meta.put("anchor_event_id", anchor == null ? -1 : anchor);
            db.metadata(restored.id, meta);
          }
        }
        JSONObject summaries = archive.optJSONObject("summaries"),
            recaps = archive.optJSONObject("recaps"),
            todos = archive.optJSONObject("todos");
        if (summaries != null)
          for (Iterator<String> it = summaries.keys(); it.hasNext(); ) {
            String day = it.next();
            java.time.LocalDate.parse(day);
            if (db.summary(day).isEmpty()) {
              JSONObject detail = recaps == null ? null : recaps.optJSONObject(day);
              if (detail != null && detail.has("summary")) {
                detail = remap(detail, ids);
                db.summary(day, detail.getString("summary"));
                ContentValues values = new ContentValues();
                values.put("details", detail.toString());
                d.update("summaries", values, "day=?", new String[] {day});
              } else db.summary(day, summaries.getString(day));
            }
          }
        if (todos != null)
          for (Iterator<String> it = todos.keys(); it.hasNext(); ) {
            String day = it.next();
            java.time.LocalDate.parse(day);
            JSONArray tasks = todos.getJSONArray(day);
            for (int i = 0; i < tasks.length(); i++) {
              JSONObject task = tasks.getJSONObject(i);
              String text = task.getString("text");
              try (Cursor cursor =
                  d.rawQuery(
                      "SELECT id FROM todos WHERE day=? AND text=?", new String[] {day, text})) {
                if (cursor.moveToFirst()) continue;
              }
              ContentValues v = new ContentValues();
              v.put("day", day);
              v.put("text", text);
              v.put("done", task.optInt("done", 0) == 1 ? 1 : 0);
              v.put("sources", mapped(task.optJSONArray("source_ids"), ids).toString());
              d.insertOrThrow("todos", null, v);
            }
          }
        d.setTransactionSuccessful();
      } finally {
        d.endTransaction();
      }
      committed = true;
      return imported;
    } finally {
      if (!committed) for (File f : copied) f.delete();
      SafeZip.remove(stage);
    }
  }

  static JSONArray mapped(JSONArray source, Map<Long, Long> ids) {
    JSONArray out = new JSONArray();
    if (source != null)
      for (int i = 0; i < source.length(); i++) {
        Long id = ids.get(source.optLong(i, -1));
        if (id != null) out.put(id);
      }
    return out;
  }

  static JSONObject remap(JSONObject detail, Map<Long, Long> ids) throws JSONException {
    JSONObject out =
        new JSONObject().put("summary", RecapEngine.compact(detail.getString("summary")));
    for (String field : new String[] {"events", "todos"}) {
      JSONArray raw = detail.optJSONArray(field), items = new JSONArray();
      if (raw != null)
        for (int i = 0; i < raw.length(); i++) {
          JSONObject item = raw.getJSONObject(i);
          JSONArray sources = mapped(item.optJSONArray("source_ids"), ids);
          if (sources.length() > 0)
            items.put(
                new JSONObject().put("text", item.getString("text")).put("source_ids", sources));
        }
      out.put(field, items);
    }
    return out;
  }

  static String hex(byte[] b) {
    StringBuilder s = new StringBuilder();
    for (byte v : b) s.append(String.format(Locale.ROOT, "%02x", v));
    return s.toString();
  }
}
