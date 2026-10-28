package dev.raf.radar;
import android.view.*;import android.webkit.*;import android.content.*;
import androidx.test.core.app.ActivityScenario;import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;import static org.junit.Assert.*;
public class YandexUiTest {
 WebView web(View v){if(v instanceof WebView)return (WebView)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){WebView w=web(g.getChildAt(i));if(w!=null)return w;}}return null;}
 @Test public void signInHasOwnBrowserAndAVisibleReturnPanel(){
  Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
  c.getSharedPreferences("radar",0).edit().clear().putBoolean("economyOnlyV1",true).commit();
  try(ActivityScenario<LoginActivity> s=ActivityScenario.launch(LoginActivity.class)){
   s.onActivity(a->{
    assertNotSame(BrowserFare.view(a),web(a.getWindow().getDecorView()));
    a.authPage("https://taxi.yandex.ru/",false);
    assertEquals(View.INVISIBLE,web(a.getWindow().getDecorView()).getVisibility());
    assertTrue(SettingsPolishTest.contains(a.getWindow().getDecorView(),"Аккаунт выбран"));
   });ScreenTest.capture("login-return-panel");
  }
 }
 @Test public void failedCheckAllowsImmediateRetryWithoutMinuteTimer(){
  Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();Store store=new Store(c);
  store.prefs.edit().putLong("yandexTestAt",System.currentTimeMillis()).commit();
  try(ActivityScenario<LoginActivity> s=ActivityScenario.launch(LoginActivity.class)){
   s.onActivity(a->{
    a.showResult(false,"fixture");a.probe();
    assertFalse(SettingsPolishTest.contains(a.getWindow().getDecorView(),"Повторная проверка будет доступна"));
    assertFalse(SettingsPolishTest.contains(a.getWindow().getDecorView(),"К списку сессий"));
   });
  }
 }

 @Test public void unauthorizedFareReloadsPageAndUsesFreshContext()throws Exception{
  Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
  java.util.concurrent.atomic.AtomicInteger loads=new java.util.concurrent.atomic.AtomicInteger();
  java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();
  InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
   BrowserFare.clearLocalSession(()->{});YandexSession.enable(c);
   WebView w=BrowserFare.view(c);w.stopLoading();WebViewClient original=w.getWebViewClient();
   w.setWebViewClient(new WebViewClient(){
    @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest req){
     if(req.isForMainFrame())return new WebResourceResponse("text/html","UTF-8",new java.io.ByteArrayInputStream("<html><body>fixture</body></html>".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
     return original.shouldInterceptRequest(v,req);
    }
    @Override public void onPageStarted(WebView v,String url,android.graphics.Bitmap icon){original.onPageStarted(v,url,icon);}
    @Override public void onPageFinished(WebView v,String url){
     original.onPageFinished(v,url);int n=loads.incrementAndGet();
     String json=n==1?"{}":"{\"service_levels\":[],\"currency_rules\":{\"code\":\"RUB\"}}";
     try{
      String script="window.fetch=()=>Promise.resolve(new Response("+org.json.JSONObject.quote(json)+",{status:"+(n==1?401:200)+"}));true";
      v.evaluateJavascript(script,ignored->{
       java.util.Map<String,String> h=new java.util.HashMap<>();h.put("X-Csrf-Token","fixture-"+n);h.put("X-YaTaxi-UserId","fixture-user");
       original.shouldInterceptRequest(v,new WebResourceRequest(){
        public android.net.Uri getUrl(){return android.net.Uri.parse(TaxiClient.ENDPOINT);}
        public boolean isForMainFrame(){return false;}public boolean isRedirect(){return false;}
        public boolean hasGesture(){return false;}public String getMethod(){return "POST";}
        public java.util.Map<String,String> getRequestHeaders(){return h;}
       });
      });
     }catch(Exception e){throw new RuntimeException(e);}
    }
   });BrowserFare.refreshSession(c);
  });
  try{
   java.util.concurrent.Future<org.json.JSONObject> result=worker.submit(()->BrowserFare.fetch(c,new org.json.JSONObject(),java.util.Collections.emptyMap()));
   assertNotNull(result.get(60,java.util.concurrent.TimeUnit.SECONDS).getJSONArray("service_levels"));
   assertEquals(2,loads.get());assertEquals("fixture-2",YandexSession.headers(c).get("X-Csrf-Token"));
  }finally{
   worker.shutdownNow();
   InstrumentationRegistry.getInstrumentation().runOnMainSync(()->BrowserFare.releaseIfIdle());
  }
 }
}
