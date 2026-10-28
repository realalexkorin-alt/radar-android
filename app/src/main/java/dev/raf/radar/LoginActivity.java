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
  showSessions();
 }
 void shell(String subtitle){
  root=column();root.setBackgroundColor(bg);root.setPadding(dp(20),dp(16),dp(20),dp(12));
  root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(dp(20),dp(16)+insets.getSystemWindowInsetTop(),dp(20),dp(12)+insets.getSystemWindowInsetBottom());return insets;});
  TextView back=label("‹  "+(signing?"Сессии Яндекса":"Настройки"),15,accent,true);back.setMinHeight(dp(48));back.setGravity(Gravity.CENTER_VERTICAL);back.setOnClickListener(v->{if(signing&&!testing)showSessions();else if(!testing)finish();});back.setFocusable(true);root.addView(back);
  root.addView(label("Яндекс авторизация",25,fg,true));space(root,8);root.addView(label(subtitle,13,muted,false));space(root,18);
  setContentView(root);root.requestApplyInsets();
 }
 void showSessions(){
  if(web!=null){BrowserFare.detach(web);web=null;}
  signing=false;shell("Сессия для получения тарифов и обновления точек.");
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);content=column();scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  content.addView(label("ПОДКЛЮЧЁННЫЕ СЕССИИ",11,muted,true));space(content,12);
  boolean saved=YandexSession.saved(this);
  LinearLayout panel=column();panel.setPadding(dp(18),dp(18),dp(18),dp(18));Appearance.panel(panel,card,20);content.addView(panel);
  if(saved){
   panel.addView(label("Основная сессия",19,fg,true));space(panel,8);
   panel.addView(label(YandexSession.status(this),14,accent,true));
   long checked=store.prefs.getLong("yandexVerifiedAt",0);
   if(checked>0){space(panel,8);panel.addView(label("Последняя успешная проверка: "+android.text.format.DateFormat.format("dd.MM · HH:mm",checked),12,muted,false));}
   button(panel,"Проверить вход",true,this::showLogin);
   button(panel,"Удалить сессию",false,this::confirmDelete);
  }else{
   panel.addView(label("Нет подключённых сессий",19,fg,true));space(panel,10);
   panel.addView(label("Войдите в Яндекс, чтобы приложение могло получать тарифы.",14,muted,false));
  }
  Button add=button(content,"+  Добавить сессию",!saved,this::showLogin);add.setEnabled(!saved);add.setAlpha(saved?0.45f:1f);
  space(content,16);content.addView(label(saved?"Сейчас доступна одна сессия. Для смены аккаунта удалите текущую и добавьте новую.":"Сейчас можно подключить один аккаунт Яндекса.",13,muted,false));
  space(content,12);content.addView(label("Сессия хранится на этом устройстве. Пароль вводится на сайте Яндекса.",12,muted,false));
 }
 void confirmDelete(){
  new AlertDialog.Builder(this).setTitle("Удалить сессию Яндекса?").setMessage("Локальный вход будет очищен, мониторинг остановлен. Для продолжения потребуется войти снова.")
   .setNegativeButton("Отмена",null).setPositiveButton("Удалить",(d,w)->{
    YandexSession.clear(this);store.prefs.edit().putBoolean("diagMonitorExpected",false).apply();
    if(MonitorService.running)startService(new Intent(this,MonitorService.class).setAction("stop"));
    content.removeAllViews();content.addView(label("Удаляем сессию…",16,muted,false));
    BrowserFare.clearLocalSession(()->{if(!isFinishing()&&!isDestroyed())showSessions();});
   }).show();
 }
 void showLogin(){
  YandexSession.enable(this);signing=true;shell("Вход через официальный сайт Яндекса.");
  hint=label("1. Войдите в аккаунт на странице ниже.\n2. Выберите маршрут и наличные, дождитесь цен.\n3. Нажмите «Подтвердить вход». Поездка не заказывается.",13,muted,false);root.addView(hint);
  progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setIndeterminate(true);progress.setVisibility(View.GONE);root.addView(progress,new LinearLayout.LayoutParams(-1,dp(4)));
  test=button(root,"Подтвердить вход",true,this::probe);space(root,12);
  web=BrowserFare.view(this);BrowserFare.detach(web);root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
 }
 void probe(){
  if(testing)return;
  long now=System.currentTimeMillis(),previous=store.prefs.getLong("yandexTestAt",0);
  if(now>=previous&&now-previous<60000){hint.setText("Повторная проверка будет доступна через "+((60000-(now-previous)+999)/1000)+" с.");return;}
  Point candidate=null;
  if(LocationState.fresh(store))candidate=new Point("me","Проверка",Double.longBitsToDouble(store.prefs.getLong("lat",0)),Double.longBitsToDouble(store.prefs.getLong("lon",0)),true);
  else for(Point p:store.points())if(!p.dynamic&&p.enabled){candidate=p;break;}
  if(candidate==null){hint.setText("Для проверки включите геолокацию или добавьте сохранённую точку в приложении.");return;}
  if(YandexSession.headers(this).isEmpty()){hint.setText("Вход ещё не готов. Войдите на странице ниже, выберите маршрут и наличные, дождитесь цен.");return;}
  store.prefs.edit().putLong("yandexTestAt",now).apply();testing=true;test.setEnabled(false);progress.setVisibility(View.VISIBLE);hint.setText("Проверяем доступ к тарифам…");
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
  if(web!=null){BrowserFare.detach(web);web=null;}
  signing=true;shell(success?"Аккаунт готов к работе.":"Вход пока не подтверждён.");
  LinearLayout panel=column();panel.setPadding(dp(20),dp(24),dp(20),dp(24));Appearance.panel(panel,card,20);root.addView(panel);
  panel.addView(label(success?"✓":"!",38,success?0xFF66C78A:accent,true));space(panel,16);
  panel.addView(label(success?"Авторизация успешна":"Не удалось подтвердить вход",22,fg,true));space(panel,12);panel.addView(label(message,15,muted,false));space(root,10);
  if(!success)button(root,"Вернуться ко входу",true,this::showLogin);
  button(root,"К списку сессий",success,this::showSessions);
 }
 @Override public void onBackPressed(){if(testing){Toast.makeText(this,"Дождитесь завершения проверки",Toast.LENGTH_SHORT).show();return;}if(signing)showSessions();else super.onBackPressed();}
 @Override public void onDestroy(){worker.shutdownNow();if(web!=null)BrowserFare.detach(web);if(!MonitorService.running)BrowserFare.releaseIfIdle();super.onDestroy();}
}
