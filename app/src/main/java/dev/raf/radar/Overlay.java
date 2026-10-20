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
  private WindowManager.LayoutParams lp;
  private boolean touching,framePending,screenRegistered;
  private int pendingX,pendingY;
  private final BroadcastReceiver screen=new BroadcastReceiver(){public void onReceive(Context context,Intent intent){if(price!=null)price.invalidate();}};
  Overlay(Context c,Store s){this.c=c;this.s=s;wm=c.getSystemService(WindowManager.class);}
  int dp(float n){return RadarUi.dp(c,n);}
  private void open(boolean settings){c.startActivity(new Intent(c,MainActivity.class).putExtra("widgetSettings",settings)
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));}
  void refresh(){
    if(!s.prefs.getBoolean("overlay",false)||!Settings.canDrawOverlays(c)){close();return;}
    if(touching)return;
    if(root==null){
      root=new LinearLayout(c);root.setOrientation(LinearLayout.VERTICAL);root.setContentDescription("Виджет радара");
      root.setOnClickListener(v->open(false));root.setOnLongClickListener(v->{open(true);return true;});
      price=new PriceText(c);root.addView(price);installGestures();
      lp=new WindowManager.LayoutParams(-2,-2,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
          WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,android.graphics.PixelFormat.TRANSLUCENT);
      lp.gravity=Gravity.TOP|Gravity.START;lp.x=s.prefs.getInt("ox",dp(20));lp.y=s.prefs.getInt("oy",dp(100));
      try{wm.addView(root,lp);}catch(RuntimeException unavailable){root=null;price=null;return;}
      if(!screenRegistered){IntentFilter filter=new IntentFilter(Intent.ACTION_SCREEN_ON);filter.addAction(Intent.ACTION_SCREEN_OFF);
        androidx.core.content.ContextCompat.registerReceiver(c,screen,filter,androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);screenRegistered=true;}
    }
    Store.Reading reading=null;for(Store.Reading r:s.readings(s.widgetPoint().id))if("econom".equals(r.tariff))reading=r;
    price.reading(reading);
    float scale=s.prefs.getInt("scale",100)/100f,textScale=s.prefs.getInt("textScale",100)/100f;
    int padding=dp(2+2*scale),glow=dp(10);root.setPadding(padding,padding,padding,padding);price.setPadding(glow,glow,glow,glow);
    root.setBackground(RadarUi.shape(c,Color.argb(s.prefs.getInt("opacity",70)*255/100,12,13,15),16,0));
    price.setTextSize(28*scale*textScale);
    float available=c.getResources().getDisplayMetrics().widthPixels-dp(12)-2*padding-2*glow;
    float wanted=android.text.Layout.getDesiredWidth(price.getText(),price.getPaint());
    if(wanted>available)price.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,price.getTextSize()*available/wanted*.98f);
    root.post(()->{if(root!=null)position(lp.x,lp.y);});
  }
  private void position(int x,int y){
    if(root==null)return;
    lp.x=Math.max(0,Math.min(x,c.getResources().getDisplayMetrics().widthPixels-root.getWidth()));
    lp.y=Math.max(0,Math.min(y,c.getResources().getDisplayMetrics().heightPixels-root.getHeight()));
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
  void close(){touching=false;framePending=false;
    if(screenRegistered){try{c.unregisterReceiver(screen);}catch(IllegalArgumentException ignored){}screenRegistered=false;}
    if(root!=null){try{wm.removeView(root);}catch(RuntimeException ignored){}root=null;price=null;}
  }
}
