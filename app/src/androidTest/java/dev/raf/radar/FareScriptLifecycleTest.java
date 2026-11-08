package dev.raf.radar;

import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/** Runs the actual generated script in Android WebView; no live Yandex requests. */
public class FareScriptLifecycleTest {
 private WebView web;
 @Before public void prepare()throws Exception{
  CountDownLatch loaded=new CountDownLatch(1);
  InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
   web=new WebView(InstrumentationRegistry.getInstrumentation().getTargetContext());
   web.getSettings().setJavaScriptEnabled(true);
   web.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView view,String url){loaded.countDown();}});
   web.loadDataWithBaseURL("https://taxi.yandex.ru/","<html><body>fixture</body></html>","text/html","UTF-8",null);
  });
  assertTrue("Fixture did not load",loaded.await(15,TimeUnit.SECONDS));
 }
 @After public void close(){if(web!=null)InstrumentationRegistry.getInstrumentation().runOnMainSync(()->web.destroy());}
 private String evaluate(String script)throws Exception{
  CountDownLatch done=new CountDownLatch(1);AtomicReference<String> result=new AtomicReference<>();
  InstrumentationRegistry.getInstrumentation().runOnMainSync(()->web.evaluateJavascript(script,value->{result.set(value);done.countDown();}));
  assertTrue("JavaScript callback missing",done.await(10,TimeUnit.SECONDS));return result.get();
 }
 @Test public void cancelledRequestCannotRecreateStateAfterLateRejection()throws Exception{
  evaluate("window.fetch=()=>new Promise((resolve,reject)=>{window.rejectFixture=reject;});true");
  assertEquals("true",evaluate(FareScript.start("sense_fare_ab12",new JSONObject(),Collections.emptyMap())));
  assertEquals("false",evaluate("window.sense_fare_ab12_abort.signal.aborted"));
  evaluate("window.fixtureSignal=window.sense_fare_ab12_abort.signal;"+FareScript.clear("sense_fare_ab12"));
  evaluate("window.rejectFixture(Object.assign(new Error('cancelled'),{name:'AbortError'}));true");
  assertEquals("true",evaluate("window.fixtureSignal.aborted && !('sense_fare_ab12' in window) && !('sense_fare_ab12_abort' in window)"));
 }
 @Test public void cancelledRequestCannotPublishLateSuccess()throws Exception{
  evaluate("window.fetch=()=>new Promise(resolve=>{window.resolveFixture=resolve;});true");
  assertEquals("true",evaluate(FareScript.start("sense_fare_cd34",new JSONObject(),Collections.emptyMap())));
  evaluate(FareScript.clear("sense_fare_cd34"));
  evaluate("window.resolveFixture(new Response(JSON.stringify({service_levels:[{class:'econom',price:'200'}],currency_rules:{code:'RUB'}}),{status:200}));true");
  assertEquals("true",evaluate("!('sense_fare_cd34' in window) && !('sense_fare_cd34_abort' in window)"));
 }
}
