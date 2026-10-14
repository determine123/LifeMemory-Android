package com.lifememory.app;

import android.graphics.*;
import androidx.exifinterface.media.ExifInterface;
import java.io.*;
import java.text.*;
import java.util.*;

public class Photos {
  public static long timestamp(File f) {
    try {
      String date = new ExifInterface(f).getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL);
      if (date != null) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US);
        format.setLenient(false);
        Date d = format.parse(date);
        if (d != null && d.getTime() > 0 && d.getTime() <= System.currentTimeMillis() + 86400000)
          return d.getTime();
      }
    } catch (Exception ignored) {
    }
    return System.currentTimeMillis();
  }

  public static Bitmap preview(String file, int max) {
    BitmapFactory.Options o = new BitmapFactory.Options();
    o.inJustDecodeBounds = true;
    BitmapFactory.decodeFile(file, o);
    o.inSampleSize = 1;
    while (Math.max(o.outWidth, o.outHeight) / o.inSampleSize > max * 2) o.inSampleSize *= 2;
    o.inJustDecodeBounds = false;
    Bitmap b = BitmapFactory.decodeFile(file, o);
    if (b == null) return null;
    try {
      int orientation = new ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
      Matrix m = new Matrix();
      switch (orientation) {
        case 2:
          m.setScale(-1, 1);
          break;
        case 3:
          m.setRotate(180);
          break;
        case 4:
          m.setScale(1, -1);
          break;
        case 5:
          m.setRotate(90);
          m.postScale(-1, 1);
          break;
        case 6:
          m.setRotate(90);
          break;
        case 7:
          m.setRotate(270);
          m.postScale(-1, 1);
          break;
        case 8:
          m.setRotate(270);
          break;
      }
      Bitmap rotated = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
      if (rotated != b) b.recycle();
      b = rotated;
    } catch (IOException ignored) {
    }
    int size = Math.max(b.getWidth(), b.getHeight());
    if (size > max) {
      float scale = (float) max / size;
      Bitmap small =
          Bitmap.createScaledBitmap(
              b,
              Math.max(1, Math.round(b.getWidth() * scale)),
              Math.max(1, Math.round(b.getHeight() * scale)),
              true);
      if (small != b) b.recycle();
      b = small;
    }
    return b;
  }
}
