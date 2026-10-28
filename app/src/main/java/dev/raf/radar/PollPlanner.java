package dev.raf.radar;

import java.util.*;

/** A single global budget; adding points or tapping refresh cannot bypass it. */
public final class PollPlanner {
  private final Map<String, Long> due = new HashMap<>();
  private long nextGlobal = 0;
  private long authUntil = 0;
  private final Map<String,Long> blocked = new HashMap<>(), recentFailures = new HashMap<>();
  private final Map<String,Integer> failures = new HashMap<>();
  private int cursor = 0;
  private boolean lastWasPriority;

  public synchronized String next(List<String> eligible, long now, long interval) {
    return next(eligible, now, interval, null);
  }

  public synchronized String next(List<String> eligible, long now, long interval, String priority) {
    return next(eligible,now,interval,priority,interval);
  }

  public synchronized String next(List<String> eligible,long now,long interval,String priority,long priorityInterval) {
    if (now < Math.max(nextGlobal,authUntil) || eligible.isEmpty()) return null;
    long gap=priorityInterval<30000?10000:15000;
    boolean otherDue=false;for(String id:eligible)if(!id.equals(priority)&&now>=due.getOrDefault(id,0L)){otherDue=true;break;}
    if ((!lastWasPriority||!otherDue) && priority != null && eligible.contains(priority)
        && now >= due.getOrDefault(priority, 0L)) {
      due.put(priority, now + Math.max(10000, priorityInterval));
      nextGlobal = now + gap;
      lastWasPriority=true;return priority;
    }
    for (int n = 0; n < eligible.size(); n++) {
      String id = eligible.get(Math.floorMod(cursor++, eligible.size()));
      if (now >= due.getOrDefault(id, 0L) && !(otherDue&&lastWasPriority&&id.equals(priority))) {
        due.put(id, now + Math.max(30000, interval));
        nextGlobal = now + gap;
        lastWasPriority=id.equals(priority);return id;
      }
    }
    return null;
  }

  public static final long POINT_INTERVAL_MS=60000L;
  public static final long OWN_INTERVAL_MS=10000L;

  /** One request at a time. Earliest deadline wins; a small lead absorbs timer/IO jitter. */
  public synchronized String nextMinute(List<String> eligible,long now,String own) {
    due.keySet().retainAll(eligible);
    if(now<Math.max(nextGlobal,authUntil)||eligible.isEmpty())return null;
    int saved=eligible.size()-(eligible.contains(own)?1:0);
    int slots=saved+(eligible.contains(own)?6:0);
    long gap=Math.max(1000,Math.min(10000,(50000L/Math.max(1,slots)/1000)*1000));
    String selected=null;long earliest=Long.MAX_VALUE;
    for(String id:eligible){
      if(now<blocked.getOrDefault(id,0L))continue;
      long deadline=due.getOrDefault(id,0L);
      long lead=id.equals(own)?0:15000;
      if(deadline>now+lead)continue;
      if(deadline<earliest||(deadline==earliest&&id.equals(own))){selected=id;earliest=deadline;}
    }
    if(selected==null)return null;
    due.put(selected,now+(selected.equals(own)?OWN_INTERVAL_MS:POINT_INTERVAL_MS));
    nextGlobal=now+gap;
    return selected;
  }

  public static boolean needed(Point p, boolean browsing, String overlayPoint) {
    return p.enabled && !p.tariffs.isEmpty();
  }

  public synchronized void expedite(String id) {
    due.remove(id);
  }

  public synchronized void refresh() {
    due.clear();
  }

  /** Transient failures cool down only their point; repeated failures across points pause the service. */
  public synchronized long failed(String id,int http,long now) {
    if(http==401||http==403){authUntil=Math.max(authUntil,now+900000);return 900000;}
    if(http==429){backoff(now,900000);return 900000;}
    int n=Math.min(5,failures.getOrDefault(id,0)+1);failures.put(id,n);
    long delay=Math.min(900000,60000L*(1L<<(n-1)));blocked.put(id,now+delay);
    if(http==0||http>=500){
      recentFailures.entrySet().removeIf(e->now-e.getValue()>60000);
      recentFailures.put(id,now);
      if(recentFailures.size()>=3){backoff(now,60000);recentFailures.clear();}
    }
    return delay;
  }
  public synchronized void succeeded(String id){failures.remove(id);blocked.remove(id);recentFailures.clear();}
  public synchronized long globalPause(long now){return Math.max(0,Math.max(nextGlobal,authUntil)-now);}

  /** A successful authenticated probe clears only the authentication pause. */
  public synchronized void authorizationRestored(long now) {
    if(authUntil==0)return;
    authUntil=0;
    nextGlobal=Math.max(nextGlobal,now+1000);
  }

  public synchronized void backoff(long now, long delay) {
    nextGlobal = Math.max(nextGlobal, now + delay);
  }
}
