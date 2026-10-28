package dev.raf.radar;
import org.junit.Test;import static org.junit.Assert.*;import org.json.*;
public class DiagnosticLoadTest {
 @Test public void rollingLoadCountsFailuresAndLatency()throws Exception{DiagnosticLoad d=new DiagnosticLoad();d.completed(1000,100,true);d.completed(2000,1500,false);JSONObject s=d.snapshot(3000);assertEquals(2,s.getInt("attempts"));assertEquals(1,s.getInt("failures"));assertEquals(1500,s.getInt("rttMs"));assertEquals(0,d.snapshot(63000).getInt("attempts"));}
 @Test public void queueStateDistinguishesAuthorizationFromNormalBudget()throws Exception{PollPlanner p=new PollPlanner();java.util.List<String> ids=java.util.Arrays.asList("me","one");assertEquals("me",p.nextMinute(ids,0,"me"));assertEquals("budget",p.diagnosticState(ids,1,"me").getString("reason"));p.failed("one",401,2);assertEquals("auth",p.diagnosticState(ids,3,"me").getString("reason"));assertEquals(2,p.diagnosticState(ids,3,"me").getInt("eligible"));}
}
