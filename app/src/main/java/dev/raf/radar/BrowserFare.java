package dev.raf.radar;
import android.content.Context;
import android.net.Uri;
import android.os.*;
import android.view.ViewGroup;
import android.webkit.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;
/** One shared website session, used sequentially by the foreground monitor and login screen. */
final class BrowserFare {
 private static WebView web;
 private static volatile boolean ready;
 private static volatile boolean contextReady;
 private static volatile boolean fareInFlight;
 private static final AuthRecovery recovery=new AuthRecovery();
 private static volatile long pageGeneration;
 private static final FareHttpState nativeHttp=new FareHttpState();
 static java.util.function.Consumer<String> pageObserver;
 private static final Handler main=new Handler(Looper.getMainLooper());
 private static final Object serial=new Object();
 private BrowserFare(){}
 static boolean trusted(String url){
  Uri u=Uri.parse(url==null?"":url);
  return "https".equals(u.getScheme())&&"taxi.yandex.ru".equals(u.getHost())&&(u.getPort()==-1||u.getPort()==443);
 }
 static WebView view(Context context){
  if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("Main thread required");
  if(web==null){
   final Context app=context.getApplicationContext();
   web=new WebView(app);
   web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);
   web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);
   web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
   CookieManager.getInstance().setAcceptCookie(true);
   web.setWebChromeClient(new WebChromeClient(){@Override public boolean onConsoleMessage(ConsoleMessage m){String text=m.message()==null?"":m.message();if(text.contains("CORS")||text.contains("Access-Control-Allow"))Diagnostics.once("yandex_browser","cors_rejected",true);else if(text.contains("net::ERR_"))Diagnostics.once("yandex_browser","browser_network_error",true);return false;}});
   web.setWebViewClient(new WebViewClient(){
    @Override public void onReceivedError(WebView w,WebResourceRequest req,WebResourceError error){if(req.isForMainFrame()||TaxiClient.ENDPOINT.equals(req.getUrl().toString())){int code=error.getErrorCode();Diagnostics.detail("yandex_browser","web_error","kind",code==WebViewClient.ERROR_HOST_LOOKUP?"dns":code==WebViewClient.ERROR_TIMEOUT?"timeout":code==WebViewClient.ERROR_CONNECT?"connect":code==WebViewClient.ERROR_FAILED_SSL_HANDSHAKE?"ssl":"network");Diagnostics.once("yandex_browser","web_load_failed",true);}}
    @Override public void onReceivedHttpError(WebView w,WebResourceRequest req,WebResourceResponse response){
     if(w!=web)return;
     Map<String,String> responseHeaders=response.getResponseHeaders();
     if(responseHeaders!=null)for(String header:responseHeaders.keySet())if("X-Sense-Local-Cooldown".equalsIgnoreCase(header))return;
     if(TaxiClient.ENDPOINT.equals(req.getUrl().toString())){
      int code=response.getStatusCode();nativeHttp.record(code,SystemClock.elapsedRealtime());
      if(code==429){String retry=null;if(responseHeaders!=null)for(Map.Entry<String,String> h:responseHeaders.entrySet())if("Retry-After".equalsIgnoreCase(h.getKey()))retry=h.getValue();FareConnection.limited(app,retry);}
     }
     if(req.isForMainFrame()||TaxiClient.ENDPOINT.equals(req.getUrl().toString()))Diagnostics.error("yandex_browser","web_http_error",null,response.getStatusCode(),-1,-1,null);
    }
    @Override public void onReceivedSslError(WebView w,SslErrorHandler handler,android.net.http.SslError error){Diagnostics.once("yandex_browser","ssl_rejected",true);handler.cancel();}
    @Override public WebResourceResponse shouldInterceptRequest(WebView w,WebResourceRequest req){
     long generation=pageGeneration;
     if(TaxiClient.ENDPOINT.equals(req.getUrl().toString())&&(FareConnection.remaining(app)>0||(ready&&contextReady&&FareConnection.prefs(app).getInt("fareRateFailures",0)>0&&!fareInFlight))){
      return new WebResourceResponse("application/json","UTF-8",503,"Local cooldown",Collections.singletonMap("X-Sense-Local-Cooldown","1"),new java.io.ByteArrayInputStream("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
     }
     if("POST".equals(req.getMethod())&&!fareInFlight){
      String url=req.getUrl().toString();Map<String,String> headers=new HashMap<>(req.getRequestHeaders());
      main.post(()->{if(w==web&&generation==pageGeneration&&!fareInFlight&&YandexSession.capture(app,url,headers)){contextReady=true;Diagnostics.detail("yandex_browser","context_ready","sessionAgeMs",0);}});
     }
     return super.shouldInterceptRequest(w,req);
    }
    @Override public void onPageStarted(WebView w,String url,android.graphics.Bitmap icon){ready=false;contextReady=false;pageGeneration++;Diagnostics.detail("yandex_browser","page_started","ready",false,"contextReady",false);if(pageObserver!=null)pageObserver.accept(url);}
    @Override public void onPageFinished(WebView w,String url){ready=trusted(url);String host=Uri.parse(url==null?"":url).getHost();if("passport.yandex.ru".equals(host)||"passport.yandex.com".equals(host)||"id.yandex.ru".equals(host))FareConnection.loginRequired(app);if(ready)CookieManager.getInstance().flush();Diagnostics.detail("yandex_browser","page_finished","ready",ready,"contextReady",contextReady);if(pageObserver!=null)pageObserver.accept(url);}
    @Override public boolean shouldOverrideUrlLoading(WebView w,WebResourceRequest req){
     Uri u=req.getUrl();String h=u.getHost();
     return !("https".equals(u.getScheme())&&h!=null&&(h.equals("yandex.ru")||h.endsWith(".yandex.ru")||h.equals("yandex.com")||h.endsWith(".yandex.com")));
    }
   });
   web.loadUrl("https://taxi.yandex.ru/");
  }
  return web;
 }
 static void detach(WebView w){if(w!=null&&w.getParent() instanceof ViewGroup)((ViewGroup)w.getParent()).removeView(w);}
 static void releaseIfIdle(){main.post(()->{if(web!=null&&web.getParent()==null){web.stopLoading();web.destroy();web=null;ready=false;}});}

