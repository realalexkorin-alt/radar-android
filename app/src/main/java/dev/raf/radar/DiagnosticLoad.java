package dev.raf.radar;
import java.util.*;import org.json.*;
/** Measured local request load, not a claimed provider limit. */
final class DiagnosticLoad {
 final ArrayDeque<long[]> rows=new ArrayDeque<>();
 synchronized void completed(long now,long duration,boolean ok){rows.addLast(new long[]{now,duration,ok?1:0});while(!rows.isEmpty()&&(rows.size()>10000||now-rows.peekFirst()[0]>3600000))rows.removeFirst();}
 synchronized JSONObject snapshot(long now){JSONObject out=new JSONObject();int attempts=0,success=0;List<Long> durations=new ArrayList<>();for(long[] r:rows)if(now-r[0]<=60000){attempts++;if(r[2]==1)success++;durations.add(r[1]);}Collections.sort(durations);try{out.put("attempts",attempts).put("successes",success).put("failures",attempts-success).put("rttMs",durations.isEmpty()?0:durations.get(Math.min(durations.size()-1,(int)Math.ceil(durations.size()*.95)-1)));}catch(JSONException ignored){}return out;}
}
