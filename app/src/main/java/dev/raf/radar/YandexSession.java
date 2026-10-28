package dev.raf.radar;

import android.content.Context;
import android.util.Base64;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;

/** Only the owner's locally established fare-request context; never sent to diagnostics. */
final class YandexSession {
  private static Map<String,String> cached;
  private static final String[] HEADERS={"X-Csrf-Token","X-YaTaxi-UserId","X-Personal-Phone-Id","X-AppMetrica-DeviceId","X-AppMetrica-UUID","X-Taxi","User-Agent","Origin","Referer"};
  static Map<String,String> select(String url,Map<String,String> input){
    try{
      URI uri=new URI(url);
      if(!"https".equalsIgnoreCase(uri.getScheme())||!"ya-authproxy.taxi.yandex.ru".equalsIgnoreCase(uri.getHost())
          ||(uri.getPort()!=-1&&uri.getPort()!=443)||uri.getRawUserInfo()!=null||!"/3.0/routestats".equals(uri.getPath()))return Collections.emptyMap();
      Map<String,String> out=new HashMap<>();
      for(String allowed:HEADERS)for(Map.Entry<String,String> e:input.entrySet())if(allowed.equalsIgnoreCase(e.getKey())){
        String v=e.getValue();if(("Origin".equals(allowed)||"Referer".equals(allowed))&&v!=null){URI source=new URI(v);if(!"https".equalsIgnoreCase(source.getScheme())||!"taxi.yandex.ru".equalsIgnoreCase(source.getHost()))continue;}
        if(v!=null&&!v.isEmpty()&&v.length()<=4096&&v.indexOf('\r')<0&&v.indexOf('\n')<0)out.put(allowed,v);
      }
      if(!out.containsKey("X-Csrf-Token")||!out.containsKey("X-YaTaxi-UserId"))return Collections.emptyMap();
      return Collections.unmodifiableMap(out);
    }catch(Exception ignored){return Collections.emptyMap();}
  }
  static synchronized boolean capture(Context c,String url,Map<String,String> input){
    if(c.getSharedPreferences("radar",0).getBoolean("yandexRemoved",false))return false;
    Map<String,String> next=select(url,input);if(next.isEmpty())return false;
    if(next.equals(cached)){c.getSharedPreferences("radar",0).edit().putLong("yandexSessionAt",System.currentTimeMillis()).apply();return true;}
    try{
      JSONObject json=new JSONObject(next);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,AccountClient.key());
      String value=Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(cipher.doFinal(json.toString().getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);
      c.getSharedPreferences("radar",0).edit().putString("yandexSession",value).putLong("yandexSessionAt",System.currentTimeMillis()).apply();
      cached=next;Diagnostics.info("yandex_auth","session_context_captured");return true;
    }catch(Exception e){Diagnostics.error("yandex_auth","session_store_failed",e,0,-1,-1,null);return false;}
  }
  static synchronized Map<String,String> headers(Context c){
    android.content.SharedPreferences p=c.getSharedPreferences("radar",0);if(p.getBoolean("yandexRemoved",false))return Collections.emptyMap();long age=System.currentTimeMillis()-p.getLong("yandexSessionAt",0);
    if(age<0||age>8*3600000L){cached=null;return Collections.emptyMap();}
    if(cached!=null)return cached;
    try{
      String[] parts=p.getString("yandexSession","").split(":");if(parts.length!=2)return Collections.emptyMap();
      Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,AccountClient.key(),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));
      JSONObject json=new JSONObject(new String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),StandardCharsets.UTF_8));Map<String,String> out=new HashMap<>();
      for(String h:HEADERS)if(json.has(h))out.put(h,json.getString(h));
      cached=select(TaxiClient.ENDPOINT,out);return cached;
    }catch(Exception ignored){return Collections.emptyMap();}
  }
  static synchronized void invalidate(Context c,Map<String,String> used){
    if(used.isEmpty()||!headers(c).equals(used))return;
    cached=null;c.getSharedPreferences("radar",0).edit().remove("yandexSession").remove("yandexSessionAt").apply();
    Diagnostics.info("yandex_auth","session_context_rejected");
  }

  static boolean saved(Context c){return !c.getSharedPreferences("radar",0).getString("yandexSession","").isEmpty();}
  static String status(boolean saved,long captured,long verified,long rejected,long now){
    if(!saved)return "Нет подключённых сессий";
    if(captured<=0||now<captured||now-captured>8*3600000L)return "Сессия устарела · войдите снова";
    if(rejected>0&&rejected>=verified)return "Требуется повторный вход";
    return verified>0?"Вход подтверждён":"Ожидает проверки";
  }
  static String status(Context c){android.content.SharedPreferences p=c.getSharedPreferences("radar",0);return status(saved(c),p.getLong("yandexSessionAt",0),p.getLong("yandexVerifiedAt",0),p.getLong("yandexRejectedAt",0),System.currentTimeMillis());}
  static synchronized void verified(Context c,Map<String,String> used){
    if(!used.isEmpty()&&headers(c).equals(used))c.getSharedPreferences("radar",0).edit().putLong("yandexVerifiedAt",System.currentTimeMillis()).remove("yandexRejectedAt").apply();
  }
  static synchronized void rejected(Context c,Map<String,String> used){
    if(!used.isEmpty()&&headers(c).equals(used))c.getSharedPreferences("radar",0).edit().putLong("yandexRejectedAt",System.currentTimeMillis()).apply();
  }
  static synchronized void enable(Context c){c.getSharedPreferences("radar",0).edit().putBoolean("yandexRemoved",false).apply();}
  static synchronized void clear(Context c){
    cached=null;
    c.getSharedPreferences("radar",0).edit().putBoolean("yandexRemoved",true).remove("yandexSession").remove("yandexSessionAt").remove("yandexVerifiedAt").remove("yandexRejectedAt").remove("yandexProbeOk").remove("yandexTestAt").apply();
    Diagnostics.info("yandex_auth","session_removed");
  }
}
