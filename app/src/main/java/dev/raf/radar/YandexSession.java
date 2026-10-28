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
  private static final String[] HEADERS={"X-Csrf-Token","X-YaTaxi-UserId","X-Personal-Phone-Id","X-AppMetrica-DeviceId","X-AppMetrica-UUID","X-Taxi","User-Agent"};
  static Map<String,String> select(String url,Map<String,String> input){
    try{
      URI uri=new URI(url);
      if(!"https".equalsIgnoreCase(uri.getScheme())||!"ya-authproxy.taxi.yandex.ru".equalsIgnoreCase(uri.getHost())
          ||(uri.getPort()!=-1&&uri.getPort()!=443)||uri.getRawUserInfo()!=null||!"/3.0/routestats".equals(uri.getPath()))return Collections.emptyMap();
      Map<String,String> out=new HashMap<>();
      for(String allowed:HEADERS)for(Map.Entry<String,String> e:input.entrySet())if(allowed.equalsIgnoreCase(e.getKey())){
        String v=e.getValue();if(v!=null&&!v.isEmpty()&&v.length()<=4096&&v.indexOf('\r')<0&&v.indexOf('\n')<0)out.put(allowed,v);
      }
      if(!out.containsKey("X-Csrf-Token")||!out.containsKey("X-YaTaxi-UserId"))return Collections.emptyMap();
      return Collections.unmodifiableMap(out);
    }catch(Exception ignored){return Collections.emptyMap();}
  }
  static synchronized boolean capture(Context c,String url,Map<String,String> input){
    Map<String,String> next=select(url,input);if(next.isEmpty())return false;
    if(next.equals(cached))return true;
    try{
      JSONObject json=new JSONObject(next);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,AccountClient.key());
      String value=Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(cipher.doFinal(json.toString().getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);
      c.getSharedPreferences("radar",0).edit().putString("yandexSession",value).putLong("yandexSessionAt",System.currentTimeMillis()).apply();
      cached=next;Diagnostics.info("yandex_auth","session_context_captured");return true;
    }catch(Exception e){Diagnostics.error("yandex_auth","session_store_failed",e,0,-1,-1,null);return false;}
  }
  static synchronized Map<String,String> headers(Context c){
    android.content.SharedPreferences p=c.getSharedPreferences("radar",0);long age=System.currentTimeMillis()-p.getLong("yandexSessionAt",0);
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
}
