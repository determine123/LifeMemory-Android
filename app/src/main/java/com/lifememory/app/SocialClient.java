package com.lifememory.app;

import android.content.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import org.json.*;

final class SocialClient {
  private static final Object SESSION_LOCK = new Object();
  final Context context;
  final SharedPreferences prefs;
  SocialClient(Context c) { context = c; prefs = c.getSharedPreferences("social", 0); }
  String base() { return prefs.getString("base", ""); }
  void configure(String value) throws Exception {
    synchronized (SESSION_LOCK) {
    String normalized = SocialAddress.normalize(value);
    SharedPreferences.Editor edit = prefs.edit().putString("base", normalized);
    if (!normalized.equals(base())) edit.remove("session");
    if (!edit.commit()) throw new IOException("Cannot save server / 无法保存服务地址");
    }
  }
  JSONObject session() {
    synchronized (SESSION_LOCK) {
    try {
      JSONObject value = new JSONObject(Secrets.decrypt(prefs.getString("session", "")));
      if (base().equals(value.getString("base"))) return value;
    } catch (Exception ignored) { }
    return new JSONObject();
    }
  }
  boolean signedIn() { return !session().optString("token").isEmpty(); }
  void remember(JSONObject reply, String server) throws Exception {
    synchronized (SESSION_LOCK) {
    String address = SocialAddress.normalize(server);
    if (!address.equals(base())) throw new IOException("Server changed; sign in again / 服务地址已变更，请重新登录");
    JSONObject value = new JSONObject().put("base", address).put("token", reply.getString("token"))
        .put("user", reply.getJSONObject("user"));
    if (!prefs.edit().putString("session", Secrets.seal(value.toString())).commit())
      throw new IOException("Cannot save session / 无法保存登录状态");
    }
  }
  void forget() { synchronized (SESSION_LOCK) { prefs.edit().remove("session").apply(); } }
  JSONObject request(String method, String route, JSONObject body) throws Exception {
    return new JSONObject(new String(bytes(method, route, body), StandardCharsets.UTF_8));
  }
  JSONObject request(String method, String route, JSONObject body, String expectedServer) throws Exception {
    return new JSONObject(new String(bytes(method, route, body, expectedServer), StandardCharsets.UTF_8));
  }
  byte[] bytes(String method, String route, JSONObject body) throws Exception {
    return bytes(method, route, body, null);
  }
  private byte[] bytes(String method, String route, JSONObject body, String expectedServer) throws Exception {
    String address, token;
    synchronized (SESSION_LOCK) {
      address = SocialAddress.normalize(base());
      if (expectedServer != null && !address.equals(SocialAddress.normalize(expectedServer)))
        throw new IOException("Server changed; try signing in again / 服务地址已变更，请重新登录");
      token = session().optString("token");
    }
    URL url = new URL(address + route);
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.setInstanceFollowRedirects(false);
    connection.setConnectTimeout(10000);
    connection.setReadTimeout(30000);
    connection.setRequestMethod(method);
    if (!token.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + token);
    try {
      if (body != null) {
        byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setFixedLengthStreamingMode(data.length);
        try (OutputStream out = connection.getOutputStream()) { out.write(data); }
      }
      int code = connection.getResponseCode();
      InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
      ByteArrayOutputStream data = new ByteArrayOutputStream();
      if (stream != null) try (InputStream in = stream) {
        byte[] buffer = new byte[8192]; int n;
        while ((n = in.read(buffer)) != -1) {
          if (data.size() + n > 4 * 1024 * 1024) throw new IOException("Server response too large");
          data.write(buffer, 0, n);
        }
      }
      if (code < 200 || code >= 300) {
        if (code == 401) synchronized (SESSION_LOCK) {
          if (address.equals(base()) && token.equals(session().optString("token"))) forget();
        }
        String detail = "Circle request failed / 朋友圈请求失败";
        try {
          Object value = new JSONObject(data.toString("UTF-8")).opt("detail");
          if (value instanceof String) detail = (String) value;
        } catch (Exception ignored) { }
        throw new IOException("HTTP " + code + " · " + detail);
      }
      return data.toByteArray();
    } finally { connection.disconnect(); }
  }
}
