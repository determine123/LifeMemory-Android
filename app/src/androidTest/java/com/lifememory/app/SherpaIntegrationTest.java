package com.lifememory.app;

import static org.junit.Assert.*;

import android.content.*;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.k2fsa.sherpa.onnx.*;
import java.io.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SherpaIntegrationTest {
  @Test
  public void realModelsRunSenseVoiceAndSpeakerSeparation() throws Exception {
    Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
    File fixtures = new File(c.getCacheDir(), "sherpa-fixtures");
    Assume.assumeTrue(
        "Native model fixtures not installed",
        new File(fixtures, "LifeMemory-SenseVoice.zip").isFile());
    SharedPreferences prefs = c.getSharedPreferences("life", 0);
    String old = prefs.getString("sherpa_model", ""), oldDiar = prefs.getString("diar_model", "");
    boolean enabled = prefs.getBoolean("diar_enabled", false);
    int oldCount = prefs.getInt("speaker_count", -1);
    try {
      SherpaAsr.install(c, Uri.fromFile(new File(fixtures, "LifeMemory-SenseVoice.zip")), false);
      prefs.edit().putBoolean("diar_enabled", false).commit();
      File media = new File(c.getFilesDir(), "media");
      media.mkdirs();
      File zh = new File(media, "sherpa-reference-zh.wav");
      java.nio.file.Files.copy(
          new File(fixtures, "zh.wav").toPath(),
          zh.toPath(),
          java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      Transcript plain = SherpaAsr.transcribe(c, zh);
      assertFalse(plain.text.isEmpty());
      assertTrue(plain.durationMs > 0);
      assertTrue(plain.segments.length() > 0);
      SherpaAsr.install(c, Uri.fromFile(new File(fixtures, "LifeMemory-SpeakerModels.zip")), true);
      File audio = new File(c.getFilesDir(), "media/sherpa-reference-four.wav");
      java.nio.file.Files.copy(
          new File(fixtures, "four-speakers.wav").toPath(),
          audio.toPath(),
          java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      prefs.edit().putBoolean("diar_enabled", true).putInt("speaker_count", 4).commit();
      Transcript result = SherpaAsr.transcribe(c, audio);
      Set<String> labels = new HashSet<>();
      for (int i = 0; i < result.segments.length(); i++)
        labels.add(result.segments.getJSONObject(i).getString("speaker"));
      assertEquals(4, labels.size());
      assertFalse(result.text.isEmpty());
      java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
      try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(buffer)) {
        zip.putNextEntry(new java.util.zip.ZipEntry("records.json"));
        zip.write(
            new org.json.JSONObject()
                .put("version", 2)
                .put("events", new org.json.JSONArray())
                .toString()
                .getBytes("UTF-8"));
        zip.closeEntry();
      }
      c.deleteDatabase("native-zip-test.db");
      try (Store backup = new Store(c, "native-zip-test.db")) {
        assertEquals(
            0,
            BackupImport.restore(
                c, backup, new java.io.ByteArrayInputStream(buffer.toByteArray())));
      } finally {
        c.deleteDatabase("native-zip-test.db");
      }
      System.out.println(
          "SHERPA_NATIVE_OK speakers="
              + labels.size()
              + " transcriptSegments="
              + result.segments.length()
              + " zh="
              + plain.text);
    } finally {
      new File(c.getFilesDir(), "media/sherpa-reference-zh.wav").delete();
      new File(c.getFilesDir(), "media/sherpa-reference-four.wav").delete();
      prefs
          .edit()
          .putString("sherpa_model", old)
          .putString("diar_model", oldDiar)
          .putBoolean("diar_enabled", enabled)
          .putInt("speaker_count", oldCount)
          .commit();
    }
  }
}