 static void clearLocalSession(Runnable finished){
  if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("Main thread required");
  if(web!=null){detach(web);web.stopLoading();web.clearCache(true);web.clearHistory();web.destroy();web=null;ready=false;}
  WebStorage.getInstance().deleteAllData();
  CookieManager.getInstance().removeAllCookies(removed->{CookieManager.getInstance().flush();finished.run();});
 }
 static void refreshSession(Context context){
  if(FareConnection.remaining(context)>0||!FareConnection.online(context))return;
  WebView current=view(context);ready=false;contextReady=false;current.loadUrl("https://taxi.yandex.ru/");
  Diagnostics.info("yandex_browser","session_refresh_started");
 }
 static void networkRestored(Context context){main.post(()->{
  if(web!=null&&!fareInFlight&&(!ready||!contextReady)&&FareConnection.remaining(context)==0&&!FareConnection.needsLogin(context)){
   web.loadUrl("https://taxi.yandex.ru/");Diagnostics.info("yandex_browser","page_connection_restored");
  }
 });}
 static JSONObject fetch(Context context,JSONObject body,Map<String,String> session)throws Exception{
  if(Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException("Worker thread required");
  if(FareConnection.remaining(context)>0)throw FareConnection.rateError(context);
  if(FareConnection.needsLogin(context))throw new TaxiClient.ApiException(401,"response_login_required");
  if(!FareConnection.online(context))throw new IOException("Нет подключения к интернету");
  final long queuedAt=SystemClock.elapsedRealtime();final int diagnosticId=Diagnostics.request.get();
  Diagnostics.detail("yandex_browser","request_queued","requestId",diagnosticId);
  synchronized(serial){
   if(FareConnection.remaining(context)>0)throw FareConnection.rateError(context);
   Diagnostics.detail("yandex_browser","request_dequeued","requestId",diagnosticId,"waitMs",SystemClock.elapsedRealtime()-queuedAt);
   final CountDownLatch done=new CountDownLatch(1);
   final JSONObject[] result=new JSONObject[1];final Exception[] failure=new Exception[1];
   final String key="sense_fare_"+UUID.randomUUID().toString().replace("-","");
   final long deadline=SystemClock.elapsedRealtime()+70000;
   main.post(new Runnable(){boolean started,retried;long attemptAt=Long.MAX_VALUE;long attemptDeadline=SystemClock.elapsedRealtime()+35000;WebView owner;Map<String,String> used=Collections.emptyMap();
    void finish(JSONObject data,Exception error){if(done.getCount()==0)return;if(retried&&error!=null)Diagnostics.info("yandex_browser","session_recovery_failed");fareInFlight=false;result[0]=data;failure[0]=error;done.countDown();if(owner!=null&&owner==web&&trusted(owner.getUrl()))owner.evaluateJavascript(FareScript.clear(key),null);}
    @Override public void run(){
     if(done.getCount()==0)return;
     if(FareConnection.remaining(context)>0){finish(null,FareConnection.rateError(context));return;}
     if(!FareConnection.online(context)){finish(null,new IOException("Нет подключения к интернету"));return;}
     if(SystemClock.elapsedRealtime()>=Math.min(deadline,attemptDeadline)){finish(null,new java.net.SocketTimeoutException("Страница Яндекса не ответила"));return;}
     try{
      if(owner==null)owner=view(context);
      if(owner!=web){finish(null,new IOException("Страница Яндекса закрыта"));return;}
      if(!ready||!contextReady||!trusted(owner.getUrl())){
       if(started){finish(null,new IOException("Откройте Яндекс для проверки входа"));return;}
       main.postDelayed(this,250);return;
      }
      if(!started){
       started=true;attemptAt=SystemClock.elapsedRealtime();
       Map<String,String> active=YandexSession.headers(context);
       if(active.isEmpty()){
        if(!retried&&recovery.begin(401,"refused",false,SystemClock.elapsedRealtime())){retried=true;started=false;refreshSession(context);main.postDelayed(this,250);return;}
        FareConnection.loginRequired(context);finish(null,new TaxiClient.ApiException(401,"response_login_required"));return;
       }
       used=active;
       JSONObject currentBody=new JSONObject(body.toString());
       currentBody.put("id",active.get("X-YaTaxi-UserId"));
       Diagnostics.detail("yandex_browser","request_context","requestId",diagnosticId,"ready",ready,"contextReady",contextReady,"sessionAgeMs",Math.max(0,System.currentTimeMillis()-context.getSharedPreferences("radar",0).getLong("yandexSessionAt",0)));
       fareInFlight=true;
       owner.evaluateJavascript(FareScript.start(key,currentBody,active),value->{if(!"true".equals(value))finish(null,new IOException("Недоступна страница Яндекса"));else main.postDelayed(this,200);});
       return;
      }
      owner.evaluateJavascript(FareScript.poll(key),value->{
       if(done.getCount()==0)return;
       if(owner!=web||!trusted(owner.getUrl())){finish(null,new IOException("Страница Яндекса закрыта"));return;}
       try{
        JSONObject envelope=FareScript.decode(value);String kind=envelope.optString("kind","pending");
        if("pending".equals(kind)){main.postDelayed(this,250);return;}
        int http=nativeHttp.resolve(envelope.optInt("http",0),attemptAt);if(http==429){FareConnection.limited(context,envelope.optString("retryAfter",null));finish(null,FareConnection.rateError(context));return;}if(http==401&&"network".equals(kind))kind="refused";Diagnostics.detail("yandex_browser","fetch_result","requestId",diagnosticId,"http",http,"kind",envelope.optString("kind"),"stage",envelope.optString("stage"),"rttMs",envelope.optLong("durationMs",0),"requestBytes",envelope.optLong("requestBytes",0),"responseBytes",envelope.optLong("responseBytes",0));
        String detail=java.util.Arrays.asList("ok","verification","refused","timeout","network","invalid_json","schema","too_large","empty","invalid_response").contains(kind)?kind:"invalid_response";
        Diagnostics.record("yandex_browser","fare_"+("ok".equals(kind)?"received":detail),http==200?"info":"error",null,http,-1,-1,null);
        if(http==401||http==403)YandexSession.rejected(context,used);
        if(recovery.begin(http,kind,retried,SystemClock.elapsedRealtime())&&!context.getSharedPreferences("radar",0).getBoolean("yandexRemoved",false)){
         retried=true;fareInFlight=false;owner.evaluateJavascript(FareScript.clear(key),null);
         started=false;attemptDeadline=SystemClock.elapsedRealtime()+35000;
         Diagnostics.info("yandex_browser","session_recovery_started");
         refreshSession(context);main.postDelayed(this,250);return;
        }
        if(retried&&http==200&&"ok".equals(kind))Diagnostics.info("yandex_browser","session_recovery_succeeded");
        if(http==401||http==403)FareConnection.loginRequired(context);
        if(http!=200){finish(null,http>0?new TaxiClient.ApiException(http,"verification".equals(kind)?"response_verification":"response_browser_refused"):new IOException("Не удалось получить ответ страницы"));return;}
        JSONObject data=envelope.getJSONObject("data");
        if(data.optJSONArray("service_levels")==null)throw new JSONException("service_levels");
        YandexSession.verified(context,used);FareConnection.succeeded(context);
        finish(data,null);
       }catch(Exception e){finish(null,new IOException("Изменился формат ответа Яндекса",e));}
      });
     }catch(Exception e){finish(null,e);}
    }
   });
   try{
    if(!done.await(72,TimeUnit.SECONDS)){done.countDown();main.post(()->fareInFlight=false);throw new java.net.SocketTimeoutException("Время ожидания Яндекса истекло");}
   }catch(InterruptedException e){done.countDown();main.post(()->fareInFlight=false);Thread.currentThread().interrupt();throw e;}
   if(failure[0]!=null)throw failure[0];
   if(result[0]==null)throw new IOException("Нет данных Яндекса");
   return result[0];
  }
 }
}

