package dev.raf.radar;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public class PollPlannerTest {
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
