package dev.raf.radar;
import org.junit.Test;import static org.junit.Assert.*;
public class YandexStatusTest {
 @Test public void captureIsNotProofOfLoginAndExpiredSessionsAreVisible(){
  long now=100000000L;
  assertEquals("Нет подключённых сессий",YandexSession.status(false,now,0,0,now));
  assertEquals("Ожидает проверки",YandexSession.status(true,now,0,0,now));
  assertEquals("Вход подтверждён",YandexSession.status(true,now,now-10,0,now));
  assertEquals("Требуется повторный вход",YandexSession.status(true,now,now-20,now-10,now));
  assertEquals("Сессия устарела · войдите снова",YandexSession.status(true,now-8*3600000L-1,now-10,0,now));
 }
}
