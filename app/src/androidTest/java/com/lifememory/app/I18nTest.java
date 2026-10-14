package com.lifememory.app;

import static org.junit.Assert.*;

import android.app.Instrumentation;
import android.content.*;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class I18nTest {
  @Test
  public void languageDefaultsToEnglishAndPersistsChinese() {
    Context base = InstrumentationRegistry.getInstrumentation().getTargetContext();
    Context c =
        new ContextWrapper(base) {
          public SharedPreferences getSharedPreferences(String name, int mode) {
            return base.getSharedPreferences("test-language-" + name, mode);
          }
        };
    c.getSharedPreferences("life", 0).edit().clear().commit();
    assertTrue(I18n.english(c));
    assertEquals("Settings", I18n.t(c, "设置"));
    assertEquals("Source text stays unchanged", I18n.t(c, "Source text stays unchanged"));
    c.getSharedPreferences("life", 0).edit().putString("language", "zh").commit();
    assertEquals("设置", I18n.t(c, "设置"));
    assertEquals(
        "zh",
        I18n.localized(c).getResources().getConfiguration().getLocales().get(0).getLanguage());
    c.getSharedPreferences("life", 0).edit().clear().commit();
  }

  @Test
  public void activityShowsEnglishEvenWithChineseDeviceLocale() {
    Instrumentation i = InstrumentationRegistry.getInstrumentation();
    Context c = i.getTargetContext();
    SharedPreferences prefs = c.getSharedPreferences("life", 0);
    String old = prefs.getString("language", null);
    prefs.edit().putString("language", "en").commit();
    MainActivity a = null;
    try {
      a =
          (MainActivity)
              i.startActivitySync(
                  new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      MainActivity activity = a;
      i.runOnMainSync(
          () -> {
            assertTrue(hasText(activity.getWindow().getDecorView(), "Timeline"));
            assertTrue(hasText(activity.getWindow().getDecorView(), "Settings"));
            assertEquals(
                "en", activity.getResources().getConfiguration().getLocales().get(0).getLanguage());
          });
    } finally {
      if (a != null) {
        MainActivity activity = a;
        i.runOnMainSync(activity::finish);
      }
      SharedPreferences.Editor edit = prefs.edit();
      if (old == null) edit.remove("language");
      else edit.putString("language", old);
      edit.commit();
    }
  }

  private static boolean hasText(View view, String expected) {
    if (view instanceof TextView && expected.contentEquals(((TextView) view).getText()))
      return true;
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int n = 0; n < group.getChildCount(); n++)
        if (hasText(group.getChildAt(n), expected)) return true;
    }
    return false;
  }
}
