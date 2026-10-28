package dev.raf.radar;
import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.util.*;
/** Local session management; credentials are entered only on the official Yandex page. */
public final class LoginActivity extends Activity {
 private Store store;
 private LinearLayout root,content;
 private WebView web;
 private TextView hint;
 private Button test;
 private ProgressBar progress;
 private LinearLayout returnPanel;
 private TextView returnStatus;
 private boolean returned;
 private boolean testing,signing;
 private int fg,muted,bg,card,accent;
 private final java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();
 int dp(int n){return RadarUi.dp(this,n);}
 TextView label(String s,int size,int color,boolean bold){TextView t=RadarUi.text(this,s,size,color,bold);t.setLineSpacing(dp(3),1);return t;}
 LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
 void space(LinearLayout v,int n){v.addView(new View(this),new LinearLayout.LayoutParams(1,dp(n)));}
 Button button(LinearLayout parent,String title,boolean primary,Runnable action){
  Button v=new Button(this);v.setAllCaps(false);v.setText(title);v.setTextSize(15);v.setTextColor(primary&&!Appearance.lineage(this)?Color.BLACK:fg);
  v.setMinHeight(dp(50));v.setMinimumHeight(dp(50));RadarUi.touch(v,primary?accent:card,14,0);
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(10);parent.addView(v,lp);v.setOnClickListener(w->action.run());return v;
 }
 @Override public void onCreate(Bundle state){super.onCreate(state);store=new Store(this);
  boolean fantasy=Appearance.lineage(this),dark=store.prefs.getBoolean("dark",true);
  fg=fantasy?Appearance.TEXT:dark?0xFFF5F5F7:0xFF17191D;
  muted=fantasy?Appearance.MUTED:dark?0xFF92969F:0xFF646974;
  bg=fantasy?Appearance.INK:dark?RadarUi.BACKGROUND:0xFFF3F4F6;
  card=fantasy?Appearance.PANEL:dark?RadarUi.CARD:Color.WHITE;accent=Appearance.accent(this);
  getWindow().setStatusBarColor(bg);getWindow().setNavigationBarColor(bg);
  if(!fantasy&&!dark)getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
  showLogin();
 }
 void shell(String subtitle){
  root=column();root.setBackgroundColor(bg);root.setPadding(dp(20),dp(16),dp(20),dp(12));
  root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(dp(20),dp(16)+insets.getSystemWindowInsetTop(),dp(20),dp(12)+insets.getSystemWindowInsetBottom());return insets;});
  TextView back=label("‹  Профиль",15,accent,true);back.setMinHeight(dp(48));back.setGravity(Gravity.CENTER_VERTICAL);back.setOnClickListener(v->{if(!testing)finish();});back.setFocusable(true);root.addView(back);
  root.addView(label("Яндекс авторизация",25,fg,true));space(root,8);root.addView(label(subtitle,13,muted,false));space(root,18);
  setContentView(root);root.requestApplyInsets();
 }
 void showLogin(){
  YandexSession.enable(this);signing=true;returned=false;shell("Вход через официальный сайт Яндекса.");
  hint=label("Войдите в аккаунт Яндекса. После выбора аккаунта вход проверится автоматически.",13,muted,false);root.addView(hint);
  progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setIndeterminate(true);progress.setVisibility(View.GONE);root.addView(progress,new LinearLayout.LayoutParams(-1,dp(4)));
  test=button(root,"Проверить вход",true,this::probe);space(root,12);
  FrameLayout website=new FrameLayout(this);root.addView(website,new LinearLayout.LayoutParams(-1,0,1));
  web=new WebView(this);
  web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);
  web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);
  web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
  CookieManager.getInstance().setAcceptCookie(true);
  website.addView(web,new FrameLayout.LayoutParams(-1,-1));
  returnPanel=column();returnPanel.setGravity(Gravity.CENTER);returnPanel.setPadding(dp(24),dp(24),dp(24),dp(24));Appearance.panel(returnPanel,card,20);
  returnPanel.addView(label("Аккаунт выбран",22,fg,true));space(returnPanel,12);
  returnStatus=label("Завершаем вход…",15,muted,false);returnStatus.setGravity(Gravity.CENTER);returnPanel.addView(returnStatus);
  website.addView(returnPanel,new FrameLayout.LayoutParams(-1,-1));returnPanel.setVisibility(View.GONE);
  web.setWebViewClient(new WebViewClient(){
   @Override public void onPageStarted(WebView w,String url,android.graphics.Bitmap icon){authPage(url,false);}
   @Override public void onPageFinished(WebView w,String url){authPage(url,true);}
   @Override public boolean shouldOverrideUrlLoading(WebView w,WebResourceRequest request){
    android.net.Uri u=request.getUrl();String h=u.getHost();
    return !("https".equals(u.getScheme())&&h!=null&&(h.equals("yandex.ru")||h.endsWith(".yandex.ru")||h.equals("yandex.com")||h.endsWith(".yandex.com")));
   }
   @Override public void onReceivedError(WebView w,WebResourceRequest request,WebResourceError error){if(request.isForMainFrame())hint.setText("Не удалось открыть страницу входа. Проверьте интернет.");}
  });
  web.loadUrl("https://passport.yandex.ru/auth?retpath=https%3A%2F%2Ftaxi.yandex.ru%2F");
 }
 void authPage(String url,boolean finished){
  if(isDestroyed()||isFinishing()||web==null)return;
  boolean taxi=BrowserFare.trusted(url);
  web.setVisibility(taxi?View.INVISIBLE:View.VISIBLE);returnPanel.setVisibility(taxi?View.VISIBLE:View.GONE);
  if(taxi){hint.setText("Проверяем вход…");if(finished&&!returned){
   returned=true;CookieManager.getInstance().flush();BrowserFare.refreshSession(this);probe();
  }}
 }
 void probe(){
  if(testing)return;
  Point candidate=null;
  if(LocationState.fresh(store))candidate=new Point("me","Проверка",Double.longBitsToDouble(store.prefs.getLong("lat",0)),Double.longBitsToDouble(store.prefs.getLong("lon",0)),true);
  else for(Point p:store.points())if(!p.dynamic&&p.enabled){candidate=p;break;}
  if(candidate==null){hint.setText("Для проверки включите геолокацию или добавьте сохранённую точку в приложении.");return;}
  testing=true;test.setEnabled(false);progress.setVisibility(View.VISIBLE);hint.setText("Проверяем доступ к тарифам…");
  Point point=candidate;
  worker.execute(()->{
   boolean success=false;String message;
   try{
    List<Store.Reading> readings=new TaxiClient(this).fetch(point);
    store.prefs.edit().putLong("yandexProbeOk",System.currentTimeMillis()).apply();
    boolean rub=false;for(Store.Reading r:readings)if(r.rub!=null)rub=true;
    success=true;message=rub?"Доступ подтверждён. Надбавка получена — можно вернуться к мониторингу.":"Доступ к тарифам подтверждён. Надбавка в рублях для этой точки пока недоступна.";
    Diagnostics.info("yandex_auth",rub?"probe_surcharge_received":"probe_no_ruble_surcharge");
   }catch(TaxiClient.ApiException e){
    message=e.status==401?"Яндекс не подтвердил вход. Войдите в аккаунт на странице и повторите проверку.":e.status==429?"Яндекс временно ограничил запросы. Попробуйте позже.":"Проверка не прошла. Откройте страницу Яндекса и проверьте, не требуется ли подтверждение входа.";
    Diagnostics.error("yandex_auth","probe_http_error",e,e.status,-1,-1,null);
   }catch(Exception e){message="Не удалось проверить вход. Проверьте интернет и повторите попытку.";Diagnostics.error("yandex_auth","probe_failed",e,0,-1,-1,null);}
   final boolean ok=success;final String detail=message;
   runOnUiThread(()->{if(isFinishing()||isDestroyed())return;testing=false;showResult(ok,detail);Diagnostics.sendManual(this,delivery->{});});
  });
 }
 void showResult(boolean success,String message){
  if(progress!=null)progress.setVisibility(View.GONE);
  if(test!=null)test.setEnabled(true);
  YandexProfile.result(this,success);
  if(returnStatus!=null)returnStatus.setText(success?"Вход выполнен":"Вход не выполнен. Можно повторить проверку или вернуться в профиль.");
  hint.setText(success?"Вход выполнен":"Вход не выполнен");
  if(success){CookieManager.getInstance().flush();finish();}
 }
 @Override public void onBackPressed(){if(testing){Toast.makeText(this,"Дождитесь завершения проверки",Toast.LENGTH_SHORT).show();return;}super.onBackPressed();}
 @Override public void onDestroy(){worker.shutdownNow();if(web!=null){BrowserFare.detach(web);web.stopLoading();web.destroy();}if(!MonitorService.running)BrowserFare.releaseIfIdle();super.onDestroy();}
}
