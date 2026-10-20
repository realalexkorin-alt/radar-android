package dev.raf.radar;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Original supplied artwork, cropped only while drawing; PRO is a separate control. */
final class RadarLogo extends View {
  private static Bitmap artwork;
  private final boolean pro;
  private boolean active;
  private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
  RadarLogo(Context c,boolean pro){
    super(c);this.pro=pro;
    if(artwork==null)try(java.io.InputStream in=c.getAssets().open("radar_logo.jpg")){
      BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=2;
      artwork=BitmapFactory.decodeStream(in,null,options);
    }catch(java.io.IOException ignored){}
    if(!pro)setContentDescription("Радар");
  }
  void active(boolean enabled){active=enabled;setSelected(enabled);setContentDescription(enabled?"PRO: мониторинг включён":"PRO: мониторинг выключен");invalidate();}
  @Override protected void onDraw(Canvas canvas){
    if(artwork==null)return;
    Rect src=pro?new Rect(728,214,963,365):new Rect(60,193,726,388);
    if(pro&&active)paint.setColorFilter(new ColorMatrixColorFilter(new float[]{0,1,0,0,0,1,0,0,0,0,0,0,1,0,0,0,0,0,1,0}));
    else paint.setColorFilter(null);
    canvas.drawBitmap(artwork,src,new RectF(0,0,getWidth(),getHeight()),paint);
  }
}
