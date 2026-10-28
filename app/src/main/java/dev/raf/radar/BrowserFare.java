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
 private static boolean ready;
 private static boolean contextReady;
 private static volatile long pageGeneration;
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
   web.setWebViewClient(new WebViewClient(){
    @Override public WebResourceResponse shouldInterceptRequest(WebView w,WebResourceRequest req){
     long generation=pageGeneration;
     if("POST".equals(req.getMethod())&&YandexSession.capture(app,req.getUrl().toString(),req.getRequestHeaders()))main.post(()->{if(w==web&&generation==pageGeneration)contextReady=true;});
     return super.shouldInterceptRequest(w,req);
    }
    @Override public void onPageStarted(WebView w,String url,android.graphics.Bitmap icon){ready=false;contextReady=false;pageGeneration++;if(pageObserver!=null)pageObserver.accept(url);}
    @Override public void onPageFinished(WebView w,String url){ready=trusted(url);if(ready)CookieManager.getInstance().flush();if(pageObserver!=null)pageObserver.accept(url);}
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
  WebView current=view(context);ready=false;contextReady=false;current.loadUrl("https://taxi.yandex.ru/");
  Diagnostics.info("yandex_browser","session_refresh_started");
 }
 static JSONObject fetch(Context context,JSONObject body,Map<String,String> session)throws Exception{
  if(Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException("Worker thread required");
  synchronized(serial){
   final CountDownLatch done=new CountDownLatch(1);
   final JSONObject[] result=new JSONObject[1];final Exception[] failure=new Exception[1];
   final String key="sense_fare_"+UUID.randomUUID().toString().replace("-","");
   final long deadline=SystemClock.elapsedRealtime()+35000;
   main.post(new Runnable(){boolean started;WebView owner;Map<String,String> used=Collections.emptyMap();
    void finish(JSONObject data,Exception error){if(done.getCount()==0)return;result[0]=data;failure[0]=error;done.countDown();if(owner!=null&&owner==web&&trusted(owner.getUrl()))owner.evaluateJavascript(FareScript.clear(key),null);}
    @Override public void run(){
     if(done.getCount()==0)return;
     if(SystemClock.elapsedRealtime()>=deadline){finish(null,new java.net.SocketTimeoutException("Страница Яндекса не ответила"));return;}
     try{
      if(owner==null)owner=view(context);
      if(owner!=web){finish(null,new IOException("Страница Яндекса закрыта"));return;}
      if(!ready||!contextReady||!trusted(owner.getUrl())){
       if(started){finish(null,new IOException("Откройте Яндекс для проверки входа"));return;}
       main.postDelayed(this,250);return;
      }
      if(!started){
       started=true;
       Map<String,String> active=YandexSession.headers(context);
       if(active.isEmpty()){finish(null,new IOException("Откройте Яндекс и войдите в аккаунт"));return;}
       used=active;
       JSONObject currentBody=new JSONObject(body.toString());
       currentBody.put("id",active.get("X-YaTaxi-UserId"));
       owner.evaluateJavascript(FareScript.start(key,currentBody,active),value->{if(!"true".equals(value))finish(null,new IOException("Недоступна страница Яндекса"));else main.postDelayed(this,200);});
       return;
      }
      owner.evaluateJavascript(FareScript.poll(key),value->{
       if(done.getCount()==0)return;
       if(owner!=web||!trusted(owner.getUrl())){finish(null,new IOException("Страница Яндекса закрыта"));return;}
       try{
        JSONObject envelope=FareScript.decode(value);String kind=envelope.optString("kind","pending");
        if("pending".equals(kind)){main.postDelayed(this,250);return;}
        int http=envelope.optInt("http",0);
        String detail=java.util.Arrays.asList("ok","verification","refused","timeout","network","invalid_json","schema","too_large","empty","invalid_response").contains(kind)?kind:"invalid_response";
        Diagnostics.record("yandex_browser","fare_"+("ok".equals(kind)?"received":detail),http==200?"info":"error",null,http,-1,-1,null);
        if(http==401||http==403)YandexSession.rejected(context,used);
        if(http!=200){finish(null,http>0?new TaxiClient.ApiException(http,"verification".equals(kind)?"response_verification":"response_browser_refused"):new IOException("Не удалось получить ответ страницы"));return;}
        JSONObject data=envelope.getJSONObject("data");
        if(data.optJSONArray("service_levels")==null)throw new JSONException("service_levels");
        YandexSession.verified(context,used);
        finish(data,null);
       }catch(Exception e){finish(null,new IOException("Изменился формат ответа Яндекса",e));}
      });
     }catch(Exception e){finish(null,e);}
    }
   });
   try{
    if(!done.await(37,TimeUnit.SECONDS)){done.countDown();throw new java.net.SocketTimeoutException("Время ожидания Яндекса истекло");}
   }catch(InterruptedException e){done.countDown();Thread.currentThread().interrupt();throw e;}
   if(failure[0]!=null)throw failure[0];
   if(result[0]==null)throw new IOException("Нет данных Яндекса");
   return result[0];
  }
 }
}
