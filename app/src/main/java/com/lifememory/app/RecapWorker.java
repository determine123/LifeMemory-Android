package com.lifememory.app;

import android.app.*;
import android.content.*;
import androidx.work.*;
import java.time.*;
import java.util.concurrent.TimeUnit;

public class RecapWorker extends Worker {
  public RecapWorker(Context c, WorkerParameters p) {
    super(c, p);
  }

  static long delay(LocalDateTime now, int hour, int minute) {
    LocalDateTime next = now.toLocalDate().atTime(hour, minute);
    if (!next.isAfter(now)) next = next.plusDays(1);
    return java.time.Duration.between(now, next).toMillis();
  }

  public static void schedule(Context c) {
    schedule(c, false);
  }

  public static void schedule(Context c, boolean replace) {
    android.content.SharedPreferences p = c.getSharedPreferences("life", 0);
    WorkManager w = WorkManager.getInstance(c);
    if (!p.getBoolean("daily_enabled", false)) {
      w.cancelUniqueWork("daily-recap");
      return;
    }
    long delay =
        delay(LocalDateTime.now(), p.getInt("recap_hour", 22), p.getInt("recap_minute", 0));
    PeriodicWorkRequest request =
        new PeriodicWorkRequest.Builder(RecapWorker.class, 1, TimeUnit.DAYS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build();
    w.enqueueUniquePeriodicWork(
        "daily-recap",
        replace ? ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE : ExistingPeriodicWorkPolicy.KEEP,
        request);
  }

  @Override
  public Result doWork() {
    Context c = getApplicationContext();
    android.content.SharedPreferences p = c.getSharedPreferences("life", 0);
    if (!p.getBoolean("daily_enabled", false)) return Result.success();
    try {
      LocalDateTime now = LocalDateTime.now();
      LocalDate day = now.toLocalDate();
      if (now.toLocalTime()
          .isBefore(LocalTime.of(p.getInt("recap_hour", 22), p.getInt("recap_minute", 0))))
        day = day.minusDays(1);
      String recap = RecapEngine.generate(c, day.toString());
      int retention = p.getInt("retention_days", 0);
      if (retention > 0)
        try (Store db = new Store(c)) {
          db.purgeAudio(System.currentTimeMillis() - retention * 86400000L);
        }
      if (!recap.isEmpty()) {
        NotificationManager n = c.getSystemService(NotificationManager.class);
        n.createNotificationChannel(
            new NotificationChannel("recap", "每日回顾", NotificationManager.IMPORTANCE_DEFAULT));
        Intent open = new Intent(c, MainActivity.class).putExtra("day", day.toString());
        PendingIntent click =
            PendingIntent.getActivity(
                c, 80, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (android.os.Build.VERSION.SDK_INT < 33
            || c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED)
          n.notify(
              80,
              new Notification.Builder(c, "recap")
                  .setSmallIcon(R.drawable.ic_mark)
                  .setContentTitle("你的一天，已经整理好了")
                  .setContentText(day + " · 点击查看每日回顾")
                  .setContentIntent(click)
                  .setAutoCancel(true)
                  .build());
        SpeechJobs.refresh(c);
      }
      p.edit().remove("recap_error").apply();
      return Result.success();
    } catch (Exception e) {
      p.edit().putString("recap_error", e.getMessage()).apply();
      return getRunAttemptCount() < 2 ? Result.retry() : Result.failure();
    }
  }
}
