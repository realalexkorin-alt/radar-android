package dev.raf.radar;
import java.util.*;
/** Foreground map requests fill gaps; the own-location deadline always wins. */
final class LivePollPlanner {
 static final long INTERVAL=10000, MAP_BUDGET=5000, GAP=1000;
 private long ownDue, mapDue, nextRequest; private int cursor;
 private final Map<String,Long> retryAt=new HashMap<>();
 private final Map<String,Integer> failures=new HashMap<>();
 synchronized String nextMinute(List<String> ids,long now,String own){
  if(now<nextRequest||ids.isEmpty())return null;
  if(ids.contains(own)&&now>=Math.max(ownDue,retryAt.getOrDefault(own,0L))){ownDue=now+INTERVAL;nextRequest=now+GAP;return own;}
  if(now<mapDue||(ids.contains(own)&&Math.max(ownDue,retryAt.getOrDefault(own,0L))-now<MAP_BUDGET+GAP))return null;
  List<String> saved=new ArrayList<>(ids);saved.remove(own);saved.removeIf(id->now<retryAt.getOrDefault(id,0L));if(saved.isEmpty())return null;
  String id=saved.get(Math.floorMod(cursor++,saved.size()));mapDue=now+INTERVAL;nextRequest=now+GAP;return id;
 }
 static boolean needed(Point p,boolean mapVisible,String ignored){return p.enabled&&!p.tariffs.isEmpty()&&(p.dynamic||mapVisible);}
 synchronized void mapHidden(){cursor=0;mapDue=0;}
 synchronized void expedite(String id){} // manual actions cannot bypass the ten-second deadline
 synchronized void succeeded(String id){retryAt.remove(id);failures.remove(id);}
 synchronized long failed(String id,int http,long now){
  if(http==0||http>=500){
   int count=Math.min(4,failures.getOrDefault(id,0)+1);failures.put(id,count);
   long delay=Math.min(60000,INTERVAL*(1L<<(count-1)));retryAt.put(id,now+delay);return delay;
  }
  return Math.max(0,("me".equals(id)?ownDue:mapDue)-now);
 }
 synchronized void backoff(long now,long delay){}
 synchronized long globalPause(long now){return 0;}
 synchronized void authorizationRestored(long now){retryAt.clear();failures.clear();}
 synchronized void transportRestored(long now){retryAt.clear();failures.clear();}
 synchronized org.json.JSONObject diagnosticState(List<String> ids,long now,String own){org.json.JSONObject j=new org.json.JSONObject();try{j.put("eligible",ids.size()).put("ownWaitMs",Math.max(0,ownDue-now)).put("mapWaitMs",Math.max(0,mapDue-now)).put("reason","timed_foreground_map");}catch(org.json.JSONException ignored){}return j;}
}
