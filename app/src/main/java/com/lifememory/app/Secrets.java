package com.lifememory.app;

import android.content.Context;
import android.security.keystore.*;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

public class Secrets {
  private static javax.crypto.SecretKey key() throws Exception {
    KeyStore k = KeyStore.getInstance("AndroidKeyStore");
    k.load(null);
    if (!k.containsAlias("life-api")) {
      KeyGenerator g = KeyGenerator.getInstance("AES", "AndroidKeyStore");
      g.init(
          new KeyGenParameterSpec.Builder(
                  "life-api", KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
              .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
              .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
              .build());
      g.generateKey();
    }
    return (javax.crypto.SecretKey) k.getKey("life-api", null);
  }

  public static void set(Context c, String value) throws Exception {
    value = Ai.normalizeKey(value);
    Cipher a = Cipher.getInstance("AES/GCM/NoPadding");
    a.init(Cipher.ENCRYPT_MODE, key());
    c.getSharedPreferences("ai", 0)
        .edit()
        .putString(
            "secret",
            Base64.encodeToString(a.getIV(), Base64.NO_WRAP)
                + ":"
                + Base64.encodeToString(
                    a.doFinal(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                    Base64.NO_WRAP))
        .apply();
  }

  public static String get(Context c) throws Exception {
    String s = c.getSharedPreferences("ai", 0).getString("secret", "");
    if (s.isEmpty()) return "";
    String[] p = s.split(":");
    Cipher a = Cipher.getInstance("AES/GCM/NoPadding");
    a.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(p[0], 0)));
    return new String(a.doFinal(Base64.decode(p[1], 0)), java.nio.charset.StandardCharsets.UTF_8);
  }
}
