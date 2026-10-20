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
  static final String ENDPOINT="https://radar-presence.vercel.app/api/presence";
  private static final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
  private static ScheduledFuture<?> publishing;
  private static volatile long nextPublish;
  private static final Handler main=new Handler(Looper.getMainLooper());
  static synchronized void start(Context c){
    if(publishing!=null)return;
    Context app=c.getApplicationContext();nextPublish=0;
    publishing=worker.scheduleWithFixedDelay(()->publish(app),0,5,TimeUnit.SECONDS);
  }
  static synchronized void stop(Context c){
    if(publishing!=null){publishing.cancel(false);publishing=null;}
    withdraw(c);
  }
  static void sharingChanged(Context c){
    if(!new Store(c).prefs.getBoolean("shareLocation",false))withdraw(c);
    else{nextPublish=0;worker.execute(()->publish(c.getApplicationContext()));}
  }
  private static void publish(Context c){
    Store s=new Store(c);long now=SystemClock.elapsedRealtime();
    if(!MonitorService.running || !s.prefs.getBoolean("shareLocation",false) || !Access.monitorReady(c) || !LocationState.fresh(s))return;
    if(now<nextPublish)return;nextPublish=now+30000;
    try{
      JSONObject body=new JSONObject().put("revision",revision(s)).put("lat",Double.longBitsToDouble(s.prefs.getLong("lat",0)))
          .put("lon",Double.longBitsToDouble(s.prefs.getLong("lon",0))).put("accuracy",s.prefs.getFloat("accuracy",500))
          .put("observedAt",s.prefs.getLong("gpsAt",0));
      request("PUT",ENDPOINT,body,token(s));
      s.prefs.edit().putBoolean("presencePublished",true).apply();
    }catch(Exception ignored){ /* Presence failure never blocks taxi data or the service. */ }
  }
  static void withdraw(Context c){
    Context app=c.getApplicationContext();worker.execute(()->{
      Store s=new Store(app);String secret=s.prefs.getString("presenceToken",null);if(secret==null)return;
      try{request("DELETE",ENDPOINT,new JSONObject().put("revision",revision(s)),secret);
        s.prefs.edit().putBoolean("presencePublished",false).apply();
      }catch(Exception ignored){ /* Server TTL removes a point even offline or after process death. */ }
    });
  }
  static void reconcile(Context c){
    if(!MonitorService.running || !new Store(c).prefs.getBoolean("shareLocation",false))withdraw(c);
  }
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
    String token=s.prefs.getString("presenceToken",null);if(token==null)return "";
    try{StringBuilder result=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)))result.append(String.format(Locale.US,"%02x",b&255));return result.toString();}
    catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
  }
  static final class Peer {
    final String id;final double lat,lon;final long observedAt;
    Peer(JSONObject p)throws JSONException{id=p.getString("id");lat=p.getDouble("lat");lon=p.getDouble("lon");observedAt=p.getLong("observedAt");}
    boolean fresh(){long age=System.currentTimeMillis()-observedAt;return age>=-5000&&age<120000;}
  }
  interface Result{void ready(List<Peer> peers);}
  static void query(double west,double south,double east,double north,Result callback){
    worker.execute(()->{List<Peer> peers=new ArrayList<>();
      try{String url=ENDPOINT+String.format(Locale.US,"?west=%.6f&south=%.6f&east=%.6f&north=%.6f",west,south,east,north);
        JSONArray points=new JSONObject(request("GET",url,null,null)).getJSONArray("points");
        for(int i=0;i<Math.min(200,points.length());i++){
          Peer p=new Peer(points.getJSONObject(i));if(p.fresh()&&Double.isFinite(p.lat)&&Double.isFinite(p.lon)&&Math.abs(p.lat)<=90&&Math.abs(p.lon)<=180)peers.add(p);
        }
      }catch(Exception ignored){}
      main.post(()->callback.ready(peers));
    });
  }
  static String request(String method,String url,JSONObject body,String secret)throws Exception{
    HttpURLConnection connection=(HttpURLConnection)new URL(url).openConnection();
    connection.setConnectTimeout(8000);connection.setReadTimeout(8000);connection.setInstanceFollowRedirects(false);connection.setRequestMethod(method);
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
