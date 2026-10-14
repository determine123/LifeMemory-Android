package com.lifememory.app;

import java.util.*;
import org.json.*;

final class RecapSources {
  static JSONArray ids(JSONArray raw, Set<Long> allowed) throws JSONException {
    JSONArray out = new JSONArray();
    Set<Long> seen = new HashSet<>();
    if (raw != null)
      for (int i = 0; i < Math.min(raw.length(), 20); i++) {
        Object x = raw.get(i);
        if (!(x instanceof Number)) continue;
        long id = ((Number) x).longValue();
        if (((Number) x).doubleValue() == id && allowed.contains(id) && seen.add(id)) out.put(id);
      }
    return out;
  }

  static JSONObject validate(JSONObject raw, List<Store.Event> provided) throws JSONException {
    Set<Long> allowed = new HashSet<>();
    for (Store.Event e : provided) allowed.add(e.id);
    JSONObject result =
        new JSONObject().put("summary", RecapEngine.compact(raw.getString("summary")));
    if (result.getString("summary").isEmpty()) throw new JSONException("Empty summary");
    for (String field : new String[] {"events", "todos"}) {
      JSONArray input = raw.optJSONArray(field), out = new JSONArray();
      if (input != null)
        for (int i = 0; i < Math.min(input.length(), 50); i++) {
          JSONObject item = input.optJSONObject(i);
          if (item == null) continue;
          String text = item.optString("text", "").trim();
          JSONArray sources = ids(item.optJSONArray("source_ids"), allowed);
          if (!text.isEmpty() && sources.length() > 0)
            out.put(new JSONObject().put("text", text).put("source_ids", sources));
        }
      result.put(field, out);
    }
    return result;
  }

  static JSONObject local(String summary, List<Store.Event> provided) throws JSONException {
    JSONArray events = new JSONArray();
    for (Store.Event e : provided)
      events.put(new JSONObject().put("text", e.text).put("source_ids", new JSONArray().put(e.id)));
    return new JSONObject()
        .put("summary", summary)
        .put("events", events)
        .put("todos", new JSONArray());
  }
}
