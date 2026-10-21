package dev.raf.radar;

import android.content.*;
import android.graphics.*;
import android.view.View;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import static org.junit.Assert.*;

/** The ordinary logo and its original PRO control remain available. */
public class LogoSmokeTest {
  @Test public void originalLogoStillWorks() throws Exception {
    Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();c.stopService(new Intent(c,MonitorService.class));EconomyPatchTest.waitFor(()->!MonitorService.running);
    c.getSharedPreferences("radar",0).edit().clear().putBoolean("autoUpdate",false).putBoolean("sounds",false).commit();ScreenTest.grantAccess();
    try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
      scenario.onActivity(a->{assertFalse(a.store.prefs.getBoolean("experimentalLogo",false));assertEquals(View.GONE,a.senseLogo.getVisibility());assertTrue(a.pro.isClickable());assertProColor(a.pro,false);});
      scenario.onActivity(a->a.pro.performClick());EconomyPatchTest.waitFor(()->MonitorService.running);
      scenario.onActivity(a->{a.refreshCards();assertProColor(a.pro,true);a.pro.performClick();});EconomyPatchTest.waitFor(()->!MonitorService.running);
    }finally{c.stopService(new Intent(c,MonitorService.class));}
  }
  static Bitmap render(View view){Bitmap b=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(b));return b;}
  static void assertProColor(View view,boolean active){
    Bitmap b=render(view);int dominant=0;for(int y=0;y<b.getHeight();y++)for(int x=0;x<b.getWidth();x++){int p=b.getPixel(x,y);if(Color.alpha(p)>100&&(active?Color.green(p)>Color.red(p)+60:Color.red(p)>Color.green(p)+60))dominant++;}
    assertTrue(dominant>100);b.recycle();
  }
}
