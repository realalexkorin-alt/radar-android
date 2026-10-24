package dev.raf.radar;

import android.content.*;
import android.graphics.Color;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

final class Overlay {
  private final Context c;
  private final Store s;
  private final WindowManager wm;
  private LinearLayout root;
  private PriceText price;
  private TextView updated;
  private final android.os.Handler ageHandler=new android.os.Handler(android.os.Looper.getMainLooper());
  private final Runnable ageTick=()->updateAge();
  private WindowManager.LayoutParams lp;
  private boolean touching,framePending,screenRegistered;
  private int pendingX,pendingY;
  private final BroadcastReceiver screen=new BroadcastReceiver(){public void onReceive(Context context,Intent intent){if(price!=null)price.invalidate();updateAge();}};
  Overlay(Context c,Store s){this.c=c;this.s=s;wm=c.getSystemService(WindowManager.class);}
  int dp(float n){return RadarUi.dp(c,n);}
  private void open(boolean settings){c.startActivity(new Intent(c,MainActivity.class).putExtra("widgetSettings",settings)
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));}
  void refresh(){
    if(!s.prefs.getBoolean("overlay",false)||!Settings.canDrawOverlays(c)){close();return;}
    if(touching)return;
    if(root==null){
      root=new LinearLayout(c);root.setOrientation(LinearLayout.VERTICAL);root.setClipToOutline(true);root.setContentDescription("Виджет радара");
      root.setOnClickListener(v->open(false));root.setOnLongClickListener(v->{open(true);return true;});
      price=new PriceText(c);price.setLayerType(View.LAYER_TYPE_NONE,null);price.setGravity(Gravity.CENTER);price.setHorizontallyScrolling(false);price.setFontFeatureSettings("tnum");root.addView(price,new LinearLayout.LayoutParams(-1,dp(36)));
      updated=new TextView(c);updated.setContentDescription("Время обновления виджета");updated.setTextColor(0x80FFFFFF);updated.setIncludeFontPadding(false);updated.setSingleLine(true);updated.setGravity(Gravity.CENTER);updated.setFontFeatureSettings("tnum");
      root.addView(updated,new LinearLayout.LayoutParams(-1,dp(12)));installGestures();
      lp=new WindowManager.LayoutParams(dp(88),dp(88),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
          WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,android.graphics.PixelFormat.TRANSLUCENT);
      lp.gravity=Gravity.TOP|Gravity.START;lp.x=s.prefs.getInt("ox",dp(20));lp.y=s.prefs.getInt("oy",dp(100));
      try{wm.addView(root,lp);}catch(RuntimeException unavailable){root=null;price=null;updated=null;return;}
      if(!screenRegistered){IntentFilter filter=new IntentFilter(Intent.ACTION_SCREEN_ON);filter.addAction(Intent.ACTION_SCREEN_OFF);
        androidx.core.content.ContextCompat.registerReceiver(c,screen,filter,androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);screenRegistered=true;}
    }
    Store.Reading reading=s.widgetReading();
    // Keep the optional prefix supported without adding another settings row.
    boolean showPlus=s.prefs.getBoolean("widgetShowPlus",false);
    price.reading(reading,showPlus);
    float scale=s.prefs.getInt("scale",100)/100f,textScale=s.prefs.getInt("textScale",100)/100f;
    root.setPadding(0,0,0,0);int side=dp(10*scale),gap=dp(2*scale);
    price.setPadding(side,0,side,0);price.setTextSize(28*scale*textScale);updated.setTextSize(9*scale);updated.setPadding(0,0,0,0);
    String referenceText=showPlus?"+999":"999";
    float reference=Math.max(android.text.Layout.getDesiredWidth(referenceText,price.getPaint()),price.getPaint().measureText(referenceText));
    android.graphics.Paint.FontMetrics fm=price.getPaint().getFontMetrics(),ageMetrics=updated.getPaint().getFontMetrics();
    int ageHeight=(int)Math.ceil(ageMetrics.descent-ageMetrics.ascent)+dp(2*scale);
    int diameter=Math.max(dp((showPlus?88:80)*scale),Math.max((int)Math.ceil(reference)+side*2,(int)Math.ceil(fm.descent-fm.ascent)+2*(ageHeight+gap+dp(4*scale))));
    int screen=Math.min(c.getResources().getDisplayMetrics().widthPixels,c.getResources().getDisplayMetrics().heightPixels)-dp(12);
    diameter=Math.min(diameter,screen);float actual=Math.max(android.text.Layout.getDesiredWidth(price.getText(),price.getPaint()),price.getPaint().measureText(price.getText().toString()));
    float widthFactor=(diameter-2f*side-dp(2))/Math.max(reference,actual),heightFactor=(diameter-2f*(ageHeight+gap+dp(4*scale)))/(fm.descent-fm.ascent);
    float fit=Math.min(1f,Math.min(widthFactor,heightFactor));if(fit<1)price.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,price.getTextSize()*Math.max(.1f,fit));
    fm=price.getPaint().getFontMetrics();int priceHeight=(int)Math.ceil(fm.descent-fm.ascent)+dp(2*scale);
    LinearLayout.LayoutParams priceParams=new LinearLayout.LayoutParams(diameter,priceHeight);priceParams.topMargin=(diameter-priceHeight)/2;price.setLayoutParams(priceParams);
    LinearLayout.LayoutParams ageParams=new LinearLayout.LayoutParams(diameter,ageHeight);ageParams.topMargin=gap;updated.setLayoutParams(ageParams);
    android.graphics.drawable.GradientDrawable circle=new android.graphics.drawable.GradientDrawable();circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);circle.setColor(Color.argb(s.prefs.getInt("opacity",70)*255/100,12,13,15));root.setBackground(circle);
    lp.width=diameter;lp.height=diameter;wm.updateViewLayout(root,lp);updateAge();
    price.requestLayout();root.requestLayout();
    root.post(()->{if(root!=null)position(lp.x,lp.y);});
  }
  private void updateAge(){
    ageHandler.removeCallbacks(ageTick);if(root==null||updated==null)return;
    boolean enabled=s.prefs.getBoolean("widgetUpdateTime",false);updated.setVisibility(enabled?View.VISIBLE:View.GONE);
    if(!enabled)return;long success=s.lastSuccessfulReading("me"),now=System.currentTimeMillis();
    updated.setText(success>0?Long.toString(Math.max(0,(now-success)/1000)):"—");updated.setContentDescription("Время обновления виджета: "+ReadingAge.label(success,now));
    android.os.PowerManager power=c.getSystemService(android.os.PowerManager.class);
    if(power!=null&&power.isInteractive())ageHandler.postDelayed(ageTick,1000);
  }
  private void position(int x,int y){
    if(root==null)return;
    lp.x=Math.max(0,Math.min(x,c.getResources().getDisplayMetrics().widthPixels-lp.width));
    lp.y=Math.max(0,Math.min(y,c.getResources().getDisplayMetrics().heightPixels-lp.height));
    wm.updateViewLayout(root,lp);
  }
  private void moveOnFrame(int x,int y){
    pendingX=x;pendingY=y;if(framePending)return;framePending=true;
    Choreographer.getInstance().postFrameCallback(time->{if(framePending){framePending=false;position(pendingX,pendingY);}});
  }
  private void installGestures(){root.setOnTouchListener(new View.OnTouchListener(){
    final WidgetGesture gesture=new WidgetGesture(ViewConfiguration.get(c).getScaledTouchSlop(),ViewConfiguration.getLongPressTimeout());
    float x,y;int ox,oy;
    public boolean onTouch(View view,MotionEvent event){switch(event.getActionMasked()){
      case MotionEvent.ACTION_DOWN:
        touching=true;x=event.getRawX();y=event.getRawY();ox=lp.x;oy=lp.y;gesture.down(x,y,event.getEventTime());return true;
      case MotionEvent.ACTION_MOVE:
        if(gesture.move(event.getRawX(),event.getRawY(),event.getEventTime()))moveOnFrame(ox+Math.round(event.getRawX()-x),oy+Math.round(event.getRawY()-y));return true;
      case MotionEvent.ACTION_UP:
        int action=gesture.up(event.getRawX(),event.getRawY(),event.getEventTime());touching=false;framePending=false;
        if(action==WidgetGesture.DRAG){position(ox+Math.round(event.getRawX()-x),oy+Math.round(event.getRawY()-y));s.prefs.edit().putInt("ox",lp.x).putInt("oy",lp.y).apply();}
        else if(action==WidgetGesture.TAP)view.performClick();else if(action==WidgetGesture.SETTINGS)view.performLongClick();return true;
      case MotionEvent.ACTION_CANCEL:case MotionEvent.ACTION_POINTER_DOWN:touching=false;framePending=false;gesture.cancel();return true;
      default:return true;
    }}
  });}
  void close(){touching=false;framePending=false;ageHandler.removeCallbacks(ageTick);
    if(screenRegistered){try{c.unregisterReceiver(screen);}catch(IllegalArgumentException ignored){}screenRegistered=false;}
    if(root!=null){try{wm.removeView(root);}catch(RuntimeException ignored){}root=null;price=null;updated=null;}
  }
}
