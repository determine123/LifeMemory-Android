package com.lifememory.app;

import org.json.*;

public class Transcript {
  final String text, engine;
  final JSONArray segments;
  final long durationMs;

  public Transcript(String text, JSONArray segments, String engine, long durationMs) {
    this.text = text;
    this.segments = segments;
    this.engine = engine;
    this.durationMs = durationMs;
  }

  public static Transcript cloud(String body) throws JSONException {
    JSONObject j = new JSONObject(body);
    JSONArray source = j.optJSONArray("segments"), segments = new JSONArray();
    if (source != null)
      for (int n = 0; n < source.length(); n++) {
        JSONObject s = source.getJSONObject(n);
        segments.put(
            new JSONObject()
                .put("start_ms", Math.max(0, Math.round(s.optDouble("start", 0) * 1000)))
                .put("end_ms", Math.max(0, Math.round(s.optDouble("end", 0) * 1000)))
                .put("text", s.optString("text", ""))
                .put("speaker", s.optString("speaker", "")));
      }
    return new Transcript(
        j.optString("text", ""), segments, "云端 ASR", Math.round(j.optDouble("duration", 0) * 1000));
  }
}
