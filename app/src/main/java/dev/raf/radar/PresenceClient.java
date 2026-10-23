package dev.raf.radar;

import android.content.*;
import android.os.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Serialized presence writes ensure a withdrawal follows any in-flight publication. */
final class PresenceClient {
  static final String LEGACY="https://radar-presence.vercel.app/api/presence";
  static final String ENDPOINT="https://radar-presence.vercel.app/api/members";
  private static final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
  private static final ExecutorService readers=Executors.newSingleThreadExecutor();
  private static ScheduledFuture<?> publishing;
  private static volatile long nextPublish;
  private static volatile boolean screenVisible;
  private static volatile long nextDirectorySync;
  private static volatile boolean syncing,accountPublishing;
  private static final Handler main=new Handler(Looper.getMainLooper());
  static synchronized void start(Context c){
    if(publishing!=null)return;
    Context app=c.getApplicationContext();nextPublish=0;
    publishing=worker.scheduleWithFixedDelay(()->{publish(app);syncDirectory(app);},0,5,TimeUnit.SECONDS);
  }
  private static void syncDirectory(Context c){
    long now=SystemClock.elapsedRealtime();if(syncing||now<nextDirectorySync)return;
    syncing=true;nextDirectorySync=now+30000;
    directory(c,result->{syncing=false;if(result!=null){cache(new Store(c),result);c.sendBroadcast(new Intent("dev.raf.radar.PEERS").setPackage(c.getPackageName()));}});
  }
  static void screen(Context c,boolean visible){screenVisible=visible;if(visible)start(c);else if(!MonitorService.running)stop(c);}
  static synchronized void stop(Context c){
    if(screenVisible)return;
    if(publishing!=null){publishing.cancel(false);publishing=null;}
    if(new Store(c).prefs.getBoolean("shareLocation",false))offline(c);else withdraw(c);
  }
  static void sharingChanged(Context c){
    if(!new Store(c).prefs.getBoolean("shareLocation",false))withdraw(c);
    else{new Store(c).prefs.edit().putBoolean("presenceDeletePending",false).apply();nextPublish=0;worker.execute(()->publish(c.getApplicationContext()));}
  }
  private static void publish(Context c){
    Store s=new Store(c);if(!AccountClient.active(s))return;long now=SystemClock.elapsedRealtime();
    if(s.prefs.getBoolean("presenceDeletePending",false)){if(now>=nextPublish){nextPublish=now+30000;withdraw(c);}return;}
    if((!MonitorService.running&&!screenVisible) || !s.prefs.getBoolean("shareLocation",false) || !Access.location(c) || !LocationState.fresh(s))return;
    if(s.prefs.getString("profileName","").isEmpty())return;
    if(now<nextPublish||accountPublishing)return;nextPublish=now+30000;
    try{
      JSONObject body=new JSONObject().put("revision",revision(s)).put("lat",Double.longBitsToDouble(s.prefs.getLong("lat",0)))
          .put("lon",Double.longBitsToDouble(s.prefs.getLong("lon",0))).put("accuracy",s.prefs.getFloat("accuracy",500))
          .put("observedAt",s.prefs.getLong("gpsAt",0)).put("name",s.prefs.getString("profileName",""));
      if(AccountClient.active(s)){body.put("action","position");accountPublishing=true;AccountClient.call(c,body,(data,error)->accountPublishing=false);return;}
      request("PUT",ENDPOINT,body,token(s));
      s.prefs.edit().putBoolean("presencePublished",true).apply();
    }catch(Exception e){Diagnostics.error("presence","sync_failed",e,0,-1,-1,null); }
  }
  static void withdraw(Context c){
    if(AccountClient.active(new Store(c))){offline(c);return;}
    Context app=c.getApplicationContext();new Store(app).prefs.edit().putBoolean("presenceDeletePending",true).apply();worker.execute(()->{
      Store s=new Store(app);String secret=s.prefs.getString("presenceToken",null);if(secret==null)return;
      try{request("DELETE",ENDPOINT,new JSONObject().put("revision",revision(s)),secret);
        s.prefs.edit().putBoolean("presencePublished",false).putBoolean("presenceDeletePending",false).apply();
      }catch(Exception e){Diagnostics.error("presence","withdraw_failed",e,0,-1,-1,null); }
    });
  }
  static void reconcile(Context c){}
  static void profileChanged(Context c){
    Context app=c.getApplicationContext();worker.execute(()->{Store s=new Store(app);try{request("POST",ENDPOINT,new JSONObject().put("revision",revision(s)).put("name",s.prefs.getString("profileName","")),token(s));}catch(Exception ignored){}});
    nextPublish=0;
  }
  static void offline(Context c){
    if(AccountClient.active(new Store(c))){AccountClient.call(c,AccountClient.action("offline"),(data,error)->{});return;}
    Context app=c.getApplicationContext();worker.execute(()->{Store s=new Store(app);String secret=s.prefs.getString("presenceToken",null);if(secret==null)return;
      try{request("POST",ENDPOINT,new JSONObject().put("revision",revision(s)).put("offline",true),secret);}catch(Exception ignored){}
    });
  }
  static void directory(Context c,Result callback){if(AccountClient.active(new Store(c))){AccountClient.refresh(c,callback);return;}callback.ready(Collections.emptyList());}
  private static long revision(Store s){
    long revision=Math.max(System.currentTimeMillis(),s.prefs.getLong("presenceRevision",0)+1);
    s.prefs.edit().putLong("presenceRevision",revision).commit();return revision;
  }
  private static synchronized String token(Store s){
    String token=s.prefs.getString("presenceToken",null);if(token!=null)return token;
    byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);StringBuilder out=new StringBuilder();
    for(byte b:bytes)out.append(String.format(Locale.US,"%02x",b&255));token=out.toString();
    s.prefs.edit().putString("presenceToken",token).commit();return token;
  }
  static String ownId(Store s){
    if(AccountClient.active(s))return String.valueOf(s.prefs.getInt("accountId",0));
    String token=token(s);
    try{StringBuilder result=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)))result.append(String.format(Locale.US,"%02x",b&255));return result.toString();}
    catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
  }
  static final class Peer {
    final String id,name;final double lat,lon;final long observedAt,lastSeen;final boolean offline,hasLocation,privateAccount;
    Peer(JSONObject p)throws JSONException{id=p.getString("id");hasLocation=p.optBoolean("hasLocation",p.has("lat")&&p.has("lon"));privateAccount=p.optBoolean("private",false);lat=p.optDouble("lat",0);lon=p.optDouble("lon",0);observedAt=p.optLong("observedAt",0);name=p.optString("name","Пользователь "+id.substring(0,Math.min(6,id.length())));lastSeen=p.optLong("lastSeen",observedAt);offline=p.optBoolean("offline",false);}
    boolean fresh(){long age=System.currentTimeMillis()-lastSeen;return !offline&&age>=-5000&&age<120000;}
    boolean retained(){if(privateAccount)return true;long age=System.currentTimeMillis()-lastSeen;return age>=-5000&&age<7L*86400000;}
    String locationAge(){return ReadingAge.label(observedAt,System.currentTimeMillis());}
    JSONObject json()throws JSONException{return new JSONObject().put("id",id).put("name",name).put("lat",lat).put("lon",lon).put("observedAt",observedAt).put("lastSeen",lastSeen).put("offline",offline).put("hasLocation",hasLocation).put("private",privateAccount);}
    String status(){if(fresh())return "В сети";long age=Math.max(0,(System.currentTimeMillis()-lastSeen)/1000);String unit="с";if(age>=86400){age/=86400;unit="д";}else if(age>=3600){age/=3600;unit="ч";}else if(age>=60){age/=60;unit="м";}return "Был в сети "+age+" "+unit+" назад";}
  }
  static void cache(Store s,List<Peer> peers){
    JSONArray array=new JSONArray();for(Peer p:peers)try{array.put(p.json());}catch(JSONException ignored){}
    s.prefs.edit().putString("peerCache",array.toString()).apply();
  }
  static List<Peer> cached(Store s){
    List<Peer> peers=new ArrayList<>();try{JSONArray array=new JSONArray(s.prefs.getString("peerCache","[]"));
      for(int i=0;i<Math.min(200,array.length());i++)try{Peer p=new Peer(array.getJSONObject(i));if(p.retained())peers.add(p);}catch(JSONException ignored){}
    }catch(JSONException ignored){}return peers;
  }
  interface Result{void ready(List<Peer> peers);}
  static void query(Context c,double west,double south,double east,double north,Result callback){
    if(AccountClient.active(new Store(c))){AccountClient.refresh(c,callback);return;}
    callback.ready(Collections.emptyList());
  }
  private static void queryUrl(String url,Result callback){
    readers.execute(()->{List<Peer> peers=new ArrayList<>();
      try{
        JSONArray points=new JSONObject(request("GET",url,null,null)).getJSONArray("points");
        for(int i=0;i<Math.min(200,points.length());i++){
          try{Peer p=new Peer(points.getJSONObject(i));if(p.retained()&&Double.isFinite(p.lat)&&Double.isFinite(p.lon)&&Math.abs(p.lat)<=90&&Math.abs(p.lon)<=180)peers.add(p);}catch(JSONException ignored){ /* A bad record must not discard every user. */ }
        }
      }catch(Exception e){Diagnostics.error("friends","refresh_failed",e,0,-1,-1,null);main.post(()->callback.ready(null));return;}
      main.post(()->callback.ready(peers));
    });
  }
  static String request(String method,String url,JSONObject body,String secret)throws Exception{
    HttpURLConnection connection=(HttpURLConnection)new URL(url).openConnection();
    connection.setConnectTimeout(15000);connection.setReadTimeout(25000);connection.setInstanceFollowRedirects(false);connection.setRequestMethod(method);
    try{
      if(secret!=null)connection.setRequestProperty("Authorization","Bearer "+secret);
      if(body!=null){connection.setRequestProperty("Content-Type","application/json");connection.setDoOutput(true);
        try(OutputStream output=connection.getOutputStream()){output.write(body.toString().getBytes(StandardCharsets.UTF_8));}}
      if(connection.getResponseCode()!=200)throw new IOException("Presence HTTP "+connection.getResponseCode());
      try(InputStream input=connection.getInputStream();ByteArrayOutputStream output=new ByteArrayOutputStream()){
        byte[] buffer=new byte[4096];int n,total=0;while((n=input.read(buffer))!=-1){total+=n;if(total>128000)throw new IOException("Response too large");output.write(buffer,0,n);}return output.toString("UTF-8");
      }
    }finally{connection.disconnect();}
  }
}
