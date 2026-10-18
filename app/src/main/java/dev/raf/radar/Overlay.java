package dev.raf.radar;

import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;

final class Overlay {
  private final Context c;
  private final Store s;
  private final WindowManager wm;
  private LinearLayout root;
  private WindowManager.LayoutParams lp;
  private boolean collapsed = false;
  private String lastAppearance = "";

  Overlay(Context c, Store s) {
    this.c = c;
    this.s = s;
    wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
  }

  int dp(float n) {
    return (int) (n * c.getResources().getDisplayMetrics().density);
  }

  void refresh() {
    if (!s.prefs.getBoolean("overlay", false) || !Settings.canDrawOverlays(c)) {
      close();
      return;
    }
    String appearance =
        s.prefs.getBoolean("dark", true)
            + ":"
            + s.prefs.getInt("scale", 100)
            + ":"
            + s.prefs.getInt("textScale", 100)
            + ":"
            + s.prefs.getInt("opacity", 90);
    if (!appearance.equals(lastAppearance)) {
      close();
      lastAppearance = appearance;
    }
    if (root == null) {
      root = new LinearLayout(c);
      root.setOrientation(LinearLayout.VERTICAL);
      lp =
          new WindowManager.LayoutParams(
              -2,
              -2,
              WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
              WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
              android.graphics.PixelFormat.TRANSLUCENT);
      lp.gravity = Gravity.TOP | Gravity.START;
      lp.x = s.prefs.getInt("ox", 20);
      lp.y = s.prefs.getInt("oy", 200);
      try {
        wm.addView(root, lp);
      } catch (Exception e) {
        root = null;
        return;
      }
    }
    root.removeAllViews();
    boolean dark = s.prefs.getBoolean("dark", true);
    int fg = dark ? Color.WHITE : Color.rgb(25, 27, 30);
    int alpha = s.prefs.getInt("opacity", 90) * 255 / 100;
    GradientDrawable bg = new GradientDrawable();
    bg.setColor(Color.argb(alpha, dark ? 25 : 255, dark ? 28 : 255, dark ? 33 : 255));
    bg.setCornerRadius(dp(18));
    root.setBackground(bg);
    root.setPadding(dp(12), dp(8), dp(12), dp(10));
    float scale = s.prefs.getInt("scale", 100) / 100f;
    float textScale = s.prefs.getInt("textScale", 100) / 100f;
    String id = s.prefs.getString("overlayPoint", "me");
    Point p = null;
    for (Point q : s.points()) if (q.id.equals(id)) p = q;
    if (p == null) p = s.points().get(0);
    TextView title = new TextView(c);
    title.setText(p.name + "  ·  " + (collapsed ? "＋" : "−"));
    title.setTextSize(12 * scale * textScale);
    title.setTextColor(fg);
    title.setShadowLayer(dp(1.5f), 0, dp(1), dark ? Color.BLACK : Color.WHITE);
    title.setMaxWidth(c.getResources().getDisplayMetrics().widthPixels - dp(40));
    root.addView(title);
    title.setOnClickListener(
        v -> {
          collapsed = !collapsed;
          refresh();
        });
    title.setOnTouchListener(
        new View.OnTouchListener() {
          float x, y;
          int ox, oy;
          boolean moved;

          public boolean onTouch(View v, android.view.MotionEvent e) {
            switch (e.getAction()) {
              case 0:
                x = e.getRawX();
                y = e.getRawY();
                ox = lp.x;
                oy = lp.y;
                moved = false;
                return true;
              case 2:
                if (!s.prefs.getBoolean("locked", false)) {
                  if (Math.abs(e.getRawX() - x) + Math.abs(e.getRawY() - y) > dp(8)) moved = true;
                  lp.x =
                      Math.max(
                          0,
                          Math.min(
                              c.getResources().getDisplayMetrics().widthPixels - root.getWidth(),
                              ox + (int) (e.getRawX() - x)));
                  lp.y =
                      Math.max(
                          0,
                          Math.min(
                              c.getResources().getDisplayMetrics().heightPixels - root.getHeight(),
                              oy + (int) (e.getRawY() - y)));
                  wm.updateViewLayout(root, lp);
                }
                return true;
              case 1:
                s.prefs.edit().putInt("ox", lp.x).putInt("oy", lp.y).apply();
                if (!moved) v.performClick();
                return true;
            }
            return false;
          }
        });
    List<Store.Reading> a = s.readings(p.id);
    LinearLayout row = new LinearLayout(c);
    root.addView(row);
    int rowWidth = 0, maxWidth = c.getResources().getDisplayMetrics().widthPixels - dp(40);
    for (String t : p.tariffs) {
      Store.Reading r = null;
      for (Store.Reading z : a) if (z.tariff.equals(t)) r = z;
      TextView cell = new TextView(c);
      cell.setPadding(dp(5), dp(5), dp(9), 0);
      cell.setTextColor(fg);
      cell.setShadowLayer(dp(1.5f), 0, dp(1), dark ? Color.BLACK : Color.WHITE);
      cell.setTextSize((collapsed ? 15 : 20) * scale * textScale);
      cell.setText(
          (collapsed ? "" : Point.label(t) + "\n")
              + (r == null ? "—" : r.value())
              + (r != null && !r.fresh() ? " ◷" : ""));
      cell.setMaxWidth(maxWidth);
      cell.measure(
          View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.AT_MOST),
          View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
      int width = cell.getMeasuredWidth();
      if (rowWidth > 0 && rowWidth + width > maxWidth) {
        row = new LinearLayout(c);
        root.addView(row);
        rowWidth = 0;
      }
      row.addView(cell);
      rowWidth += width;
      if (collapsed) break;
    }
    if (!collapsed) {
      TextView note = new TextView(c);
      note.setText(p.tariffs.size() == 1 ? "× — не надбавка ₽" : "× — множитель, не надбавка ₽");
      note.setTextSize(10 * scale);
      note.setTextColor(fg);
      note.setShadowLayer(dp(1.5f), 0, dp(1), dark ? Color.BLACK : Color.WHITE);
      note.setMaxWidth(maxWidth);
      root.addView(note);
      LinearLayout actions = new LinearLayout(c);
      root.addView(actions);
      button(
          actions,
          "↗",
          () ->
              c.startActivity(
                  new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
      button(
          actions,
          "↻",
          () -> c.sendBroadcast(new Intent("dev.raf.radar.MANUAL").setPackage(c.getPackageName())));
      button(
          actions,
          "×",
          () -> {
            s.prefs.edit().putBoolean("overlay", false).apply();
            close();
          });
    }
    root.post(
        () -> {
          if (root != null) {
            lp.x =
                Math.max(
                    0,
                    Math.min(
                        lp.x, c.getResources().getDisplayMetrics().widthPixels - root.getWidth()));
            lp.y =
                Math.max(
                    0,
                    Math.min(
                        lp.y,
                        c.getResources().getDisplayMetrics().heightPixels - root.getHeight()));
            wm.updateViewLayout(root, lp);
          }
        });
  }

  void button(LinearLayout row, String label, Runnable r) {
    TextView b = new TextView(c);
    b.setText(label);
    b.setTextSize(12 * s.prefs.getInt("scale", 100) / 100f);
    b.setShadowLayer(dp(1), 0, dp(1), s.prefs.getBoolean("dark", true) ? Color.BLACK : Color.WHITE);
    b.setContentDescription(
        label.equals("↗") ? "Открыть карту" : label.equals("↻") ? "Обновить" : "Закрыть виджет");
    b.setTextColor(Color.rgb(170, 140, 35));
    b.setPadding(dp(7), dp(8), dp(7), 0);
    b.setOnClickListener(v -> r.run());
    row.addView(b);
  }

  void close() {
    if (root != null) {
      try {
        wm.removeView(root);
      } catch (Exception ignored) {
      }
      root = null;
    }
  }
}
