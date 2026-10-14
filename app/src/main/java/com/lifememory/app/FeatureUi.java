package com.lifememory.app;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import org.json.*;

/** Auday-inspired local recording, replay, reflection and storage controls. */
class FeatureUi {
  private final MainActivity a;

  FeatureUi(MainActivity a) {
    this.a = a;
  }

  void recording(LinearLayout hero) {
    TextView status = a.text("", 12, 0xffd2ddd3);
    hero.addView(status);
    updateStatus(status);
    a.statusLabel = status;
    if (CaptureService.active)
      a.row(
          hero,
          a.button(
              CaptureService.paused ? I18n.t(a, "继续录音") : I18n.t(a, "暂停录音"),
              () -> a.startService(new Intent(a, CaptureService.class).setAction("pause"))),
          a.button(
              I18n.t(a, "标记此刻"),
              () -> {
                if (CaptureService.paused) {
                  a.message(I18n.t(a, "录音已暂停，请继续后再标记。"));
                  return;
                }
                a.startService(new Intent(a, CaptureService.class).setAction("mark"));
              }));
    a.row(
        a.body,
        a.button(I18n.t(a, "记录此刻"), this::context),
        a.button(I18n.t(a, "重要时刻"), this::highlights),
        a.button(
            I18n.t(a, "待转写"),
            () -> {
              if (!SpeechJobs.available(a)) {
                a.message(SpeechJobs.unavailableReason(a));
                return;
              }
              int count = SpeechJobs.retry(a);
              a.message(I18n.t(a, "已加入转写队列：") + count + I18n.t(a, " 条"));
            }));
    a.body.addView(a.button(I18n.t(a, "处理中心 · 查看进度与失败记录"), () -> ProcessingCenter.show(a)));
  }

  void updateStatus(TextView label) {
    String state =
        CaptureService.active
            ? (CaptureService.paused ? I18n.t(a, "已暂停 · 麦克风关闭") : I18n.t(a, "录音中 · 麦克风开启"))
            : I18n.t(a, "已停止");
    long saved = a.getSharedPreferences("capture", 0).getLong("last_saved", 0);
    String last =
        saved == 0
            ? I18n.t(a, "尚无片段")
            : I18n.t(a, "最近保存 ")
                + new java.text.SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(new Date(saved));
    label.setText(
        state
            + (CaptureService.active
                ? I18n.t(a, " · 会话 ")
                    + clock(System.currentTimeMillis() - CaptureService.sessionStarted)
                : "")
            + "\n"
            + last
            + I18n.t(a, " · 转写队列 ")
            + SpeechJobs.pendingCount());
  }

