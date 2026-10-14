package com.lifememory.app;

import static org.junit.Assert.*;

import android.content.*;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.nio.*;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AutomationTest {
  @Test
  public void manualStopDisarmsUntilEnabledAgain() {
    Context base = InstrumentationRegistry.getInstrumentation().getTargetContext();
    Context c =
        new ContextWrapper(base) {
          public SharedPreferences getSharedPreferences(String name, int mode) {
            return base.getSharedPreferences("test-auto-" + name, mode);
          }
        };
    AutoCapture.enable(c, true);
    assertTrue(AutoCapture.armed(c));
    AutoCapture.manualStop(c);
    assertFalse(AutoCapture.armed(c));
    assertTrue(c.getSharedPreferences("life", 0).getBoolean("auto_capture", false));
    AutoCapture.enable(c, true);
    assertTrue(AutoCapture.armed(c));
    AutoCapture.enable(c, false);
    assertFalse(AutoCapture.armed(c));
  }

  @Test
  public void externalWavIsCopiedAndKeptAsAnAudioRecord() throws Exception {
    Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
    File source = File.createTempFile("external-test-", ".wav", c.getCacheDir());
    int size = 16000 * 2;
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
    try (OutputStream out = new FileOutputStream(source)) {
      out.write(h.array());
      out.write(new byte[size]);
    }
    long id = -1;
    try (Store db = new Store(c)) {
      id = AudioImport.save(c, Uri.fromFile(source));
      Store.Event e = db.find(id);
      assertEquals("audio", e.kind);
      assertTrue(new File(e.path).isFile());
      assertEquals("external-recorder", e.metadata().getString("source"));
      assertTrue(e.metadata().getLong("duration_ms") > 0);
      assertNotEquals(source.getAbsolutePath(), e.path);
      if (SpeechJobs.pendingCount() > 0) SpeechJobs.submit(c, id).get();
    } finally {
      try (Store db = new Store(c)) {
        if (id >= 0 && db.find(id) != null) db.delete(db.find(id));
      }
      source.delete();
    }
  }
}
