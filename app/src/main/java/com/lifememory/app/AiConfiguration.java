package com.lifememory.app;

import android.content.*;
import java.io.IOException;

/** Store the encrypted credential and its provider/configuration in one preferences commit. */
final class AiConfiguration {
  static void save(Context c, String base, String enteredKey, String model, String asr, String vision)
      throws Exception {
    String normalized = ApiSettings.base(base);
    if (model.trim().isEmpty() || asr.trim().isEmpty())
      throw new IOException("Enter chat and speech model names / 请填写对话与语音模型名称");
    SharedPreferences prefs = c.getSharedPreferences("ai", 0);
    boolean saved = false;
    if (enteredKey.trim().isEmpty()) {
      try { saved = !Secrets.get(c).isEmpty(); }
      catch (Exception e) {
        throw new IOException("Saved key cannot be read; enter it again / 无法读取已保存密钥，请重新填写");
      }
    }
    String oldProvider = prefs.getString("key_provider", "");
    if (oldProvider.isEmpty()) {
      try { oldProvider = ApiSettings.provider(prefs.getString("base", "")); }
      catch (IOException ignored) { }
    }
    ApiSettings.checkKeyReuse(oldProvider, normalized, saved, enteredKey);
    SharedPreferences.Editor edit = prefs.edit()
        .putString("base", normalized).putString("model", model.trim())
        .putString("asr", asr.trim()).putString("vision", vision.trim())
        .putString("key_provider", ApiSettings.provider(normalized));
    if (!enteredKey.trim().isEmpty()) edit.putString("secret", Secrets.encrypt(enteredKey));
    if (!edit.commit()) throw new IOException("Cannot save AI settings / 无法保存 AI 配置");
    c.getSharedPreferences("life", 0).edit().remove("asr_blocked").apply();
  }
}
