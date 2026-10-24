package dev.raf.radar;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

/** Theme selection is presentation only; it never starts or stops monitoring. */
final class Appearance {
  static final String KEY="appearanceTheme", STANDARD="standard", LINEAGE="lineage2";
  static final int GOLD=0xFFD4B474, INK=0xFF101719, PANEL=0xFF202725, TEXT=0xFFF1E8D3, MUTED=0xFFAEA995;
  static boolean lineage(Context c){return LINEAGE.equals(c.getSharedPreferences("radar",0).getString(KEY,STANDARD));}
  static boolean sounds(Context c){return c.getSharedPreferences("radar",0).getBoolean(lineage(c)?"lineageSounds":"sounds",true);}
  static int accent(Context c){return lineage(c)?GOLD:RadarUi.YELLOW;}
  static int foreground(Context c){return lineage(c)?TEXT:0xFFF5F5F7;}
  static String notificationChannel(Context c,String normal,String label){
    if(!lineage(c))return normal;
    String id=normal+"_lineage_silent";
    NotificationChannel channel=new NotificationChannel(id,label+" · Lineage 2",NotificationManager.IMPORTANCE_DEFAULT);
    channel.setSound(null,null);channel.enableVibration(false);
    c.getSystemService(NotificationManager.class).createNotificationChannel(channel);return id;
  }
  static Drawable frame(Context c,boolean button){return new MetalFrame(c,button);}
  static final class MetalFrame extends Drawable {
    final Paint p=new Paint(3);final float density;final boolean button;int opacity=255;
    MetalFrame(Context c,boolean b){density=c.getResources().getDisplayMetrics().density;button=b;}
    public void draw(Canvas canvas){
      Rect b=getBounds();float d=density,r=button?5*d:7*d;RectF box=new RectF(b.left+d,b.top+d,b.right-d,b.bottom-d);
      boolean pressed=getState()!=null&&android.util.StateSet.stateSetMatches(new int[]{android.R.attr.state_pressed},getState());
      p.setStyle(Paint.Style.FILL);p.setAlpha(opacity);
      p.setShader(new LinearGradient(0,b.top,0,b.bottom,button?(pressed?new int[]{0xFF1A211F,0xFF343D35}:new int[]{0xFF5D604B,0xFF343A32,0xFF222A27}):new int[]{0xF229302C,0xF2131B1D},null,Shader.TileMode.CLAMP));
      canvas.drawRoundRect(box,r,r,p);p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(d);
      p.setColor(0xFFAC9566);p.setAlpha(opacity);canvas.drawRoundRect(box,r,r,p);
      box.inset(2*d,2*d);p.setColor(0xFF454B3F);canvas.drawRoundRect(box,r/2,r/2,p);
      if(!button){p.setColor(GOLD);p.setStrokeWidth(d);float len=10*d;
        for(int x:new int[]{b.left+5*(int)d,b.right-5*(int)d})for(int y:new int[]{b.top+5*(int)d,b.bottom-5*(int)d}){
          int sx=x<b.centerX()?1:-1,sy=y<b.centerY()?1:-1;
          canvas.drawLine(x,y,x+sx*len,y,p);canvas.drawLine(x,y,x,y+sy*len,p);
        }
      }p.setStyle(Paint.Style.FILL);p.setAlpha(255);
    }
    @Override public boolean isStateful(){return true;}
    @Override protected boolean onStateChange(int[] state){invalidateSelf();return true;}
    public void setAlpha(int a){opacity=a;invalidateSelf();}public void setColorFilter(ColorFilter f){p.setColorFilter(f);}
    public int getOpacity(){return PixelFormat.TRANSLUCENT;}
  }
  /** Recolour existing native controls without rebuilding the map or resuming the service. */
  static void recolor(View v,boolean oldLineage,boolean nextLineage){
    if(v instanceof TextView){TextView t=(TextView)v;int color=t.getCurrentTextColor();
      int[] classic={0xFFF5F5F7,0xFF92969F,RadarUi.YELLOW},fantasy={TEXT,MUTED,GOLD};
      for(int i=0;i<classic.length;i++)if(color==(oldLineage?fantasy[i]:classic[i])){t.setTextColor(nextLineage?fantasy[i]:classic[i]);break;}
      if(Boolean.TRUE.equals(t.getTag(R.id.theme_heading)))t.setTypeface(Typeface.create(nextLineage?"serif":"sans-serif-medium",nextLineage?Typeface.BOLD:Typeface.NORMAL));
    }
    Object role=v.getTag(R.id.theme_surface);
    if(role instanceof int[]){int[] spec=(int[])role;if(nextLineage)v.setBackground(frame(v.getContext(),spec[3]!=0));else if(spec[3]!=0)RadarUi.touch(v,spec[0],spec[1],spec[2]);else v.setBackground(RadarUi.shape(v.getContext(),spec[0],spec[1],spec[2]));
      if(v instanceof TextView&&spec[0]==RadarUi.YELLOW)((TextView)v).setTextColor(nextLineage?TEXT:Color.BLACK);
    }
    if(v instanceof SeekBar){SeekBar s=(SeekBar)v;s.setProgressTintList(android.content.res.ColorStateList.valueOf(nextLineage?GOLD:RadarUi.YELLOW));}
    if(v instanceof ViewGroup){ViewGroup group=(ViewGroup)v;for(int i=0;i<group.getChildCount();i++)recolor(group.getChildAt(i),oldLineage,nextLineage);}
    v.invalidate();
  }
  static void panel(View v,int classic,int radius){v.setTag(R.id.theme_surface,new int[]{classic,radius,0,0});v.setBackground(lineage(v.getContext())?frame(v.getContext(),false):RadarUi.shape(v.getContext(),classic,radius,0));}
}
