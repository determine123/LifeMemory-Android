package com.lifememory.app;

import android.app.Service;
import android.content.Intent;
import android.os.*;
import com.k2fsa.sherpa.onnx.*;
import java.io.*;
import java.util.concurrent.*;
import org.json.*;

public class SherpaService extends Service {
  private final ExecutorService queue = Executors.newSingleThreadExecutor();
  private final Messenger messenger =
      new Messenger(
          new Handler(Looper.getMainLooper()) {
            public void handleMessage(Message request) {
              Bundle data = request.getData();
              int what = request.what;
              Messenger reply = request.replyTo;
              queue.submit(
                  () -> {
                    Bundle result = new Bundle();
                    try {
                      File root = privatePath(data.getString("model"), "sherpa-models");
                      JSONObject json;
                      if (what == 1) {
                        File audio = privatePath(data.getString("audio"), "media");
                        String dp = data.getString("diar_model", "");
                        File diar = dp.isEmpty() ? null : privatePath(dp, "sherpa-models");
                        Transcript t =
                            SherpaAsr.run(root, diar, audio, data.getInt("speakers", -1));
                        json =
                            new JSONObject()
                                .put("text", t.text)
                                .put("segments", t.segments)
                                .put("engine", t.engine)
                                .put("duration_ms", t.durationMs);
                      } else if (what == 2) {
                        OfflineRecognizer r = SherpaAsr.recognizer(root);
                        r.release();
                        json = new JSONObject().put("ok", true);
                      } else if (what == 3) {
                        OfflineSpeakerDiarization d = SherpaAsr.diarizer(root);
                        try {
                          if (d.sampleRate() != 16000)
                            throw new IOException("16 kHz speaker model required");
                        } finally {
                          d.release();
                        }
                        json = new JSONObject().put("ok", true);
                      } else throw new IOException("Unknown speech operation");
                      String encoded = json.toString();
                      if (encoded.length() > 300000)
                        throw new IOException("Transcript exceeds transfer limit; split recording");
                      result.putString("result", encoded);
                    } catch (Exception | LinkageError e) {
                      result.putString(
                          "error",
                          e.getMessage() == null ? "Speech inference failed" : e.getMessage());
                    }
                    try {
                      Message response = Message.obtain();
                      response.setData(result);
                      reply.send(response);
                    } catch (Exception ignored) {
                    }
                  });
            }
          });

  private File privatePath(String path, String directory) throws IOException {
    if (path == null) throw new IOException("Missing file path");
    File f = new File(path);
    if (!f.getCanonicalPath()
        .startsWith(new File(getFilesDir(), directory).getCanonicalPath() + File.separator))
      throw new IOException("Invalid private file path");
    return f;
  }

  public IBinder onBind(Intent intent) {
    return messenger.getBinder();
  }

  public void onDestroy() {
    queue.shutdownNow();
    super.onDestroy();
  }
}
