package com.lifememory.app;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Explicitly shared social records; never auto-publishes the private journal. */
final class CircleUi {
  final MainActivity a;
  final SocialClient api;
  final ExecutorService io = Executors.newFixedThreadPool(3);
  final ArrayList<Uri> chosen = new ArrayList<>();
  String draft = "";
  boolean privatePost, working;
  LinearLayout feed;
  TextView status;
  Button more;
  long before;
  interface Task { JSONObject run() throws Exception; }
  interface Done { void run(JSONObject value) throws Exception; }
  CircleUi(MainActivity a) { this.a = a; api = new SocialClient(a); }
  void close() { io.shutdownNow(); }
  String t(String en, String zh) { return I18n.english(a) ? en : zh; }
  void run(Task task, Done done) {
    if (working) return;
    working = true;
    if (status != null) status.setText(t("Connecting…", "正在连接…"));
    LinearLayout view = a.body;
    io.submit(() -> {
      JSONObject value = null; String error = null;
      try { value = task.run(); } catch (Exception e) { error = e.getMessage(); }
      final JSONObject result = value; final String problem = error;
      a.runOnUiThread(() -> {
        working = false;
        if (a.isFinishing() || a.isDestroyed()) return;
        if (view != a.body) { if (a.circleVisible()) a.render(); return; }
        if (status != null) status.setText(problem == null ? "" : t("Request failed; retry when connected.", "请求失败，请检查连接后重试。"));
        if (problem != null) { a.message(problem); return; }
        try { done.run(result); } catch (Exception e) { a.message(e.getMessage()); }
      });
    });
  }

  void render() {
    LinearLayout intro = a.card();
    intro.addView(a.text(t("Circle", "朋友圈"), 24, 0xff223d33));
    intro.addView(a.text(t("Only posts you choose to publish go to your Circle server. Your journal stays separate.",
        "只有主动发布的内容会上传朋友圈后台；私人日记不会自动公开。"), 13, 0xff6b786f));
    intro.addView(a.text(api.base().isEmpty()
        ? t("Server not configured. Deploy social-server, then enter its HTTPS address.", "尚未配置后台，请先部署 social-server，再填写 HTTPS 服务地址。")
        : api.base(), 12, 0xff6b786f));
    status = a.text(working ? t("Connecting…", "正在连接…") : "", 13, 0xff6b786f);
    intro.addView(status);
    if (!api.signedIn()) {
      EditText server = a.editor(t("Circle server HTTPS address", "朋友圈后台 HTTPS 地址"), api.base());
      server.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
      intro.addView(server);
      EditText name = a.editor(t("Username (3–32 letters, digits, _)", "账号（3–32 位字母、数字、下划线）"), "");
      EditText password = a.editor(t("Password (at least 10 characters)", "密码（至少 10 位）"), "");
      password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
      EditText display = a.editor(t("Display name (registration)", "昵称（注册时填写）"), "");
      EditText invite = a.editor(t("Server invitation code (registration)", "服务器邀请码（注册时填写）"), "");
      intro.addView(name); intro.addView(password); intro.addView(display); intro.addView(invite);
      a.row(intro,
          a.button(t("Sign in", "登录"), () -> authenticate(false, server, name, password, display, invite)),
          a.button(t("Register", "注册"), () -> authenticate(true, server, name, password, display, invite)));
      intro.addView(a.text(t("Friends must use the same deployed server. A LifeMemory AI key is not a Circle account.",
          "好友须使用同一后台。DeepSeek 密钥不能作为朋友圈账号登录。"), 12, 0xff6b786f));
      return;
    }
    JSONObject user = api.session().optJSONObject("user");
    if (user == null) { api.forget(); a.render(); return; }
    intro.addView(a.text(user.optString("display_name") + " · @" + user.optString("username"), 18, 0xff223d33));
    a.row(intro, a.button(t("Post", "发动态"), this::compose),
        a.button(t("Friends", "好友与申请"), this::friends),
        a.button(t("Refresh", "刷新"), () -> load(false)));
    a.row(intro, a.button(t("Invite", "邀请好友"), () -> {
      Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain")
          .putExtra(Intent.EXTRA_TEXT, "LifeMemory Circle\n" + api.base() + "\n@" + user.optString("username"));
      a.startActivity(Intent.createChooser(share, t("Invite a friend", "邀请好友")));
    }), a.button(t("Account", "账号管理"), this::account));
    feed = a.column(); a.body.addView(feed);
    more = a.button(t("Load more", "加载更多"), () -> load(true)); a.body.addView(more);
    load(false);
  }

