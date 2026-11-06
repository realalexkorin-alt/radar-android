package dev.raf.radar;
import java.util.*;
/** Foreground map requests fill gaps; the own-location deadline always wins. */
final class LivePollPlanner {
 static final long INTERVAL=10000, MAP_BUDGET=5000, GAP=1000;
 private long ownDue, mapDue, nextRequest; private int cursor;
 synchronized String nextMinute(List<String> ids,long now,String own){
  if(now<nextRequest||ids.isEmpty())return null;
  if(ids.contains(own)&&now>=ownDue){ownDue=now+INTERVAL;nextRequest=now+GAP;return own;}
  if(now<mapDue||(ids.contains(own)&&ownDue-now<MAP_BUDGET+GAP))return null;
  List<String> saved=new ArrayList<>(ids);saved.remove(own);if(saved.isEmpty())return null;
  String id=saved.get(Math.floorMod(cursor++,saved.size()));mapDue=now+INTERVAL;nextRequest=now+GAP;return id;
 }
 static boolean needed(Point p,boolean mapVisible,String ignored){return p.enabled&&!p.tariffs.isEmpty()&&(p.dynamic||mapVisible);}
 synchronized void mapHidden(){cursor=0;mapDue=0;}
 synchronized void expedite(String id){} // manual actions cannot bypass the ten-second deadline
 synchronized void succeeded(String id){}
 synchronized long failed(String id,int http,long now){return Math.max(0,("me".equals(id)?ownDue:mapDue)-now);}
 synchronized void backoff(long now,long delay){}
 synchronized long globalPause(long now){return 0;}
 synchronized void authorizationRestored(long now){}
 synchronized void transportRestored(long now){}
 synchronized org.json.JSONObject diagnosticState(List<String> ids,long now,String own){org.json.JSONObject j=new org.json.JSONObject();try{j.put("eligible",ids.size()).put("ownWaitMs",Math.max(0,ownDue-now)).put("mapWaitMs",Math.max(0,mapDue-now)).put("reason","timed_foreground_map");}catch(org.json.JSONException ignored){}return j;}
}
