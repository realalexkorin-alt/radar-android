package dev.raf.radar;
import android.content.*;
import android.net.*;
import android.os.SystemClock;
/** Shared by monitor, login probes and the website; no session secrets are stored here. */
final class FareConnection {
 static android.content.SharedPreferences prefs(Context c){return c.getSharedPreferences("radar",0);}
 static boolean online(Context c){
  ConnectivityManager cm=c.getSystemService(ConnectivityManager.class);NetworkCapabilities caps=cm==null?null:cm.getNetworkCapabilities(cm.getActiveNetwork());
  return caps!=null&&caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)&&caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
 }
 static synchronized long remaining(Context c){return Math.max(0,prefs(c).getLong("fareRateUntil",0)-System.currentTimeMillis());}
 static synchronized long limited(Context c,String retryAfter){
  long remaining=remaining(c);if(remaining>0)return remaining; // duplicate native/JS notifications must not extend a pause
  android.content.SharedPreferences p=prefs(c);int failures=p.getInt("fareRateFailures",0);
  long delay=RateLimitPolicy.delay(retryAfter,System.currentTimeMillis(),failures);
  long until=System.currentTimeMillis()+delay;
  p.edit().putLong("fareRateUntil",until).putInt("fareRateFailures",Math.min(5,failures+1)).putLong("diagPauseUntil",until).apply();
  Diagnostics.record("yandex_browser","rate_limit_pause","error",null,429,-1,delay/1000,null);
  return delay;
 }
 static synchronized void succeeded(Context c){
  android.content.SharedPreferences.Editor edit=prefs(c).edit().putBoolean("yandexNeedsLogin",false);
  if(remaining(c)==0)edit.remove("fareRateUntil").remove("fareRateFailures");
  edit.apply();
 }
 static boolean needsLogin(Context c){return prefs(c).getBoolean("yandexNeedsLogin",false);}
 static void loginRequired(Context c){prefs(c).edit().putBoolean("yandexNeedsLogin",true).apply();Diagnostics.once("yandex_auth","login_required",true);}
 static void manualLogin(Context c){prefs(c).edit().putBoolean("yandexNeedsLogin",false).apply();}
 static TaxiClient.ApiException rateError(Context c){return new TaxiClient.ApiException(429,"response_rate_limit",remaining(c));}
 static String issue(Context c,Store s){
  if(!online(c))return "Нет сети";
  long pause=remaining(c);if(pause>0)return "Пауза "+((pause+999)/1000)+"с";
  if(needsLogin(c))return "Нужен вход";
  if(!LocationState.fresh(s))return "Нет GPS";
  return "";
 }
}
