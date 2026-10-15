package com.lifememory.app;

import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** Optional owner-only provisioning asset; public builds never include this asset. */
final class PersonalAiSetup {
  static void apply(Context c) {
    try (InputStream in = c.getAssets().open("personal-ai.json")) {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      byte[] buffer = new byte[1024];
      int count;
      while ((count = in.read(buffer)) != -1) {
        if (bytes.size() + count > 8192) throw new IOException("Invalid setup asset");
        bytes.write(buffer, 0, count);
      }
      JSONObject config = new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
      String id = config.getString("deployment_id");
      if (id.isEmpty()) throw new IOException("Missing setup ID");
      if (id.equals(c.getSharedPreferences("ai", 0).getString("personal_setup_id", ""))) return;
      // Restrict this optional owner preset to the requested official provider.
      String base = config.getString("base");
      if (!ApiSettings.provider(base).equals("api.deepseek.com"))
        throw new IOException("Unsupported personal provider");
      AiConfiguration.save(c, base, config.getString("key"), "deepseek-flash", "whisper-1",
          "deepseek-flash");
      c.getSharedPreferences("ai", 0).edit().putString("personal_setup_id", id)
          .remove("personal_setup_error").commit();
    } catch (FileNotFoundException notPersonalBuild) {
      // Expected for public APKs; existing settings are left untouched.
    } catch (Exception e) {
      c.getSharedPreferences("ai", 0).edit().putBoolean("personal_setup_error", true).apply();
    }
  }
}
