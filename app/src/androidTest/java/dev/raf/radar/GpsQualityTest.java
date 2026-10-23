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
 static Location fix(double lat,double lon,float accuracy,long age){Location l=new Location("gps");l.setLatitude(lat);l.setLongitude(lon);l.setAccuracy(accuracy);l.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos()-age*1000000L);return l;}
}
