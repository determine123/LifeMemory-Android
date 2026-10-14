package com.lifememory.app;

import static org.junit.Assert.*;

import android.content.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.nio.*;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class RecordingSafetyTest {
  private Context context() {
    return InstrumentationRegistry.getInstrumentation().getTargetContext();
  }

  private File file(String suffix) throws Exception {
    File dir = new File(context().getFilesDir(), "media");
    dir.mkdirs();
    return File.createTempFile("recovery-test-", suffix, dir);
  }

  private void wav(File f) throws Exception {
    int size = 32000;
    ByteBuffer h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
    h.put("RIFF".getBytes("US-ASCII"))
        .putInt(36 + size)
        .put("WAVEfmt ".getBytes("US-ASCII"))
        .putInt(16)
        .putShort((short) 1)
        .putShort((short) 1)
        .putInt(16000)
        .putInt(32000)
        .putShort((short) 2)
        .putShort((short) 16)
        .put("data".getBytes("US-ASCII"))
        .putInt(size);
    try (OutputStream out = new FileOutputStream(f)) {
      out.write(h.array());
      out.write(new byte[size]);
    }
  }

  @Test
  public void interruptedPlayableAudioIsRecoveredOnce() throws Exception {
    Context c = context();
    File f = file(".wav");
    wav(f);
    String name = "recovery-test.db";
    c.deleteDatabase(name);
    try (Store db = new Store(c, name)) {
      RecordingSafety.recover(c, db, f.getAbsolutePath(), 1234000);
      RecordingSafety.recover(c, db, f.getAbsolutePath(), 1234000);
      assertEquals(1, db.events(null, null).size());
      Store.Event e = db.anchor(1234000);
      assertEquals("pending", e.status);
      assertTrue(e.metadata().getBoolean("interrupted"));
      assertEquals(1000, e.metadata().getLong("duration_ms"));
    } finally {
      f.delete();
      c.deleteDatabase(name);
    }
  }

  @Test
  public void incompleteMp4IsRetainedButNotQueued() throws Exception {
    Context c = context();
    File f = file(".m4a");
    try (FileOutputStream out = new FileOutputStream(f)) {
      out.write(new byte[] {1, 2, 3, 4});
    }
    String name = "recovery-broken.db";
    c.deleteDatabase(name);
    try (Store db = new Store(c, name)) {
      RecordingSafety.recover(c, db, f.getAbsolutePath(), 1235000);
      assertTrue(f.exists());
      assertEquals("error", db.anchor(1235000).status);
      assertTrue(db.anchor(1235000).metadata().has("asr_error"));
    } finally {
      f.delete();
      c.deleteDatabase(name);
    }
  }

  @Test
  public void recoveryRejectsPathsOutsidePrivateMedia() throws Exception {
    Context c = context();
    File f = File.createTempFile("outside-", ".wav", c.getCacheDir());
    wav(f);
    String name = "recovery-path.db";
    c.deleteDatabase(name);
    try (Store db = new Store(c, name)) {
      RecordingSafety.recover(c, db, f.getAbsolutePath(), 1236000);
      assertTrue(db.events(null, null).isEmpty());
      assertTrue(f.exists());
    } finally {
      f.delete();
      c.deleteDatabase(name);
    }
  }

  @Test
  public void lowStorageReserveIsEnforced() {
    assertFalse(RecordingSafety.enoughSpace(0));
    assertFalse(RecordingSafety.enoughSpace(RecordingSafety.RESERVE_BYTES));
    assertTrue(RecordingSafety.enoughSpace(RecordingSafety.RESERVE_BYTES + 1));
  }

  @Test
  public void screenOffRecordingSavesPlayableAudio() throws Exception {
    android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    Context c = context();
    assertFalse(CaptureService.active);
    boolean auto = c.getSharedPreferences("life", 0).getBoolean("auto_armed", false);
    try {
      instrumentation.runOnMainSync(
          () -> c.startForegroundService(new Intent(c, CaptureService.class)));
      android.os.SystemClock.sleep(1500);
      assertTrue(CaptureService.active);
      String path = c.getSharedPreferences("capture", 0).getString("current_file", "");
      long start = c.getSharedPreferences("capture", 0).getLong("current_start", 0);
      try (android.os.ParcelFileDescriptor command =
          instrumentation.getUiAutomation().executeShellCommand("input keyevent 223")) {}
      android.os.SystemClock.sleep(2500);
      assertTrue(CaptureService.active);
      instrumentation.runOnMainSync(
          () -> c.startService(new Intent(c, CaptureService.class).setAction("stop")));
      android.os.SystemClock.sleep(1000);
      assertFalse(CaptureService.active);
      try (Store db = new Store(c)) {
        Store.Event e = db.anchor(start);
        assertNotNull(e);
        assertTrue(RecordingSafety.duration(new File(path)) > 0);
        if (SpeechJobs.pendingCount() > 0) SpeechJobs.submit(c, e.id).get();
        db.delete(e);
      }
      assertEquals("", c.getSharedPreferences("capture", 0).getString("current_file", ""));
    } finally {
      instrumentation.runOnMainSync(() -> c.stopService(new Intent(c, CaptureService.class)));
      c.getSharedPreferences("life", 0).edit().putBoolean("auto_armed", auto).commit();
      try (android.os.ParcelFileDescriptor command =
          instrumentation.getUiAutomation().executeShellCommand("input keyevent 224")) {}
    }
  }
}
