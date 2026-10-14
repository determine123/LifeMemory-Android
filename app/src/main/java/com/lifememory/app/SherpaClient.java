package com.lifememory.app;

import android.content.*;
import android.os.*;
import java.io.*;
import java.util.concurrent.*;
import org.json.*;

/** Keep native runtime symbols and native failures out of the UI/import process. */
final class SherpaClient {
  static JSONObject request(Context c, int operation, Bundle payload) throws Exception {
    if (Looper.myLooper() == Looper.getMainLooper())
      throw new IOException("Speech inference must run off the UI thread");
    CountDownLatch done = new CountDownLatch(1);
    String[] result = {null}, error = {null};
    Messenger reply =
        new Messenger(
            new Handler(Looper.getMainLooper()) {
              public void handleMessage(Message msg) {
                result[0] = msg.getData().getString("result");
                error[0] = msg.getData().getString("error");
                done.countDown();
              }
            });
    ServiceConnection connection =
        new ServiceConnection() {
          public void onServiceConnected(ComponentName name, IBinder binder) {
            try {
              Message msg = Message.obtain(null, operation);
              msg.setData(payload);
              msg.replyTo = reply;
              new Messenger(binder).send(msg);
            } catch (Exception e) {
              error[0] = "Speech process connection failed";
              done.countDown();
            }
          }

          public void onServiceDisconnected(ComponentName name) {
            error[0] =
                "Speech process stopped. Original audio retained; retry with a smaller recording.";
            done.countDown();
          }

          public void onNullBinding(ComponentName name) {
            error[0] = "Speech process unavailable";
            done.countDown();
          }

          public void onBindingDied(ComponentName name) {
            onServiceDisconnected(name);
          }
        };
    boolean bound =
        c.bindService(new Intent(c, SherpaService.class), connection, Context.BIND_AUTO_CREATE);
    if (!bound) throw new IOException("Cannot start speech process");
    try {
      if (!done.await(10, TimeUnit.MINUTES)) throw new IOException("Speech inference timed out");
      if (error[0] != null) throw new IOException(error[0]);
      return new JSONObject(result[0]);
    } finally {
      c.unbindService(connection);
    }
  }

  static void validate(Context c, File root, boolean diar) throws Exception {
    Bundle b = new Bundle();
    b.putString("model", root.getAbsolutePath());
    request(c, diar ? 3 : 2, b);
  }

  static Transcript transcribe(Context c, File file) throws Exception {
    File root = SherpaAsr.root(c, false);
    if (root == null) throw new IOException("Import a SenseVoice model first");
    boolean diar = c.getSharedPreferences("life", 0).getBoolean("diar_enabled", false);
    File d = SherpaAsr.root(c, true);
    if (diar && d == null) throw new IOException("Import speaker models first");
    Bundle b = new Bundle();
    b.putString("model", root.getAbsolutePath());
    b.putString("audio", file.getAbsolutePath());
    b.putInt("speakers", c.getSharedPreferences("life", 0).getInt("speaker_count", -1));
    b.putString("diar_model", diar ? d.getAbsolutePath() : "");
    JSONObject j = request(c, 1, b);
    return new Transcript(
        j.getString("text"),
        j.getJSONArray("segments"),
        j.getString("engine"),
        j.getLong("duration_ms"));
  }
}
