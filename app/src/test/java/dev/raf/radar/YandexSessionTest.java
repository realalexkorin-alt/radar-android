package dev.raf.radar;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class YandexSessionTest {
  private Map<String,String> input(){Map<String,String> h=new HashMap<>();h.put("x-csrf-token","test");h.put("x-yataxi-userid","user");h.put("Cookie","private");h.put("Authorization","private");return h;}
  @Test public void acceptsOnlyExactTrustedFareEndpointAndNeverCookieOrBearer(){
    Map<String,String> selected=YandexSession.select("https://ya-authproxy.taxi.yandex.ru/3.0/routestats",input());
    assertEquals("test",selected.get("X-Csrf-Token"));assertEquals("user",selected.get("X-YaTaxi-UserId"));
    assertEquals(2,selected.size());assertFalse(selected.containsKey("Cookie"));assertFalse(selected.containsKey("Authorization"));
    for(String u:new String[]{"http://ya-authproxy.taxi.yandex.ru/3.0/routestats","https://ya-authproxy.taxi.yandex.ru.evil/3.0/routestats","https://ya-authproxy.taxi.yandex.ru/3.0/order","https://ya-authproxy.taxi.yandex.ru:444/3.0/routestats"})
      assertTrue(YandexSession.select(u,input()).isEmpty());
  }
  @Test public void incompleteOrHeaderInjectionContextsAreRejected(){
    Map<String,String> h=input();h.remove("x-csrf-token");assertTrue(YandexSession.select(TaxiClient.ENDPOINT,h).isEmpty());
    h.put("x-csrf-token","bad\r\nInjected: yes");assertTrue(YandexSession.select(TaxiClient.ENDPOINT,h).isEmpty());
  }
}
