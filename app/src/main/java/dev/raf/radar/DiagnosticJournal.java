package dev.raf.radar;
import java.io.*;import java.nio.charset.StandardCharsets;import java.nio.file.*;import java.util.*;import java.util.function.LongSupplier;import org.json.*;
/** Structured data only. No exception messages, URLs, credentials or coordinates. */
final class DiagnosticJournal {
 static final long HOUR=3600000L,RETENTION=24*HOUR,REPORT_GAP=900000L;
 static final int MAX_EVENTS=60000,MAX_BYTES=16000000;
 final File root;final LongSupplier clock;JSONArray events=new JSONArray();long lastReport;boolean dirty;int bytes;long dropped;
 DiagnosticJournal(File root,LongSupplier clock){this.root=root;this.clock=clock;root.mkdirs();
  File log=new File(root,"journal.ndjson");
  try{if(log.exists()){try(BufferedReader r=Files.newBufferedReader(log.toPath(),StandardCharsets.UTF_8)){String line;while((line=r.readLine())!=null)try{events.put(new JSONObject(line));bytes+=line.length();}catch(JSONException ignored){}}}
   else{events=new JSONArray(read(new File(root,"journal.json")));for(int i=0;i<events.length();i++)bytes+=events.optJSONObject(i).toString().length();}
  }catch(Exception ignored){}
  try{JSONObject state=new JSONObject(read(new File(root,"state.json")));lastReport=state.optLong("lastReport",0);dirty=state.optBoolean("dirty",false);dropped=state.optLong("dropped",0);}catch(Exception ignored){}
  for(int i=0;i<events.length();i++){JSONObject e=events.optJSONObject(i);if(e!=null&&!"info".equals(e.optString("level"))&&e.optLong("at")>=lastReport)dirty=true;}
  prune();maintain();
 }
 static String word(String s){return s!=null&&s.matches("[a-zA-Z0-9_.$:-]{1,80}")?s:"unknown";}
 static JSONObject details(JSONObject input){JSONObject out=new JSONObject();if(input==null)return out;
  for(String k:Arrays.asList("requestId","eligible","saved","overdue","blocked","inFlight","gapMs","waitMs","ageMs","latenessMs","attempts","successes","failures","http401","http403","http429","networkErrors","bytes","responseBytes","requestBytes","rttMs","dnsMs","connectMs","accuracyM","fixAgeMs","pending","events","dropped","usedHeapKb","maxHeapKb","pssKb","cpuMs","sessionAgeMs","sessionVerifiedAgeMs","http"))try{Object v=input.opt(k);if(v instanceof Number){long n=((Number)v).longValue();if(n>=0&&n<=2147483647L)out.put(k,n);}}catch(JSONException ignored){}
  for(String k:Arrays.asList("validated","vpn","metered","roaming","ready","contextReady","removed","foreground","powerSave","interactive"))try{if(input.opt(k) instanceof Boolean)out.put(k,input.getBoolean(k));}catch(JSONException ignored){}
  for(String k:Arrays.asList("kind","stage","transport","reason","provider"))try{String v=input.optString(k,"");if(Arrays.asList("wifi","cellular","ethernet","vpn","other","none","dns","connect","write","headers","read","parse","complete","waiting","budget","auth","rate_limit","transient","point_backoff","deadline","empty","ok","network","timeout","invalid_json","schema","too_large","invalid_response","refused","verification","ssl","socket","unknown","gps","network_provider","passive","moved","disabled","changed","tariffs_changed","expired","missing").contains(v))out.put(k,v);}catch(JSONException ignored){}
  return out;
 }
 synchronized void event(String area,String event,String level,Throwable error,int http,long duration,long pause,String target){event(area,event,level,error,http,duration,pause,target,null);}
 synchronized void event(String area,String event,String level,Throwable error,int http,long duration,long pause,String target,JSONObject detail){
  try{JSONObject e=new JSONObject().put("at",clock.getAsLong()).put("area",word(area)).put("event",word(event)).put("level",level);if(http>=100&&http<=599)e.put("http",http);if(duration>=0)e.put("durationMs",Math.min(3600000,duration));if(pause>=0)e.put("pauseSeconds",Math.min(86400,pause));if(target!=null)e.put("target",target.equals("own")?"own":target.equals("saved")?"saved":"none");
   if(error!=null){e.put("error",word(error.getClass().getSimpleName()));JSONArray frames=new JSONArray();Throwable cause=error;for(int n=0;n<3&&cause!=null;n++,cause=cause.getCause())for(StackTraceElement f:cause.getStackTrace())if(f.getClassName().startsWith("dev.raf.radar.")&&frames.length()<8)frames.put(f.getClassName()+"."+f.getMethodName()+":"+f.getLineNumber());e.put("frames",frames);}
   JSONObject safe=details(detail);if(safe.length()>0)e.put("details",safe);
   String line=e.toString();events.put(e);bytes+=line.length();if(!"info".equals(level))dirty=true;
   try(FileOutputStream stream=new FileOutputStream(new File(root,"journal.ndjson"),true)){stream.write((line+"\n").getBytes(StandardCharsets.UTF_8));if("fatal".equals(level))stream.getFD().sync();}
   if(events.length()>MAX_EVENTS||bytes>MAX_BYTES)prune();
  }catch(Exception ignored){}
 }
 synchronized void prune(){long now=clock.getAsLong(),cutoff=now-RETENTION;JSONArray kept=new JSONArray();int size=0;for(int i=0;i<events.length();i++){JSONObject e=events.optJSONObject(i);if(e!=null&&e.optLong("at",0)>=cutoff&&e.optLong("at",0)<=now+5000){kept.put(e);size+=e.toString().length();}}
  int from=0;boolean full=kept.length()>MAX_EVENTS||size>MAX_BYTES;while(kept.length()-from>(full?MAX_EVENTS*9/10:MAX_EVENTS)||size>(full?MAX_BYTES*9/10:MAX_BYTES)){size-=kept.optJSONObject(from++).toString().length();dropped++;}if(from>0){JSONArray tail=new JSONArray();for(int i=from;i<kept.length();i++)tail.put(kept.opt(i));kept=tail;}events=kept;bytes=size;
  File[] queued=root.listFiles((d,n)->n.startsWith("report-")&&n.endsWith(".json"));if(queued!=null){Arrays.sort(queued,Comparator.comparing(File::getName));for(int i=0;i<queued.length;i++)if(i<queued.length-8||now-queued[i].lastModified()>7L*86400000)queued[i].delete();}
 }
 synchronized File report(String trigger,JSONObject device,JSONObject snapshot,boolean force){prune();if(!force&&(!dirty||(lastReport>0&&clock.getAsLong()-lastReport<REPORT_GAP&&clock.getAsLong()>=lastReport)))return null;
  try{JSONArray exported=new JSONArray();int from=force?0:Math.max(0,events.length()-1200);for(int i=from;i<events.length();i++)exported.put(events.opt(i));snapshot.put("journalEvents",events.length()).put("journalDropped",Math.min(Integer.MAX_VALUE,dropped));JSONObject report=new JSONObject().put("reportId",UUID.randomUUID().toString()).put("createdAt",clock.getAsLong()).put("trigger",word(trigger)).put("device",device).put("snapshot",snapshot).put("events",exported);File file=new File(root,"report-"+clock.getAsLong()+"-"+report.getString("reportId")+".json");write(file,report.toString());lastReport=clock.getAsLong();dirty=false;state();prune();return file;}catch(Exception e){return null;}}
 synchronized File pending(){prune();File[] files=root.listFiles((d,n)->n.startsWith("report-")&&n.endsWith(".json"));if(files==null||files.length==0)return null;Arrays.sort(files,Comparator.comparing(File::getName));return files[0];}
 synchronized void acknowledged(File f){f.delete();}
 synchronized void maintain(){prune();try{StringBuilder b=new StringBuilder(bytes+events.length());for(int i=0;i<events.length();i++)b.append(events.optJSONObject(i).toString()).append('\n');write(new File(root,"journal.ndjson"),b.toString());new File(root,"journal.json").delete();state();}catch(Exception ignored){}}
 void state()throws IOException{try{write(new File(root,"state.json"),new JSONObject().put("lastReport",lastReport).put("dirty",dirty).put("dropped",dropped).toString());}catch(JSONException e){throw new IOException(e);}}
 static String read(File file)throws IOException{return new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8);}
 static void write(File file,String value)throws IOException{File temp=new File(file.getPath()+".tmp");try(FileOutputStream stream=new FileOutputStream(temp)){stream.write(value.getBytes(StandardCharsets.UTF_8));stream.getFD().sync();}try{Files.move(temp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(temp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);}}
}
