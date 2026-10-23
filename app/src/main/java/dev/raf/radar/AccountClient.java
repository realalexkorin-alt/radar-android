package dev.raf.radar;
import android.content.*;import android.os.*;import android.security.keystore.*;import android.util.Base64;
import java.net.*;import java.io.*;import java.nio.charset.StandardCharsets;import java.security.*;import javax.crypto.*;import javax.crypto.spec.GCMParameterSpec;
import java.util.*;import java.util.concurrent.*;import org.json.*;
final class AccountClient {
 static final String ENDPOINT="https://radar-presence.vercel.app/api/account";
 static final ExecutorService worker=Executors.newSingleThreadExecutor();static final Handler main=new Handler(Looper.getMainLooper());
 interface Result{void ready(JSONObject data,String error);}
 static boolean active(Store s){return s.prefs.getInt("accountId",0)>0;}
 static String session(Store s){try{String value=s.prefs.getString("accountSession","");if(value.isEmpty())return null;String[] parts=value.split(":");Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));return new String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),StandardCharsets.UTF_8);}catch(Exception e){return null;}}
 static javax.crypto.SecretKey key()throws Exception{KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);if(ks.containsAlias("radar-account"))return (javax.crypto.SecretKey)ks.getKey("radar-account",null);KeyGenerator g=KeyGenerator.getInstance("AES","AndroidKeyStore");g.init(new KeyGenParameterSpec.Builder("radar-account",KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());return g.generateKey();}
 static synchronized void save(Store s,JSONObject data)throws Exception{
  JSONObject u=data.optJSONObject("user");if(u==null)return;SharedPreferences.Editor edit=s.prefs.edit();int id=u.getInt("id");if(id!=s.prefs.getInt("accountId",0))edit.remove("peerCache").remove("accountState");
  if(data.has("session")){Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());String encrypted=Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(cipher.doFinal(data.getString("session").getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);edit.putString("accountSession",encrypted);}
  edit.putBoolean("presenceDeletePending",false).putInt("accountId",id).putString("profileName",u.optString("name","")).apply();if(data.has("friends")){s.prefs.edit().putString("accountState",data.toString()).apply();PresenceClient.cache(s,peers(data));}
 }
 static void call(Context c,JSONObject body,Result callback){Context app=c.getApplicationContext();String token=session(new Store(app));worker.execute(()->{JSONObject data=null;String error=null;try{data=request(body,token);save(new Store(app),data);FriendNotifications.sync(app,new Store(app),data);}catch(Exception e){error=e.getMessage();}JSONObject result=data;String message=error;main.post(()->callback.ready(result,message));});}
 static JSONObject request(JSONObject body,String token)throws Exception{
  HttpURLConnection conn=(HttpURLConnection)new URL(ENDPOINT).openConnection();conn.setConnectTimeout(15000);conn.setReadTimeout(25000);conn.setRequestMethod("POST");conn.setDoOutput(true);conn.setRequestProperty("Content-Type","application/json");if(token!=null)conn.setRequestProperty("Authorization","Bearer "+token);
  try{try(OutputStream out=conn.getOutputStream()){out.write(body.toString().getBytes(StandardCharsets.UTF_8));}int status=conn.getResponseCode();InputStream stream=status==200?conn.getInputStream():conn.getErrorStream();if(stream==null)throw new IOException("Нет ответа сервера");ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=stream){byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1){if(bytes.size()+n>256000)throw new IOException("Ответ слишком большой");bytes.write(buffer,0,n);}}JSONObject data=new JSONObject(bytes.toString("UTF-8"));if(status!=200)throw new IOException(data.optString("error","Ошибка сервера: "+status));return data;}catch(SocketTimeoutException e){throw new IOException("Сервер не ответил. Повторите позже");}finally{conn.disconnect();}
 }
 static JSONObject action(String action){try{return new JSONObject().put("action",action);}catch(JSONException e){throw new IllegalStateException(e);}}
 static List<PresenceClient.Peer> peers(JSONObject data){List<PresenceClient.Peer> out=new ArrayList<>();JSONArray rows=data.optJSONArray("friends");if(rows!=null)for(int i=0;i<rows.length();i++)try{JSONObject p=new JSONObject(rows.getJSONObject(i).toString());p.put("id",String.valueOf(p.getInt("id"))).put("private",true);out.add(new PresenceClient.Peer(p));}catch(JSONException ignored){}return out;}
 static void refresh(Context c,PresenceClient.Result callback){call(c,action("poll"),(data,error)->callback.ready(error==null?peers(data):null));}
 static void clear(Store s){s.prefs.edit().remove("accountId").remove("accountSession").remove("accountState").remove("peerCache").putString("profileName","").putBoolean("shareLocation",false).apply();}
}
