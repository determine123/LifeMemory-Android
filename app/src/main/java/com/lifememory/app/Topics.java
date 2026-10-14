package com.lifememory.app;

import java.util.*;
import org.json.*;

final class Topics {
  static JSONArray parse(String text) {
    JSONArray out = new JSONArray();
    Set<String> seen = new HashSet<>();
    for (String token : text.split("[,，;；\n]")) {
      String t = token.trim();
      if (!t.isEmpty() && t.length() <= 40 && seen.add(t.toLowerCase(Locale.ROOT))) out.put(t);
      if (out.length() == 12) break;
    }
    return out;
  }

  static List<Store.Event> select(String topic, List<Store.Event> all) {
    List<Store.Event> result = new ArrayList<>();
    for (Store.Event e : all) {
      JSONArray tags = e.metadata().optJSONArray("topics");
      if (tags != null)
        for (int i = 0; i < tags.length(); i++)
          if (topic.equalsIgnoreCase(tags.optString(i))) {
            result.add(e);
            break;
          }
    }
    return result;
  }

  static List<String> list(List<Store.Event> all) {
    Map<String, String> tags = new TreeMap<>();
    for (Store.Event e : all) {
      JSONArray a = e.metadata().optJSONArray("topics");
      if (a != null)
        for (int i = 0; i < a.length(); i++) {
          String t = a.optString(i).trim();
          if (!t.isEmpty()) tags.putIfAbsent(t.toLowerCase(Locale.ROOT), t);
        }
    }
    return new ArrayList<>(tags.values());
  }
}
