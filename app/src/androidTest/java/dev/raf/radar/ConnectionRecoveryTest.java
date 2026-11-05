package dev.raf.radar;
import android.content.*;import android.webkit.*;import android.net.Uri;import android.os.SystemClock;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;import static org.junit.Assert.*;import java.util.*;import java.util.concurrent.*;
public class ConnectionRecoveryTest {
 Context c;Store store;java.util.concurrent.ExecutorService worker;
 @Before public void setup()throws Exception{
  c=InstrumentationRegistry.getInstrumentation().getTargetContext();store=new Store(c);c.stopService(new Intent(c,MonitorService.class));
  main(()->BrowserFare.releaseIfIdle());InstrumentationRegistry.getInstrumentation().waitForIdleSync();
  store.prefs.edit().clear().putBoolean("sounds",false).putBoolean("economyOnlyV1",true).commit();
  java.lang.reflect.Field f=BrowserFare.class.getDeclaredField("recovery");f.setAccessible(true);Object recovery=f.get(null);java.lang.reflect.Field last=AuthRecovery.class.getDeclaredField("last");last.setAccessible(true);last.setLong(recovery,-1);
  worker=Executors.newSingleThreadExecutor();
 }
 @After public void cleanup(){worker.shutdownNow();main(()->BrowserFare.releaseIfIdle());store.prefs.edit().remove("fareRateUntil").remove("fareRateFailures").remove("yandexNeedsLogin").commit();}
 static void main(Runnable r){InstrumentationRegistry.getInstrumentation().runOnMainSync(r);}
 static WebResourceRequest request(){return new WebResourceRequest(){public Uri getUrl(){return Uri.parse(TaxiClient.ENDPOINT);}public boolean isForMainFrame(){return false;}public boolean isRedirect(){return false;}public boolean hasGesture(){return false;}public String getMethod(){return "POST";}public Map<String,String> getRequestHeaders(){Map<String,String> h=new HashMap<>();h.put("X-Csrf-Token","fixture");h.put("X-YaTaxi-UserId","fixture-user");return h;}};}
 @Test public void nativeRateLimitPausesAllProbesAndWebsiteWithoutLosingLogin()throws Exception{
  WebView[] web={null};WebViewClient[] client={null};
  main(()->{web[0]=BrowserFare.view(c);web[0].stopLoading();client[0]=web[0].getWebViewClient();
   client[0].onReceivedHttpError(web[0],request(),new WebResourceResponse("application/json","UTF-8",429,"Too Many Requests",Collections.singletonMap("Retry-After","120"),new java.io.ByteArrayInputStream("{}".getBytes())));
  });
  long until=store.prefs.getLong("fareRateUntil",0);assertTrue(FareConnection.remaining(c)>115000);assertFalse(FareConnection.needsLogin(c));
  assertEquals(until,new Store(c).prefs.getLong("fareRateUntil",0));FareConnection.limited(c,"999");assertEquals(until,store.prefs.getLong("fareRateUntil",0));
  WebResourceResponse blocked=client[0].shouldInterceptRequest(web[0],request());assertNotNull(blocked);assertEquals("1",blocked.getResponseHeaders().get("X-Sense-Local-Cooldown"));
  for(int i=0;i<2;i++)try{worker.submit(()->BrowserFare.fetch(c,new org.json.JSONObject(),Collections.emptyMap())).get(3,TimeUnit.SECONDS);fail();}catch(ExecutionException expected){assertTrue(expected.getCause() instanceof TaxiClient.ApiException);assertEquals(429,((TaxiClient.ApiException)expected.getCause()).status);}
  assertEquals(until,store.prefs.getLong("fareRateUntil",0));
 }
 java.util.concurrent.atomic.AtomicInteger fixture(boolean unauthorized)throws Exception{
  java.util.concurrent.atomic.AtomicInteger loads=new java.util.concurrent.atomic.AtomicInteger();CountDownLatch ready=new CountDownLatch(1);
  main(()->{
   WebView w=BrowserFare.view(c);w.stopLoading();WebViewClient original=w.getWebViewClient();
   w.setWebViewClient(new WebViewClient(){
    @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){if(r.isForMainFrame())return new WebResourceResponse("text/html","UTF-8",new java.io.ByteArrayInputStream("<html>fixture</html>".getBytes()));return original.shouldInterceptRequest(v,r);}
    @Override public void onPageStarted(WebView v,String url,android.graphics.Bitmap icon){original.onPageStarted(v,url,icon);}
    @Override public void onPageFinished(WebView v,String url){if(!url.equals(v.getUrl()))return;original.onPageFinished(v,url);loads.incrementAndGet();String body=unauthorized?"{}":"{\"service_levels\":[],\"currency_rules\":{\"code\":\"RUB\"}}";
     v.evaluateJavascript("window.fetch=()=>Promise.resolve(new Response("+org.json.JSONObject.quote(body)+",{status:"+(unauthorized?401:200)+"}));true",done->{original.shouldInterceptRequest(v,request());ready.countDown();});}
   });w.loadUrl("https://taxi.yandex.ru/?senseFixture=066");
  });assertTrue(ready.await(10,TimeUnit.SECONDS));return loads;
 }
 @Test public void expiredPauseAllowsOneSuccessfulProbeThenResumes()throws Exception{
  java.util.concurrent.atomic.AtomicInteger loads=fixture(false);FareConnection.limited(c,"1");store.prefs.edit().putLong("fareRateUntil",System.currentTimeMillis()-1).commit();
  assertNotNull(worker.submit(()->BrowserFare.fetch(c,new org.json.JSONObject(),Collections.emptyMap())).get(15,TimeUnit.SECONDS));
  assertEquals(0,FareConnection.remaining(c));assertEquals(0,store.prefs.getInt("fareRateFailures",0));assertEquals(1,loads.get());assertFalse(FareConnection.needsLogin(c));
 }
 @Test public void failedAuthRecoversOnceThenRequiresExplicitLogin()throws Exception{
  java.util.concurrent.atomic.AtomicInteger loads=fixture(true);
  try{worker.submit(()->BrowserFare.fetch(c,new org.json.JSONObject(),Collections.emptyMap())).get(40,TimeUnit.SECONDS);fail();}catch(ExecutionException expected){assertEquals(401,((TaxiClient.ApiException)expected.getCause()).status);}
  assertEquals(2,loads.get());assertTrue(FareConnection.needsLogin(c));
  try{worker.submit(()->BrowserFare.fetch(c,new org.json.JSONObject(),Collections.emptyMap())).get(3,TimeUnit.SECONDS);fail();}catch(ExecutionException expected){assertEquals(401,((TaxiClient.ApiException)expected.getCause()).status);}
  assertEquals(2,loads.get());
 }
 @Test public void networkLossPreservesSessionAndReconnectNeedsNoLoginReload()throws Exception{
  java.util.concurrent.atomic.AtomicInteger loads=fixture(false);String session=store.prefs.getString("yandexSession","");
  try{
   ScreenTest.shell("svc wifi disable");ScreenTest.shell("svc data disable");long deadline=SystemClock.elapsedRealtime()+15000;
   while(FareConnection.online(c)&&SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(100);
   assertFalse(FareConnection.online(c));
   try{worker.submit(()->BrowserFare.fetch(c,new org.json.JSONObject(),Collections.emptyMap())).get(3,TimeUnit.SECONDS);fail();}catch(ExecutionException expected){assertTrue(expected.getCause() instanceof java.io.IOException);assertFalse(expected.getCause() instanceof TaxiClient.ApiException);}
   assertFalse(FareConnection.needsLogin(c));assertEquals(session,store.prefs.getString("yandexSession",""));assertEquals(1,loads.get());
  }finally{ScreenTest.shell("svc wifi enable");ScreenTest.shell("svc data enable");long restore=SystemClock.elapsedRealtime()+45000;while(!FareConnection.online(c)&&SystemClock.elapsedRealtime()<restore)SystemClock.sleep(100);}
  assertTrue("Emulator network did not return",FareConnection.online(c));
  assertNotNull(worker.submit(()->BrowserFare.fetch(c,new org.json.JSONObject(),Collections.emptyMap())).get(15,TimeUnit.SECONDS));assertEquals(1,loads.get());
 }
 @Test public void staleWidgetRetainsLastGoodPriceAndShowsAgeInBothThemes()throws Exception{
  ScreenTest.grantAccess();long at=System.currentTimeMillis()-45000;
  store.prefs.edit().putBoolean("overlay",true).putLong("gpsAt",System.currentTimeMillis()).putLong("lat",Double.doubleToLongBits(55)).putLong("lon",Double.doubleToLongBits(61)).commit();
  store.readings("me",Collections.singletonList(new Store.Reading("econom",180,null,at,"fixture")));
  store.readings("me",Collections.singletonList(new Store.Reading("econom",null,null,System.currentTimeMillis(),"Ошибка")));
  assertEquals(Integer.valueOf(180),new Store(c).lastWidgetReading().rub);assertEquals(at,new Store(c).lastWidgetReading().time);
  for(String theme:new String[]{Appearance.STANDARD,Appearance.LINEAGE}){
   store.prefs.edit().putString(Appearance.KEY,theme).putBoolean("widgetUpdateTime",false).commit();Overlay[] o={null};
   try{main(()->{o[0]=new Overlay(c,new Store(c));o[0].refresh();});InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    main(()->{android.widget.LinearLayout root=WidgetAgeTest.root(o[0]);android.widget.TextView price=(android.widget.TextView)root.getChildAt(0);assertEquals("180",price.getText().toString());assertEquals(.45f,price.getAlpha(),.001f);assertEquals(android.view.View.VISIBLE,root.getChildAt(1).getVisibility());assertTrue(WidgetAgeTest.label(o[0]).getText().toString().endsWith("с"));});
    ScreenTest.capture("connection-stale-"+theme);
    store.prefs.edit().putLong("gpsAt",System.currentTimeMillis()-300000).commit();main(()->o[0].refresh());main(()->assertTrue(WidgetAgeTest.label(o[0]).getText().toString().contains("GPS")));
    store.prefs.edit().putLong("gpsAt",System.currentTimeMillis()).commit();
   }finally{main(()->o[0].close());}
  }
 }
}
