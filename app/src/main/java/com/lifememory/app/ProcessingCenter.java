package com.lifememory.app;

import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import java.util.List;

/** A transparent inbox for audio processing, inspired by Orca's task overview. */
class ProcessingCenter {
  static String state(Store.Event e) {
    if (e.status.equals("processing")) return "转写中";
    if (!e.metadata().optString("asr_error").isEmpty()) return "需处理";
    if (e.metadata().has("segments") && e.status.equals("ready")) return "已完成";
    return "待转写";
  }

  static int[] counts(List<Store.Event> events) {
    int[] result = new int[3];
    for (Store.Event e : events) {
      if (!e.kind.equals("audio")) continue;
      String s = state(e);
      result[s.equals("已完成") ? 2 : s.equals("需处理") ? 1 : 0]++;
    }
    return result;
  }

  static void show(MainActivity a) {
    LinearLayout box = new LinearLayout(a);
    box.setOrientation(LinearLayout.VERTICAL);
    int pad = (int) (16 * a.getResources().getDisplayMetrics().density);
    box.setPadding(pad, pad, pad, pad);
    ScrollView scroll = new ScrollView(a);
    scroll.addView(box);
    AlertDialog dialog =
        new AlertDialog.Builder(a)
            .setTitle(I18n.t(a, "处理中心 · ") + a.selected)
            .setView(scroll)
            .setPositiveButton(I18n.t(a, "关闭"), null)
            .create();
    Handler handler = new Handler(Looper.getMainLooper());
    String[] filter = {"全部"}, previous = {""};
    Runnable[] refresh = new Runnable[1];
    refresh[0] =
        () -> {
          if (!dialog.isShowing() || a.isFinishing()) return;
          List<Store.Event> events = a.db.events(a.selected, null);
          StringBuilder statuses = new StringBuilder();
          for (Store.Event e : events) statuses.append(e.id).append(e.status);
          String signature = Store.fingerprint(events) + statuses + filter[0];
          if (!signature.equals(previous[0])) {
            previous[0] = signature;
            box.removeAllViews();
            int[] count = counts(events);
            box.addView(
                a.text(
                    I18n.t(a, "进行/等待 ")
                        + count[0]
                        + I18n.t(a, "  ·  需处理 ")
                        + count[1]
                        + I18n.t(a, "  ·  已完成 ")
                        + count[2],
                    14,
                    0xff547567));
            a.row(
                box,
                a.button(
                    I18n.t(a, "全部"),
                    () -> {
                      filter[0] = "全部";
                      handler.removeCallbacks(refresh[0]);
                      refresh[0].run();
                    }),
                a.button(
                    I18n.t(a, "需处理"),
                    () -> {
                      filter[0] = "需处理";
                      handler.removeCallbacks(refresh[0]);
                      refresh[0].run();
                    }),
                a.button(
                    I18n.t(a, "已完成"),
                    () -> {
                      filter[0] = "已完成";
                      handler.removeCallbacks(refresh[0]);
                      refresh[0].run();
                    }));
            int shown = 0;
            for (Store.Event e : events) {
              if (!e.kind.equals("audio")) continue;
              String s = state(e);
              if (!filter[0].equals("全部") && !filter[0].equals(s)) continue;
              if (shown++ >= 100) {
                box.addView(a.text(I18n.t(a, "仅显示最近100段，请在时间线查看其余记录。"), 12, 0xff788278));
                break;
              }
              box.addView(
                  a.text(
                      I18n.t(a, s)
                          + " · "
                          + new java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA)
                              .format(new java.util.Date(e.time)),
                      17,
                      0xff263d34));
              String error = e.metadata().optString("asr_error");
              box.addView(
                  a.text(
                      error.isEmpty()
                          ? (s.equals("已完成")
                              ? I18n.t(a, "文字与时间戳已保存，可回听核对。")
                              : I18n.t(a, "音频已保存；离线转写需导入模型，云端需自行配置。"))
                          : error,
                      13,
                      0xff788278));
              a.row(
                  box,
                  a.button(I18n.t(a, "查看转写"), () -> new FeatureUi(a).transcript(e)),
                  a.button(
                      I18n.t(a, "重试这段"),
                      () -> {
                        if (!SpeechJobs.available(a)) {
                          a.message(SpeechJobs.unavailableReason(a));
                          return;
                        }
                        SpeechJobs.submit(a, e.id);
                        a.message(I18n.t(a, "已加入队列，原转写保留。"));
                      }));
            }
            if (shown == 0)
              box.addView(a.text(I18n.t(a, "这里还没有对应的录音。记录一段声音后再来看看。"), 14, 0xff788278));
          }
          handler.postDelayed(refresh[0], 1000);
        };
    dialog.setOnDismissListener(d -> handler.removeCallbacks(refresh[0]));
    dialog.show();
    refresh[0].run();
  }
}
