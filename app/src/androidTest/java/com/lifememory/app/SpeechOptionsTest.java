package com.lifememory.app;

import static org.junit.Assert.*;
import org.junit.Test;

public class SpeechOptionsTest {
  @Test public void defaultAndUnknownLanguagesStayAutomatic() {
    assertEquals("auto", new SpeechOptions(null, true).language);
    assertEquals("auto", new SpeechOptions("invalid", true).language);
    assertEquals("auto", new SpeechOptions("", true).language);
  }
  @Test public void allSupportedCodesRemainSelectable() {
    for (String language : SpeechOptions.LANGUAGES)
      assertEquals(language, new SpeechOptions(language, true).language);
    assertEquals("zh", new SpeechOptions(" ZH ", true).language);
  }
  @Test public void normalizationDoesNotDependOnLanguage() {
    assertFalse(new SpeechOptions("zh", false).normalizeText);
    assertTrue(new SpeechOptions("en", true).normalizeText);
  }
  @Test public void labelsExistInBothInterfaceLanguages() {
    assertEquals("Cantonese", SpeechOptions.label("yue", true));
    assertEquals("粤语", SpeechOptions.label("yue", false));
    assertEquals("Auto", SpeechOptions.label("unknown", true));
  }
}
