package dev.raf.radar;
import android.content.Context;import android.location.Location;import android.os.SystemClock;
import androidx.test.platform.app.InstrumentationRegistry;import org.junit.Test;import static org.junit.Assert.*;
public class GpsQualityTest {
 @Test public void rejectsJitterAndTeleportThenReacquires(){
  Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();Store s=new Store(c);s.prefs.edit().clear().commit();
  Location good=fix(55,61,8,15000);assertTrue(LocationState.accept(s,good));long at=s.prefs.getLong("gpsAt",0);
  assertFalse(LocationState.accept(s,fix(55.0001,61,300,0)));assertEquals(at,s.prefs.getLong("gpsAt",0));
  assertFalse(LocationState.accept(s,fix(56,61,8,0)));assertEquals(at,s.prefs.getLong("gpsAt",0));
  assertTrue(LocationState.accept(s,fix(55.0005,61,8,0)));
  s.prefs.edit().putLong("gpsAt",System.currentTimeMillis()-121000).commit();assertTrue(LocationState.accept(s,fix(56,61,10,0)));
  assertFalse(LocationState.accept(s,fix(56,61,600,0)));assertFalse(LocationState.accept(s,fix(56,61,10,121000)));
 }
 @Test public void monitorSurvivesClosedScreenAndExplicitStopStaysStopped()throws Exception{
  Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
  c.stopService(new android.content.Intent(c,MonitorService.class));
  InstrumentationRegistry.getInstrumentation().waitForIdleSync();ScreenTest.grantAccess();
  Store store=new Store(c);store.prefs.edit().clear().putBoolean("sounds",false).commit();
  java.util.List<Point> points=store.points();for(Point point:points)point.enabled=false;store.save(points);
  try{
   try(androidx.test.core.app.ActivityScenario<MainActivity> scenario=androidx.test.core.app.ActivityScenario.launch(MainActivity.class)){
    scenario.onActivity(a->a.startForegroundService(new android.content.Intent(a,MonitorService.class).setAction("user_start")));
    waitRunning(true);assertTrue(store.prefs.getBoolean("diagMonitorExpected",false));
   }
   SystemClock.sleep(1200);assertTrue("Closing the app screen must preserve monitoring",MonitorService.running);
   c.startService(new android.content.Intent(c,MonitorService.class).setAction("stop"));waitRunning(false);
   SystemClock.sleep(1200);assertFalse(MonitorService.running);assertFalse(store.prefs.getBoolean("diagMonitorExpected",true));
  }finally{store.prefs.edit().putBoolean("diagMonitorExpected",false).commit();c.stopService(new android.content.Intent(c,MonitorService.class));}
 }
 static void waitRunning(boolean expected){long end=SystemClock.elapsedRealtime()+5000;while(MonitorService.running!=expected&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(100);assertEquals(expected,MonitorService.running);}
 static Location fix(double lat,double lon,float accuracy,long age){Location l=new Location("gps");l.setLatitude(lat);l.setLongitude(lon);l.setAccuracy(accuracy);l.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos()-age*1000000L);return l;}
}
