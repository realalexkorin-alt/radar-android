package dev.raf.radar;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Shared native drawing primitives for the reference design. */
final class RadarUi {
  static final int YELLOW = 0xFFFFD500, BACKGROUND = 0xFF0B0C0E, CARD = 0xFF1B1D20;
  static int dp(Context c, float n) { return Math.round(n * c.getResources().getDisplayMetrics().density); }
  static GradientDrawable shape(Context c, int color, int radius, int border) {
    GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(c,radius));
    if (border != 0) d.setStroke(dp(c,1), border); return d;
  }
  static void touch(View v, int color, int radius, int border) {
    v.setBackground(new RippleDrawable(ColorStateList.valueOf(0x24FFFFFF),shape(v.getContext(),color,radius,border),null));
  }
  static TextView text(Context c, String value, float size, int color, boolean bold) {
    TextView t = new TextView(c); t.setText(value); t.setTextSize(size); t.setTextColor(color);
    t.setFontFeatureSettings("kern"); t.setIncludeFontPadding(false);
    t.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL)); return t;
  }
  static int tariffColor(String tariff) {
    switch (tariff) { case "business": return 0xFFFF9F0A; case "comfortplus": return 0xFFB425F4;
      case "vip": return 0xFF858892; case "child_tariff": return 0xFF8BDD24;
      case "minivan": return 0xFF21C8DF; default: return YELLOW; }
  }
  static String value(Store.Reading reading) {
    if (reading == null) return "—";
    return reading.rub != null ? (reading.rub > 0 ? "+" : "") + reading.rub : reading.value();
  }
  static ImageView icon(Context c, String name, int color, int size) {
    ImageView image = new ImageView(c); image.setImageDrawable(new Icon(name,color));
    image.setLayoutParams(new LinearLayout.LayoutParams(dp(c,size),dp(c,size))); return image;
  }
  static final class Toggle extends CompoundButton {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    Toggle(Context c) { super(c); setButtonDrawable(null); setClickable(true); setFocusable(true); }
    @Override public CharSequence getAccessibilityClassName() { return "android.widget.Switch"; }
    @Override public void setChecked(boolean checked) { super.setChecked(checked); invalidate(); }
    @Override protected void onMeasure(int w,int h) { setMeasuredDimension(resolveSize(dp(getContext(),56),w),resolveSize(dp(getContext(),48),h)); }
    @Override protected void onDraw(Canvas canvas) {
      float width=dp(getContext(),44), height=dp(getContext(),26), left=(getWidth()-width)/2f, top=(getHeight()-height)/2f;
      paint.setColor(isChecked()?YELLOW:0xFF41454C); canvas.drawRoundRect(left,top,left+width,top+height,height/2,height/2,paint);
      paint.setColor(Color.WHITE); float radius=dp(getContext(),11);
      canvas.drawCircle(left+(isChecked()?width-height/2:height/2),top+height/2,radius,paint);
    }
  }
  static final class Icon extends Drawable {
    final String name; final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG); final int color;
    Icon(String name,int color) { this.name=name;this.color=color; }
    private void line(Canvas c,float... xy) { Path path=new Path();path.moveTo(xy[0],xy[1]);for(int i=2;i<xy.length;i+=2)path.lineTo(xy[i],xy[i+1]);c.drawPath(path,p); }
    @Override public void draw(Canvas c) {
      c.save(); Rect b=getBounds();c.translate(b.left,b.top);c.scale(b.width()/24f,b.height()/24f);
      p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.9f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
      switch(name) {
        case "search": c.drawCircle(10.5f,10.5f,6.5f,p);line(c,15.5f,15.5f,21,21);break;
        case "layers": line(c,3,9,12,3,21,9,12,15,3,9);line(c,3,14,12,20,21,14);break;
        case "locate": line(c,3,10,21,3,14,21,11,13,3,10);break;
        case "plus": line(c,12,5,12,19);line(c,5,12,19,12);break;
        case "close": line(c,5,5,19,19);line(c,19,5,5,19);break;
        case "right": line(c,9,5,16,12,9,19);break;
        case "down": line(c,5,9,12,16,19,9);break;
        case "list": for(int i=0;i<3;i++){c.drawCircle(4,5+7*i,0.7f,p);line(c,9,5+7*i,21,5+7*i);}break;
        case "map": line(c,3,5,9,3,16,6,21,4,21,20,15,22,9,19,3,21,3,5);line(c,9,3,9,19);line(c,16,6,15,22);break;
        case "pause": p.setStrokeWidth(3);line(c,8,5,8,19);line(c,16,5,16,19);break;
        case "play": line(c,7,4,20,12,7,20,7,4);break;
        case "settings": c.drawCircle(12,12,6.5f,p);c.drawCircle(12,12,2,p);p.setStrokeWidth(3);
          for(int i=0;i<8;i++){double a=i*Math.PI/4;line(c,12+(float)Math.cos(a)*8,12+(float)Math.sin(a)*8,12+(float)Math.cos(a)*10,12+(float)Math.sin(a)*10);}break;
        default: c.drawCircle(12,12,8,p);break;
      }
      c.restore();
    }
    @Override public void setAlpha(int alpha) { p.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter f) { p.setColorFilter(f); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
  }
}
