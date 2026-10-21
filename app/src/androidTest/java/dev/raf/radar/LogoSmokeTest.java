package dev.raf.radar;

import android.content.*;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;

/** Checks the opt-in, exact fallback, live PRO control and visible-only animation. */
public class LogoSmokeTest {
  @Test public void originalFallbackAndExperimentalAnimation() throws Exception {
    Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
    c.stopService(new Intent(c,MonitorService.class));EconomyPatchTest.waitFor(()->!MonitorService.running);
    c.getSharedPreferences("radar",0).edit().clear().putBoolean("autoUpdate",false).putBoolean("sounds",false).commit();
    ScreenTest.grantAccess();
    try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
      Bitmap[] legacy={null},frames={null,null};MainActivity[] activity={null};
      scenario.onActivity(a->{
        activity[0]=a;assertFalse(a.store.prefs.getBoolean("experimentalLogo",false));
        assertTrue(a.pro.isClickable());assertFalse(a.pro.isSelected());
        legacy[0]=render(a.brandLogo);assertFalse(a.brandLogo.animationRunning());assertProColor(a.pro,false);
      });
      ScreenTest.capture("logo-default-original");
      onView(withContentDescription("Вкладка Настройки")).perform(click());
      onView(withContentDescription("Экспериментальный")).perform(scrollTo(),click());
      scenario.onActivity(a->assertTrue(a.store.prefs.getBoolean("experimentalLogo",false)));
      scenario.recreate();
      scenario.onActivity(a->{activity[0]=a;assertTrue(a.store.prefs.getBoolean("experimentalLogo",false));});
      onView(withContentDescription("Вкладка Карта")).perform(click());
      scenario.onActivity(a->{assertFalse(a.brandLogo.animationRunning());assertProColor(a.pro,false);});
      ScreenTest.capture("logo-experimental-off");
      scenario.onActivity(a->a.pro.performClick());EconomyPatchTest.waitFor(()->MonitorService.running);
      scenario.onActivity(MainActivity::refreshCards);SystemClock.sleep(650);
      scenario.onActivity(a->{
        a.refreshCards();assertTrue(a.pro.isSelected());assertTrue(a.brandLogo.animationRunning());
        assertProColor(a.pro,true);frames[0]=render(a.brandLogo);assertGreen(frames[0]);
      });
      SystemClock.sleep(350);
      scenario.onActivity(a->{frames[1]=render(a.brandLogo);assertFalse("Radar sweep advances",frames[0].sameAs(frames[1]));});
      ScreenTest.capture("logo-experimental-green");
      // Actual PRO stop starts a transition without teleporting the vector beam.
      scenario.onActivity(a->{
        float before=a.brandLogo.sweepAngle();a.pro.performClick();
        // Observe the transition in the same UI turn, before asynchronous service teardown.
        a.brandLogo.active(false);a.pro.active(false);
        assertTrue("No stop-angle reset",angularDistance(before,a.brandLogo.sweepAngle())<3);
        assertTrue("Stop starts from green",a.brandLogo.motion.level>.8f);
      });
      EconomyPatchTest.waitFor(()->!MonitorService.running);
      SystemClock.sleep(250);
      ScreenTest.capture("logo-transition-stopping");SystemClock.sleep(500);
      float[] frozen={0};scenario.onActivity(a->{assertFalse(a.brandLogo.animationRunning());assertFalse(a.pro.animationRunning());frozen[0]=a.brandLogo.sweepAngle();assertProColor(a.pro,false);});
      SystemClock.sleep(150);scenario.onActivity(a->assertEquals("Stopped beam holds angle",frozen[0],a.brandLogo.sweepAngle(),.01f));
      ScreenTest.capture("logo-transition-stopped");
      scenario.onActivity(a->a.pro.performClick());EconomyPatchTest.waitFor(()->MonitorService.running);
      scenario.onActivity(a->{float before=a.brandLogo.sweepAngle();a.refreshCards();assertTrue("Restart keeps angle",angularDistance(before,a.brandLogo.sweepAngle())<3);});
      SystemClock.sleep(650);
      scenario.moveToState(Lifecycle.State.STARTED);
      InstrumentationRegistry.getInstrumentation().runOnMainSync(()->assertFalse("Background stops frames",activity[0].brandLogo.animationRunning()));
      scenario.moveToState(Lifecycle.State.RESUMED);
      scenario.onActivity(a->assertTrue(a.brandLogo.animationRunning()));
      onView(withContentDescription("Вкладка Настройки")).perform(click());
      scenario.onActivity(a->assertFalse("Hidden map stops frames",a.brandLogo.animationRunning()));
      onView(withContentDescription("Экспериментальный")).perform(scrollTo(),click());
      onView(withContentDescription("Вкладка Карта")).perform(click());
      scenario.onActivity(a->{
        Bitmap restored=render(a.brandLogo);assertTrue("Original PNG restored exactly",legacy[0].sameAs(restored));restored.recycle();
        assertFalse(a.brandLogo.animationRunning());assertTrue(a.pro.isSelected());
      });
      ScreenTest.capture("logo-restored-original");
      scenario.recreate();scenario.onActivity(a->{assertFalse(a.store.prefs.getBoolean("experimentalLogo",true));a.pro.performClick();});
      EconomyPatchTest.waitFor(()->!MonitorService.running);
      scenario.onActivity(a->{a.refreshCards();assertFalse(a.pro.isSelected());assertProColor(a.pro,false);assertFalse(a.store.prefs.getBoolean("overlay",true));});
      legacy[0].recycle();frames[0].recycle();frames[1].recycle();
    }finally{c.stopService(new Intent(c,MonitorService.class));}
  }
  static float angularDistance(float a,float b){float d=Math.abs(a-b)%360;return Math.min(d,360-d);}
  static Bitmap render(View view){
    Bitmap b=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(b));return b;
  }
  static void assertGreen(Bitmap b){
    int green=0;for(int y=0;y<b.getHeight();y++)for(int x=0;x<b.getWidth()/2;x++){
      int p=b.getPixel(x,y);if(Color.alpha(p)>100&&Color.green(p)>Color.red(p)+50)green++;
    }
    assertTrue("Radar and first letter turn green",green>50);
  }
  static void assertProColor(View view,boolean active){
    Bitmap b=render(view);int dominant=0,visible=0;
    for(int y=0;y<b.getHeight();y++)for(int x=0;x<b.getWidth();x++){
      int p=b.getPixel(x,y);if(Color.alpha(p)<100)continue;visible++;
      if(active?Color.green(p)>Color.red(p)+60:Color.red(p)>Color.green(p)+60)dominant++;
    }
    assertEquals(0,Color.alpha(b.getPixel(0,0)));assertTrue(visible>100);assertTrue(dominant>100);b.recycle();
  }
}
