package com.lifememory.app;

import android.content.Context;
import androidx.work.*;
import java.time.*;
import java.util.concurrent.TimeUnit;

public class DayPeriodWorker extends Worker {
  public DayPeriodWorker(Context c, WorkerParameters p) {
    super(c, p);
  }

  static long delay(ZonedDateTime now, int i) {
    ZonedDateTime end = DayPeriods.end(now.toLocalDate(), i);
    if (!end.isAfter(now)) end = DayPeriods.end(now.toLocalDate().plusDays(1), i);
    return Duration.between(now, end).toMillis();
  }

  static LocalDate latestDay(ZonedDateTime now, int i) {
    LocalDate day = now.toLocalDate();
    return DayPeriods.end(day, i).isAfter(now) ? day.minusDays(1) : day;
  }

  static void schedule(Context c, boolean replace) {
    WorkManager manager = WorkManager.getInstance(c);
    for (int i = 0; i < 4; i++) {
      String name = "period-recap-" + i;
      if (!c.getSharedPreferences("life", 0).getBoolean("period_enabled", true)) {
        manager.cancelUniqueWork(name);
        continue;
      }
      PeriodicWorkRequest request =
          new PeriodicWorkRequest.Builder(DayPeriodWorker.class, 1, TimeUnit.DAYS)
              .setInputData(new Data.Builder().putInt("period", i).build())
              .setInitialDelay(delay(ZonedDateTime.now(), i), TimeUnit.MILLISECONDS)
              .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
              .build();
      manager.enqueueUniquePeriodicWork(
          name,
          replace
              ? ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE
              : ExistingPeriodicWorkPolicy.KEEP,
          request);
    }
  }

  public Result doWork() {
    Context c = getApplicationContext();
    if (!c.getSharedPreferences("life", 0).getBoolean("period_enabled", true))
      return Result.success();
    int i = getInputData().getInt("period", 0);
    try {
      DayPeriods.generate(c, latestDay(ZonedDateTime.now(), i).toString(), i, false);
      c.getSharedPreferences("life", 0).edit().remove("period_error_" + i).apply();
      SpeechJobs.refresh(c);
      return Result.success();
    } catch (Exception e) {
      c.getSharedPreferences("life", 0)
          .edit()
          .putString("period_error_" + i, e.getMessage())
          .apply();
      SpeechJobs.refresh(c);
      if (e instanceof ApiFailure && !((ApiFailure) e).retryable()) return Result.success();
      return getRunAttemptCount() < 2 ? Result.retry() : Result.success();
    }
  }
}
