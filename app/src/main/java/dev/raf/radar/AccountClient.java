package dev.raf.radar;
import android.content.*;import android.os.*;import android.security.keystore.*;import android.util.Base64;
import java.net.*;import java.io.*;import java.nio.charset.StandardCharsets;import java.security.*;import javax.crypto.*;import javax.crypto.spec.GCMParameterSpec;
import java.util.*;import java.util.concurrent.*;import org.json.*;
final class AccountClient {
 static final String ENDPOINT="https://radar-presence.vercel.app/api/account";
 static final ExecutorService worker=Executors.newSingleThreadExecutor();static final Handler main=new Handler(Looper.getMainLooper());
 interface Transport{JSONObject send(JSONObject body,String session)throws Exception;}
 static Transport transport=AccountClient::request;
 private static final List<PresenceClient.Result> refreshWaiters=new ArrayList<>();private static boolean refreshing;
 interface Result{void ready(JSONObject data,String error);}
 static boolean active(Store s){return s.prefs.getInt("accountId",0)>0;}
 static String session(Store s){try{String value=s.prefs.getString("accountSession","");if(value.isEmpty())return null;String[] parts=value.split(":");Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));return new String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),StandardCharsets.UTF_8);}catch(Exception e){return null;}}
 static javax.crypto.SecretKey key()throws Exception{KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);if(ks.containsAlias("radar-account"))return (javax.crypto.SecretKey)ks.getKey("radar-account",null);KeyGenerator g=KeyGenerator.getInstance("AES","AndroidKeyStore");g.init(new KeyGenParameterSpec.Builder("radar-account",KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());return g.generateKey();}
 static synchronized void save(Store s,JSONObject data)throws Exception{
  JSONObject u=data.optJSONObject("user");if(u==null)return;SharedPreferences.Editor edit=s.prefs.edit();int id=u.getInt("id");if(id!=s.prefs.getInt("accountId",0))edit.remove("peerCache").remove("accountState");
  if(data.has("session")){Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());String encrypted=Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(cipher.doFinal(data.getString("session").getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);edit.putString("accountSession",encrypted);}
  edit.putBoolean("presenceDeletePending",false).putInt("accountId",id).putString("profileName",u.optString("name","")).apply();if(data.has("friends")){s.prefs.edit().putString("accountState",data.toString()).apply();PresenceClient.cache(s,peers(data));}
 }
 static void call(Context c,JSONObject body,Result callback){Context app=c.getApplicationContext();String token=session(new Store(app));worker.execute(()->{JSONObject data=null;String error=null;try{data=transport.send(body,token);save(new Store(app),data);FriendNotifications.sync(app,new Store(app),data);}catch(Exception e){Diagnostics.error("account","request_failed",e,0,-1,-1,null);error=e.getMessage();}JSONObject result=data;String message=error;main.post(()->callback.ready(result,message));});}
 static JSONObject request(JSONObject body,String token)throws Exception{
  long started=SystemClock.elapsedRealtime();String stage="connect";HttpURLConnection conn=(HttpURLConnection)new URL(ENDPOINT).openConnection();conn.setConnectTimeout(15000);conn.setReadTimeout(25000);conn.setRequestMethod("POST");conn.setDoOutput(true);conn.setRequestProperty("Content-Type","application/json");if(token!=null)conn.setRequestProperty("Authorization","Bearer "+token);
  try{byte[] payload=body.toString().getBytes(StandardCharsets.UTF_8);stage="write";try(OutputStream out=conn.getOutputStream()){out.write(payload);}stage="headers";int status=conn.getResponseCode();Diagnostics.detail("account","http_response","rttMs",SystemClock.elapsedRealtime()-started,"requestBytes",payload.length);if(status!=200)Diagnostics.error("account","http_error",null,status,-1,-1,null);InputStream stream=status==200?conn.getInputStream():conn.getErrorStream();if(stream==null)throw new IOException("Нет ответа сервера");stage="read";ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=stream){byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1){if(bytes.size()+n>256000)throw new IOException("Ответ слишком большой");bytes.write(buffer,0,n);}}Diagnostics.detail("account","response_read","responseBytes",bytes.size());stage="parse";JSONObject data=new JSONObject(bytes.toString("UTF-8"));if(status!=200)throw new IOException(data.optString("error","Ошибка сервера: "+status));return data;}catch(Exception e){Diagnostics.detail("account","transport_failed","stage",stage,"kind",Diagnostics.cause(e),"rttMs",SystemClock.elapsedRealtime()-started);if(e instanceof SocketTimeoutException)throw new IOException("Сервер не ответил. Повторите позже",e);throw e;}finally{Diagnostics.detail("account","request_finished","rttMs",SystemClock.elapsedRealtime()-started);conn.disconnect();}
 }
 static JSONObject action(String action){try{return new JSONObject().put("action",action);}catch(JSONException e){throw new IllegalStateException(e);}}
 static List<PresenceClient.Peer> peers(JSONObject data){List<PresenceClient.Peer> out=new ArrayList<>();JSONArray rows=data.optJSONArray("friends");if(rows!=null)for(int i=0;i<rows.length();i++)try{JSONObject p=new JSONObject(rows.getJSONObject(i).toString());p.put("id",String.valueOf(p.getInt("id"))).put("private",true);out.add(new PresenceClient.Peer(p));}catch(JSONException e){Diagnostics.error("friends","malformed_peer",e,0,-1,-1,null);}return out;}
 static synchronized void refresh(Context c,PresenceClient.Result callback){
  refreshWaiters.add(callback);if(refreshing)return;refreshing=true;
  call(c,action("poll"),(data,error)->{List<PresenceClient.Result> waiting;synchronized(AccountClient.class){waiting=new ArrayList<>(refreshWaiters);refreshWaiters.clear();refreshing=false;}List<PresenceClient.Peer> result=error==null?peers(data):null;for(PresenceClient.Result listener:waiting)listener.ready(result);});
 }
 static void clear(Store s){s.prefs.edit().remove("accountId").remove("accountSession").remove("accountState").remove("peerCache").putString("profileName","").putBoolean("shareLocation",false).apply();}
}

