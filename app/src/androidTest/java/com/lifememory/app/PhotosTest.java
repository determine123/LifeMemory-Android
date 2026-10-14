package com.lifememory.app;

import static org.junit.Assert.*;

import android.graphics.*;
import androidx.exifinterface.media.ExifInterface;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class PhotosTest {
  @Test
  public void previewHonorsExifRotationAndBounds() throws Exception {
    File f =
        new File(
            InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir(),
            "rotation-test.jpg");
    try {
      Bitmap b = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888);
      b.eraseColor(Color.GREEN);
      try (OutputStream out = new FileOutputStream(f)) {
        b.compress(Bitmap.CompressFormat.JPEG, 95, out);
      }
      b.recycle();
      ExifInterface exif = new ExifInterface(f);
      exif.setAttribute(ExifInterface.TAG_ORIENTATION, "6");
      exif.saveAttributes();
      Bitmap result = Photos.preview(f.getAbsolutePath(), 100);
      assertNotNull(result);
      assertEquals(50, result.getWidth());
      assertEquals(100, result.getHeight());
      result.recycle();
    } finally {
      f.delete();
    }
  }

  @Test
  public void importUsesOriginalCaptureDate() throws Exception {
    File f =
        new File(
            InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir(),
            "date-test.jpg");
    TimeZone prior = TimeZone.getDefault();
    try {
      TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
      Bitmap b = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888);
      try (OutputStream out = new FileOutputStream(f)) {
        b.compress(Bitmap.CompressFormat.JPEG, 80, out);
      }
      b.recycle();
      ExifInterface exif = new ExifInterface(f);
      exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2025:10:12 23:59:00");
      exif.saveAttributes();
      assertEquals("2025-10-12", Store.day(Photos.timestamp(f)));
    } finally {
      TimeZone.setDefault(prior);
      f.delete();
    }
  }
}
