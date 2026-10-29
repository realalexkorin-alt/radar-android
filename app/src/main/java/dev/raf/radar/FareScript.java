package dev.raf.radar;
import java.util.*;
import org.json.*;
/** Same-origin browser request; only fare fields leave the page. */
final class FareScript {
 static void key(String key){if(!key.matches("sense_fare_[a-f0-9]+"))throw new IllegalArgumentException("key");}
 static String start(String key,JSONObject body,Map<String,String> session)throws JSONException{
  key(key);
  JSONObject headers=new JSONObject().put("Content-Type","application/json");
  for(String n:Arrays.asList("X-Csrf-Token","X-YaTaxi-UserId","X-Personal-Phone-Id","X-AppMetrica-DeviceId","X-AppMetrica-UUID","X-Taxi"))
   if(session.containsKey(n))headers.put(n,session.get(n));
  JSONObject options=new JSONObject().put("method","POST").put("credentials","include").put("redirect","error").put("headers",headers).put("body",body.toString());
  return "(function(){if(location.origin!=='https://taxi.yandex.ru')return false;const k="+JSONObject.quote(key)+";window[k]={kind:'pending'};let status=0,stage='connect',size=0;const started=performance.now();const c=new AbortController();const timer=setTimeout(()=>c.abort(),2500);const o="+options+";o.signal=c.signal;fetch("+JSONObject.quote(TaxiClient.ENDPOINT)+",o).then(async r=>{status=r.status;stage='read';let text='';const reader=r.body&&r.body.getReader();if(!reader&&status===200)throw Error('empty');const decoder=new TextDecoder();try{while(reader){const part=await reader.read();if(part.done)break;size+=part.value.length;if(size>2000000)throw Error('too_large');text+=decoder.decode(part.value,{stream:true});}text+=decoder.decode();}finally{if(reader)await reader.cancel();}if(r.status!==200){const t=text.toLowerCase();window[k]={http:r.status,kind:(t.includes('smartcaptcha')||t.includes('/showcaptcha')||t.includes('verify you are human')||t.includes('вы не робот'))?'verification':'refused'};return;}stage='parse';const j=JSON.parse(text);if(!Array.isArray(j.service_levels))throw Error('schema');const levels=j.service_levels.slice(0,64).map(x=>({class:x.class,price:x.price,paid_options:x.paid_options?{value:x.paid_options.value}:null}));window[k]={http:200,kind:'ok',data:{service_levels:levels,currency_rules:{code:j.currency_rules&&j.currency_rules.code}}};}).catch(e=>{window[k]={http:status,kind:e.name==='AbortError'?'timeout':e.name==='SyntaxError'?'invalid_json':['empty','schema','too_large'].includes(e.message)?e.message:status===0?'network':'invalid_response'};}).finally(()=>{clearTimeout(timer);if(window[k])Object.assign(window[k],{stage:stage,durationMs:Math.round(performance.now()-started),requestBytes:new TextEncoder().encode(o.body).length,responseBytes:size});});return true;})()";
 }
 static String poll(String key){key(key);return "JSON.stringify(window["+JSONObject.quote(key)+"]||{kind:'pending'})";}
 static String clear(String key){key(key);return "delete window["+JSONObject.quote(key)+"]";}
 static JSONObject decode(String value)throws JSONException{
  String raw=new JSONArray("["+value+"]").optString(0,"{}");
  if(raw.length()>2000000)throw new JSONException("Fare response too large");
  return new JSONObject(raw);
 }
}

