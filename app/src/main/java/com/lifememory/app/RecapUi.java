package com.lifememory.app;

import android.widget.*;
import org.json.*;

final class RecapUi {
  static void events(MainActivity a, LinearLayout box, JSONObject detail) {
    if (detail.optInt("provided_count", 0) < detail.optInt("record_count", 0))
      box.addView(
          a.text(
              I18n.english(a)
                  ? "Context limit: some records were not included in this recap."
                  : "上下文有限，本次回顾未包含全部记录。",
              12,
              0xff788278));
    JSONArray items = detail.optJSONArray("events");
    if (items == null) return;
    for (int i = 0; i < items.length(); i++) {
      JSONObject item = items.optJSONObject(i);
      if (item == null) continue;
      box.addView(a.text(item.optString("text"), 14, 0xff263d34));
      sources(a, box, item.optJSONArray("source_ids"));
    }
  }

  static void sources(MainActivity a, LinearLayout box, JSONArray ids) {
    if (ids == null) return;
    for (int i = 0; i < ids.length(); i++) {
      long id = ids.optLong(i, -1);
      Store.Event e = a.db.find(id);
      if (e == null) {
        box.addView(
            a.text((I18n.english(a) ? "Source deleted #" : "来源已删除 #") + id, 12, 0xff788278));
        continue;
      }
      box.addView(
          a.button(
              (I18n.english(a) ? "Open source #" : "查看来源 #") + id + " · " + Store.day(e.time),
              () -> a.openMemoryRecord(e)));
    }
  }
}
