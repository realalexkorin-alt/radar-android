package dev.raf.radar;

import android.content.Context;
import android.graphics.*;
import android.os.*;
import android.view.View;
import android.widget.TextView;

/** GPU text glow. No animator survives a detached overlay or an inactive screen. */
final class PriceText extends TextView {
  private final Paint flare=new Paint(Paint.ANTI_ALIAS_FLAG);
  private final PowerManager power;
  private Store.Reading reading;
  private float shadow=-1;private int shadowColor;
  PriceText(Context c){
    super(c);power=c.getSystemService(PowerManager.class);setIncludeFontPadding(false);
    setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));setSingleLine(true);
    setLayerType(View.LAYER_TYPE_SOFTWARE,null);
  }
  void reading(Store.Reading value){reading=value;setText(RadarUi.value(value));
    setTextColor(PriceLevel.color(value==null?null:value.rub));
    setContentDescription("Эконом: "+getText()+", "+PriceLevel.label(value==null?null:value.rub)
        +(value!=null&&!value.fresh()?", данные устарели":""));invalidate();}
  @Override protected void onDraw(Canvas canvas){
    boolean animate=reading!=null&&reading.rub!=null&&reading.fresh()&&power!=null&&power.isInteractive();
    int level=PriceLevel.of(reading==null?null:reading.rub),color=getCurrentTextColor();
    float phase=(SystemClock.uptimeMillis()%(3000-level*350))/(float)(3000-level*350);
    float wave=(1f-(float)Math.cos(phase*Math.PI*2))/2f;
    float density=getResources().getDisplayMetrics().density;
    float radius=animate?(1.5f+level+wave*(1+level))*density:0;
    if(Math.abs(radius-shadow)>.1f||color!=shadowColor){shadow=radius;shadowColor=color;getPaint().setShadowLayer(radius,0,0,color);}
    if(animate&&level==4){
      int red=Color.argb((int)(25+60*wave),255,25,45);
      float center=(getWidth()+getHeight())*phase-getHeight()/2f;
      flare.setShader(new LinearGradient(center-getHeight()/2f,0,center+getHeight()/2f,0,
          new int[]{Color.TRANSPARENT,red,Color.TRANSPARENT},null,Shader.TileMode.CLAMP));
      canvas.drawRoundRect(0,0,getWidth(),getHeight(),12*density,12*density,flare);
    }
    canvas.saveLayerAlpha(0,0,getWidth(),getHeight(),animate?(int)(level==0?244+11*wave:238+17*wave):255);
    super.onDraw(canvas);canvas.restore();
    if(animate&&isAttachedToWindow()&&getWindowVisibility()==VISIBLE)postInvalidateDelayed(33);
  }
}
