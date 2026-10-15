package com.lifememory.app;

import android.content.*;
import android.net.Uri;
import com.k2fsa.sherpa.onnx.*;
import java.io.*;
import java.util.*;
import org.json.*;

final class SherpaAsr {
  static File root(Context c, boolean diar) {
    String path =
        c.getSharedPreferences("life", 0).getString(diar ? "diar_model" : "sherpa_model", "");
    if (path.isEmpty()) return null;
    try {
      File f = new File(path);
      if (f.getCanonicalPath()
              .startsWith(
                  new File(c.getFilesDir(), "sherpa-models").getCanonicalPath() + File.separator)
          && new File(f, diar ? "segmentation.onnx" : "tokens.txt").isFile()
          && (diar ? new File(f, "embedding.onnx").isFile() : modelFile(f).isFile())) return f;
    } catch (IOException ignored) {
    }
    return null;
  }

  private static File modelFile(File root) {
    File f = new File(root, "model.int8.onnx");
    return f.isFile() ? f : new File(root, "model.onnx");
  }

  private static File find(File f, boolean diar, int depth) {
    if (depth > 8) return null;
    if (diar
        ? new File(f, "segmentation.onnx").isFile() && new File(f, "embedding.onnx").isFile()
        : new File(f, "tokens.txt").isFile() && modelFile(f).isFile()) return f;
    File[] a = f.listFiles();
    if (a != null)
      for (File child : a)
        if (child.isDirectory()) {
          File found = find(child, diar, depth + 1);
          if (found != null) return found;
        }
    return null;
  }

  static synchronized void install(Context c, Uri uri, boolean diar) throws Exception {
    File stage = new File(c.getFilesDir(), "sherpa-models/" + UUID.randomUUID());
    boolean ok = false;
    try {
      InputStream in = c.getContentResolver().openInputStream(uri);
      if (in == null) throw new IOException("Cannot read model ZIP");
      SafeZip.extract(in, stage, 1024L * 1024 * 1024);
      File f = find(stage, diar, 0);
      if (f == null)
        throw new IOException(
            diar
                ? "ZIP requires segmentation.onnx and embedding.onnx"
                : "ZIP requires model.int8.onnx (or model.onnx) and tokens.txt");
      SherpaClient.validate(c, f, diar);
      if (!c.getSharedPreferences("life", 0)
          .edit()
          .putString(diar ? "diar_model" : "sherpa_model", f.getAbsolutePath())
          .commit()) throw new IOException("Cannot save model setting");
      ok = true;
    } finally {
      if (!ok) SafeZip.remove(stage);
    }
  }

  static OfflineRecognizer recognizer(File root) {
    OfflineSenseVoiceModelConfig sv = new OfflineSenseVoiceModelConfig();
    sv.setModel(modelFile(root).getAbsolutePath());
    sv.setLanguage("auto");
    sv.setUseInverseTextNormalization(true);
    OfflineModelConfig model = new OfflineModelConfig();
    model.setSenseVoice(sv);
    model.setTokens(new File(root, "tokens.txt").getAbsolutePath());
    model.setNumThreads(2);
    model.setProvider("cpu");
    OfflineRecognizerConfig config = new OfflineRecognizerConfig();
    config.setModelConfig(model);
    return new OfflineRecognizer(null, config);
  }

  static OfflineSpeakerDiarization diarizer(File root) {
    return diarizer(root, -1);
  }

  static OfflineSpeakerDiarization diarizer(File root, int speakers) {
    OfflineSpeakerSegmentationPyannoteModelConfig py =
        new OfflineSpeakerSegmentationPyannoteModelConfig();
    py.setModel(new File(root, "segmentation.onnx").getAbsolutePath());
    OfflineSpeakerSegmentationModelConfig seg = new OfflineSpeakerSegmentationModelConfig();
    seg.setPyannote(py);
    seg.setNumThreads(2);
    seg.setProvider("cpu");
    OfflineSpeakerDiarizationConfig config = new OfflineSpeakerDiarizationConfig();
    config.setSegmentation(seg);
    config.setEmbedding(
        new SpeakerEmbeddingExtractorConfig(
            new File(root, "embedding.onnx").getAbsolutePath(), 2, false, "cpu"));
    FastClusteringConfig clustering = new FastClusteringConfig();
    clustering.setNumClusters(speakers >= 2 && speakers <= 8 ? speakers : -1);
    clustering.setThreshold(0.5f);
    config.setClustering(clustering);
    return new OfflineSpeakerDiarization(null, config);
  }

  static Transcript transcribe(Context c, File file) throws Exception {
    return SherpaClient.transcribe(c, file);
  }

  static Transcript run(File root, File diarRoot, File file, int speakers) throws Exception {
    float[] samples = PcmAudio.read(file);
    JSONArray parts = new JSONArray();
    StringBuilder text = new StringBuilder();
    List<float[]> turns = new ArrayList<>();
    boolean diar = diarRoot != null;
    if (diar) {
      File droot = diarRoot;
      if (droot == null)
        throw new IOException("Import speaker models or turn off automatic speaker separation");
      OfflineSpeakerDiarization d = diarizer(droot, speakers);
      try {
        for (OfflineSpeakerDiarizationSegment s : d.process(samples))
          turns.add(new float[] {s.getStart(), s.getEnd(), s.getSpeaker()});
      } finally {
        d.release();
      }
    } else turns.add(new float[] {0, samples.length / 16000f, -1});
    turns.sort((a, b) -> Float.compare(a[0], b[0]));
    OfflineRecognizer recognizer = recognizer(root);
    try {
      for (float[] turn : turns) {
        // Keep exact sample bounds without a float seconds round-trip for whole recordings.
        int start = diar ? Math.max(0, Math.round(turn[0] * 16000)) : 0,
            end = diar ? Math.min(samples.length, Math.round(turn[1] * 16000)) : samples.length;
        for (int at = start; at < end; ) {
          int until = SpeechChunks.nextEnd(samples, at, end);
          if (SpeechChunks.digitalSilence(samples, at, until)) {
            at = until;
            continue;
          }
          OfflineStream stream = recognizer.createStream();
          String value;
          try {
            stream.acceptWaveform(Arrays.copyOfRange(samples, at, until), 16000);
            recognizer.decode(stream);
            value = recognizer.getResult(stream).getText().trim();
          } finally {
            stream.release();
          }
          if (value.isEmpty()) {
            at = until;
            continue;
          }
          if (text.length() > 0) text.append('\n');
          text.append(value);
          parts.put(
              new JSONObject()
                  .put("start_ms", at * 1000L / 16000)
                  .put("end_ms", until * 1000L / 16000)
                  .put("text", value)
                  .put("speaker", turn[2] < 0 ? "" : "Speaker " + ((int) turn[2] + 1))
                  .put("speaker_auto", diar));
          at = until;
        }
      }
    } finally {
      recognizer.release();
    }
    return new Transcript(
        text.toString(),
        parts,
        diar ? "sherpa-onnx SenseVoice + diarization" : "sherpa-onnx SenseVoice",
        samples.length * 1000L / 16000);
  }
}
