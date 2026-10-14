package com.lifememory.app;

import static org.junit.Assert.*;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.time.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MemorySearchTest {
  private Store.Event event(long id, String day, String text) {
    long time =
        LocalDate.parse(day).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    return new Store.Event(id, time, "text", text, "", "ready");
  }

  @Test
  public void yesterdayDoesNotGetCrowdedOutByTodaysRecording() {
    List<Store.Event> all = new ArrayList<>();
    for (int i = 0; i < 200; i++) all.add(event(i + 1, "2026-10-13", "今天录音"));
    all.add(event(999, "2026-10-12", "昨天的会议"));
    List<Store.Event> found = MemorySearch.select("我昨天干了什么", all, LocalDate.of(2026, 10, 13));
    assertEquals(1, found.size());
    assertEquals(999, found.get(0).id);
  }

  @Test
  public void olderChineseAndEnglishTopicsRankAheadOfUnrelatedRecentEvents() {
    List<Store.Event> all =
        Arrays.asList(
            event(1, "2026-10-13", "午餐"), event(2, "2026-09-01", "跟黄老师讨论 OpenClaw 的部署决定"));
    List<Store.Event> found =
        MemorySearch.select("跟黄老师讨论OpenClaw的时候决定了什么", all, LocalDate.of(2026, 10, 13));
    assertEquals(1, found.size());
    assertEquals(2, found.get(0).id);
  }

  @Test
  public void lastWeekMeansPreviousCalendarWeek() {
    List<Store.Event> all =
        Arrays.asList(
            event(1, "2026-10-04", "旧记录"),
            event(2, "2026-10-05", "周一"),
            event(3, "2026-10-11", "周日"),
            event(4, "2026-10-12", "本周"));
    List<Store.Event> found = MemorySearch.select("上周发生了什么", all, LocalDate.of(2026, 10, 13));
    assertEquals(2, found.size());
    assertEquals(3, found.get(0).id);
    assertEquals(2, found.get(1).id);
  }

  @Test
  public void unmatchedTopicNeverFallsBackToUnrelatedHistory() {
    List<Store.Event> all = Arrays.asList(event(1, "2026-10-13", "lunch"));
    assertTrue(MemorySearch.select("quantum computing", all, LocalDate.of(2026, 10, 13)).isEmpty());
    assertTrue(MemorySearch.select("今天量子计算", all, LocalDate.of(2026, 10, 13)).isEmpty());
  }

  @Test
  public void englishDateQuestionReturnsOnlyThatDay() {
    List<Store.Event> all =
        Arrays.asList(event(1, "2026-10-13", "lunch"), event(2, "2026-10-12", "meeting"));
    List<Store.Event> found =
        MemorySearch.select("What did I do yesterday?", all, LocalDate.of(2026, 10, 13));
    assertEquals(1, found.size());
    assertEquals(2, found.get(0).id);
  }

  @Test
  public void placeAndActivityAreSearchable() {
    long time =
        LocalDate.of(2026, 10, 13).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    Store.Event e =
        new Store.Event(
            3,
            time,
            "context",
            "",
            "",
            "ready",
            "{\"place\":\"实验室\",\"activity\":\"project review\"}",
            false);
    assertEquals(
        3, MemorySearch.select("实验室", Arrays.asList(e), LocalDate.of(2026, 10, 13)).get(0).id);
    assertEquals(
        3,
        MemorySearch.select("project review", Arrays.asList(e), LocalDate.of(2026, 10, 13))
            .get(0)
            .id);
  }

  @Test
  public void englishFillerWithoutTopicOrDateIsNotAHistoryDump() {
    assertTrue(
        MemorySearch.select(
                "What did I do?",
                Arrays.asList(event(1, "2026-10-13", "lunch")),
                LocalDate.of(2026, 10, 13))
            .isEmpty());
  }

  @Test public void explicitDateDoesNotBecomeKeywordDigits() {
    assertEquals(1, MemorySearch.select("2026-10-12", Arrays.asList(event(1, "2026-10-12", "meeting")), LocalDate.of(2026,10,13)).size());
  }
  @Test public void datedPeriodFiltersUseLocalTimeBoundaries() {
    long t = LocalDate.of(2026,10,12).atTime(18,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    List<Store.Event> all = Arrays.asList(event(1,"2026-10-12","meeting"), new Store.Event(2,t,"audio","meeting","","ready"));
    assertEquals(2,MemorySearch.select("yesterday evening",all,LocalDate.of(2026,10,13)).get(0).id);
    assertEquals(1,MemorySearch.select("昨天晚上",all,LocalDate.of(2026,10,13)).size());
    assertEquals(2,MemorySearch.select("period:evening type:audio",all,LocalDate.of(2026,10,13)).get(0).id);
  }
  @Test public void exactTopicsAndPinnedFiltersCombine() {
    Store.Event e = new Store.Event(3, event(1,"2026-10-12","").time,"audio","review","","ready","{\"topics\":[\"Work\"]}",true);
    assertEquals(1,MemorySearch.select("topic:work pinned:true type:audio",Arrays.asList(e),LocalDate.of(2026,10,13)).size());
    assertTrue(MemorySearch.select("topic:workshop",Arrays.asList(e),LocalDate.of(2026,10,13)).isEmpty());
    assertTrue(MemorySearch.select("pinned:false",Arrays.asList(e),LocalDate.of(2026,10,13)).isEmpty());
  }
}
