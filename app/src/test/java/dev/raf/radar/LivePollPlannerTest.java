package dev.raf.radar;
import org.junit.Test;import static org.junit.Assert.*;import java.util.*;
public class LivePollPlannerTest {
 @Test public void backgroundOnlyOwnAtTenSeconds(){LivePollPlanner p=new LivePollPlanner();List<String> own=Collections.singletonList("me");assertEquals("me",p.nextMinute(own,100000,"me"));for(long n=100001;n<110000;n+=100)assertNull(p.nextMinute(own,n,"me"));assertEquals("me",p.nextMinute(own,110000,"me"));}
 @Test public void mapFillsGapsWithoutStealingOwnDeadline(){LivePollPlanner p=new LivePollPlanner();List<String> ids=Arrays.asList("a","me","b");assertEquals("me",p.nextMinute(ids,100000,"me"));assertEquals("a",p.nextMinute(ids,101000,"me"));assertNull(p.nextMinute(ids,108000,"me"));assertEquals("me",p.nextMinute(ids,110000,"me"));assertEquals("b",p.nextMinute(ids,111000,"me"));assertEquals("me",p.nextMinute(ids,120000,"me"));}
 @Test public void repeatedTransportFailuresBackOffOnlyBrokenPointAndResetAfterSuccess(){
  LivePollPlanner p=new LivePollPlanner();List<String> ids=Arrays.asList("me","a");
  assertEquals("me",p.nextMinute(ids,100000,"me"));
  assertEquals(10000,p.failed("me",0,101000));
  assertEquals("a",p.nextMinute(ids,101000,"me"));
  assertNull(p.nextMinute(Collections.singletonList("me"),110000,"me"));
  assertEquals("me",p.nextMinute(ids,111000,"me"));
  assertEquals(20000,p.failed("me",500,112000));
  assertEquals("a",p.nextMinute(ids,112000,"me"));
  assertNull(p.nextMinute(Collections.singletonList("me"),131999,"me"));
  assertEquals("me",p.nextMinute(ids,132000,"me"));
  p.succeeded("me");assertEquals(10000,p.failed("me",0,133000));
 }
 @Test public void retryDelayIsBoundedAndNetworkRecoveryKeepsNormalCadence(){
  LivePollPlanner p=new LivePollPlanner();List<String> own=Collections.singletonList("me");
  assertEquals("me",p.nextMinute(own,100000,"me"));
  for(int n=0;n<8;n++)assertTrue(p.failed("me",500,101000)<=60000);
  p.transportRestored(102000);assertNull(p.nextMinute(own,109999,"me"));
  assertEquals("me",p.nextMinute(own,110000,"me"));
 }
 @Test public void manualRefreshCannotAccelerateOwn(){LivePollPlanner p=new LivePollPlanner();List<String> ids=Collections.singletonList("me");assertEquals("me",p.nextMinute(ids,100000,"me"));p.expedite("me");assertNull(p.nextMinute(ids,109999,"me"));}
 @Test public void hiddenMapExcludesSavedEvenWhenWidgetOrNotifySelectsIt(){Point a=new Point("a","a",55,61,false);a.notify=true;assertFalse(LivePollPlanner.needed(a,false,"a"));assertTrue(LivePollPlanner.needed(a,true,"me"));a.enabled=false;assertFalse(LivePollPlanner.needed(a,true,"a"));Point own=new Point("me","me",0,0,true);assertTrue(LivePollPlanner.needed(own,false,"a"));}
 @Test public void hidingMapDropsCycleWithoutResettingOwn(){LivePollPlanner p=new LivePollPlanner();List<String> ids=Arrays.asList("me","a","b");assertEquals("me",p.nextMinute(ids,100000,"me"));assertEquals("a",p.nextMinute(ids,101000,"me"));p.mapHidden();assertNull(p.nextMinute(Collections.singletonList("me"),102000,"me"));assertEquals("me",p.nextMinute(ids,110000,"me"));assertEquals("a",p.nextMinute(ids,111000,"me"));}
}
