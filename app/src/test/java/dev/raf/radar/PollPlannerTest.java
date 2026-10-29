package dev.raf.radar;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public class PollPlannerTest {
 @Test public void verifiedProbeRecoversBrokenBrowserPointsButNotRateLimit(){
  PollPlanner p=new PollPlanner();java.util.List<String> ids=java.util.Arrays.asList("me","a","b");
  p.failed("me",0,100000);p.failed("a",0,101000);p.failed("b",0,102000);
  assertNull(p.nextMinute(ids,110000,"me"));
  p.authorizationRestored(110000);
  assertNotNull(p.nextMinute(ids,111000,"me"));assertNull(p.nextMinute(ids,111001,"me"));
  p.failed("me",429,120000);p.failed("a",0,121000);p.authorizationRestored(122000);
  assertNull(p.nextMinute(ids,1019999,"me"));assertNotNull(p.nextMinute(ids,1020000,"me"));
 }

  @Test public void successfulLoginProbeResumesAuthPausedQueueWithoutBurst(){
    PollPlanner p=new PollPlanner();List<String> ids=Arrays.asList("me","saved");
    assertEquals("me",p.nextMinute(ids,100000,"me"));
    p.failed("me",401,101000);
    assertNull(p.nextMinute(ids,150000,"me"));
    p.authorizationRestored(150000);
    assertNull(p.nextMinute(ids,150999,"me"));
    assertNotNull(p.nextMinute(ids,151000,"me"));
    assertNull(p.nextMinute(ids,151001,"me"));
  }
  @Test public void loginSuccessCannotClearRateLimitOrPointFailure(){
    PollPlanner p=new PollPlanner();List<String> ids=Arrays.asList("me","saved");
    p.failed("saved",500,100000);p.failed("me",401,101000);p.failed("me",429,102000);
    p.authorizationRestored(110000);
    assertNull(p.nextMinute(ids,1001999,"me"));
    assertNotNull(p.nextMinute(ids,1002000,"me"));
    PollPlanner q=new PollPlanner();q.failed("saved",500,100000);q.failed("me",401,101000);
    q.authorizationRestored(110000);
    assertEquals("me",q.nextMinute(ids,111000,"me"));
    assertNull(q.nextMinute(Collections.singletonList("saved"),120000,"me"));
  }


  @Test public void oneBrokenPointDoesNotPauseOthersAndRefreshCannotBypassCooldown(){
    PollPlanner p=new PollPlanner();List<String> ids=Arrays.asList("a","b");
    assertEquals("a",p.nextMinute(ids,100000,"me"));
    assertEquals(60000,p.failed("a",500,100000));
    p.expedite("a");p.refresh();
    assertEquals("b",p.nextMinute(ids,110000,"me"));
    p.succeeded("b");
    assertEquals("a",p.nextMinute(ids,160000,"me"));
  }
  @Test public void broadFailuresAndRateLimitStillPauseAllRequests(){
    PollPlanner p=new PollPlanner();List<String> ids=Arrays.asList("a","b","c","d");
    p.failed("a",500,100000);p.failed("b",504,110000);p.failed("c",0,120000);
    assertNull(p.nextMinute(ids,179999,"me"));
    assertEquals("a",p.nextMinute(ids,180000,"me"));
    p.failed("a",429,180000);p.refresh();p.expedite("d");
    assertNull(p.nextMinute(ids,1079999,"me"));
    assertNotNull(p.nextMinute(ids,1080000,"me"));
  }
  @Test public void retriesGrowOnlyForTheFailingPointAndResetAfterSuccess(){
    PollPlanner p=new PollPlanner();
    assertEquals(60000,p.failed("a",500,100000));
    assertEquals(120000,p.failed("a",500,160000));
    assertEquals(60000,p.failed("b",500,170000));
    p.succeeded("a");
    assertEquals(60000,p.failed("a",500,300000));
  }

  @Test public void fiveAndTenSavedPointsMeetMinuteCadenceWithTwoSecondResponses(){
    for(int count:new int[]{5,10}){
      PollPlanner p=new PollPlanner();List<String> ids=new ArrayList<>();ids.add("me");
      for(int i=0;i<count;i++)ids.add("saved"+i);
      Map<String,Long> last=new HashMap<>();Map<String,Integer> calls=new HashMap<>();
      for(long now=100000;now<3700000;){
        String id=p.nextMinute(ids,now,"me");
        if(id==null){now+=1000;continue;}
        if(!id.equals("me")&&last.containsKey(id)){
          long gap=now-last.get(id);assertTrue(id+" delayed "+gap,gap<=60000);assertTrue(gap>=45000);
        }
        last.put(id,now);calls.put(id,calls.getOrDefault(id,0)+1);
        assertNull(p.nextMinute(ids,now,"me"));
        now+=3000; // a two-second response plus the service's one-second idle delay
      }
      for(String id:ids)assertTrue(id+" starved",calls.getOrDefault(id,0)>50);
    }
  }
  @Test public void minuteCycleKeepsBackoffAndResumesWithoutBurst(){
    PollPlanner p=new PollPlanner();List<String> ids=Arrays.asList("me","a","b");
    assertEquals("me",p.nextMinute(ids,100000,"me"));p.backoff(100000,900000);p.expedite("me");
    assertNull(p.nextMinute(ids,999999,"me"));assertNotNull(p.nextMinute(ids,1000000,"me"));
    assertNull(p.nextMinute(ids,1000001,"me"));
  }
  @Test public void addingRemovingPointsAndLosingGpsDoNotResetOtherDeadlines(){
    PollPlanner p=new PollPlanner();assertEquals("me",p.nextMinute(Arrays.asList("me","a"),100000,"me"));
    assertEquals("a",p.nextMinute(Arrays.asList("a"),110000,"me"));
    assertEquals("b",p.nextMinute(Arrays.asList("a","b"),120000,"me"));
    assertNull(p.nextMinute(Arrays.asList("a","b"),130000,"me"));
    assertNull(p.nextMinute(Collections.emptyList(),140000,"me"));
  }
  @Test public void fixedPointDisableSurvivesReload()throws Exception{
    Point fixed=new Point("a","a",55,61,false);fixed.enabled=false;
    assertFalse(Point.from(fixed.json()).enabled);
    fixed.dynamic=true;assertFalse(Point.from(fixed.json()).enabled);
  }

  @Test public void fastMainDoesNotStarveSavedPoints(){PollPlanner p=new PollPlanner();List<String> ids=Arrays.asList("me","a","b");assertEquals("me",p.next(ids,100000,120000,"me",10000));assertEquals("a",p.next(ids,110000,120000,"me",10000));assertEquals("me",p.next(ids,120000,120000,"me",10000));assertEquals("b",p.next(ids,130000,120000,"me",10000));assertEquals("me",p.next(ids,140000,120000,"me",10000));}

  @Test public void mainWidgetTenSecondTargetPreservesBackoffAndSavedInterval(){
    PollPlanner p=new PollPlanner();List<String> main=Collections.singletonList("me");
    assertEquals("me",p.next(main,100000,120000,"me",10000));
    assertNull(p.next(main,109999,120000,"me",10000));
    assertEquals("me",p.next(main,110000,120000,"me",10000));
    p.backoff(110000,900000);p.refresh();assertNull(p.next(main,1009999,120000,"me",10000));
    assertEquals("me",p.next(main,1010000,120000,"me",10000));
    PollPlanner saved=new PollPlanner();List<String> onlySaved=Collections.singletonList("saved");
    assertEquals("saved",saved.next(onlySaved,100000,120000,"me",10000));
    assertNull(saved.next(onlySaved,110000,120000,"me",10000));
    assertEquals("saved",saved.next(onlySaved,220000,120000,"me",10000));
  }

  @Test public void dynamicFirstEvenWhenCursorIsOnSavedPoint() {
    PollPlanner p = new PollPlanner();
    List<String> ids = Arrays.asList("saved", "me");
    assertEquals("me", p.next(ids, 100000, 30000, "me"));
    assertEquals("saved", p.next(ids, 115000, 30000, "me"));
    assertEquals("me", p.next(ids, 130000, 30000, "me"));
  }
  @Test public void priorityDoesNotBypassBudgetOrBackoff() {
    PollPlanner p = new PollPlanner(); List<String> ids = Arrays.asList("saved", "me");
    p.next(ids, 100000, 120000, "me"); p.expedite("me");
    assertNull(p.next(ids, 100001, 120000, "me"));
    p.backoff(100000, 900000); p.expedite("me");
    assertNull(p.next(ids, 999999, 120000, "me"));
  }
  @Test public void savedPointsIncludedInBackground() {
    Point saved = new Point("saved", "saved", 55, 61, false);
    assertTrue(PollPlanner.needed(saved, false, ""));
    assertTrue(PollPlanner.needed(saved, true, ""));
    assertTrue(PollPlanner.needed(saved, false, "saved"));
    assertTrue(PollPlanner.needed(saved, false, "other"));
    saved.notify = true; assertTrue(PollPlanner.needed(saved, false, ""));
    saved.tariffs.clear(); assertFalse(PollPlanner.needed(saved, true, "saved"));
  }
  @Test public void disabledSavedPointNeverJoinsQueue() {
    Point point = new Point("saved", "saved", 55, 61, false);
    point.notify = true; point.enabled = false;
    assertFalse(PollPlanner.needed(point, true, "saved"));
    assertFalse(PollPlanner.needed(point, false, ""));
    point.enabled=true;assertTrue(PollPlanner.needed(point,false,""));
  }
  @Test public void dynamicAlwaysNeeded() {
    assertTrue(PollPlanner.needed(new Point("me", "me", 0, 0, true), false, ""));
  }
  @Test
  public void thirtyPointsFairAndSpaced() {
    PollPlanner p = new PollPlanner();
    List<String> ids = new ArrayList<>();
    for (int i = 0; i < 30; i++) ids.add("p" + i);
    Set<String> seen = new HashSet<>();
    long now = 100000;
    for (int i = 0; i < 30; i++) {
      String id = p.next(ids, now, 120000);
      assertNotNull(id);
      assertTrue(seen.add(id));
      assertNull(p.next(ids, now + 14999, 120000));
      now += 15000;
    }
    assertEquals(30, seen.size());
    assertEquals("p0", p.next(ids, now, 120000));
  }

  @Test
  public void refreshCannotBypassGlobalBudget() {
    PollPlanner p = new PollPlanner();
    List<String> ids = Arrays.asList("me");
    assertEquals("me", p.next(ids, 100000, 120000));
    p.refresh();
    assertNull(p.next(ids, 100001, 120000));
    assertEquals("me", p.next(ids, 115000, 120000));
  }

  @Test
  public void pointIntervalRespected() {
    PollPlanner p = new PollPlanner();
    List<String> ids = Arrays.asList("me");
    p.next(ids, 100000, 120000);
    assertNull(p.next(ids, 115000, 120000));
    assertEquals("me", p.next(ids, 220000, 120000));
  }

  @Test
  public void backoffSurvivesRefresh() {
    PollPlanner p = new PollPlanner();
    List<String> ids = Arrays.asList("a");
    p.next(ids, 100000, 120000);
    p.backoff(100001, 900000);
    p.refresh();
    assertNull(p.next(ids, 100001 + 899999, 120000));
    assertEquals("a", p.next(ids, 100001 + 900000, 120000));
  }

  @Test
  public void noEligibleGpsNoRequest() {
    assertNull(new PollPlanner().next(Collections.emptyList(), 100000, 120000));
  }

  @Test
  public void removedPointDoesNotBlockOthers() {
    PollPlanner p = new PollPlanner();
    p.next(Arrays.asList("a", "b"), 100000, 120000);
    assertEquals("b", p.next(Arrays.asList("b"), 115000, 120000));
  }
}
