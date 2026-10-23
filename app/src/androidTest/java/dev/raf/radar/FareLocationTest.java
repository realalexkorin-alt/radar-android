package dev.raf.radar;
import android.content.Context;import androidx.test.platform.app.InstrumentationRegistry;import android.location.Location;import android.os.SystemClock;import org.junit.Test;import static org.junit.Assert.*;import java.util.*;
public class FareLocationTest {
 @Test public void gradualMovementInvalidatesOldCoordinateFare(){Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();Store s=new Store(c);s.prefs.edit().clear().commit();long base=System.currentTimeMillis()-15000;
  assertTrue(LocationState.accept(s,fix(55,61,15000)));s.readings("me",Collections.singletonList(new Store.Reading("econom",260,null,base,"fixture")));assertNotNull(s.widgetReading());
  assertTrue(LocationState.accept(s,fix(55.001,61,10000)));assertNotNull(s.widgetReading());assertTrue(LocationState.accept(s,fix(55.002,61,5000)));assertNotNull(s.widgetReading());
  assertTrue(LocationState.accept(s,fix(55.003,61,0)));assertNull(s.widgetReading());assertEquals(base,s.lastSuccessfulReading("me"));
  s.readings("me",Collections.singletonList(new Store.Reading("econom",0,null,System.currentTimeMillis(),"fixture")));assertEquals(Integer.valueOf(0),s.widgetReading().rub);
 }
 static Location fix(double lat,double lon,long age){Location l=new Location("gps");l.setLatitude(lat);l.setLongitude(lon);l.setAccuracy(5);l.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos()-age*1000000L);return l;}
}
