package dev.raf.radar;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import static org.junit.Assert.*;

/** Cosmetic-only gate: original alpha, proportional rendering and existing PRO control. */
public class LogoSmokeTest {
  @Test public void transparentLogoAndProStates() throws Exception {
    Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
    c.getSharedPreferences("radar",0).edit().clear().putBoolean("autoUpdate",false).putBoolean("sounds",false).commit();
    try(java.io.InputStream in=c.getAssets().open("radar_logo.png")){
      Bitmap source=BitmapFactory.decodeStream(in);assertTrue(source.hasAlpha());
      assertEquals(0,Color.alpha(source.getPixel(0,0)));assertEquals(2048,source.getWidth());source.recycle();
    }
    ScreenTest.grantAccess();
    try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
      scenario.onActivity(a->{assertEquals("0.1.14-alpha",BuildConfig.VERSION_NAME);assertTrue(a.pro.isClickable());assertFalse(a.pro.isSelected());assertProColor(a.pro,false);});
      ScreenTest.capture("logo-off-transparent");
      scenario.onActivity(a->a.pro.performClick());
      EconomyPatchTest.waitFor(()->MonitorService.running);
      scenario.onActivity(a->{a.refreshCards();assertTrue(a.pro.isSelected());assertProColor(a.pro,true);});
      ScreenTest.capture("logo-on-transparent");
      scenario.onActivity(a->a.pro.performClick());EconomyPatchTest.waitFor(()->!MonitorService.running);
      scenario.onActivity(a->{a.refreshCards();assertFalse(a.pro.isSelected());assertFalse(a.store.prefs.getBoolean("overlay",true));});
    }finally{c.stopService(new android.content.Intent(c,MonitorService.class));}
  }
  static void assertProColor(View view,boolean active){
    Bitmap b=Bitmap.createBitmap(240,180,Bitmap.Config.ARGB_8888);view.layout(0,0,240,180);view.draw(new Canvas(b));
    int dominant=0,visible=0;
    for(int y=0;y<b.getHeight();y++)for(int x=0;x<b.getWidth();x++){
      int p=b.getPixel(x,y);if(Color.alpha(p)<100)continue;visible++;
      if(active?Color.green(p)>Color.red(p)+60:Color.red(p)>Color.green(p)+60)dominant++;
    }
    assertEquals(0,Color.alpha(b.getPixel(0,0)));assertTrue(visible>1000);assertTrue(dominant>1000);b.recycle();
    view.requestLayout();
  }
}
