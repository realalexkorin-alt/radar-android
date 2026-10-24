package dev.raf.radar;

import android.app.*;import android.content.*;import android.graphics.*;import android.view.*;import android.widget.*;
import androidx.test.core.app.ActivityScenario;import androidx.test.platform.app.InstrumentationRegistry;import org.json.*;import org.junit.Test;import static org.junit.Assert.*;

public class AppearanceTest {
  Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
  void prepare() throws Exception {
    Context c=context();c.stopService(new Intent(c,MonitorService.class));Store s=new Store(c);s.prefs.edit().clear().putBoolean("autoUpdate",false).putBoolean("sounds",true).commit();
    AccountClient.save(s,new JSONObject().put("user",new JSONObject().put("id",999).put("name","Искатель")).put("session","a".repeat(64)));
    AccountClient.transport=(body,token)->new JSONObject().put("friends",new JSONArray()).put("incoming",new JSONArray());ScreenTest.grantAccess();
  }
  @Test public void selectionPreservesMapDataAndStandardSoundPreference() throws Exception {
    prepare();Store s=new Store(context());
    try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
      scenario.onActivity(a->{a.showTab(2);a.themes();});ScreenTest.capture("theme-picker-standard");
      scenario.onActivity(a->{a.selectTheme(Appearance.LINEAGE);assertTrue(Appearance.lineage(a));assertFalse(Appearance.sounds(a));assertTrue(s.prefs.getBoolean("sounds",false));assertFalse(SettingsPolishTest.contains(a.settingsContents(),"Звук"));assertNotNull(a.lineageLogo);
        a.lineageLogo.state(true,true);a.lineageLogo.advance();if(!android.animation.ValueAnimator.areAnimatorsEnabled())assertEquals(1f,a.lineageLogo.power,0f);a.lineageLogo.state(false,false);assertFalse(a.lineageLogo.animationRunning());});
      // Dismiss the picker via the platform back action, leaving the selected settings visible.
      InstrumentationRegistry.getInstrumentation().getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);
      ScreenTest.capture("lineage-settings");
      final org.maplibre.android.maps.MapView[] original={null};final String[] points={null};final boolean[] running={false};
      scenario.onActivity(a->{original[0]=a.mapView;points[0]=a.store.prefs.getString("points","");running[0]=MonitorService.running;a.showTab(0);assertEquals(View.VISIBLE,a.lineageLogo.getVisibility());assertEquals(View.GONE,a.senseLogo.getVisibility());});ScreenTest.capture("lineage-map");
      scenario.onActivity(a->{a.selectTheme(Appearance.STANDARD);assertSame(original[0],a.mapView);assertEquals(points[0],a.store.prefs.getString("points",""));assertEquals(running[0],MonitorService.running);assertTrue(Appearance.sounds(a));assertEquals(View.GONE,a.lineageLogo.getVisibility());assertFalse(a.lineageLogo.animationRunning());});ScreenTest.capture("standard-restored");
      scenario.onActivity(a->a.selectTheme(Appearance.LINEAGE));scenario.recreate();scenario.onActivity(a->{assertTrue(Appearance.lineage(a));assertFalse(Appearance.sounds(a));a.showTab(2);assertFalse(a.lineageLogo.animationRunning());});
    }finally{AccountClient.transport=AccountClient::request;AccountClient.clear(s);}
  }
  @Test public void silentChannelsVectorArtworkAndOverlayStayReadable() throws Exception {
    prepare();Context c=context();Store s=new Store(c);s.prefs.edit().putString(Appearance.KEY,Appearance.LINEAGE).putBoolean("overlay",true).putBoolean("widgetUpdateTime",true).putLong("gpsAt",System.currentTimeMillis()).commit();
    String channel=Appearance.notificationChannel(c,"alerts","Надбавки");assertNotEquals("alerts",channel);assertNull(c.getSystemService(NotificationManager.class).getNotificationChannel(channel).getSound());
    Overlay[] overlay={null};
    try{
      WidgetAgeTest.main(()->{LineageLogo logo=new LineageLogo(c);logo.layout(0,0,800,876);Bitmap image=Bitmap.createBitmap(800,876,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(image);logo.drawScene(canvas,0,0);assertNotEquals(Color.TRANSPARENT,image.getPixel(400,366));assertTrue(logo.inButton(400,366));assertFalse(logo.inButton(5,5));image.recycle();
        s.readings("me",java.util.Collections.singletonList(new Store.Reading("econom",999,null,System.currentTimeMillis(),"fixture")));overlay[0]=new Overlay(c,s);overlay[0].refresh();});
      InstrumentationRegistry.getInstrumentation().waitForIdleSync();WidgetAgeTest.main(()->{LinearLayout root=WidgetAgeTest.root(overlay[0]);TextView price=(TextView)root.getChildAt(0);assertEquals("999",price.getText().toString());assertEquals(root.getWidth(),root.getHeight());assertTrue(price.getPaint().measureText("999")<=price.getWidth()-price.getPaddingLeft()-price.getPaddingRight());});ScreenTest.capture("lineage-widget");
    }finally{WidgetAgeTest.main(()->{if(overlay[0]!=null)overlay[0].close();});s.prefs.edit().putString(Appearance.KEY,Appearance.STANDARD).apply();AccountClient.transport=AccountClient::request;AccountClient.clear(s);}
  }
}
