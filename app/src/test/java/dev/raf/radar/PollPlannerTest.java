package dev.raf.radar;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public class PollPlannerTest {
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
  @Test public void savedPointsOnlyWhenNeeded() {
    Point saved = new Point("saved", "saved", 55, 61, false);
    assertFalse(PollPlanner.needed(saved, false, ""));
    assertTrue(PollPlanner.needed(saved, true, ""));
    assertTrue(PollPlanner.needed(saved, false, "saved"));
    assertFalse(PollPlanner.needed(saved, false, "other"));
    saved.notify = true; assertTrue(PollPlanner.needed(saved, false, ""));
    saved.tariffs.clear(); assertFalse(PollPlanner.needed(saved, true, "saved"));
  }
  @Test public void disabledPointCannotPollEvenWithNotificationOrWidget() {
    Point point = new Point("saved", "saved", 55, 61, false);
    point.notify = true; point.enabled = false;
    assertFalse(PollPlanner.needed(point, true, "saved"));
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
