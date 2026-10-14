package com.lifememory.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;

final class AutoCapture {
  static boolean armed(Context c) {
    return c.getSharedPreferences("life", 0).getBoolean("auto_capture", false)
        && c.getSharedPreferences("life", 0).getBoolean("auto_armed", false);
  }

  static void enable(Context c, boolean enabled) {
    c.getSharedPreferences("life", 0)
        .edit()
        .putBoolean("auto_capture", enabled)
        .putBoolean("auto_armed", enabled)
        .apply();
    if (CaptureService.active)
      c.startService(new Intent(c, CaptureService.class).setAction("auto_config"));
  }

  static void manualStop(Context c) {
    c.getSharedPreferences("life", 0).edit().putBoolean("auto_armed", false).apply();
    ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(91);
  }

  static boolean startWhenVisible(MainActivity a) {
    if (a.getIntent().getBooleanExtra("external_audio_flow", false)) return false;
    if (!armed(a)
        || CaptureService.active
        || a.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) return false;
    try {
      a.startForegroundService(new Intent(a, CaptureService.class));
      return true;
    } catch (Exception error) {
      a.message(error.getMessage());
      return false;
    }
  }

  static void afterBoot(Context c) {
    if (!armed(c)) return;
    if (Build.VERSION.SDK_INT < 30
        && c.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED) {
      try {
        c.startForegroundService(new Intent(c, CaptureService.class));
        return;
      } catch (Exception ignored) {
      }
    }
    NotificationManager manager =
        (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
    manager.createNotificationChannel(
        new NotificationChannel(
            "auto_capture",
            "LifeMemory · Resume recording",
            NotificationManager.IMPORTANCE_DEFAULT));
    if (Build.VERSION.SDK_INT >= 33
        && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) return;
    PendingIntent open =
        PendingIntent.getActivity(
            c,
            91,
            new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    manager.notify(
        91,
        new Notification.Builder(c, "auto_capture")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(I18n.english(c) ? "Open LifeMemory to resume recording" : "打开拾光恢复录音")
            .setContentText(
                I18n.english(c)
                    ? "Android requires you to open the app before microphone recording can resume."
                    : "安卓要求先打开应用，再恢复麦克风录音。")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build());
  }
}