  void authenticate(boolean register, EditText server, EditText name, EditText password, EditText display, EditText invite) {
    String host = server.getText().toString(), username = name.getText().toString().trim();
    String secret = password.getText().toString(), nickname = display.getText().toString(), code = invite.getText().toString();
    run(() -> {
      api.configure(host);
      JSONObject result = api.request("POST", register ? "/v1/register" : "/v1/login",
          new JSONObject().put("username", username).put("password", secret)
              .put("display_name", nickname).put("invite_code", code), host);
      api.remember(result, host); return result;
    }, value -> { password.setText(""); invite.setText(""); a.render(); });
  }

  void load(boolean append) {
    if (!api.signedIn()) { a.render(); return; }
    if (append && before == 0) return;
    run(() -> api.request("GET", "/v1/feed" + (append ? "?before=" + before : ""), null), value -> {
      if (!append) feed.removeAllViews();
      JSONArray posts = value.getJSONArray("items");
      if (!append && posts.length() == 0) feed.addView(a.text(t("No shared posts yet. Add friends or publish your first moment.",
          "还没有动态，先添加好友或发布第一条吧。"), 16, 0xff6b786f));
      for (int i = 0; i < posts.length(); i++) post(posts.getJSONObject(i));
      before = value.optLong("next", 0); more.setVisibility(before == 0 ? View.GONE : View.VISIBLE);
    });
  }

  void post(JSONObject value) throws Exception {
    LinearLayout card = a.column();
    card.setPadding(a.dp(12), a.dp(18), a.dp(12), a.dp(18));
    JSONObject author = value.getJSONObject("author"); long id = value.getLong("id");
    card.addView(a.text(author.getString("display_name") + " · @" + author.getString("username"), 18, 0xff223d33));
    card.addView(a.text(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        .format(new Date(value.getLong("created") * 1000))
        + (value.getString("visibility").equals("private") ? t(" · Only me", " · 仅自己") : t(" · Friends", " · 好友可见")), 12, 0xff6b786f));
    if (!value.getString("text").isEmpty()) card.addView(a.text(value.getString("text"), 16, 0xff223d33));
    JSONArray photos = value.getJSONArray("photos");
    if (photos.length() > 0) {
      image(card, photos.getLong(0));
      card.addView(a.text(photos.length() + t(" photo(s) · Open details to view all", " 张照片 · 查看详情可浏览全部"), 12, 0xff6b786f));
    }
    a.row(card,
        a.button((value.getBoolean("liked") ? "♥ " : "♡ ") + value.getInt("likes"),
            () -> run(() -> api.request("POST", "/v1/posts/" + id + "/like",
                new JSONObject().put("liked", !value.getBoolean("liked"))), result -> load(false))),
        a.button(t("Comments ", "评论 ") + value.getInt("comments"), () -> detail(id)));
    feed.addView(card);
  }

