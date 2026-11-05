package dev.raf.radar;
import org.junit.Test;import static org.junit.Assert.*;
public class FareHttpStateTest {
 @Test public void recoversHiddenHttpButDoesNotReuseOldOrUnrelatedErrors(){
  FareHttpState s=new FareHttpState();s.record(429,100);
  assertEquals(429,s.resolve(0,99));assertEquals(0,s.resolve(0,101));assertEquals(200,s.resolve(200,99));
  s.record(401,200);assertEquals(401,s.resolve(0,200));s.record(403,300);assertEquals(403,s.resolve(0,300));
  s.record(404,400);assertEquals(0,s.resolve(0,400));
 }
}
