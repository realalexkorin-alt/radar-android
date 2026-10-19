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
  private LinearLayout root, menu;
  private WindowManager.LayoutParams menuLp;
  private WindowManager.LayoutParams lp;
  private String lastAppearance = "";
  private boolean touching, expanded;
  Overlay(Context c, Store s) {
    this.c = c; this.s = s;
    wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
  }
  int dp(float n) { return (int) (n * c.getResources().getDisplayMetrics().density); }
  private void open(boolean settings) {
    hideMenu();
    c.startActivity(new Intent(c, MainActivity.class).putExtra("widgetSettings", settings)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
            | Intent.FLAG_ACTIVITY_SINGLE_TOP));
  }
  void refresh() {
    if (!s.prefs.getBoolean("overlay", false) || !Settings.canDrawOverlays(c)) { close(); return; }
    if (touching) return;
    String appearance = s.prefs.getBoolean("dark", true) + ":" + s.prefs.getInt("scale", 100)
        + ":" + s.prefs.getInt("textScale", 100) + ":" + s.prefs.getInt("opacity", 70)
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
    int fg = Color.WHITE;
    int alpha = s.prefs.getInt("opacity", 70) * 255 / 100;
    GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
        new int[]{Color.argb(alpha,28,28,20),Color.argb(alpha,12,13,15)});
    background.setCornerRadius(dp(22));
    root.setBackground(background); root.setPadding(dp(14),dp(12),dp(10),dp(12));
    root.setElevation(dp(8));
    float scale=s.prefs.getInt("scale",100)/100f, textScale=s.prefs.getInt("textScale",100)/100f;
    int available=Math.max(dp(100),c.getResources().getDisplayMetrics().widthPixels-dp(92));
    String id=s.prefs.getString("overlayPoint","me"); Point point=null;
    for(Point candidate:s.points())if(candidate.id.equals(id))point=candidate;
    if(point==null)point=s.points().get(0);
    LinearLayout body=new LinearLayout(c);body.setGravity(Gravity.CENTER_VERTICAL);root.addView(body);
    LinearLayout rows=new LinearLayout(c);rows.setOrientation(LinearLayout.VERTICAL);body.addView(rows);
    LinearLayout row=new LinearLayout(c);rows.addView(row);int rowWidth=0;
    java.util.List<Store.Reading> readings=s.readings(point.id);
    for(String tariff:point.tariffs){
      Store.Reading reading=null;for(Store.Reading r:readings)if(tariff.equals(r.tariff))reading=r;
      String name=s.prefs.getBoolean("showTariff",true)?Point.label(tariff)+"\n":"";
      String value=RadarUi.value(reading);
      android.text.SpannableString content=new android.text.SpannableString(name+value);
      content.setSpan(new android.text.style.AbsoluteSizeSpan(Math.round(30*scale*textScale),true),name.length(),content.length(),0);
      content.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD),name.length(),content.length(),0);
      TextView cell=RadarUi.text(c,"",16*scale*textScale,fg,false);cell.setText(content);cell.setLineSpacing(dp(3),1);
      cell.setPadding(dp(4),0,dp(10),0);cell.setMinWidth(dp(point.tariffs.size()==1?100*scale:66*scale));cell.setMaxWidth(available);
      cell.setContentDescription(point.name+": "+Point.label(tariff)+", "+value);
      if(reading!=null&&!reading.fresh()){cell.setAlpha(.5f);cell.setContentDescription(Point.label(tariff)+": данные устарели");}
      cell.measure(View.MeasureSpec.makeMeasureSpec(available,View.MeasureSpec.AT_MOST),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
      int width=cell.getMeasuredWidth();if(rowWidth>0&&rowWidth+width>available){row=new LinearLayout(c);rows.addView(row);rowWidth=0;}
      row.addView(cell);rowWidth+=width;
    }
    FrameLayout expand=new FrameLayout(c);expand.setContentDescription(expanded?"Свернуть меню виджета":"Раскрыть меню виджета");
    ImageView arrow=RadarUi.icon(c,expanded?"down":"right",fg,24);expand.addView(arrow,new FrameLayout.LayoutParams(dp(24),dp(24),Gravity.CENTER));
    expand.setOnClickListener(v->{if(expanded)hideMenu();else showMenu();});body.addView(expand,new LinearLayout.LayoutParams(dp(36),dp(52)));
    root.post(()->{if(root!=null)position(lp.x,lp.y);});
  }
  private void showMenu() {
    if(root==null)return;
    hideMenu();
    menu=new LinearLayout(c);menu.setOrientation(LinearLayout.VERTICAL);
    menu.setContentDescription("Боковое меню виджета");
    menu.setPadding(dp(4),dp(4),dp(4),dp(4));
    menu.setBackground(RadarUi.shape(c,0xFF202226,16,0));
    menu.addView(menuAction("settings","Настройки","Настройки виджета",Color.WHITE,()->open(true)),new LinearLayout.LayoutParams(dp(96),dp(64)));
    menu.addView(menuAction("close","Закрыть","Закрыть виджет",Color.WHITE,()->{s.prefs.edit().putBoolean("overlay",false).apply();close();}),new LinearLayout.LayoutParams(dp(96),dp(64)));
    menuLp=new WindowManager.LayoutParams(-2,-2,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,android.graphics.PixelFormat.TRANSLUCENT);
    menuLp.gravity=Gravity.TOP|Gravity.START;
    menu.setOnTouchListener((v,event)->{if(event.getActionMasked()==MotionEvent.ACTION_OUTSIDE){hideMenu();return true;}return false;});
    menu.measure(View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
    placeMenu();
    try{wm.addView(menu,menuLp);expanded=true;}catch(Exception e){menu=null;expanded=false;}
  }
  private void placeMenu(){
    if(menu==null||root==null)return;
    int width=c.getResources().getDisplayMetrics().widthPixels,height=c.getResources().getDisplayMetrics().heightPixels;
    int menuWidth=menu.getMeasuredWidth(),menuHeight=menu.getMeasuredHeight();
    int right=lp.x+root.getWidth()+dp(8);
    menuLp.x=Math.max(0,Math.min(right+menuWidth<=width?right:lp.x-menuWidth-dp(8),width-menuWidth));
    menuLp.y=Math.max(0,Math.min(lp.y,height-menuHeight));
  }
  private void hideMenu(){
    if(menu!=null){try{wm.removeView(menu);}catch(Exception ignored){}menu=null;}
    expanded=false;
  }
  private LinearLayout menuAction(String icon,String title,String description,int color,Runnable action){
    LinearLayout cell=new LinearLayout(c);cell.setOrientation(LinearLayout.VERTICAL);cell.setGravity(Gravity.CENTER);cell.setContentDescription(description);
    cell.addView(RadarUi.icon(c,icon,color,23));TextView text=RadarUi.text(c,title,12,color,false);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);p.topMargin=dp(6);cell.addView(text,p);cell.setOnClickListener(v->action.run());return cell;
  }
  private void position(int x, int y) {
    if (root == null) return;
    lp.x = Math.max(0, Math.min(x, c.getResources().getDisplayMetrics().widthPixels - root.getWidth()));
    lp.y = Math.max(0, Math.min(y, c.getResources().getDisplayMetrics().heightPixels - root.getHeight()));
    wm.updateViewLayout(root, lp);
    if(menu!=null){placeMenu();wm.updateViewLayout(menu,menuLp);}
  }
  private void installGestures() {
    root.setOnTouchListener(new View.OnTouchListener() {
      final WidgetGesture gesture = new WidgetGesture(
          ViewConfiguration.get(c).getScaledTouchSlop(), ViewConfiguration.getLongPressTimeout());
      float x, y; int ox, oy;
      public boolean onTouch(View v, MotionEvent e) {
        switch (e.getActionMasked()) {
          case MotionEvent.ACTION_DOWN:
            hideMenu();
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
    hideMenu();
    touching = false;
    if (root != null) { try { wm.removeView(root); } catch (Exception ignored) {} root = null; }
  }
}
