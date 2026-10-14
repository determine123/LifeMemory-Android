package com.lifememory.app;

import android.app.*;
import android.widget.*;
import java.time.*;
import java.util.*;
import org.json.*;

final class DayPeriodUi {
  static void render(MainActivity a, String day, List<Store.Event> all) {
    for (int i = 0; i < 4; i++) {
      final int period = i;
      List<Store.Event> events = DayPeriods.select(all, i);
      JSONObject saved = DayPeriods.saved(a, day, i);
      LinearLayout card = a.card();
      TextView title = a.text(String.format(java.util.Locale.US, "%02d / ", i + 1) + DayPeriods.label(a, i), 19, 0xff223d33);
      title.setTypeface(null, android.graphics.Typeface.BOLD);
      card.addView(title);
      card.addView(a.text(DayPeriods.RANGE[i], 12, 0xff6b786f));
      String recap = saved.optString("summary");
      if (recap.isEmpty())
        recap =
            events.isEmpty()
                ? (I18n.english(a) ? "No records in this period." : "这个时段还没有记录。")
                : (I18n.english(a)
                    ? events.size() + " records awaiting a recap."
                    : events.size() + "条记录，等待整理这个时段。");
      card.addView(a.text(RecapEngine.compact(recap), 16, 0xff263d34));
      String status =
          DayPeriods.end(LocalDate.parse(day), i).isAfter(ZonedDateTime.now())
              ? (I18n.english(a) ? "In progress" : "时段未结束")
              : (I18n.english(a) ? "Period finished" : "时段已结束");
      if (!events.isEmpty() && !DayPeriods.fresh(a, saved, events))
        status += I18n.english(a) ? " · recap needs updating" : " · 回顾待更新";
      card.addView(
          a.text(
              status + " · " + events.size() + (I18n.english(a) ? " source records" : "条原始记录"),
              12,
              0xff788278));
      String error = a.getSharedPreferences("life", 0).getString("period_error_" + i, "");
      if (!error.isEmpty()) card.addView(a.text(error, 12, 0xff985e42));
      a.row(
          card,
          a.button(I18n.english(a) ? "View details" : "查看详情", () -> details(a, day, period)),
          a.button(I18n.english(a) ? "Update recap" : "整理这个时段", () -> update(a, day, period)));
    }
  }

  static void tasks(MainActivity a) {
    try {
      JSONArray tasks = a.db.todos(a.selected);
      if (tasks.length() == 0) return;
      LinearLayout card = a.card();
      card.addView(a.text(I18n.english(a) ? "Today's tasks" : "今日待办", 18, 0xff263d34));
      for (int i = 0; i < tasks.length(); i++) {
        JSONObject t = tasks.getJSONObject(i);
        CheckBox box = new CheckBox(a);
        box.setText(t.getString("text"));
        box.setChecked(t.getInt("done") == 1);
        box.setEnabled(!a.busy);
        long id = t.getLong("id");
        box.setOnCheckedChangeListener((v, on) -> a.db.toggle(id, on));
        card.addView(box);
        RecapUi.sources(a, card, t.optJSONArray("source_ids"));
      }
    } catch (JSONException e) {
      a.message(e.getMessage());
    }
  }

  static void update(MainActivity a, String day, int i) {
    if (a.busy) return;
    a.busy = true;
    a.render();
    a.worker.submit(
        () -> {
          String error = null;
          try {
            DayPeriods.generate(a, day, i, true);
          } catch (Exception e) {
            error = e.getMessage();
          }
          final String msg = error;
          a.runOnUiThread(
              () -> {
                a.busy = false;
                a.render();
                if (msg != null) a.message(msg);
              });
        });
  }

  static void details(MainActivity a, String day, int i) {
    LinearLayout list = a.column();
    JSONObject detail = DayPeriods.saved(a, day, i);
    RecapUi.events(a, list, detail);
    JSONArray tasks = detail.optJSONArray("todos");
    if (tasks != null)
      for (int n = 0; n < tasks.length(); n++) {
        JSONObject t = tasks.optJSONObject(n);
        if (t == null) continue;
        list.addView(
            a.text((I18n.english(a) ? "Task: " : "待办：") + t.optString("text"), 15, 0xff263d34));
        RecapUi.sources(a, list, t.optJSONArray("source_ids"));
      }
    list.addView(a.text(I18n.english(a) ? "Original records" : "原始记录", 18, 0xff263d34));
    for (Store.Event e : DayPeriods.select(a.db.events(day, null), i)) {
      String text = e.text;
      if (text.length() > 90) text = text.substring(0, 90) + "…";
      list.addView(a.button(text, () -> a.openMemoryRecord(e)));
      list.addView(a.button(I18n.english(a) ? "Edit record" : "编辑记录", () -> a.edit(e)));
    }
    ScrollView scroll = new ScrollView(a);
    scroll.addView(list);
    new AlertDialog.Builder(a)
        .setTitle(day + " · " + DayPeriods.label(a, i))
        .setView(scroll)
        .setPositiveButton(I18n.t(a, "关闭"), null)
        .show();
  }
}
