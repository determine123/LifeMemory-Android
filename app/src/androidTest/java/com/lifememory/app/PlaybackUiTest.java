package com.lifememory.app;

import static org.junit.Assert.*;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.accessibility.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.nio.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class PlaybackUiTest {
  private Instrumentation instrument;
  private MainActivity activity;
  private Store db;
  private Store.Event event;
  private File audio;

  @Before
  public void setup() throws Exception {
    instrument = InstrumentationRegistry.getInstrumentation();
    Context c = instrument.getTargetContext();
    audio = File.createTempFile("playback-test-", ".wav", c.getCacheDir());
    int size = 16000 * 2 * 12;
    try (OutputStream out = new FileOutputStream(audio)) {
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
      out.write(h.array());
      out.write(new byte[size]);
    }
    db = new Store(c);
    long id =
        db.add(
            System.currentTimeMillis(),
            "audio",
            "UI测试用转写，非真实ASR结果",
            audio.getAbsolutePath(),
            "ready");
    db.transcript(
        id,
        new Transcript(
            "第一段\n第二段",
            new JSONArray()
                .put(
                    new JSONObject()
                        .put("start_ms", 1000)
                        .put("end_ms", 1800)
                        .put("text", "第一段")
                        .put("speaker", ""))
                .put(
                    new JSONObject()
                        .put("start_ms", 2000)
                        .put("end_ms", 2800)
                        .put("text", "第二段")
                        .put("speaker", "B")),
            "测试夹具",
            4000));
    event = db.find(id);
    activity =
        (MainActivity)
            instrument.startActivitySync(
                new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    instrument.runOnMainSync(() -> new FeatureUi(activity).transcript(event));
    instrument.waitForIdleSync();
  }

  @After
  public void cleanup() {
    if (activity != null) instrument.runOnMainSync(() -> activity.finish());
    if (db != null) {
      if (event != null) db.delete(event);
      db.close();
    }
    if (audio != null) audio.delete();
  }

  private AccessibilityNodeInfo find(AccessibilityNodeInfo root, String text, boolean contains) {
    if (root == null) return null;
    CharSequence value = root.getText();
    if (value != null
        && (contains ? value.toString().contains(text) : value.toString().equals(text)))
      return root;
    for (int i = 0; i < root.getChildCount(); i++) {
      AccessibilityNodeInfo result = find(root.getChild(i), text, contains);
      if (result != null) return result;
    }
    return null;
  }

  private AccessibilityNodeInfo node(String text, boolean contains) throws Exception {
    for (int n = 0; n < 30; n++) {
      AccessibilityNodeInfo result =
          find(instrument.getUiAutomation().getRootInActiveWindow(), text, contains);
      if (result != null) return result;
      Thread.sleep(100);
    }
    throw new AssertionError("找不到界面文字：" + text);
  }

  private AccessibilityNodeInfo edit(AccessibilityNodeInfo root) {
    if (root == null) return null;
    if ("android.widget.EditText".contentEquals(root.getClassName())) return root;
    for (int i = 0; i < root.getChildCount(); i++) {
      AccessibilityNodeInfo result = edit(root.getChild(i));
      if (result != null) return result;
    }
    return null;
  }

  private void label(String name) throws Exception {
    assertTrue(node("00:01", true).performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK));
    node("标注这一段", true);
    AccessibilityNodeInfo input = edit(instrument.getUiAutomation().getRootInActiveWindow());
    assertNotNull(input);
    Bundle b = new Bundle();
    b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, name);
    assertTrue(input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b));
    assertTrue(node("保存", false).performAction(AccessibilityNodeInfo.ACTION_CLICK));
    node(name + "：第一段", true);
  }

  @Test
  public void speakerLabelPersistsAndCanBeEditedAgain() throws Exception {
    label("我");
    assertEquals(
        "我",
        db.find(event.id)
            .metadata()
            .getJSONArray("segments")
            .getJSONObject(0)
            .getString("speaker"));
    assertEquals(
        "B",
        db.find(event.id)
            .metadata()
            .getJSONArray("segments")
            .getJSONObject(1)
            .getString("speaker"));
    assertTrue(node("00:01", true).performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK));
    node("标注这一段", true);
    assertEquals(
        "我", edit(instrument.getUiAutomation().getRootInActiveWindow()).getText().toString());
    assertTrue(node("取消", false).performAction(AccessibilityNodeInfo.ACTION_CLICK));
    label("同事");
    assertEquals(
        "同事",
        db.find(event.id)
            .metadata()
            .getJSONArray("segments")
            .getJSONObject(0)
            .getString("speaker"));
  }

  @Test
  public void transcriptStartsSeekablePlayerAndPauseResume() throws Exception {
    assertTrue(node("00:01", true).performAction(AccessibilityNodeInfo.ACTION_CLICK));
    node("原声回听", false);
    node(" / 00:12", true);
    assertTrue(node("暂停", false).performAction(AccessibilityNodeInfo.ACTION_CLICK));
    node("继续播放", false);
    assertTrue(node("继续播放", false).performAction(AccessibilityNodeInfo.ACTION_CLICK));
    node("暂停", false);
    assertTrue(node("停止", false).performAction(AccessibilityNodeInfo.ACTION_CLICK));
    node("转写 · 点击回听", true);
  }
}
