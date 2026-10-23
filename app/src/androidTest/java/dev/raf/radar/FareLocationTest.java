package dev.raf.radar;
import android.content.Context;import androidx.test.platform.app.InstrumentationRegistry;import android.location.Location;import android.os.SystemClock;import org.junit.Test;import static org.junit.Assert.*;import java.util.*;
public class FareLocationTest {
 @Test public void concurrentFriendRefreshUsesOneRequest()throws Exception{
  Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();Store s=new Store(c);s.prefs.edit().clear().commit();
  java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();java.util.concurrent.CountDownLatch replies=new java.util.concurrent.CountDownLatch(3);AccountClient.Transport original=AccountClient.transport;
  try{AccountClient.transport=(body,token)->{calls.incrementAndGet();Thread.sleep(100);return new org.json.JSONObject().put("user",new org.json.JSONObject().put("id",999).put("name","Fixture")).put("friends",new org.json.JSONArray());};
   for(int i=0;i<3;i++)AccountClient.refresh(c,rows->{assertNotNull(rows);replies.countDown();});assertTrue(replies.await(5,java.util.concurrent.TimeUnit.SECONDS));assertEquals(1,calls.get());
  }finally{AccountClient.transport=original;AccountClient.clear(s);}
 }

 @Test public void gradualMovementInvalidatesOldCoordinateFare(){Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();Store s=new Store(c);s.prefs.edit().clear().commit();long base=System.currentTimeMillis()-15000;
  assertTrue(LocationState.accept(s,fix(55,61,15000)));s.readings("me",Collections.singletonList(new Store.Reading("econom",260,null,base,"fixture")));assertNotNull(s.widgetReading());
  assertTrue(LocationState.accept(s,fix(55.001,61,10000)));assertNotNull(s.widgetReading());assertTrue(LocationState.accept(s,fix(55.002,61,5000)));assertNotNull(s.widgetReading());
  assertTrue(LocationState.accept(s,fix(55.003,61,0)));assertNull(s.widgetReading());assertEquals(base,s.lastSuccessfulReading("me"));
  s.readings("me",Collections.singletonList(new Store.Reading("econom",0,null,System.currentTimeMillis(),"fixture")));assertEquals(Integer.valueOf(0),s.widgetReading().rub);
 }
 static Location fix(double lat,double lon,long age){Location l=new Location("gps");l.setLatitude(lat);l.setLongitude(lon);l.setAccuracy(5);l.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos()-age*1000000L);return l;}
}
