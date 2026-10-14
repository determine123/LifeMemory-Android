package com.lifememory.app;

import android.content.Context;
import androidx.work.*;
import java.util.concurrent.TimeUnit;

public class AudioCleanupWorker extends Worker {
  public AudioCleanupWorker(Context c, WorkerParameters p) {
    super(c, p);
  }

  public static void schedule(Context c) {
    WorkManager manager = WorkManager.getInstance(c);
    if (c.getSharedPreferences("life", 0).getInt("retention_days", 0) == 0) {
      manager.cancelUniqueWork("audio-retention");
      return;
    }
    manager.enqueueUniquePeriodicWork(
        "audio-retention",
        ExistingPeriodicWorkPolicy.KEEP,
        new PeriodicWorkRequest.Builder(AudioCleanupWorker.class, 1, TimeUnit.DAYS).build());
  }

  public Result doWork() {
    Context c = getApplicationContext();
    int days = c.getSharedPreferences("life", 0).getInt("retention_days", 0);
    if (days > 0)
      try (Store db = new Store(c)) {
        db.purgeAudio(System.currentTimeMillis() - days * 86400000L);
        SpeechJobs.refresh(c);
      }
    return Result.success();
  }
}
