package dev.raf.radar;
import android.content.*;import android.os.SystemClock;import androidx.test.platform.app.InstrumentationRegistry;import org.junit.*;import static org.junit.Assert.*;import java.util.*;import java.util.concurrent.*;
public class ForegroundPollingTest {
 @Test public void cancelledSavedRequestStopsWithoutReloadOrLoginReset()throws Exception{
  ConnectionRecoveryTest f=new ConnectionRecoveryTest();f.setup();try{java.util.concurrent.atomic.AtomicInteger loads=f.fixture(false);java.util.concurrent.atomic.AtomicBoolean hidden=new java.util.concurrent.atomic.AtomicBoolean();
   ConnectionRecoveryTest.main(()->BrowserFare.view(f.c).evaluateJavascript("window.fetch=(u,o)=>new Promise((resolve,reject)=>o.signal.addEventListener('abort',()=>reject(new DOMException('cancelled','AbortError'))));true",null));
   Future<?> pending=f.worker.submit(()->{try{return BrowserFare.fetch(f.c,new org.json.JSONObject(),Collections.emptyMap(),3000,hidden::get);}catch(Exception e){throw new CompletionException(e);}});
   SystemClock.sleep(400);hidden.set(true);try{pending.get(3,TimeUnit.SECONDS);fail();}catch(ExecutionException expected){assertTrue(expected.getCause().getCause() instanceof CancellationException);}
   assertEquals(1,loads.get());assertFalse(FareConnection.needsLogin(f.c));
  }finally{f.cleanup();}
 }
 @Test public void oldPauseClearedOnceAndNewServerPauseSurvives(){Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();Store s=new Store(c);s.prefs.edit().remove("timedPolling67").putLong("fareRateUntil",System.currentTimeMillis()+900000).commit();assertEquals(0,FareConnection.remaining(c));FareConnection.limited(c,"120");assertTrue(FareConnection.remaining(c)>115000);assertTrue(FareConnection.remaining(c)>115000);s.prefs.edit().remove("fareRateUntil").commit();}
 @Test public void analyticsWorkersCannotSendOrCapture()throws Exception{Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();Statistics.Transport original=Statistics.transport;try{Statistics.transport=(b,t)->{calls.incrementAndGet();throw new AssertionError("upload");};Statistics.init(c);Statistics.syncSources(c);Statistics.capture(c,new Point("a","a",55,61,false),Collections.emptyList());Statistics.flush();assertEquals(0,calls.get());assertFalse(Statistics.COLLECTION_ENABLED);}finally{Statistics.transport=original;}}
 @Test public void visibilityLeaseExpiresAndHiddenMapHasNoEligibility(){Store s=new Store(InstrumentationRegistry.getInstrumentation().getTargetContext());s.prefs.edit().putLong("viewingUntil",System.currentTimeMillis()+15000).commit();assertTrue(MapVisibility.visible(s));s.prefs.edit().putLong("viewingUntil",0).commit();assertFalse(MapVisibility.visible(s));s.prefs.edit().putLong("viewingUntil",System.currentTimeMillis()-1).commit();assertFalse(MapVisibility.visible(s));}
}