  static String clock(long ms) {
    long seconds = Math.max(0, ms) / 1000;
    return String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60);
  }

  void event(Store.Event e, LinearLayout card) {
    if (e.pinned) card.addView(a.text(I18n.t(a, "★ 重要时刻 · 已保护原音频"), 12, 0xff547567));
    if (e.kind.equals("audio")) {
      JSONObject meta = e.metadata();
      card.addView(
          a.text(
              I18n.t(a, "时长 ")
                  + (meta.has("duration_ms")
                      ? clock(meta.optLong("duration_ms", 0))
                      : I18n.t(a, "未分析"))
                  + " · "
                  + meta.optString("asr", I18n.t(a, "未转写"))
                  + (e.path.isEmpty() ? I18n.t(a, " · 原音频未保留") : ""),
              12,
              0xff788278));
      if (meta.has("asr_error")) card.addView(a.text(meta.optString("asr_error"), 12, 0xff985e42));
      a.row(
          card,
          a.button(
              e.pinned ? I18n.t(a, "取消重要标记") : I18n.t(a, "保留为重要"),
              () -> {
                a.db.pin(e.id, !e.pinned);
                a.render();
              }),
          a.button(I18n.t(a, "转写与定位"), () -> transcript(e)));
    }
    if (e.kind.equals("bookmark"))
      a.row(
          card,
          a.button(
              I18n.t(a, "回听此刻"),
              () -> {
                Store.Event audio = a.db.anchor(e.metadata().optLong("anchor_time", 0));
                if (audio == null || audio.path.isEmpty()) {
                  a.message(I18n.t(a, "对应录音尚未保存或已不存在。"));
                  return;
                }
                a.play(audio.path, e.metadata().optLong("offset_ms", 0));
              }));
  }

  void transcript(Store.Event e) {
    JSONArray segments = e.metadata().optJSONArray("segments");
    if (segments == null || segments.length() == 0) {
      a.message(I18n.t(a, "这条记录还没有带时间戳的转写。可使用离线模型，或支持 verbose_json / diarized_json 的云端语音服务。"));
      return;
    }
    LinearLayout list = a.column();
    try {
      for (int i = 0; i < segments.length(); i++) {
        JSONObject s = segments.getJSONObject(i);
        String speaker = s.optString("speaker", "");
        Button line =
            a.button(
                clock(s.optLong("start_ms", 0))
                    + "  "
                    + (speaker.isEmpty() ? "" : speaker + "：")
                    + s.optString("text", ""),
                () -> a.play(e.path, s.optLong("start_ms", 0)));
        line.setMinHeight(a.dp(64));
        line.setOnLongClickListener(
            v -> {
              renameSpeaker(e, s, line);
              return true;
            });
        list.addView(line);
      }
    } catch (JSONException ex) {
      a.message(ex.getMessage());
      return;
    }
    ScrollView scroll = new ScrollView(a);
    scroll.addView(list);
    new AlertDialog.Builder(a)
        .setTitle(I18n.t(a, "转写 · 点击回听 / 长按标注说话人"))
        .setView(scroll)
        .setPositiveButton(I18n.t(a, "关闭"), null)
        .show();
  }

  private void renameSpeaker(Store.Event e, JSONObject segment, Button line) {
    EditText name = a.editor(I18n.t(a, "例如：我 / 同事（手动标注）"), segment.optString("speaker", ""));
    new AlertDialog.Builder(a)
        .setTitle(I18n.t(a, "标注这一段的说话人"))
        .setView(name)
        .setNegativeButton(I18n.t(a, "取消"), null)
        .setPositiveButton(
            I18n.t(a, "保存"),
            (d, w) -> {
              try {
                Store.Event latest = a.db.find(e.id);
                if (latest == null) return;
                JSONObject meta = latest.metadata();
                JSONArray parts = meta.optJSONArray("segments");
                if (parts == null) return;
                for (int i = 0; i < parts.length(); i++) {
                  JSONObject item = parts.getJSONObject(i);
                  if (item.optLong("start_ms") == segment.optLong("start_ms")
                      && item.optString("text").equals(segment.optString("text"))) {
                    item.put("speaker", name.getText().toString().trim());
                    break;
                  }
                }
                a.db.metadata(e.id, meta);
                String speaker = name.getText().toString().trim();
                segment.put("speaker", speaker);
                line.setText(
                    clock(segment.optLong("start_ms", 0))
                        + "  "
                        + (speaker.isEmpty() ? "" : speaker + "：")
                        + segment.optString("text", ""));
                a.render();
              } catch (JSONException ex) {
                a.message(ex.getMessage());
              }
            })
        .show();
  }

  private void highlights() {
    LinearLayout list = a.column();
    int count = 0;
    for (Store.Event e : a.db.events(a.selected, null))
      if (e.pinned || e.kind.equals("bookmark")) {
        count++;
        list.addView(a.text("#" + e.id + " · " + e.text, 16, 0xff263d34));
        if (e.kind.equals("audio") && !e.path.isEmpty())
          list.addView(a.button(I18n.t(a, "回听"), () -> a.play(e.path, 0)));
        else if (e.kind.equals("bookmark"))
          list.addView(
              a.button(
                  I18n.t(a, "回听标记"),
                  () -> {
                    Store.Event audio = a.db.anchor(e.metadata().optLong("anchor_time", 0));
                    if (audio != null) a.play(audio.path, e.metadata().optLong("offset_ms", 0));
                    else a.message(I18n.t(a, "等待对应片段保存。"));
                  }));
      }
    if (count == 0) list.addView(a.text(I18n.t(a, "录音时标记此刻，或将已有录音保留为重要。"), 15, 0xff788278));
    ScrollView scroll = new ScrollView(a);
    scroll.addView(list);
    new AlertDialog.Builder(a)
        .setTitle(a.selected + I18n.t(a, " · 重要时刻"))
        .setView(scroll)
        .setPositiveButton(I18n.t(a, "关闭"), null)
        .show();
  }

  private void context() {
    LinearLayout form = a.column();
    EditText place = a.editor(I18n.t(a, "地点（如实验室）"), ""),
        activity = a.editor(I18n.t(a, "活动（如讨论项目）"), ""),
        mood = a.editor(I18n.t(a, "心情 / 状态（由你自评）"), "");
    form.addView(place);
    form.addView(activity);
    form.addView(mood);
    new AlertDialog.Builder(a)
        .setTitle(I18n.t(a, "记录此刻的生活上下文"))
        .setView(form)
        .setNegativeButton(I18n.t(a, "取消"), null)
        .setPositiveButton(
            I18n.t(a, "保存"),
            (d, w) -> {
              String p = place.getText().toString().trim(),
                  act = activity.getText().toString().trim(),
                  m = mood.getText().toString().trim();
              if (p.isEmpty() && act.isEmpty() && m.isEmpty()) return;
              long id =
                  a.db.add(
                      System.currentTimeMillis(),
                      "context",
                      (p.isEmpty() ? "" : I18n.t(a, "地点：") + p + "\n")
                          + (act.isEmpty() ? "" : I18n.t(a, "活动：") + act + "\n")
                          + (m.isEmpty() ? "" : I18n.t(a, "自评状态：") + m),
                      "",
                      "ready");
              try {
                a.db.metadata(
                    id,
                    new JSONObject()
                        .put("place", p)
                        .put("activity", act)
                        .put("mood", m)
                        .put("source", "manual"));
              } catch (JSONException ignored) {
              }
              a.selected = Store.day(System.currentTimeMillis());
              a.render();
            })
        .show();
  }

  void settings() {
    android.content.SharedPreferences prefs = a.getSharedPreferences("life", 0);
    LinearLayout automation = a.card();
    automation.addView(
        a.text(I18n.english(a) ? "Automatic recording & imports" : "自动记录与录音机接入", 21, 0xff263d34));
    CheckBox auto = new CheckBox(a);
    auto.setText(
        I18n.english(a) ? "Resume recording after reboot / opening the app" : "重启或打开应用后恢复录音");
    auto.setChecked(AutoCapture.armed(a));
    auto.setOnCheckedChangeListener(
        (v, enabled) -> {
          if (!enabled) {
            AutoCapture.enable(a, false);
            return;
          }
          new AlertDialog.Builder(a)
              .setTitle(I18n.english(a) ? "Enable automatic recording?" : "启用自动记录？")
              .setMessage(
                  I18n.english(a)
                      ? "Audio recording will resume when the app is visible. Android 11+ uses a"
                            + " boot notification. Manual Stop disarms this setting. Grant"
                            + " microphone/notification permissions using Start recording first."
                      : "应用可见时恢复录音；Android 11及以上开机通过通知恢复。手动停止会取消自动恢复。请先使用开始记录授予麦克风和通知权限。")
              .setPositiveButton(
                  I18n.english(a) ? "Enable" : "启用",
                  (d, w) -> {
                    AutoCapture.enable(a, true);
                    AutoCapture.startWhenVisible(a);
                  })
              .setNegativeButton(
                  I18n.english(a) ? "Cancel" : "取消", (d, w) -> auto.setChecked(false))
              .show();
        });
    automation.addView(auto);
    CheckBox summarize = new CheckBox(a);
    summarize.setText(
        I18n.english(a)
            ? "Recap automatically after transcription (AI sends text)"
            : "转写完成后自动回顾（AI发送文字）");
    summarize.setChecked(prefs.getBoolean("recap_after_asr", false));
    summarize.setOnCheckedChangeListener(
        (v, enabled) -> prefs.edit().putBoolean("recap_after_asr", enabled).apply());
    automation.addView(summarize);
    a.row(
        automation,
        a.button(I18n.english(a) ? "System recorder" : "系统录音机", a::systemRecorder),
        a.button(I18n.english(a) ? "Import audio" : "导入音频", a::chooseAudio));
    LinearLayout c = a.card();
    if (prefs.getBoolean("asr_blocked", false))
      c.addView(
          a.text(
              I18n.english(a)
                  ? "Cloud queue paused after an authentication/access failure. Correct and save"
                      + " API settings before retrying. On-device transcription is unaffected."
                  : "云端队列因鉴权或权限失败暂停。请修正并保存API配置后重试；本机转写不受影响。",
              14,
              0xff985e42));
    c.addView(a.text(I18n.t(a, "本地语音与录音"), 21, 0xff263d34));
    c.addView(
        a.text(
            I18n.t(a, "当前转写：")
                + (prefs.getString("asr_mode", "local").equals("local")
                    ? I18n.t(a, "本机离线")
                    : I18n.t(a, "云端（上传音频）"))
                + I18n.t(a, " · 模型")
                + (OfflineAsr.model(a) == null ? I18n.t(a, "未导入") : I18n.t(a, "已就绪")),
            14,
            0xff547567));
    a.row(
        c,
        a.button(
            I18n.t(a, "转写方式"),
            () ->
                new AlertDialog.Builder(a)
                    .setTitle(I18n.t(a, "选择转写方式"))
                    .setItems(
                        new String[] {I18n.t(a, "本机离线（不上传音频）"), I18n.t(a, "云端接口（上传音频）")},
                        (d, which) -> {
                          if (which == 1 && new Ai(a).deepSeek()) {
                            a.message(
                                I18n.english(a)
                                    ? "Use on-device transcription with this DeepSeek chat"
                                        + " configuration. Import the Chinese model first."
                                    : "当前DeepSeek聊天配置请配合本机转写，先导入中文模型。");
                            return;
                          }
                          if (which == 1)
                            new AlertDialog.Builder(a)
                                .setTitle(I18n.t(a, "启用云端转写"))
                                .setMessage(I18n.t(a, "之后的录音会发送到你设置的语音服务进行转写。仅日记整理和聊天不需要上传原始音频。"))
                                .setNegativeButton(I18n.t(a, "取消"), null)
                                .setPositiveButton(
                                    I18n.t(a, "启用"),
                                    (dd, w) -> {
                                      prefs.edit().putString("asr_mode", "cloud").apply();
                                      a.render();
                                    })
                                .show();
                          else {
                            prefs.edit().putString("asr_mode", "local").apply();
                            a.render();
                          }
                        })
                    .show()),
        a.button(
            I18n.t(a, "导入离线模型"),
            () ->
                a.startActivityForResult(
                    new Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .setType("application/zip")
                        .addCategory(Intent.CATEGORY_OPENABLE),
                    40)));
    c.addView(
        a.text(I18n.t(a, "下载 Vosk 小型中文模型 ZIP，保持压缩包原样导入。约 42 MB，首次导入后可离线使用。"), 13, 0xff788278));
    a.row(
        c,
        a.button(
            I18n.t(a, "获取中文模型"),
            () ->
                a.startActivity(
                    new Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(
                            "https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip")))),
        a.button(
            I18n.t(a, "录音分段"),
            () ->
                new AlertDialog.Builder(a)
                    .setTitle(I18n.t(a, "每段长度（较长可减少切换间隙）"))
                    .setItems(
                        new String[] {I18n.t(a, "1 分钟"), I18n.t(a, "3 分钟"), I18n.t(a, "5 分钟")},
                        (d, w) -> {
                          prefs
                              .edit()
                              .putInt("segment_seconds", new int[] {60, 180, 300}[w])
                              .apply();
                          a.render();
                        })
                    .show()));
    CheckBox silence = new CheckBox(a);
    silence.setText(I18n.t(a, "过滤静音片段（实验性，默认保留全部）"));
    silence.setChecked(prefs.getBoolean("discard_silence", false));
    silence.setOnCheckedChangeListener(
        (v, enabled) -> prefs.edit().putBoolean("discard_silence", enabled).apply());
    c.addView(silence);
    c.addView(a.text(I18n.t(a, "静音过滤使用简单音量阈值，安静或远处的讲话可能被误判；重要录音请保持关闭。"), 12, 0xff788278));
    LinearLayout recap = a.card();
    recap.addView(a.text(I18n.t(a, "晚间每日回顾"), 21, 0xff263d34));
    CheckBox automatic = new CheckBox(a);
    automatic.setText(I18n.t(a, "自动整理并通知我"));
    automatic.setChecked(prefs.getBoolean("daily_enabled", false));
    automatic.setOnCheckedChangeListener(
        (v, on) -> {
          prefs.edit().putBoolean("daily_enabled", on).apply();
          RecapWorker.schedule(a, true);
          if (on
              && Build.VERSION.SDK_INT >= 33
              && a.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                  != android.content.pm.PackageManager.PERMISSION_GRANTED)
            a.requestPermissions(new String[] {android.Manifest.permission.POST_NOTIFICATIONS}, 11);
        });
    recap.addView(automatic);
    String time =
        String.format(
            Locale.US,
            "%02d:%02d",
            prefs.getInt("recap_hour", 22),
            prefs.getInt("recap_minute", 0));
    a.row(
        recap,
        a.button(
            I18n.t(a, "回顾时间 ") + time,
            () ->
                new TimePickerDialog(
                        a,
                        (picker, h, m) -> {
                          prefs.edit().putInt("recap_hour", h).putInt("recap_minute", m).apply();
                          RecapWorker.schedule(a, true);
                          a.render();
                        },
                        prefs.getInt("recap_hour", 22),
                        prefs.getInt("recap_minute", 0),
                        true)
                    .show()),
        a.button(
            I18n.t(a, "回顾风格"),
            () ->
                new AlertDialog.Builder(a)
                    .setTitle(I18n.t(a, "回顾风格"))
                    .setItems(
                        new String[] {I18n.t(a, "温暖日记"), I18n.t(a, "工作复盘"), I18n.t(a, "简洁事实")},
                        (d, w) -> {
                          prefs
                              .edit()
                              .putString(
                                  "recap_style",
                                  new String[] {
                                        I18n.t(a, "温暖日记"), I18n.t(a, "工作复盘"), I18n.t(a, "简洁事实")
                                      }
                                      [w])
                              .apply();
                        })
                    .show()));
    recap.addView(
        a.text(
            I18n.t(a, "每日概览最多50字，待办单独列出，可展开原始记录。无AI接口时显示记录概况；有接口时仅发送文字与相关上下文。系统后台调度可能延迟。"),
            13,
            0xff788278));
    String error = prefs.getString("recap_error", "");
    if (!error.isEmpty()) recap.addView(a.text(I18n.t(a, "最近回顾未完成：") + error, 12, 0xff985e42));
    LinearLayout storage = a.card();
    storage.addView(a.text(I18n.t(a, "原始音频与存储"), 21, 0xff263d34));
    storage.addView(
        a.text(
            String.format(Locale.CHINA, I18n.t(a, "已保存音频：%.1f MB"), a.db.audioBytes() / 1048576.0)
                + I18n.t(a, " · 保留 ")
                + (prefs.getInt("retention_days", 0) == 0
                    ? I18n.t(a, "永久")
                    : prefs.getInt("retention_days", 0) + I18n.t(a, " 天")),
            14,
            0xff547567));
    a.row(
        storage,
        a.button(
            I18n.t(a, "保留期限"),
            () ->
                new AlertDialog.Builder(a)
                    .setTitle(I18n.t(a, "过期音频将删除，文字与重要时刻保留"))
                    .setItems(
                        new String[] {
                          I18n.t(a, "永久保留"), I18n.t(a, "7 天"), I18n.t(a, "30 天"), I18n.t(a, "90 天")
                        },
                        (d, w) -> {
                          prefs
                              .edit()
                              .putInt("retention_days", new int[] {0, 7, 30, 90}[w])
                              .apply();
                          AudioCleanupWorker.schedule(a);
                          a.render();
                        })
                    .show()),
        a.button(
            I18n.t(a, "清理过期音频"),
            () -> {
              int days = prefs.getInt("retention_days", 0);
              if (days == 0) {
                a.message(I18n.t(a, "当前为永久保留；请先选择保留期限。"));
                return;
              }
              new AlertDialog.Builder(a)
                  .setTitle(I18n.t(a, "清理 ") + days + I18n.t(a, " 天前的原始音频？"))
                  .setMessage(I18n.t(a, "已标记的重要音频不会删除。未转写的过期录音也会删除，建议先导出备份。"))
                  .setNegativeButton(I18n.t(a, "取消"), null)
                  .setPositiveButton(
                      I18n.t(a, "清理"),
                      (d, w) -> {
                        int count = a.db.purgeAudio(System.currentTimeMillis() - days * 86400000L);
                        a.render();
                        a.message(I18n.t(a, "已清理 ") + count + I18n.t(a, " 个音频文件，文字仍保留。"));
                      })
                  .show();
            }));
    storage.addView(
        a.text(I18n.t(a, "选择期限后每天自动清理过期音频。文字、重要音频与标记关联的音频会保留。手动清理前建议备份。"), 12, 0xff788278));
  }

  void install(Uri uri) {
    if (a.busy) return;
    a.busy = true;
    a.render();
    a.worker.submit(
        () -> {
          try {
            OfflineAsr.install(a, uri);
            a.runOnUiThread(
                () -> {
                  a.busy = false;
                  a.render();
                  a.message(I18n.t(a, "离线模型已就绪，录音会在本机转写。"));
                });
          } catch (Exception | LinkageError e) {
            a.runOnUiThread(
                () -> {
                  a.busy = false;
                  a.render();
                  a.message(I18n.t(a, "模型导入失败：") + e.getMessage());
                });
          }
        });
  }
}
