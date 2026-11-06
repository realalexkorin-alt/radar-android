package dev.raf.radar;
import org.junit.Test;import static org.junit.Assert.*;
public class RateLimitPolicyTest {
 @Test public void serverDelayAndDateOverrideBackoff(){
  long now=java.time.Instant.parse("2026-11-05T12:00:00Z").toEpochMilli();
  assertEquals(120000,RateLimitPolicy.delay("120",now,0));
  assertEquals(180000,RateLimitPolicy.delay("Thu, 5 Nov 2026 12:03:00 GMT",now,0));
  assertEquals(1800000,RateLimitPolicy.delay("1800",now,4));
 }
 @Test public void malformedOrPastHeadersUseBoundedBackoff(){
  assertEquals(10000,RateLimitPolicy.delay(null,0,0));assertEquals(10000,RateLimitPolicy.delay("bad",0,1));
  assertEquals(10000,RateLimitPolicy.delay("-5",0,99));assertEquals(10000,RateLimitPolicy.delay("0",0,0));
 }
}
