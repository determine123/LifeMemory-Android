package com.lifememory.app;

import java.time.LocalDate;
import java.util.*;
import java.util.regex.*;

/** Local date filtering and keyword ranking; every result retains its original event ID. */
public class MemorySearch {
  public static List<Store.Event> select(String query, List<Store.Event> all, LocalDate today) {
    LocalDate start = null, end = null;
    Matcher date = Pattern.compile("20\\d{2}-\\d{2}-\\d{2}").matcher(query);
    if (date.find()) {
      try {
        start = LocalDate.parse(date.group());
        end = start;
      } catch (Exception ignored) {
      }
    }
    if (start == null) {
      if (query.contains("前天")) start = today.minusDays(2);
      else if (query.contains("昨天")) start = today.minusDays(1);
      else if (query.contains("今天")) start = today;
      else if (query.contains("上周")) {
        end = today.minusDays(today.getDayOfWeek().getValue());
        start = end.minusDays(6);
      } else if (query.contains("本周") || query.contains("这周")) {
        start = today.minusDays(today.getDayOfWeek().getValue() - 1);
        end = today;
      }
    }
    if (start != null && end == null) end = start;
    String cleaned = query.toLowerCase(Locale.ROOT);
    for (String stop :
        new String[] {
          "前天", "昨天", "今天", "上周", "本周", "这周", "的时候", "告诉我", "什么", "哪些", "发生", "做了", "干了", "请问",
          "记录", "回忆", "20"
        }) cleaned = cleaned.replace(stop, " ");
    Set<String> tokens = new HashSet<>();
    Matcher latin = Pattern.compile("[a-z][a-z0-9_-]{1,}").matcher(cleaned);
    while (latin.find()) tokens.add(latin.group());
    Matcher chinese = Pattern.compile("[\u3400-\u4DBF\u4E00-\u9FFF]{2,}").matcher(cleaned);
    while (chinese.find()) {
      String term = chinese.group();
      for (int i = 0; i + 1 < term.length(); i++) tokens.add(term.substring(i, i + 2));
    }
    List<Store.Event> scoped = new ArrayList<>();
    Map<Long, Integer> scores = new HashMap<>();
    int best = 0;
    for (Store.Event e : all) {
      String day = Store.day(e.time);
      if (start != null
          && (day.compareTo(start.toString()) < 0 || day.compareTo(end.toString()) > 0)) continue;
      int score = 0;
      String text = e.text.toLowerCase(Locale.ROOT);
      for (String token : tokens) if (text.contains(token)) score++;
      scores.put(e.id, score);
      best = Math.max(best, score);
      scoped.add(e);
    }
    if (best > 0) scoped.removeIf(e -> scores.get(e.id) == 0);
    scoped.sort(
        (a, b) -> {
          int rank = Integer.compare(scores.get(b.id), scores.get(a.id));
          return rank != 0 ? rank : Long.compare(b.time, a.time);
        });
    return new ArrayList<>(scoped.subList(0, Math.min(120, scoped.size())));
  }
}
