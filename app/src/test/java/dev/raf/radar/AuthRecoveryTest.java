package dev.raf.radar;
import org.junit.Test;
import static org.junit.Assert.*;
public class AuthRecoveryTest {
 @Test public void renewsOnlyOneUnauthorizedAttempt(){
  AuthRecovery r=new AuthRecovery();
  assertTrue(r.begin(401,"refused",false,0));
  assertFalse(r.begin(401,"refused",true,300000));
  assertFalse(r.begin(401,"refused",false,299999));
  assertTrue(r.begin(401,"refused",false,300000));
 }
 @Test public void neverRetriesVerificationOrOtherFailures(){
  AuthRecovery r=new AuthRecovery();
  assertFalse(r.begin(401,"verification",false,0));
  assertFalse(r.begin(403,"refused",false,0));
  assertFalse(r.begin(429,"refused",false,0));
  assertFalse(r.begin(0,"network",false,0));
  assertFalse(r.begin(200,"ok",false,0));
  assertTrue(r.begin(401,"refused",false,0));
 }
}
