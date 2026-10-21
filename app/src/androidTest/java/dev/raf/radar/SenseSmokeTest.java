package dev.raf.radar;

import android.content.*;
import android.graphics.*;
import android.os.SystemClock;
import android.view.*;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;

public class SenseSmokeTest {
  @Test public void centralButtonTransformationRealServiceAndOriginalFallback() throws Exception {
    Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();c.stopService(new Intent(c,MonitorService.class));EconomyPatchTest.waitFor(()->!MonitorService.running);
    c.getSharedPreferences("radar",0).edit().clear().putBoolean("autoUpdate",false).putBoolean("sounds",false).commit();ScreenTest.grantAccess();
    try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
      MainActivity[] activity={null};Bitmap[] legacy={null};
      scenario.onActivity(a->{activity[0]=a;assertFalse(a.store.prefs.getBoolean("experimentalLogo",false));assertEquals(View.GONE,a.senseLogo.getVisibility());legacy[0]=LogoSmokeTest.render(a.brandLogo);});
      onView(withContentDescription("Вкладка Настройки")).perform(click());
      onView(withContentDescription("Экспериментальный")).perform(scrollTo(),click());
      onView(withContentDescription("Вкладка Карта")).perform(click());
      scenario.onActivity(a->{
        assertEquals(View.VISIBLE,a.senseLogo.getVisibility());assertEquals(View.GONE,a.classicBrand.getVisibility());assertFalse(a.pro.isEnabled());
        assertFalse(a.senseLogo.inButton(a.senseLogo.getWidth()*.52f,a.senseLogo.getHeight()*.9f));
        tap(a.senseLogo,663,1118);assertFalse("PRO is an indicator",MonitorService.running);
        assertEquals(0,a.senseLogo.motion.pro,.001);assertFalse(a.senseLogo.animationRunning());
      });capture("sense-off-reference");
      scenario.onActivity(a->tap(a.senseLogo,SenseLogo.CX,SenseLogo.CY));EconomyPatchTest.waitFor(()->MonitorService.running);
      scenario.onActivity(a->{a.refreshCards();assertTrue("Service starts before five-second animation",a.senseLogo.motion.progress<.8f);});
      SystemClock.sleep(2100);capture("sense-fragments");SystemClock.sleep(1400);capture("sense-locator-emerging");SystemClock.sleep(2100);
      scenario.onActivity(a->{
        a.refreshCards();assertEquals(1,a.senseLogo.motion.progress,.001);assertEquals(1,a.senseLogo.motion.pro,.001);assertTrue(a.senseLogo.animationRunning());
        // SENSE and the metal PRO frame are identical in both endpoint renders.
        SenseLogo logo=a.senseLogo;float old=logo.motion.progress,pro=logo.motion.pro;
        Bitmap on=LogoSmokeTest.render(logo);logo.motion.progress=0;logo.motion.pro=0;Bitmap off=LogoSmokeTest.render(logo);logo.motion.progress=old;logo.motion.pro=pro;
        int y=Math.round(on.getHeight()*980f/SenseLogo.HEIGHT);for(int x=on.getWidth()/10;x<on.getWidth()*9/10;x++)assertEquals("SENSE text remains unchanged",off.getPixel(x,y),on.getPixel(x,y));on.recycle();off.recycle();
      });capture("sense-working-green");
      scenario.moveToState(Lifecycle.State.STARTED);SystemClock.sleep(200);
      InstrumentationRegistry.getInstrumentation().runOnMainSync(()->assertFalse(activity[0].senseLogo.animationRunning()));
      scenario.moveToState(Lifecycle.State.RESUMED);scenario.onActivity(a->{assertEquals("No five-second replay on return",1,a.senseLogo.motion.progress,.001);assertEquals(1,a.senseLogo.motion.pro,.001);});
      scenario.recreate();scenario.onActivity(a->{activity[0]=a;assertEquals(1,a.senseLogo.motion.progress,.001);});
      scenario.onActivity(a->tap(a.senseLogo,SenseLogo.CX,SenseLogo.CY));EconomyPatchTest.waitFor(()->!MonitorService.running);
      scenario.onActivity(MainActivity::refreshCards);SystemClock.sleep(350);capture("sense-stopping");
      // Reverse while fragments are returning; the same service path handles the click.
      scenario.onActivity(a->tap(a.senseLogo,SenseLogo.CX,SenseLogo.CY));EconomyPatchTest.waitFor(()->MonitorService.running);scenario.onActivity(MainActivity::refreshCards);
      SystemClock.sleep(100);scenario.onActivity(a->tap(a.senseLogo,SenseLogo.CX,SenseLogo.CY));EconomyPatchTest.waitFor(()->!MonitorService.running);
      scenario.onActivity(MainActivity::refreshCards);SystemClock.sleep(1800);
      scenario.onActivity(a->{assertEquals(0,a.senseLogo.motion.progress,.001);assertEquals(0,a.senseLogo.motion.pro,.001);assertFalse(a.senseLogo.animationRunning());assertFalse(a.store.prefs.getBoolean("overlay",true));});capture("sense-stopped-s-restored");
      onView(withContentDescription("Вкладка Настройки")).perform(click());onView(withContentDescription("Экспериментальный")).perform(scrollTo(),click());onView(withContentDescription("Вкладка Карта")).perform(click());
      scenario.onActivity(a->{Bitmap restored=LogoSmokeTest.render(a.brandLogo);assertTrue("Exact legacy fallback",legacy[0].sameAs(restored));restored.recycle();assertEquals(View.GONE,a.senseLogo.getVisibility());assertTrue(a.pro.isClickable());});
      scenario.recreate();scenario.onActivity(a->assertFalse(a.store.prefs.getBoolean("experimentalLogo",true)));legacy[0].recycle();
    }finally{c.stopService(new Intent(c,MonitorService.class));}
  }
  static void tap(SenseLogo view,float nx,float ny){
    float scale=Math.min(view.getWidth()/SenseLogo.WIDTH,view.getHeight()/SenseLogo.HEIGHT);
    float x=(view.getWidth()-SenseLogo.WIDTH*scale)/2+nx*scale,y=(view.getHeight()-SenseLogo.HEIGHT*scale)/2+ny*scale;
    long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,x,y,0),up=MotionEvent.obtain(now,now+30,MotionEvent.ACTION_UP,x,y,0);
    view.dispatchTouchEvent(down);view.dispatchTouchEvent(up);down.recycle();up.recycle();
  }
  static void capture(String name) throws Exception {
    InstrumentationRegistry.getInstrumentation().waitForIdleSync();Bitmap bitmap=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();if(bitmap==null)return;
    Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();ContentValues values=new ContentValues();values.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,name+".png");values.put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/png");values.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,"Pictures/RadarSmoke");
    android.net.Uri uri=c.getContentResolver().insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);if(uri!=null)try(java.io.OutputStream out=c.getContentResolver().openOutputStream(uri)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
  }
}
