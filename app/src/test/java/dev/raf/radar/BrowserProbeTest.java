package dev.raf.radar;
import org.junit.Test;import java.util.*;import org.json.*;import static org.junit.Assert.*;
public class BrowserProbeTest {
 @Test public void browserOwnsCredentialsAndOriginAndOnlyFareRequestIsBuilt()throws Exception{
  Map<String,String> headers=new HashMap<>();headers.put("X-Csrf-Token","fixture");headers.put("X-YaTaxi-UserId","fixture-user");headers.put("Cookie","private-cookie");headers.put("Authorization","private-token");headers.put("User-Agent","private-agent");
  String script=BrowserProbe.start("sense_probe_ab12",new JSONObject().put("route",new JSONArray()),headers);
  assertTrue(script.contains("location.origin!=='https://taxi.yandex.ru'"));assertTrue(script.contains(TaxiClient.ENDPOINT));assertTrue(script.contains("include"));assertTrue(script.contains("AbortController"));
  assertFalse(script.contains("private-cookie"));assertFalse(script.contains("private-token"));assertFalse(script.contains("private-agent"));
 }
 @Test(expected=IllegalArgumentException.class)public void pollKeysCannotInjectScript(){BrowserProbe.poll("x');alert(1)");}
 @Test public void verificationAndCsrfAreDistinguishedFromGenericHtml(){
  assertEquals("response_verification",ServiceError.classifyText("<h1>Forbidden</h1><form action='/showcaptcha'>", "text/html"));
  assertEquals("response_csrf",ServiceError.classifyText("{\"code\":\"INVALID_CSRF_TOKEN\"}","application/json"));
  assertEquals("response_access_denied",ServiceError.classifyText("<h1>Access denied</h1>","text/html"));
 }
}
