package dev.raf.radar;
import android.app.*;
import android.content.*;
import android.widget.*;
/** One local Yandex session, managed inline in the existing Profile group. */
final class YandexProfile {
 private static boolean checking;
 static void add(MainActivity a,LinearLayout profile){
  a.settingsDivider(profile);
  TextView title=a.text("Яндекс авторизация",14,a.fg);title.setPadding(0,a.dp(14),0,a.dp(4));profile.addView(title);
  profile.addView(a.text(YandexSession.status(a),12,a.muted));
  if(YandexSession.saved(a)){
   a.settingAction(profile,"Проверить вход Яндекса",()->check(a));
   a.settingAction(profile,"Войти в Яндекс",()->a.startActivity(new Intent(a,LoginActivity.class)));
   a.settingAction(profile,"Удалить сессию Яндекса",()->remove(a));
  }else a.settingAction(profile,"Войти в Яндекс",()->a.startActivity(new Intent(a,LoginActivity.class)));
 }
 static void result(Activity a,boolean success){Toast.makeText(a,success?"Вход выполнен":"Вход не выполнен",Toast.LENGTH_LONG).show();}
 static void check(MainActivity a){
  if(checking)return;
  Point p=null;
  if(LocationState.fresh(a.store))p=new Point("me","Проверка",Double.longBitsToDouble(a.store.prefs.getLong("lat",0)),Double.longBitsToDouble(a.store.prefs.getLong("lon",0)),true);
  else for(Point point:a.store.points())if(!point.dynamic&&point.enabled){p=point;break;}
  if(p==null){result(a,false);return;}
  checking=true;Point point=p;
  ProgressDialog progress=new ProgressDialog(a);progress.setMessage("Проверяем вход…");progress.setCancelable(false);progress.show();
  new Thread(()->{
   boolean ok=false;
   try{new TaxiClient(a).fetch(point);a.store.prefs.edit().putLong("yandexProbeOk",System.currentTimeMillis()).apply();ok=true;}
   catch(Exception e){Diagnostics.error("yandex_auth","profile_check_failed",e,e instanceof TaxiClient.ApiException?((TaxiClient.ApiException)e).status:0,-1,-1,null);}
   final boolean success=ok;
   a.runOnUiThread(()->{checking=false;if(!a.isDestroyed()&&!a.isFinishing()){progress.dismiss();a.rebuildSettings();result(a,success);}if(!MonitorService.running)BrowserFare.releaseIfIdle();});
  },"yandex-profile-check").start();
 }
 static void remove(MainActivity a){
  new AlertDialog.Builder(a).setTitle("Удалить сессию Яндекса?").setMessage("Локальный вход будет очищен, мониторинг остановлен.").setNegativeButton("Отмена",null).setPositiveButton("Удалить",(d,w)->{
   YandexSession.clear(a);a.store.prefs.edit().putBoolean("diagMonitorExpected",false).apply();
   if(MonitorService.running)a.startService(new Intent(a,MonitorService.class).setAction("stop"));
   BrowserFare.clearLocalSession(()->{if(!a.isDestroyed()&&!a.isFinishing())a.rebuildSettings();});
  }).show();
 }
}
