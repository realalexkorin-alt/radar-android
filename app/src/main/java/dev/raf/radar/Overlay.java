package dev.raf.radar;

import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
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
  private String lastAppearance = "";
  private boolean touching, expanded;
  Overlay(Context c, Store s) {
    this.c = c; this.s = s;
    wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
  }
  int dp(float n) { return (int) (n * c.getResources().getDisplayMetrics().density); }
  private void open(boolean settings) {
    c.startActivity(new Intent(c, MainActivity.class).putExtra("widgetSettings", settings)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
            | Intent.FLAG_ACTIVITY_SINGLE_TOP));
  }
  void refresh() {
    if (!s.prefs.getBoolean("overlay", false) || !Settings.canDrawOverlays(c)) { close(); return; }
    if (touching) return;
    String appearance = s.prefs.getBoolean("dark", true) + ":" + s.prefs.getInt("scale", 100)
        + ":" + s.prefs.getInt("textScale", 100) + ":" + s.prefs.getInt("opacity", 90)
        + ":" + s.prefs.getBoolean("showTariff", true);
    if (!appearance.equals(lastAppearance)) { close(); lastAppearance = appearance; }
    if (root == null) {
      root = new LinearLayout(c); root.setOrientation(LinearLayout.VERTICAL);
      root.setContentDescription("Виджет радара");
      root.setOnClickListener(v -> open(false));
      root.setOnLongClickListener(v -> { open(true); return true; });
      installGestures();
      lp = new WindowManager.LayoutParams(-2, -2,
          WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
          WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
          android.graphics.PixelFormat.TRANSLUCENT);
      lp.gravity = Gravity.TOP | Gravity.START;
      lp.x = s.prefs.getInt("ox", dp(20)); lp.y = s.prefs.getInt("oy", dp(100));
      try { wm.addView(root, lp); } catch (Exception e) { root = null; return; }
    }
    root.removeAllViews();
    boolean dark = s.prefs.getBoolean("dark", true);
    int fg = dark ? Color.WHITE : Color.rgb(25, 27, 30);
    int alpha = s.prefs.getInt("opacity", 90) * 255 / 100;
    GradientDrawable bg = new GradientDrawable();
    bg.setColor(Color.argb(alpha, dark ? 25 : 255, dark ? 28 : 255, dark ? 33 : 255));
    bg.setCornerRadius(dp(18)); root.setBackground(bg);
    root.setPadding(dp(12), dp(4), dp(8), dp(10));
    float scale = s.prefs.getInt("scale", 100) / 100f;
    float textScale = s.prefs.getInt("textScale", 100) / 100f;
    int maxWidth = Math.max(dp(80), c.getResources().getDisplayMetrics().widthPixels - dp(72));
    String id = s.prefs.getString("overlayPoint", "me");
    Point p = null;
    for (Point q : s.points()) if (q.id.equals(id)) p = q;
    if (p == null) p = s.points().get(0);
    LinearLayout content = new LinearLayout(c);
    content.setGravity(Gravity.CENTER_VERTICAL);
    root.addView(content);
    LinearLayout values = new LinearLayout(c);
    values.setOrientation(LinearLayout.VERTICAL);
    content.addView(values);
    TextView expand = label(expanded ? "▴" : "▾", 18, fg, dark);
    expand.setGravity(Gravity.CENTER);
    expand.setContentDescription(expanded ? "Свернуть меню виджета" : "Раскрыть меню виджета");
    expand.setOnClickListener(v -> { expanded = !expanded; refresh(); });
    content.addView(expand, new LinearLayout.LayoutParams(dp(40), dp(48)));
    List<Store.Reading> readings = s.readings(p.id);
    LinearLayout row = new LinearLayout(c); values.addView(row);
    int rowWidth = 0;
    for (String tariff : p.tariffs) {
      Store.Reading reading = null;
      for (Store.Reading r : readings) if (r.tariff.equals(tariff)) reading = r;
      TextView cell = label((s.prefs.getBoolean("showTariff", true) ? Point.label(tariff) + "\n" : "")
          + (reading == null ? "—" : reading.value()), 16 * scale * textScale, fg, dark);
      cell.setGravity(Gravity.CENTER);
      cell.setContentDescription(p.name + ": " + Point.label(tariff) + ", " + (reading == null ? "нет данных" : reading.value()));
      cell.setPadding(dp(5), 0, dp(9), 0);
      if (reading != null && !reading.fresh()) {
        cell.setAlpha(0.55f); cell.setContentDescription(Point.label(tariff) + ": данные устарели");
      }
      cell.setMaxWidth(maxWidth);
      cell.measure(View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.AT_MOST),
          View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
      int width = cell.getMeasuredWidth();
      if (rowWidth > 0 && rowWidth + width > maxWidth) {
        row = new LinearLayout(c); values.addView(row); rowWidth = 0;
      }
      row.addView(cell); rowWidth += width;
    }
    if (expanded) {
      LinearLayout menu = new LinearLayout(c);
      menu.setGravity(Gravity.CENTER);
      TextView settings = label("⚙\nНастройки", 12, fg, dark);
      settings.setGravity(Gravity.CENTER);
      settings.setContentDescription("Настройки виджета");
      settings.setOnClickListener(v -> open(true));
      TextView closeButton = label("×\nЗакрыть", 12, fg, dark);
      closeButton.setGravity(Gravity.CENTER);
      closeButton.setContentDescription("Закрыть виджет");
      closeButton.setOnClickListener(v -> { s.prefs.edit().putBoolean("overlay", false).apply(); close(); });
      menu.addView(settings, new LinearLayout.LayoutParams(dp(88), dp(54)));
      menu.addView(closeButton, new LinearLayout.LayoutParams(dp(88), dp(54)));
      root.addView(menu);
    }
    root.post(() -> { if (root != null) position(lp.x, lp.y); });
  }
  private TextView label(String text, float size, int color, boolean dark) {
    TextView v = new TextView(c); v.setText(text); v.setTextSize(size); v.setTextColor(color);
    v.setShadowLayer(dp(1.5f), 0, dp(1), dark ? Color.BLACK : Color.WHITE); return v;
  }
  private void position(int x, int y) {
    if (root == null) return;
    lp.x = Math.max(0, Math.min(x, c.getResources().getDisplayMetrics().widthPixels - root.getWidth()));
    lp.y = Math.max(0, Math.min(y, c.getResources().getDisplayMetrics().heightPixels - root.getHeight()));
    wm.updateViewLayout(root, lp);
  }
  private void installGestures() {
    root.setOnTouchListener(new View.OnTouchListener() {
      final WidgetGesture gesture = new WidgetGesture(
          ViewConfiguration.get(c).getScaledTouchSlop(), ViewConfiguration.getLongPressTimeout());
      float x, y; int ox, oy;
      public boolean onTouch(View v, MotionEvent e) {
        switch (e.getActionMasked()) {
          case MotionEvent.ACTION_DOWN:
            touching = true; x = e.getRawX(); y = e.getRawY(); ox = lp.x; oy = lp.y;
            gesture.down(x, y, e.getEventTime()); return true;
          case MotionEvent.ACTION_MOVE:
            if (gesture.move(e.getRawX(), e.getRawY(), e.getEventTime()))
              position(ox + Math.round(e.getRawX() - x), oy + Math.round(e.getRawY() - y));
            return true;
          case MotionEvent.ACTION_UP:
            int action = gesture.up(e.getRawX(), e.getRawY(), e.getEventTime()); touching = false;
            if (action == WidgetGesture.DRAG) {
              position(ox + Math.round(e.getRawX() - x), oy + Math.round(e.getRawY() - y));
              s.prefs.edit().putInt("ox", lp.x).putInt("oy", lp.y).apply();
            } else if (action == WidgetGesture.TAP) v.performClick();
            else if (action == WidgetGesture.SETTINGS) v.performLongClick();
            return true;
          case MotionEvent.ACTION_CANCEL:
          case MotionEvent.ACTION_POINTER_DOWN:
            touching = false; gesture.cancel(); return true;
          default: return true;
        }
      }
    });
  }
  void close() {
    touching = false;
    if (root != null) { try { wm.removeView(root); } catch (Exception ignored) {} root = null; }
  }
}
