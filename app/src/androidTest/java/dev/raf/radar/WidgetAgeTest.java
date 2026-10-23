package dev.raf.radar;
import android.content.Context;import android.os.SystemClock;import android.view.*;import android.widget.*;
import androidx.test.platform.app.InstrumentationRegistry;import org.junit.Test;import static org.junit.Assert.*;import java.util.*;
public class WidgetAgeTest {
 @Test public void optionalBottomLeftAgeSurvivesFailedReadingAndRecreation() throws Exception {
  Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();Store s=new Store(c);
  s.prefs.edit().clear().putBoolean("overlay",true).putLong("gpsAt",System.currentTimeMillis()).commit();
  ScreenTest.shell("appops set dev.raf.radar SYSTEM_ALERT_WINDOW allow");
  Overlay[] overlay={null};String[] initial={null};long success=System.currentTimeMillis()-12000;
  s.readings("me",Collections.singletonList(new Store.Reading("econom",260,null,success,"fixture")));
  try{
   main(()->{overlay[0]=new Overlay(c,s);overlay[0].refresh();assertEquals(View.GONE,label(overlay[0]).getVisibility());s.prefs.edit().putBoolean("widgetUpdateTime",true).commit();overlay[0].refresh();assertEquals(View.VISIBLE,label(overlay[0]).getVisibility());assertTrue(label(overlay[0]).getText().toString().startsWith("Обновлено "));initial[0]=label(overlay[0]).getText().toString();assertEquals(0x80FFFFFF,label(overlay[0]).getCurrentTextColor());});
   InstrumentationRegistry.getInstrumentation().waitForIdleSync();
   main(()->{LinearLayout root=root(overlay[0]);assertTrue(label(overlay[0]).getTop()>=root.getChildAt(0).getBottom());assertEquals(Gravity.START,label(overlay[0]).getGravity()&Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK);});
   ScreenTest.capture("widget-update-age-bottom-left");
   SystemClock.sleep(1100);main(()->assertNotEquals(initial[0],label(overlay[0]).getText().toString()));
   s.readings("me",Collections.singletonList(new Store.Reading("econom",null,null,System.currentTimeMillis(),"Ошибка сети")));assertEquals(success,s.lastSuccessfulReading("me"));
   main(()->{overlay[0].close();overlay[0]=new Overlay(c,new Store(c));overlay[0].refresh();assertEquals(View.VISIBLE,label(overlay[0]).getVisibility());assertTrue(label(overlay[0]).getText().toString().startsWith("Обновлено "));s.prefs.edit().putBoolean("widgetUpdateTime",false).commit();overlay[0].refresh();assertEquals(View.GONE,label(overlay[0]).getVisibility());});
  }finally{main(()->{if(overlay[0]!=null)overlay[0].close();});}
 }
 static void main(Runnable r){InstrumentationRegistry.getInstrumentation().runOnMainSync(r);}
 static LinearLayout root(Overlay o){try{java.lang.reflect.Field f=Overlay.class.getDeclaredField("root");f.setAccessible(true);return (LinearLayout)f.get(o);}catch(Exception e){throw new AssertionError(e);}}
 static TextView label(Overlay o){return (TextView)root(o).getChildAt(1);}
}
