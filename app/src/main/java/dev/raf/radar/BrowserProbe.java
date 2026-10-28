package dev.raf.radar;
import java.util.*;
import org.json.*;
/** Ordinary browser fetch from the trusted page; browser origin/CORS protections stay enabled. */
final class BrowserProbe {
  static final Set<String> KINDS=new HashSet<>(Arrays.asList("pending","empty","verification","csrf","access_denied","html","json","text","network","timeout","unreadable"));
  static String start(String key,JSONObject body,Map<String,String> session)throws JSONException {
    if(!key.matches("sense_probe_[a-f0-9]+"))throw new IllegalArgumentException("key");
    JSONObject headers=new JSONObject().put("Content-Type","application/json");
    for(String name:Arrays.asList("X-Csrf-Token","X-YaTaxi-UserId","X-Personal-Phone-Id","X-AppMetrica-DeviceId","X-AppMetrica-UUID","X-Taxi"))
      if(session.containsKey(name))headers.put(name,session.get(name));
    JSONObject options=new JSONObject().put("method","POST").put("credentials","include").put("headers",headers).put("body",body.toString());
    return "(function(){if(location.origin!=='https://taxi.yandex.ru')return false;const k="+JSONObject.quote(key)+";window[k]={http:0,kind:'pending'};const c=new AbortController();const timer=setTimeout(()=>c.abort(),15000);const o="+options+";o.signal=c.signal;fetch("+JSONObject.quote(TaxiClient.ENDPOINT)+",o).then(async r=>{let kind='unreadable';try{let text='';const reader=r.body&&r.body.getReader();if(reader){let n=0;try{while(n<8192){const part=await reader.read();if(part.done)break;const bytes=part.value.slice(0,8192-n);n+=bytes.length;text+=new TextDecoder().decode(bytes);}}finally{await reader.cancel();}}text=text.toLowerCase();const type=r.headers.get('content-type')||'';kind=!text.trim()?'empty':(text.includes('smartcaptcha')||text.includes('/showcaptcha')||text.includes('verify you are human')||text.includes('вы не робот'))?'verification':(text.includes('invalid_csrf_token')||text.includes('csrf token'))?'csrf':(text.includes('access denied')||text.includes('forbidden'))?'access_denied':type.includes('html')?'html':type.includes('json')?'json':'text';}catch(e){}window[k]={http:r.status,kind:kind};}).catch(e=>{window[k]={http:0,kind:e.name==='AbortError'?'timeout':'network'};}).finally(()=>clearTimeout(timer));return true;})()";
  }
  static String poll(String key){if(!key.matches("sense_probe_[a-f0-9]+"))throw new IllegalArgumentException("key");return "JSON.stringify(window["+JSONObject.quote(key)+"]||{http:0,kind:'pending'})";}
}
