package dev.raf.radar;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;

/** Supplied metal textures + clipped S fragments + vector locator. Only the center is a button. */
final class SenseLogo extends View {
  static final float WIDTH=1268,HEIGHT=1240,CX=634,CY=480,INNER=310;
  private static Bitmap artwork,greenLights,greenPro;
  private static final RectF REFERENCE=new RectF(0,0,WIDTH,HEIGHT);
  final SenseMotion motion=new SenseMotion();
  private boolean running,requestedVisible,attached,framePosted,tracking;
  private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
  private static final float[] DX={-45,145,-145,45},DY={-160,-35,35,160};
  private final Path letter=new Path(),inside=new Path();
  private final Path[] fragments=new Path[4];
  private final Shader plate=new RadialGradient(CX,CY,INNER,new int[]{0xFF14252A,0xFF071014,0xFF17212A},new float[]{0,.8f,1},Shader.TileMode.CLAMP);
  private final Shader beam=new SweepGradient(CX,CY,new int[]{0x0038FF86,0x0038FF86,0x2038FF86,0xA038FF86},new float[]{0,.72f,.94f,1});
  private final Shader proOrange=new RadialGradient(663,1118,225,new int[]{0x40FF8500,0x00FF8500},null,Shader.TileMode.CLAMP);
  private final Shader proGreen=new RadialGradient(663,1118,225,new int[]{0x7038FF86,0x0038FF86},null,Shader.TileMode.CLAMP);
  private final Runnable frame=new Runnable(){public void run(){framePosted=false;motion.advance(SystemClock.elapsedRealtime());invalidate();schedule();}};
  SenseLogo(Context context){
    super(context);setFocusable(true);setClickable(true);
    // Outer silhouette of the supplied S. Its four bands retain the original metal pixels.
    float[] outline={503,222,744,222,835,300,835,385,725,385,725,326,690,290,575,290,534,332,534,373,811,493,842,533,842,639,758,720,500,720,420,641,420,548,532,548,532,602,575,644,692,644,730,606,730,567,453,446,419,399,419,304};
    letter.moveTo(outline[0],outline[1]);for(int i=2;i<outline.length;i+=2)letter.lineTo(outline[i],outline[i+1]);letter.close();
    inside.addCircle(CX,CY,INNER,Path.Direction.CW);
    for(int i=0;i<4;i++){Path band=new Path();band.addRect(380,210+i*132,870,210+(i+1)*132,Path.Direction.CW);fragments[i]=new Path();fragments[i].op(letter,band,Path.Op.INTERSECT);}
  }
  boolean available(){return load(getContext());}
  private static synchronized boolean load(Context context){
    if(artwork!=null)return true;
    try(java.io.InputStream input=context.getAssets().open("sense_pro.png")){
      BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=2;artwork=BitmapFactory.decodeStream(input,null,options);if(artwork==null)throw new java.io.IOException("Invalid SENSE artwork");
      int width=artwork.getWidth(),height=artwork.getHeight();int[] pixels=new int[width*height];artwork.getPixels(pixels,0,width,0,0,width,height);
      int[] lights=new int[pixels.length],indicator=new int[pixels.length];
      for(int y=0;y<height;y++)for(int x=0;x<width;x++){
        float nx=x*WIDTH/width,ny=y*HEIGHT/height;
        int i=y*width+x,p=pixels[i],a=Color.alpha(p),r=Color.red(p),g=Color.green(p),b=Color.blue(p);
        // Only cyan illumination inside the emblem; neutral metal and SENSE stay original.
        if(ny<880&&Math.hypot(nx-CX,ny-CY)<440&&a>0&&b-r>30&&g-r>25)lights[i]=Color.argb(a,r,Math.max(g,b),Math.round(r+(g-r)*.35f));
        // PRO's illuminated inset only, preserving the surrounding metal frame.
        if(nx>490&&nx<837&&ny>1065&&ny<1168&&a>0&&r>g*1.1f&&g>b*1.25f)
          indicator[i]=Color.argb(a,Math.round(b+(r-b)*.22f),r,Math.round(b+(r-b)*.52f));
      }
      greenLights=Bitmap.createBitmap(lights,width,height,Bitmap.Config.ARGB_8888);
      greenPro=Bitmap.createBitmap(indicator,width,height,Bitmap.Config.ARGB_8888);return true;
    }catch(java.io.IOException e){artwork=null;android.util.Log.w("SenseLogo","SENSE artwork unavailable",e);return false;}
  }
  void state(boolean active,boolean visible){if(getVisibility()==VISIBLE)load(getContext());running=active;requestedVisible=visible;bind();setSelected(active);setContentDescription(active?"S: выключить мониторинг":"S: включить мониторинг");invalidate();}
  boolean animationRunning(){return framePosted;}
  private void bind(){motion.bind(running,requestedVisible&&attached&&getWindowVisibility()==VISIBLE&&isShown(),SystemClock.elapsedRealtime());schedule();}
  private void schedule(){if(motion.needsFrames()){if(!framePosted){framePosted=true;postDelayed(frame,33);}}else{removeCallbacks(frame);framePosted=false;}}
  @Override protected void onAttachedToWindow(){super.onAttachedToWindow();attached=true;bind();}
  @Override protected void onDetachedFromWindow(){attached=false;bind();super.onDetachedFromWindow();}
  @Override protected void onWindowVisibilityChanged(int v){super.onWindowVisibilityChanged(v);if(motion!=null)bind();}
  @Override public void onVisibilityAggregated(boolean visible){super.onVisibilityAggregated(visible);bind();}
  @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setClassName("android.widget.Button");}
  private float scale(){return Math.min(getWidth()/WIDTH,getHeight()/HEIGHT);}
  boolean inButton(float x,float y){float s=scale();if(s<=0)return false;float nx=(x-(getWidth()-WIDTH*s)/2)/s,ny=(y-(getHeight()-HEIGHT*s)/2)/s;return Math.hypot(nx-CX,ny-CY)<=INNER;}
  @Override public boolean onTouchEvent(MotionEvent event){
    switch(event.getActionMasked()){
      case MotionEvent.ACTION_DOWN:if(!inButton(event.getX(),event.getY()))return false;tracking=true;setPressed(true);invalidate();return true;
      case MotionEvent.ACTION_MOVE:if(!tracking)return false;setPressed(inButton(event.getX(),event.getY()));invalidate();return true;
      case MotionEvent.ACTION_UP:if(!tracking)return false;boolean click=isPressed()&&inButton(event.getX(),event.getY());tracking=false;setPressed(false);invalidate();if(click)performClick();return true;
      case MotionEvent.ACTION_CANCEL:tracking=false;setPressed(false);invalidate();return true;
      default:return tracking;
    }
  }
  @Override public boolean performClick(){return super.performClick();}
  @Override protected void onDraw(Canvas canvas){
    if(!load(getContext()))return;motion.advance(SystemClock.elapsedRealtime());float s=scale();int save=canvas.save();canvas.translate((getWidth()-WIDTH*s)/2,(getHeight()-HEIGHT*s)/2);canvas.scale(s,s);
    float p=motion.progress,light=SenseMotion.smooth(p,0,.8f);
    paint.setStyle(Paint.Style.FILL);paint.setShader(null);paint.setColorFilter(null);paint.setAlpha(255);
    // The supplied reference is the exact resting state, including its blue mechanism.
    if(p<=.0001f&&!isPressed()){canvas.drawBitmap(artwork,null,REFERENCE,paint);}else{
      paint.setShader(plate);canvas.drawCircle(CX,CY,INNER,paint);paint.setShader(null);
      int body=canvas.save();canvas.clipOutPath(letter);canvas.drawBitmap(artwork,null,REFERENCE,paint);paint.setAlpha(Math.round(255*light));canvas.drawBitmap(greenLights,null,REFERENCE,paint);paint.setAlpha(255);canvas.restoreToCount(body);
      drawFragments(canvas,p,light);drawLocator(canvas,p);
    }
    // Indicator follows the real service, independently of the five-second mechanism.
    paint.setStyle(Paint.Style.FILL);paint.setShader(proOrange);paint.setAlpha(Math.round(90*(1-motion.pro)));canvas.drawRect(485,1068,840,1165,paint);
    paint.setShader(proGreen);paint.setAlpha(Math.round(130*motion.pro));canvas.drawRect(485,1068,840,1165,paint);paint.setShader(null);
    paint.setAlpha(Math.round(255*motion.pro));canvas.drawBitmap(greenPro,null,REFERENCE,paint);paint.setAlpha(255);
    canvas.restoreToCount(save);
  }
  private void drawFragments(Canvas canvas,float p,float light){
    float press=1-.06f*SenseMotion.smooth(p,0,.2f)-(isPressed()?.025f:0);
    float split=SenseMotion.smooth(p,.2f,.6f),opacity=1-SenseMotion.smooth(p,.4f,.6f);
    for(int i=0;i<4;i++){
      int saved=canvas.save();canvas.clipPath(inside);canvas.translate(DX[i]*split,DY[i]*split);canvas.scale(press,press,CX,CY);canvas.rotate((i%2==0?-12:12)*split,CX,CY);canvas.clipPath(fragments[i]);
      paint.setAlpha(Math.round(255*opacity));canvas.drawBitmap(artwork,null,REFERENCE,paint);paint.setAlpha(Math.round(255*opacity*light));canvas.drawBitmap(greenLights,null,REFERENCE,paint);paint.setAlpha(255);canvas.restoreToCount(saved);
    }
  }
  private void drawLocator(Canvas canvas,float p){
    float visibility=SenseMotion.smooth(p,.5f,.8f);if(visibility<=0)return;
    int saved=canvas.save();canvas.clipPath(inside);paint.setStyle(Paint.Style.STROKE);paint.setShader(null);paint.setColor(0xFF38FF86);paint.setAlpha(Math.round(150*visibility));paint.setStrokeWidth(2);
    for(int radius=70;radius<=280;radius+=70)canvas.drawCircle(CX,CY,radius,paint);
    canvas.drawLine(CX-280,CY,CX+280,CY,paint);canvas.drawLine(CX,CY-280,CX,CY+280,paint);
    int ticks=canvas.save();canvas.translate(CX,CY);for(int i=0;i<36;i++){canvas.drawLine(i%3==0?260:270,0,280,0,paint);canvas.rotate(10);}canvas.restoreToCount(ticks);
    canvas.rotate(motion.angle,CX,CY);paint.setStyle(Paint.Style.FILL);paint.setShader(beam);paint.setAlpha(Math.round(255*visibility));canvas.drawCircle(CX,CY,275,paint);paint.setShader(null);
    paint.setColor(0xFF38FF86);paint.setStrokeWidth(8);paint.setAlpha(Math.round(45*visibility));canvas.drawLine(CX,CY,CX+275,CY,paint);paint.setStrokeWidth(2);paint.setAlpha(Math.round(255*visibility));canvas.drawLine(CX,CY,CX+275,CY,paint);canvas.drawCircle(CX,CY,4,paint);
    paint.setAlpha(255);canvas.restoreToCount(saved);
  }
}