  void image(LinearLayout parent, long id) {
    ImageView image = new ImageView(a); image.setScaleType(ImageView.ScaleType.FIT_CENTER);
    image.setContentDescription(t("Shared photo", "动态照片"));
    parent.addView(image, new LinearLayout.LayoutParams(-1, a.dp(210)));
    io.submit(() -> {
      try {
        byte[] data = api.bytes("GET", "/v1/photos/" + id, null);
        BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, options);
        options.inSampleSize = 1;
        while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 1000) options.inSampleSize *= 2;
        options.inJustDecodeBounds = false;
        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
        a.runOnUiThread(() -> {
          if (!a.isDestroyed() && image.isAttachedToWindow()) image.setImageBitmap(bitmap);
          else if (bitmap != null) bitmap.recycle();
        });
      } catch (Exception e) { a.runOnUiThread(() -> image.setContentDescription(t("Photo unavailable; refresh", "照片加载失败，请刷新"))); }
    });
  }

  void detail(long id) {
    run(() -> api.request("GET", "/v1/posts/" + id, null), value -> {
      LinearLayout content = a.column(); content.setPadding(a.dp(18), 0, a.dp(18), 0);
      content.addView(a.text(value.getString("text"), 17, 0xff223d33));
      for (int i = 0; i < value.getJSONArray("photos").length(); i++) image(content, value.getJSONArray("photos").getLong(i));
      content.addView(a.text(t("Visible friends can read comments on this post.", "此动态的可见好友都可阅读这里的评论。"), 12, 0xff6b786f));
      content.addView(a.text(t("Long-press your comment to delete it.", "长按自己的评论可删除；作者也可管理此动态下的评论。"), 12, 0xff6b786f));
      JSONArray replies = value.getJSONArray("replies"); long me = api.session().getJSONObject("user").getLong("id");
      for (int i = 0; i < replies.length(); i++) {
        JSONObject reply = replies.getJSONObject(i);
        TextView text = a.text(reply.getString("display_name") + ": " + reply.getString("text"), 15, 0xff223d33);
        content.addView(text);
        if (reply.getLong("author") == me || value.getJSONObject("author").getLong("id") == me) {
          long commentId = reply.getLong("id");
          text.setOnLongClickListener(v -> { confirm(t("Delete comment?", "删除这条评论？"),
              () -> run(() -> api.request("DELETE", "/v1/comments/" + commentId, null), result -> { text.setVisibility(View.GONE); })); return true; });
        }
      }
      EditText comment = a.editor(t("Write a comment (max 500 characters)", "写评论（最多 500 字）"), ""); content.addView(comment);
      ScrollView scroll = new ScrollView(a); scroll.addView(content);
      AlertDialog dialog = new AlertDialog.Builder(a).setTitle(t("Post details", "动态详情")).setView(scroll)
          .setPositiveButton(t("Comment", "评论"), null).setNegativeButton(t("Close", "关闭"), null).create();
      if (value.getJSONObject("author").getLong("id") == me)
        content.addView(a.button(t("Delete this post", "删除这条动态"), () -> confirm(t("Delete this post and photos?", "删除此动态和照片？"),
            () -> run(() -> api.request("DELETE", "/v1/posts/" + id, null), result -> { dialog.dismiss(); load(false); }))));
      dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
        String text = comment.getText().toString();
        run(() -> api.request("POST", "/v1/posts/" + id + "/comments", new JSONObject().put("text", text)),
            result -> { dialog.dismiss(); detail(id); });
      })); dialog.show();
    });
  }

  void compose() {
    LinearLayout content = a.column(); content.setPadding(a.dp(18), 0, a.dp(18), 0);
    EditText text = a.editor(t("Share a moment (max 2000 characters)", "分享这一刻（最多 2000 字）"), draft); content.addView(text);
    CheckBox onlyMe = new CheckBox(a); onlyMe.setText(t("Only me (stored on Circle server)", "仅自己可见（仍存储在朋友圈后台）"));
    onlyMe.setChecked(privatePost); content.addView(onlyMe);
    TextView count = a.text(chosen.size() + t(" / 9 photos selected", " / 9 张照片已选择"), 14, 0xff6b786f); content.addView(count);
    AlertDialog dialog = new AlertDialog.Builder(a).setTitle(t("New post", "发动态")).setView(content)
        .setPositiveButton(t("Publish", "发布"), null).setNegativeButton(t("Cancel", "取消"), (d, w) -> { draft = text.getText().toString(); privatePost = onlyMe.isChecked(); }).create();
    content.addView(a.button(t("Choose photos", "选择照片"), () -> {
      draft = text.getText().toString(); privatePost = onlyMe.isChecked(); dialog.dismiss();
      a.startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*")
          .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true), 80);
    }));
    content.addView(a.button(t("Clear photos", "清空照片"), () -> { chosen.clear(); count.setText(t("0 / 9 photos selected", "0 / 9 张照片已选择")); }));
    dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
      draft = text.getText().toString(); privatePost = onlyMe.isChecked();
      String message = draft, visibility = privatePost ? "private" : "friends";
      ArrayList<Uri> photos = new ArrayList<>(chosen);
      run(() -> {
        JSONArray images = new JSONArray(); for (Uri uri : photos) images.put(encodePhoto(uri));
        return api.request("POST", "/v1/posts", new JSONObject().put("text", message).put("visibility", visibility).put("photos", images));
      }, result -> { draft = ""; chosen.clear(); dialog.dismiss(); load(false); });
    })); dialog.show();
  }

  void picked(Intent data) {
    if (data != null) {
      if (data.getClipData() != null) for (int i = 0; i < data.getClipData().getItemCount() && chosen.size() < 9; i++) {
        Uri uri = data.getClipData().getItemAt(i).getUri(); if (!chosen.contains(uri)) chosen.add(uri);
      }
      else if (data.getData() != null && chosen.size() < 9 && !chosen.contains(data.getData())) chosen.add(data.getData());
    }
    compose();
  }

  String encodePhoto(Uri uri) throws Exception {
    File temp = File.createTempFile("circle-", ".image", a.getCacheDir()); Bitmap bitmap = null;
    try {
      try (InputStream in = a.getContentResolver().openInputStream(uri); OutputStream out = new FileOutputStream(temp)) {
        if (in == null) throw new IOException("Cannot read selected photo");
        byte[] buffer = new byte[8192]; int n, size = 0;
        while ((n = in.read(buffer)) != -1) { size += n; if (size > 32 * 1024 * 1024) throw new IOException("Photo exceeds 32 MB"); out.write(buffer, 0, n); }
      }
      bitmap = Photos.preview(temp.getAbsolutePath(), 1280);
      if (bitmap == null) throw new IOException("Unsupported photo");
      ByteArrayOutputStream out = new ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out);
      if (out.size() > 900000) { out.reset(); bitmap.compress(Bitmap.CompressFormat.JPEG, 50, out); }
      if (out.size() > 1000000) throw new IOException("Photo too large after compression");
      return android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP);
    } finally { if (bitmap != null) bitmap.recycle(); temp.delete(); }
  }

  void friends() {
    run(() -> api.request("GET", "/v1/friends", null), value -> {
      LinearLayout content = a.column(); content.setPadding(a.dp(18), 0, a.dp(18), 0);
      EditText name = a.editor(t("Exact friend's username", "好友的完整账号"), ""); content.addView(name);
      content.addView(a.button(t("Send request", "发送好友申请"), () -> {
        String username = name.getText().toString().trim();
        run(() -> api.request("POST", "/v1/friends/requests", new JSONObject().put("username", username)),
            result -> a.message(t("Request sent. Ask your friend to accept it.", "申请已发送，请好友在申请列表中通过。")));
      }));
      JSONArray items = value.getJSONArray("items");
      AlertDialog dialog = new AlertDialog.Builder(a).setTitle(t("Friends and requests", "好友与申请"))
          .setNegativeButton(t("Close", "关闭"), null).create();
      for (int i = 0; i < items.length(); i++) {
        JSONObject item = items.getJSONObject(i), person = item.getJSONObject("user");
        long requestId = item.getLong("request_id"), uid = person.getLong("id");
        boolean pending = item.getString("status").equals("pending");
        content.addView(a.text(person.getString("display_name") + " · @" + person.getString("username")
            + (pending ? (item.getBoolean("incoming") ? t(" · Incoming", " · 待通过") : t(" · Sent", " · 已申请")) : t(" · Friend", " · 好友")), 16, 0xff223d33));
        if (pending && item.getBoolean("incoming"))
          content.addView(a.button(t("Accept", "通过申请"), () -> run(() -> api.request("POST", "/v1/friends/requests/" + requestId + "/accept", new JSONObject()),
              result -> { dialog.dismiss(); a.render(); })));
        content.addView(a.button(pending ? t("Dismiss/cancel request", "拒绝或撤回申请") : t("Remove friend", "删除好友"),
            () -> confirm(t("Remove this relationship?", "移除此好友或申请？"), () -> run(() -> api.request("DELETE", "/v1/friends/" + uid, null),
                result -> { dialog.dismiss(); a.render(); }))));
      }
      ScrollView scroll = new ScrollView(a); scroll.addView(content); dialog.setView(scroll); dialog.show();
    });
  }

  void confirm(String title, Runnable action) {
    new AlertDialog.Builder(a).setMessage(title).setNegativeButton(t("Cancel", "取消"), null)
        .setPositiveButton(t("Confirm", "确认"), (d, w) -> action.run()).show();
  }

  void account() {
    new AlertDialog.Builder(a).setTitle(t("Circle account", "朋友圈账号"))
        .setItems(new String[] {t("Sign out", "退出登录"), t("Delete Circle account and shared data", "注销朋友圈账号及已分享数据")}, (d, item) -> {
          if (item == 0) run(() -> {
            try { api.request("POST", "/v1/logout", new JSONObject()); } catch (Exception ignored) { }
            api.forget(); return new JSONObject();
          }, value -> a.render());
          else confirm(t("Delete your server account, posts, photos and comments? Your local journal is kept.",
              "注销后台账号，删除动态、照片和评论？本机日记会保留。"),
              () -> run(() -> api.request("DELETE", "/v1/account", null), value -> { api.forget(); a.render(); }));
        }).show();
  }
}
