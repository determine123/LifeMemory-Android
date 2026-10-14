package com.lifememory.app;

import static org.junit.Assert.*;

import android.graphics.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class OcrTest {
  @Test
  public void bundledRecognizerReadsPhotographedMeetingText() throws Exception {
    Bitmap bitmap = Bitmap.createBitmap(1200, 300, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bitmap);
    canvas.drawColor(Color.WHITE);
    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    p.setColor(Color.BLACK);
    p.setTextSize(110);
    canvas.drawText("OpenClaw meeting", 30, 180, p);
    var recognizer = TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
    try {
      String text =
          Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)), 60, TimeUnit.SECONDS)
              .getText();
      assertTrue(
          "Expected meeting text, got: " + text,
          text.toLowerCase(java.util.Locale.ROOT).contains("openclaw"));
    } finally {
      recognizer.close();
      bitmap.recycle();
    }
  }
}
