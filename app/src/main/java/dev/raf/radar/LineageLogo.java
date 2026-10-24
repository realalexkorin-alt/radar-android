package dev.raf.radar;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.*;

/** Layered vector artwork: no embedded bitmap, and no animated outer frame or letter. */
final class LineageLogo extends View {
  static final float WIDTH=400,HEIGHT=438,CX=200,CY=183,INNER=132;
  final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
  final Path letter=new Path(),wordSense=new Path(),wordPro=new Path(),wing=new Path(),crest=new Path();
  final Shader gold=new LinearGradient(0,20,120,360,new int[]{0xFF73502C,0xFFFFEDB8,0xFFD3A34F,0xFF65421F,0xFFFFE8A7},null,Shader.TileMode.CLAMP);
  final Shader silver=new LinearGradient(140,80,250,290,new int[]{0xFF42535C,0xFFFFFFFF,0xFFBFCBD0,0xFF5B6D78,0xFFF8FBF3},null,Shader.TileMode.CLAMP);
  final Shader sapphire=new RadialGradient(CX,CY,INNER,new int[]{0xFF12598B,0xFF073450,0xFF051D35},null,Shader.TileMode.CLAMP);
  final Shader energy=new SweepGradient(CX,CY,new int[]{0x002CDFFF,0x002CDFFF,0x1837CCFF,0xC67DEBFF,0x002CDFFF},new float[]{0,.5f,.85f,.96f,1});
  final Shader crystal=new LinearGradient(-10,-20,10,20,new int[]{0xFFE6FFFF,0xFF39DFFF,0xFF005481},null,Shader.TileMode.CLAMP);
  boolean active,visible,posted,tracking;float power,phase;long last,transitionStart;float transitionFrom,transitionTo;
  final Runnable frame=()->{posted=false;advance();invalidate();schedule();};
  LineageLogo(Context c){
    super(c);setFocusable(true);setClickable(true);
    p.setTypeface(Typeface.create("serif",Typeface.BOLD));p.setTextSize(274);p.getTextPath("S",0,1,0,0,letter);RectF bounds=new RectF();letter.computeBounds(bounds,true);Matrix m=new Matrix();m.setTranslate(CX-bounds.centerX(),CY-bounds.centerY()+3);letter.transform(m);
    p.setTextSize(49);p.getTextPath("Sense",0,5,0,0,wordSense);p.setTextSize(49);p.getTextPath("Pro",0,3,0,0,wordPro);
    RectF a=new RectF(),b=new RectF();wordSense.computeBounds(a,true);wordPro.computeBounds(b,true);float left=(WIDTH-a.width()-b.width()-5)/2;
    m.setTranslate(left-a.left,404);wordSense.transform(m);m.setTranslate(left+a.width()+5-b.left,404);wordPro.transform(m);
    wing.moveTo(48,171);wing.cubicTo(12,142,8,104,17,71);wing.cubicTo(20,112,48,105,61,133);wing.cubicTo(67,145,61,163,48,171);wing.close();
    crest.moveTo(CX,8);crest.cubicTo(192,35,177,40,174,59);crest.lineTo(CX,83);crest.lineTo(226,59);crest.cubicTo(223,40,208,35,CX,8);crest.close();
    setContentDescription("S: включить мониторинг");
  }
  void state(boolean running,boolean shown){
    if(active!=running){advance();active=running;transitionFrom=power;transitionTo=running?1:0;transitionStart=SystemClock.elapsedRealtime();}
    visible=shown;setSelected(running);setContentDescription(running?"S: выключить мониторинг":"S: включить мониторинг");schedule();invalidate();
  }
  void advance(){long now=SystemClock.elapsedRealtime();float t=Math.min(1,(now-transitionStart)/900f);power=transitionFrom+(transitionTo-transitionFrom)*(t*t*(3-2*t));if(last>0)phase=(phase+Math.min(100,now-last)*.025f)%360;last=now;}
  boolean animationRunning(){return posted;}
  boolean animateAllowed(){return visible&&isAttachedToWindow()&&isShown()&&getWindowVisibility()==VISIBLE&&android.animation.ValueAnimator.areAnimatorsEnabled();}
  void schedule(){if(animateAllowed()&&(active||Math.abs(power-transitionTo)>.001f)){if(!posted){posted=true;postDelayed(frame,33);}}else{removeCallbacks(frame);posted=false;last=0;if(!android.animation.ValueAnimator.areAnimatorsEnabled())power=transitionTo;}}
  @Override protected void onAttachedToWindow(){super.onAttachedToWindow();schedule();}
  @Override protected void onDetachedFromWindow(){removeCallbacks(frame);posted=false;last=0;super.onDetachedFromWindow();}
  @Override public void onVisibilityAggregated(boolean shown){super.onVisibilityAggregated(shown);if(p!=null)schedule();}
  @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(p!=null)schedule();}
  boolean inButton(float x,float y){float s=Math.min(getWidth()/WIDTH,getHeight()/HEIGHT);if(s<=0)return false;return Math.hypot((x-(getWidth()-WIDTH*s)/2)/s-CX,(y-(getHeight()-HEIGHT*s)/2)/s-CY)<=INNER;}
  @Override public boolean onTouchEvent(MotionEvent e){switch(e.getActionMasked()){
    case MotionEvent.ACTION_DOWN:if(!inButton(e.getX(),e.getY()))return false;tracking=true;setPressed(true);invalidate();return true;
    case MotionEvent.ACTION_MOVE:if(!tracking)return false;setPressed(inButton(e.getX(),e.getY()));invalidate();return true;
    case MotionEvent.ACTION_UP:if(!tracking)return false;boolean click=isPressed()&&inButton(e.getX(),e.getY());tracking=false;setPressed(false);invalidate();if(click)performClick();return true;
    case MotionEvent.ACTION_CANCEL:tracking=false;setPressed(false);invalidate();return true;
    default:return tracking;
  }}
  @Override public boolean performClick(){return super.performClick();}
  void fill(Canvas c,Path path,Shader shader,int color){p.setStyle(Paint.Style.FILL);p.setShader(shader);p.setColor(color);p.setAlpha(255);c.drawPath(path,p);p.setShader(null);}
  void stroke(Canvas c,Path path,float width,int color){p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(width);p.setColor(color);c.drawPath(path,p);p.setStyle(Paint.Style.FILL);}
  void gem(Canvas c,float x,float y,float size){int save=c.save();c.translate(x,y);c.scale(size,size);Path d=new Path();d.moveTo(0,-19);d.lineTo(10,0);d.lineTo(0,19);d.lineTo(-10,0);d.close();fill(c,d,crystal,Color.WHITE);stroke(c,d,1.1f,0xFFFFE8A4);p.setColor(0xDDE1FFFF);p.setStrokeWidth(.8f);c.drawLine(0,-18,0,18,p);c.drawLine(-9,0,9,0,p);if(power>0){p.setColor(Color.argb((int)(power*(95+45*Math.sin(Math.toRadians(phase*2)))),140,240,255));c.drawCircle(0,0,4,p);}c.restoreToCount(save);}
  @Override protected void onDraw(Canvas c){advance();drawScene(c,power,phase);}
  void drawScene(Canvas c,float strength,float angle){
    float s=Math.min(getWidth()/WIDTH,getHeight()/HEIGHT);int scene=c.save();c.translate((getWidth()-WIDTH*s)/2,(getHeight()-HEIGHT*s)/2);c.scale(s,s);if(isPressed())c.scale(.976f,.976f,CX,CY);
    p.setShader(null);p.setStyle(Paint.Style.FILL);p.setColor(0x66000000);c.drawCircle(CX,CY+4,159,p);
    fill(c,wing,gold,Color.WHITE);stroke(c,wing,1.6f,0xFFF1D190);int mirror=c.save();c.scale(-1,1,CX,CY);fill(c,wing,gold,Color.WHITE);stroke(c,wing,1.6f,0xFFF1D190);c.restoreToCount(mirror);
    p.setShader(gold);c.drawCircle(CX,CY,154,p);p.setShader(null);p.setColor(0xFF1B2A31);c.drawCircle(CX,CY,146,p);p.setShader(gold);c.drawCircle(CX,CY,139,p);p.setShader(sapphire);c.drawCircle(CX,CY,132,p);p.setShader(null);
    p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.4f);p.setColor(0xFFEDDA9A);c.drawCircle(CX,CY,151,p);p.setColor(0xFF917343);c.drawCircle(CX,CY,143,p);
    // Fixed filigree follows the circular frame, separate from the rotating light.
    int ornaments=c.save();c.translate(CX,CY);for(int i=0;i<12;i++){Path leaf=new Path();leaf.moveTo(0,-145);leaf.cubicTo(-10,-137,-12,-123,0,-116);leaf.cubicTo(12,-123,10,-137,0,-145);stroke(c,leaf,1.3f,i%3==0?0xFFE3C482:0xFF8D784F);c.rotate(30);}c.restoreToCount(ornaments);
    p.setStyle(Paint.Style.FILL);int inner=c.save();Path clip=new Path();clip.addCircle(CX,CY,131,Path.Direction.CW);c.clipPath(clip);
    c.rotate(angle,CX,CY);p.setShader(energy);p.setAlpha((int)(40+strength*215));c.drawCircle(CX,CY,131,p);p.setShader(null);p.setAlpha(255);c.restoreToCount(inner);
    p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.2f);p.setColor(Color.argb((int)(50+strength*130),68,212,255));c.drawCircle(CX,CY,125,p);
    for(int i=0;i<8;i++){double a=Math.toRadians(i*45-90);float x=CX+(float)Math.cos(a)*112,y=CY+(float)Math.sin(a)*112;Path rune=new Path();rune.moveTo(x,y-7);rune.lineTo(x+4,y);rune.lineTo(x,y+7);rune.lineTo(x-4,y);rune.close();c.drawPath(rune,p);}
    p.setStyle(Paint.Style.FILL);int shadow=c.save();c.translate(0,4);fill(c,letter,null,0xDD010C15);c.restoreToCount(shadow);stroke(c,letter,4.5f,0xFF63431D);stroke(c,letter,2.4f,0xFFFFD88C);fill(c,letter,silver,Color.WHITE);
    fill(c,crest,gold,Color.WHITE);stroke(c,crest,1.3f,0xFFFFE4A4);gem(c,200,48,1.25f);gem(c,50,183,.95f);gem(c,350,183,.95f);gem(c,200,331,1.05f);
    Path lower=new Path();lower.moveTo(42,416);lower.lineTo(180,416);lower.lineTo(200,428);lower.lineTo(220,416);lower.lineTo(358,416);stroke(c,lower,2,0xFFB79854);
    stroke(c,wordSense,2.5f,0xFF5B3C20);fill(c,wordSense,silver,Color.WHITE);stroke(c,wordPro,2.5f,0xFF62401D);
    fill(c,wordPro,strength>.5f?null:gold,strength>.5f?0xFF73EEAA:Color.WHITE);
    p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);p.setColor(strength>.5f?0x8873EEAA:0x77FFC56B);c.drawPath(wordPro,p);p.setStyle(Paint.Style.FILL);
    c.restoreToCount(scene);
  }
}
