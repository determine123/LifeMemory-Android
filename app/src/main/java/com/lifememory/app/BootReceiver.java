package com.lifememory.app;

import android.content.*;

public final class BootReceiver extends BroadcastReceiver {
  @Override
  public void onReceive(Context c, Intent intent) {
    if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) AutoCapture.afterBoot(c);
  }
}
