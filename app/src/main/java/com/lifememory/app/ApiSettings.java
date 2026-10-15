package com.lifememory.app;

import java.io.IOException;
import java.net.*;
import java.util.Locale;

/** Validation shared by form saving and HTTP requests, with no Android dependencies. */
final class ApiSettings {
  static String base(String value) throws IOException {
    String base = value.trim().replaceAll("/+$", "");
    for (String endpoint : new String[] {"/chat/completions", "/audio/transcriptions", "/models"})
      if (base.endsWith(endpoint)) base = base.substring(0, base.length() - endpoint.length());
    try {
      URL url = new URL(base);
      if (!url.getProtocol().equals("https") || url.getHost().isEmpty()
          || url.getUserInfo() != null || url.getQuery() != null || url.getRef() != null)
        throw new IOException("Use an HTTPS base URL without credentials, query or fragment / 请使用不含凭据、查询或锚点的 HTTPS 地址");
    } catch (MalformedURLException e) {
      throw new IOException("Invalid API base URL / API 地址无效");
    }
    return base;
  }

  static String key(String value) throws IOException {
    String key = value.trim();
    if (key.regionMatches(true, 0, "Bearer ", 0, 7)) key = key.substring(7).trim();
    if (key.isEmpty() || key.matches("(?s).*\\s.*"))
      throw new IOException("Enter a key without spaces or line breaks / 密钥不能为空或包含空格、换行");
    return key;
  }

  static String provider(String value) throws IOException {
    URL url = new URL(base(value));
    int port = url.getPort();
    return url.getHost().toLowerCase(Locale.ROOT) + (port == -1 || port == 443 ? "" : ":" + port);
  }

  static void checkKeyReuse(String savedProvider, String newBase, boolean savedKey, String enteredKey)
      throws IOException {
    if (!enteredKey.trim().isEmpty()) { key(enteredKey); return; }
    if (!savedKey)
      throw new IOException("Enter this provider's API key / 请填写当前服务商的 API 密钥");
    if (!provider(newBase).equals(savedProvider))
      throw new IOException("Provider changed; enter its key before saving / 服务商已改变，请填写对应密钥后保存");
  }
}
