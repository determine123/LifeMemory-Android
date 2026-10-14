package com.lifememory.app;

import java.time.LocalDate;
import java.util.*;
import java.util.regex.*;

/** Local date filtering and keyword ranking; every result retains its original event ID. */
public class MemorySearch {
  public static List<Store.Event> select(String query, List<Store.Event> all, LocalDate today) {
    String q = query.toLowerCase(Locale.ROOT);
    String kind = null, topic = null;
    Integer period = null;
    Boolean pinned = null;
    Matcher filters = Pattern.compile("(?<!\\S)(type|topic|period|pinned):([^\\s]+)").matcher(q);
    StringBuffer remainder = new StringBuffer();
    while (filters.find()) {
      String key = filters.group(1), value = filters.group(2);
      boolean valid = true;
      if (key.equals("type") && Arrays.asList("audio", "photo", "text", "context").contains(value)) kind = value;
      else if (key.equals("topic")) topic = value;
      else if (key.equals("pinned") && Arrays.asList("true", "false").contains(value)) pinned = Boolean.valueOf(value);
      else if (key.equals("period") && Arrays.asList("morning", "midday", "afternoon", "evening").contains(value))
        period = Arrays.asList("morning", "midday", "afternoon", "evening").indexOf(value);
      else valid = false;
      filters.appendReplacement(remainder, valid ? " " : Matcher.quoteReplacement(filters.group()));
    }
    filters.appendTail(remainder);
    q = remainder.toString();
    // Natural period wording is a time filter only when paired with a date.
    if (Pattern.compile("今天|昨天|前天|上周|本周|这周|20\\d{2}-\\d{2}-\\d{2}|\\b(today|yesterday|last week|this week)\\b").matcher(q).find()) {
      String[] words = {"早上|上午|\\bmorning\\b", "中午|\\bmidday\\b", "下午|\\bafternoon\\b", "晚上|\\bevening\\b"};
      for (int i = 0; i < words.length; i++) {
        Matcher m = Pattern.compile(words[i]).matcher(q);
        if (m.find()) { period = i; q = m.replaceAll(" "); break; }
      }
    }
    boolean filtered = kind != null || topic != null || pinned != null || period != null;
    query =
        q.replaceAll("\\bday before yesterday\\b", "前天")
            .replaceAll("\\byesterday\\b", "昨天")
            .replaceAll("\\btoday\\b", "今天")
            .replaceAll("\\blast week\\b", "上周")
            .replaceAll("\\bthis week\\b", "本周");
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
          "记录", "回忆"
        }) cleaned = cleaned.replace(stop, " ");
    cleaned = cleaned.replaceAll("20\\d{2}-\\d{2}-\\d{2}", " ");
    cleaned =
        cleaned.replaceAll(
            "\\b(what|did|do|i|we|my|me|the|a|an|about|tell|show|find|please|happened|records|memories|was|were|in|on|at|of|to|and)\\b",
            " ");
    Set<String> tokens = new HashSet<>();
    Matcher latin = Pattern.compile("[a-z][a-z0-9_-]{1,}").matcher(cleaned);
    while (latin.find()) tokens.add(latin.group());
    Matcher chinese = Pattern.compile("[\u3400-\u4DBF\u4E00-\u9FFF]{2,}").matcher(cleaned);
    while (chinese.find()) {
      String term = chinese.group();
      for (int i = 0; i + 1 < term.length(); i++) tokens.add(term.substring(i, i + 2));
    }
    if (tokens.isEmpty() && start == null && !filtered) return new ArrayList<>();
    List<Store.Event> scoped = new ArrayList<>();
    Map<Long, Integer> scores = new HashMap<>();
    for (Store.Event e : all) {
      String day = Store.day(e.time);
      if (start != null
          && (day.compareTo(start.toString()) < 0 || day.compareTo(end.toString()) > 0)) continue;
      if (kind != null && !kind.equals(e.kind)) continue;
      if (pinned != null && pinned.booleanValue() != e.pinned) continue;
      if (period != null && DayPeriods.index(java.time.Instant.ofEpochMilli(e.time).atZone(java.time.ZoneId.systemDefault()).getHour()) != period) continue;
      if (topic != null) {
        org.json.JSONArray tags = e.metadata().optJSONArray("topics");
        boolean matches = false;
        if (tags != null) for (int n = 0; n < tags.length(); n++)
          if (topic.equalsIgnoreCase(tags.optString(n))) matches = true;
        if (!matches) continue;
      }
      int score = 0;
      org.json.JSONObject meta = e.metadata();
      String text =
          (e.text
                  + " "
                  + meta.optString("place")
                  + " "
                  + meta.optString("activity")
                  + " "
                  + meta.optString("mood")
                  + " "
                  + meta.optJSONArray("topics"))
              .toLowerCase(Locale.ROOT);
      for (String token : tokens) if (text.contains(token)) score++;
      scores.put(e.id, score);
      scoped.add(e);
    }
    if (!tokens.isEmpty()) scoped.removeIf(e -> scores.get(e.id) == 0);
    scoped.sort(
        (a, b) -> {
          int rank = Integer.compare(scores.get(b.id), scores.get(a.id));
          return rank != 0 ? rank : Long.compare(b.time, a.time);
        });
    return new ArrayList<>(scoped.subList(0, Math.min(120, scoped.size())));
  }
}
