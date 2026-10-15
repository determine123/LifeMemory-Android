package com.lifememory.app;

import java.util.Arrays;
import java.util.Locale;

/** SenseVoice options supported by the pinned sherpa-onnx runtime. */
final class SpeechOptions {
  static final String[] LANGUAGES = {"auto", "zh", "en", "yue", "ja", "ko"};
  final String language;
  final boolean normalizeText;

  SpeechOptions(String language, boolean normalizeText) {
    String value = language == null ? "auto" : language.trim().toLowerCase(Locale.ROOT);
    this.language = Arrays.asList(LANGUAGES).contains(value) ? value : "auto";
    this.normalizeText = normalizeText;
  }

  static String label(String language, boolean english) {
    int index = Arrays.asList(LANGUAGES).indexOf(new SpeechOptions(language, true).language);
    return (english
        ? new String[] {"Auto", "Mandarin", "English", "Cantonese", "Japanese", "Korean"}
        : new String[] {"自动识别", "普通话", "英语", "粤语", "日语", "韩语"})[index];
  }
}
