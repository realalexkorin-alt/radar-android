package dev.raf.radar;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;

/** Original artwork stays intact; the optional radar is drawn as scalable geometry. */
final class RadarLogo extends View {
  private static Bitmap artwork;
  private static final ColorMatrixColorFilter GREEN=new ColorMatrixColorFilter(new float[]{0,1,0,0,0,1,0,0,0,0,0,0,1,0,0,0,0,0,1,0});
  private final boolean pro;
  private boolean active,experimental,motionAllowed,attached,framePosted;
  private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
  private final Rect source=new Rect();
  private final RectF destination=new RectF();
  private final Shader greenGlow=new RadialGradient(142,134,134,new int[]{0x0038FF86,0x0038FF86,0x5038FF86,0x0038FF86},new float[]{0,.75f,.90f,1},Shader.TileMode.CLAMP);
  private final Shader redGlow=new RadialGradient(142,134,134,new int[]{0x00FF6528,0x00FF6528,0x50FF6528,0x00FF6528},new float[]{0,.75f,.90f,1},Shader.TileMode.CLAMP);
  private final Shader sweep=new SweepGradient(142,134,new int[]{0x0038FF86,0x0038FF86,0x3038FF86,0xB038FF86},new float[]{0,.72f,.94f,1});
  private final Runnable frame=new Runnable(){public void run(){
    framePosted=false;if(!shouldAnimate())return;invalidate();scheduleFrame();
  }};
  RadarLogo(Context c,boolean pro){
    super(c);this.pro=pro;
    if(artwork==null)try(java.io.InputStream in=c.getAssets().open("radar_logo.png")){
      BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=2;
      artwork=BitmapFactory.decodeStream(in,null,options);
    }catch(java.io.IOException ignored){}
    if(!pro)setContentDescription("Радар");
  }
  void active(boolean enabled){
    active=enabled;setSelected(enabled);
    setContentDescription(pro?(enabled?"PRO: мониторинг включён":"PRO: мониторинг выключен"):"Радар");
    syncAnimation();invalidate();
  }
  void experimental(boolean enabled){experimental=enabled;syncAnimation();invalidate();}
  void motionAllowed(boolean enabled){motionAllowed=enabled;syncAnimation();}
  boolean animationRunning(){return framePosted;}
  float sweepAngle(){return (SystemClock.elapsedRealtime()%4000L)*.09f;}
  private boolean shouldAnimate(){return !pro&&experimental&&active&&motionAllowed&&attached&&getWindowVisibility()==VISIBLE&&isShown();}
  private void scheduleFrame(){if(!framePosted){framePosted=true;postDelayed(frame,33);}}
  private void syncAnimation(){if(shouldAnimate())scheduleFrame();else{removeCallbacks(frame);framePosted=false;}}
  @Override protected void onAttachedToWindow(){super.onAttachedToWindow();attached=true;syncAnimation();}
  @Override protected void onDetachedFromWindow(){attached=false;syncAnimation();super.onDetachedFromWindow();}
  @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);syncAnimation();}
  @Override public void onVisibilityAggregated(boolean visible){super.onVisibilityAggregated(visible);syncAnimation();}
  @Override protected void onDraw(Canvas canvas){
    if(artwork==null)return;
    source.set(pro?716:22,pro?76:30,pro?1008:716,pro?270:310);
    float scale=Math.min(getWidth()/(float)source.width(),getHeight()/(float)source.height());
    float width=source.width()*scale,height=source.height()*scale;
    float left=(getWidth()-width)/2f,top=(getHeight()-height)/2f;
    paint.setShader(null);paint.setStyle(Paint.Style.FILL);
    paint.setColorFilter(pro&&active?GREEN:null);
    if(pro||!experimental){
      destination.set(left,top,left+width,top+height);canvas.drawBitmap(artwork,source,destination,paint);return;
    }
    int saved=canvas.save();canvas.translate(left,top);canvas.scale(scale,scale);
    // Keep the supplied lettering, tinting only its first red letter when PRO really runs.
    source.set(288,30,402,310);destination.set(266,0,380,280);
    paint.setColorFilter(active?GREEN:null);canvas.drawBitmap(artwork,source,destination,paint);
    source.set(402,30,716,310);destination.set(380,0,694,280);
    paint.setColorFilter(null);canvas.drawBitmap(artwork,source,destination,paint);
    drawRadar(canvas);canvas.restoreToCount(saved);
  }
  private void drawRadar(Canvas canvas){
    int color=active?0xFF38FF86:0xFFFF6528;
    paint.setColor(Color.WHITE);paint.setStyle(Paint.Style.FILL);paint.setShader(active?greenGlow:redGlow);
    canvas.drawCircle(142,134,134,paint);paint.setShader(null);
    paint.setColor(0xFF0B1817);canvas.drawCircle(142,134,120,paint);
    paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(9);paint.setColor(0xFF414B4B);canvas.drawCircle(142,134,124,paint);
    paint.setStrokeWidth(2);paint.setColor(0xFFB5C5BE);canvas.drawCircle(142,134,128,paint);
    paint.setColor(color);canvas.drawCircle(142,134,119,paint);
    paint.setAlpha(100);paint.setStrokeWidth(1);
    for(int radius=30;radius<=90;radius+=30)canvas.drawCircle(142,134,radius,paint);
    canvas.drawLine(24,134,260,134,paint);canvas.drawLine(142,16,142,252,paint);
    int ticks=canvas.save();canvas.translate(142,134);paint.setAlpha(210);paint.setStrokeWidth(2);
    for(int i=0;i<24;i++){canvas.drawLine(i%3==0?108:113,0,118,0,paint);canvas.rotate(15);}
    canvas.restoreToCount(ticks);paint.setAlpha(255);
    int beam=canvas.save();canvas.rotate(active?sweepAngle():-45,142,134);
    if(active){
      paint.setStyle(Paint.Style.FILL);paint.setShader(sweep);
      canvas.drawCircle(142,134,106,paint);paint.setShader(null);
    }
    paint.setColor(color);paint.setStrokeWidth(7);paint.setAlpha(40);
    canvas.drawLine(142,134,248,134,paint);paint.setStrokeWidth(2);paint.setAlpha(255);
    canvas.drawLine(142,134,248,134,paint);canvas.restoreToCount(beam);
    paint.setStyle(Paint.Style.FILL);paint.setColor(color);canvas.drawCircle(142,134,4,paint);
    paint.setColor(0xFFE3FFEB);canvas.drawCircle(142,134,1.6f,paint);
  }
}
