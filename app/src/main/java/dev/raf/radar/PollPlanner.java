package dev.raf.radar;

import java.util.*;

/** A single global budget; adding points or tapping refresh cannot bypass it. */
public final class PollPlanner {
  private final Map<String, Long> due = new HashMap<>();
  private long nextGlobal = 0;
  private long authUntil = 0, transientUntil = 0;
  private final Map<String,Long> blocked = new HashMap<>(), recentFailures = new HashMap<>();
  private final Map<String,Integer> failures = new HashMap<>(), failedHttp = new HashMap<>();
  private int cursor = 0;
  private boolean lastWasPriority;

  public synchronized String next(List<String> eligible, long now, long interval) {
    return next(eligible, now, interval, null);
  }

  public synchronized String next(List<String> eligible, long now, long interval, String priority) {
    return next(eligible,now,interval,priority,interval);
  }

  public synchronized String next(List<String> eligible,long now,long interval,String priority,long priorityInterval) {
    if (now < Math.max(nextGlobal,Math.max(authUntil,transientUntil)) || eligible.isEmpty()) return null;
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
  public static final long OWN_INTERVAL_MS=5000L;

  /** One request at a time. Earliest deadline wins; a small lead absorbs timer/IO jitter. */
  public synchronized String nextMinute(List<String> eligible,long now,String own) {
    due.keySet().retainAll(eligible);
    if(now<Math.max(nextGlobal,Math.max(authUntil,transientUntil))||eligible.isEmpty())return null;
    // Own location always wins once due, even when saved points are overdue.
    String selected=null;long earliest=Long.MAX_VALUE;
    if(eligible.contains(own)&&now>=blocked.getOrDefault(own,0L)&&now>=due.getOrDefault(own,0L))selected=own;
    if(selected==null){
      // Reserve enough time for one bounded map request before the next widget slot.
      if(eligible.contains(own)&&now>=blocked.getOrDefault(own,0L)&&due.getOrDefault(own,0L)-now<2750)return null;
      for(String id:eligible){
        if(id.equals(own)||now<blocked.getOrDefault(id,0L))continue;
        long deadline=due.getOrDefault(id,0L);
        if(deadline>now+15000)continue;
        if(deadline<earliest){selected=id;earliest=deadline;}
      }
    }
    if(selected==null)return null;
    due.put(selected,now+(selected.equals(own)?OWN_INTERVAL_MS:POINT_INTERVAL_MS));
    nextGlobal=now+1000;
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
    if(http==401||http==403){authUntil=Math.max(authUntil,now+30000);return 30000;}
    if(http==429){backoff(now,900000);return 900000;}
    failedHttp.put(id,http);
    int n=Math.min(5,failures.getOrDefault(id,0)+1);failures.put(id,n);
    long delay="me".equals(id)&&http==0?Math.min(30000,5000L*(1L<<(n-1))):Math.min(900000,60000L*(1L<<(n-1)));blocked.put(id,now+delay);
    if(http==0||http>=500){
      recentFailures.entrySet().removeIf(e->now-e.getValue()>60000);
      recentFailures.put(id,now);
      if(recentFailures.size()>=3){transientUntil=Math.max(transientUntil,now+60000);recentFailures.clear();}
    }
    return delay;
  }
  public synchronized void succeeded(String id){failures.remove(id);failedHttp.remove(id);blocked.remove(id);recentFailures.clear();}
  public synchronized long globalPause(long now){return Math.max(0,Math.max(nextGlobal,Math.max(authUntil,transientUntil))-now);}

  /** A verified manual probe recovers auth/transport pauses; rate limits and point HTTP errors remain. */
  public synchronized void authorizationRestored(long now) {
    boolean recovered=authUntil>0||transientUntil>0;
    authUntil=0;transientUntil=0;
    for(String id:new ArrayList<>(failedHttp.keySet()))if(failedHttp.get(id)==0){blocked.remove(id);failures.remove(id);failedHttp.remove(id);due.remove(id);recovered=true;}
    recentFailures.clear();
    if(recovered)nextGlobal=Math.max(nextGlobal,now+1000);
  }

  public synchronized void backoff(long now, long delay) {
    nextGlobal = Math.max(nextGlobal, now + delay);
  }
  synchronized org.json.JSONObject diagnosticState(List<String> eligible,long now,String own){
    org.json.JSONObject out=new org.json.JSONObject();int overdue=0,blockedCount=0;for(String id:eligible){if(now>due.getOrDefault(id,now))overdue++;if(now<blocked.getOrDefault(id,0L))blockedCount++;}
    int saved=eligible.size()-(eligible.contains(own)?1:0),slots=saved+(eligible.contains(own)?6:0);
    String reason=now<authUntil?"auth":now<transientUntil?"transient":now<nextGlobal?(nextGlobal-now>10000?"rate_limit":"budget"):eligible.isEmpty()?"empty":blockedCount==eligible.size()?"point_backoff":"deadline";
    try{out.put("eligible",eligible.size()).put("saved",saved).put("overdue",overdue).put("blocked",blockedCount).put("gapMs",1000).put("ownIntervalMs",OWN_INTERVAL_MS).put("waitMs",globalPause(now)).put("reason",reason);}catch(org.json.JSONException ignored){}return out;
  }
}

