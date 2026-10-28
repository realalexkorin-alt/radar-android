package dev.raf.radar;

import android.app.*;
import android.content.*;
import android.net.*;
import android.os.*;
import android.webkit.*;
import android.widget.*;

/** Experimental website sign-in. No password extraction, JS bridge, or custom TLS overrides. */
public final class LoginActivity extends Activity {
  private WebView web;
  private TextView hint;
  private Button test;
  private boolean testing;
  private final Handler probeHandler=new Handler(Looper.getMainLooper());
  private final java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();

  @Override
  public void onCreate(Bundle b) {
    super.onCreate(b);
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    Button done = new Button(this);
    done.setText("Готово · вернуться в Радар");
    done.setOnClickListener(
        v -> {
          CookieManager.getInstance().flush();
          finish();
        });
    root.addView(done);
    hint = new TextView(this);
    hint.setPadding(16,12,16,12);
    hint.setText("Войдите на сайте Яндекса ниже и выберите маршрут и оплату наличными, не заказывая поездку. Когда сайт покажет цены, нажмите «Проверить запрос». Заказ поездки не создаётся. Проверяем Эконом с оплатой наличными. Cookies и платёжные данные в отчёт не входят.");
    root.addView(hint);
    test=new Button(this);test.setText("Проверить запрос");test.setOnClickListener(v->probe());root.addView(test);
    web = BrowserFare.view(this);
    BrowserFare.detach(web);
    root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
    setContentView(root);

  }


  private void probe(){
    if(testing)return;
    Store store=new Store(this);long now=System.currentTimeMillis();
    // A user-initiated probe is independent of the monitoring circuit breaker.
    long previous=store.prefs.getLong("yandexTestAt",0);
    if(now>=previous&&now-previous<60000){Updates.show(this,"Повторная проверка доступна через минуту");return;}
    Point candidate=null;
    if(LocationState.fresh(store)){
      candidate=new Point("me","Проверка",Double.longBitsToDouble(store.prefs.getLong("lat",0)),Double.longBitsToDouble(store.prefs.getLong("lon",0)),true);
    }else for(Point p:store.points())if(!p.dynamic&&p.enabled){candidate=p;break;}
    if(candidate==null){Updates.show(this,"Нет свежей геопозиции или включённых сохранённых точек");return;}
    boolean contextReady=!YandexSession.headers(this).isEmpty();
    Diagnostics.info("yandex_auth",contextReady?"session_context_present":"session_context_missing");
    if(!contextReady){hint.setText("Сессия запроса ещё не получена. Войдите на сайте ниже, выберите маршрут и наличные, дождитесь цен и повторите проверку.");return;}
    String apiCookie=CookieManager.getInstance().getCookie(TaxiClient.ENDPOINT);
    String webCookie=CookieManager.getInstance().getCookie("https://taxi.yandex.ru/");
    boolean apiCookies=apiCookie!=null&&!apiCookie.isEmpty(),webCookies=webCookie!=null&&!webCookie.isEmpty();
    Diagnostics.info("yandex_auth",apiCookies?"api_cookies_present":"api_cookies_absent");
    Diagnostics.info("yandex_auth",webCookies?"website_cookies_present":"website_cookies_absent");
    String cookies=apiCookies?"Cookies для запроса: есть (действительность не подтверждена).":"Cookies для запроса: нет.";
    store.prefs.edit().putLong("yandexTestAt",now).apply();
    testing=true;test.setEnabled(false);hint.setText(cookies+"\nПроверяем запрос…");
    Point point=candidate;
    worker.execute(()->{
      String result;
      try{
        java.util.List<Store.Reading> readings=new TaxiClient(this).fetch(point);
        store.prefs.edit().putLong("yandexProbeOk",System.currentTimeMillis()).apply();
        boolean rub=false;for(Store.Reading r:readings)if(r.rub!=null)rub=true;
        result=rub?"Запрос прошёл. Надбавка получена.":"Запрос прошёл, но надбавку в рублях рассчитать не удалось.";
        Diagnostics.info("yandex_auth",rub?"probe_surcharge_received":"probe_no_ruble_surcharge");
      }catch(TaxiClient.ApiException e){result="Запрос отклонён: HTTP "+e.status+" ("+e.reason+").";Diagnostics.error("yandex_auth","probe_http_error",e,e.status,-1,-1,null);}
      catch(java.net.UnknownHostException e){result="Ошибка DNS: телефон не нашёл адрес сервиса.";Diagnostics.error("yandex_auth","probe_dns_error",e,0,-1,-1,null);}
      catch(java.net.SocketTimeoutException e){result="Сервис не ответил вовремя.";Diagnostics.error("yandex_auth","probe_timeout",e,0,-1,-1,null);}
      catch(Exception e){result="Запрос не выполнен: "+e.getClass().getSimpleName()+".";Diagnostics.error("yandex_auth","probe_failed",e,0,-1,-1,null);}
      String message=result;
      runOnUiThread(()->{if(isFinishing()||isDestroyed())return;finishProbe(cookies,message);});
    });
  }


  private void finishProbe(String cookies,String message){
    testing=false;if(isFinishing()||isDestroyed())return;
    test.setEnabled(true);hint.setText(cookies+"\n"+message+"\nОтправляем диагностический отчёт…");
    Diagnostics.sendManual(this,delivery->hint.setText(cookies+"\n"+message+"\n"+delivery));
  }
  @Override
  public void onDestroy() {
    probeHandler.removeCallbacksAndMessages(null);
    worker.shutdownNow();
    if (web != null) {
      BrowserFare.detach(web);
      if(!MonitorService.running)BrowserFare.releaseIfIdle();
    }
    super.onDestroy();
  }
}
