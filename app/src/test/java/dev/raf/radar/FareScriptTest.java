package dev.raf.radar;
import org.junit.Test;
import org.json.*;
import java.util.*;
import static org.junit.Assert.*;
public class FareScriptTest {
 @Test public void onlyTrustedFareEndpointAndBrowserCredentials()throws Exception{
  Map<String,String> h=new HashMap<>();h.put("X-Csrf-Token","fixture");h.put("Cookie","secret-cookie");h.put("Authorization","secret-auth");
  String s=FareScript.start("sense_fare_ab12",new JSONObject().put("route",new JSONArray()),h);
  assertTrue(s.contains("location.origin!=='https://taxi.yandex.ru'"));assertTrue(s.contains(TaxiClient.ENDPOINT));
  assertTrue(s.contains("2000000"));assertTrue(s.contains("service_levels"));assertTrue(s.contains("redirect"));
  assertFalse(s.contains("secret-cookie"));assertFalse(s.contains("secret-auth"));
 }
 @Test public void webViewResultDecodesAndFeedsExistingCalculator()throws Exception{
  JSONObject data=new JSONObject().put("currency_rules",new JSONObject().put("code","RUB")).put("service_levels",new JSONArray().put(new JSONObject().put("class","econom").put("price","229 ₽")));
  String value=JSONObject.quote(new JSONObject().put("http",200).put("kind","ok").put("data",data).toString());
  JSONObject decoded=FareScript.decode(value);
  java.util.List<Store.Reading> readings=TaxiClient.parse(decoded.getJSONObject("data"),Arrays.asList("econom"),System.currentTimeMillis(),new JSONObject().put("priceEconom",149),"test");
  assertEquals(Integer.valueOf(80),readings.get(0).rub);
 }
 @Test(expected=IllegalArgumentException.class)public void rejectsScriptInjection(){FareScript.clear("x');alert(1)");}
 @Test public void pendingAndFailureAreNotFareData()throws Exception{
  assertEquals("pending",FareScript.decode(JSONObject.quote("{\"kind\":\"pending\"}")).getString("kind"));
  assertFalse(FareScript.decode(JSONObject.quote("{\"http\":403,\"kind\":\"verification\"}")).has("data"));
 }
}
