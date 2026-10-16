package com.lifememory.app;

import java.io.IOException;
import java.net.URL;

final class SocialAddress {
  static String normalize(String input) throws IOException {
    String base = input.trim().replaceAll("/+$", "");
    try {
      URL url = new URL(base);
      boolean local = url.getHost().equals("127.0.0.1") || url.getHost().equals("localhost")
          || url.getHost().equals("10.0.2.2");
      if ((!url.getProtocol().equals("https") && !(local && url.getProtocol().equals("http")))
          || url.getHost().isEmpty() || url.getUserInfo() != null || url.getQuery() != null
          || url.getRef() != null || !url.getPath().isEmpty()) throw new IOException();
      return base;
    } catch (Exception e) {
      throw new IOException("Use your Circle server HTTPS origin (local test: http://127.0.0.1:8787) / 请填写朋友圈后台 HTTPS 根地址");
    }
  }
}
