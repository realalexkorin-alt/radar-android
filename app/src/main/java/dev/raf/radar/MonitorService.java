package dev.raf.radar;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.*;
import android.os.*;
import java.util.*;
import java.util.concurrent.*;

public final class MonitorService extends Service implements LocationListener {
  public static volatile boolean running = false;
  private Store store;
  private ScheduledExecutorService queue;
  private LocationManager locations;
  private Overlay overlay;
  private final PollPlanner planner = new PollPlanner();
  private final SharedPreferences.OnSharedPreferenceChangeListener appearanceListener=(prefs,key)->{
    if("yandexProbeOk".equals(key)&&queue!=null&&!queue.isShutdown())queue.execute(()->{
      planner.authorizationRestored(SystemClock.elapsedRealtime());
      long pause=planner.globalPause(SystemClock.elapsedRealtime());
      store.prefs.edit().putLong("diagPauseUntil",pause>0?System.currentTimeMillis()+pause:0).apply();
      Diagnostics.info("monitor","authorization_restored");
      tick();broadcast();
    });
    if(key!=null&&java.util.Arrays.asList(Appearance.KEY,"scale","textScale","opacity","widgetUpdateTime","showTariff","dark","overlay","points","overlayPoint","widgetTariffs").contains(key))
      new Handler(Looper.getMainLooper()).post(()->{if(overlay!=null)overlay.refresh();});
    if(key!=null&&java.util.Arrays.asList("points","widgetTariffs","overlayPoint","overlay").contains(key)&&queue!=null)
      queue.execute(()->{tick();});
  };
  private long lastGpsSubscribe,lastQueueDiagnostic;
  private final Runnable gpsWatchdog=new Runnable(){public void run(){if(!running||stopping)return;if(!LocationState.fresh(store)&&SystemClock.elapsedRealtime()-lastGpsSubscribe>=120000)subscribeGps();cueHandler.postDelayed(this,30000);}};
  private boolean announced,stopping;
  private final android.os.Handler cueHandler=new android.os.Handler(android.os.Looper.getMainLooper());
  private final BroadcastReceiver manual =
      new BroadcastReceiver() {
        public void onReceive(Context c, Intent i) {
          if (queue != null)
            queue.execute(
                () -> {
                  tick();
                });
        }
      };

  @Override
  public void onCreate() {
    super.onCreate();
    if (!Access.monitorReady(this)) { Diagnostics.once("permissions","monitor_unavailable",true); stopSelf(); return; }
    store = new Store(this);
    NotificationManager nm = getSystemService(NotificationManager.class);
    nm.createNotificationChannel(
        new NotificationChannel("monitor", "Мониторинг", NotificationManager.IMPORTANCE_LOW));
    nm.createNotificationChannel(
        new NotificationChannel("alerts", "Надбавки", NotificationManager.IMPORTANCE_DEFAULT));
    Intent stop = new Intent(this, MonitorService.class).setAction("stop");
    Notification n =
        new Notification.Builder(this, "monitor")
            .setSmallIcon(dev.raf.radar.R.drawable.ic_radar)
            .setContentTitle("Радар · мониторинг включён")
            .setContentText("Очередь точек · остановка в любой момент")
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE))
            .addAction(
                new Notification.Action.Builder(
                        null,
                        "Остановить",
                        PendingIntent.getService(this, 0, stop, PendingIntent.FLAG_IMMUTABLE))
                    .build())
            .build();
    startForeground(1, n);
    running = true;
    store.prefs.edit().putBoolean("diagMonitorExpected",true).putLong("diagMonitorSince",System.currentTimeMillis()).putLong("diagTickAt",System.currentTimeMillis()).apply();
    Diagnostics.info("monitor","started");
    PresenceClient.start(this);
    IntentFilter requests = new IntentFilter("dev.raf.radar.MANUAL");
    requests.addAction("dev.raf.radar.VIEWING");
    androidx.core.content.ContextCompat.registerReceiver(
        this,
        manual,
        requests,
        androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);
    locations = (LocationManager) getSystemService(LOCATION_SERVICE);
    subscribeGps();
    cueHandler.postDelayed(gpsWatchdog,30000);
    overlay = new Overlay(this, store);
    store.prefs.registerOnSharedPreferenceChangeListener(appearanceListener);
    queue = Executors.newSingleThreadScheduledExecutor();
    queue.scheduleWithFixedDelay(this::tick, 1, 1, TimeUnit.SECONDS);
  }

  @Override
  public int onStartCommand(Intent i, int flags, int id) {
    if (store == null || !Access.monitorReady(this)) { stopSelf(); return START_NOT_STICKY; }
    if (i != null && "stop".equals(i.getAction())) {
      // A newer queued start wins over an earlier stop from rapid button presses.
      stopping=true;store.prefs.edit().putBoolean("diagMonitorExpected",false).apply();Diagnostics.info("monitor","user_stop");AppSounds.stopMonitor();stopSelf(id);
      return START_NOT_STICKY;
    }
    stopping=false;
    if (overlay != null) overlay.refresh();
    if(i!=null&&"user_start".equals(i.getAction())&&!announced){announced=true;cueHandler.post(()->{if(running&&!stopping)AppSounds.play(this,AppSounds.Event.MONITOR_READY);});}
    sendBroadcast(new Intent("dev.raf.radar.REFRESH").setPackage(getPackageName()));
    return START_NOT_STICKY;
  }

  private void tick() {
    String attempted=null;long requestStarted=0;boolean requestSuccess=false;int requestHttp=0;
    try {
      if (!Access.monitorReady(this)) { Diagnostics.once("permissions","monitor_unavailable",true); stopSelf(); return; }
      long now = System.currentTimeMillis();
      store.prefs.edit().putLong("diagTickAt",now).apply();
      List<Point> a = store.points();
      List<String> eligible = new ArrayList<>();
      boolean browsing = store.prefs.getLong("viewingUntil", 0) > now;
      String widgetPoint = store.prefs.getBoolean("overlay", false)
          ? store.widgetPoint().id : "";
      for (Point q : a) {
        if (!PollPlanner.needed(q, browsing, widgetPoint)) continue;
        if (q.dynamic) {
          long age = now - store.prefs.getLong("gpsAt", 0);
          if (age < 0 || age > 120000) {Diagnostics.once("gps","own_point_waiting_fix",false);continue;}
          q.lat = Double.longBitsToDouble(store.prefs.getLong("lat", 0));
          q.lon = Double.longBitsToDouble(store.prefs.getLong("lon", 0));
        }
        eligible.add(q.id);
      }
      if(Diagnostics.verbose()&&SystemClock.elapsedRealtime()-lastQueueDiagnostic>=15000){lastQueueDiagnostic=SystemClock.elapsedRealtime();Diagnostics.detail("monitor","queue_state",planner.diagnosticState(eligible,lastQueueDiagnostic,"me"));}
      String id = planner.nextMinute(eligible, android.os.SystemClock.elapsedRealtime(), "me");
      if (id == null) {Diagnostics.once("monitor","queue_wait",false);return;}
      Point p = null;
      for (Point q : a)
        if (q.id.equals(id)) {
          p = q;
          break;
        }
      if (p == null) return;
      attempted=p.id;requestStarted=SystemClock.elapsedRealtime();int trace=Diagnostics.sequence.incrementAndGet();Diagnostics.request.set(trace);
      Diagnostics.detail(Diagnostics.pointArea(p.id),"request_started","requestId",trace,"ageMs",Math.max(0,now-store.prefs.getLong("diagPointSuccess:"+p.id,now)));
      Point request=Point.from(p.json());
      request.tariffs=TariffSelection.requested(p,store.widgetTariffs(),p.id.equals(widgetPoint));
      store.prefs.edit().putLong("diagAttemptAt",now).putLong("diagPointAttempt:"+p.id,now).apply();
      Diagnostics.info(Diagnostics.pointArea(p.id),"request");
      Diagnostics.record("taxi","request","info",null,0,-1,-1,p.dynamic?"own":"saved");
      List<Store.Reading> readings = new TaxiClient(this).fetch(request);
      if (p.dynamic) {
        float[] distance = new float[1];
        Location.distanceBetween(
            p.lat,
            p.lon,
            Double.longBitsToDouble(store.prefs.getLong("lat", 0)),
            Double.longBitsToDouble(store.prefs.getLong("lon", 0)),
            distance);
        if (distance[0] > 250) {
          Diagnostics.info("taxi","result_discarded_moved");
          Diagnostics.detail(Diagnostics.pointArea(p.id),"result_discarded","reason","moved");planner.expedite("me");
          return;
        }
      }
      Point current = null;
      for (Point candidate : store.points()) if (candidate.id.equals(p.id)) current = candidate;
      if (current == null || !current.enabled || (!p.dynamic && (current.lat != p.lat || current.lon != p.lon))){Diagnostics.detail(Diagnostics.pointArea(p.id),"result_discarded","reason",current==null||!current.enabled?"disabled":"changed");return;}
      List<String> stillNeeded=TariffSelection.requested(current,store.widgetTariffs(),store.prefs.getBoolean("overlay",false)&&current.id.equals(store.widgetPoint().id));
      if(!request.tariffs.containsAll(stillNeeded)){Diagnostics.detail(Diagnostics.pointArea(p.id),"result_discarded","reason","tariffs_changed");planner.expedite(p.id);return;}
      p.notify = current.notify; p.threshold = current.threshold;
      store.readings(p.id, readings);
      Statistics.capture(this,request,readings);
      if(p.dynamic)store.prefs.edit().putLong("readQueryLat",Double.doubleToLongBits(request.lat)).putLong("readQueryLon",Double.doubleToLongBits(request.lon)).apply();
      store.prefs.edit().putString("status", "Обновлено · " + p.name).apply();
      planner.succeeded(p.id);
      Store.Reading best = null;
      for (Store.Reading r : readings)
        if (current.tariffs.contains(r.tariff) && r.rub != null && r.fresh() && (best == null || r.rub > best.rub)) best = r;
      boolean valid=false;for(Store.Reading r:readings)if(r.rub!=null&&r.fresh())valid=true;
      requestSuccess=valid;requestHttp=200;
      if(valid){store.prefs.edit().putLong("diagSuccessAt",System.currentTimeMillis()).putLong("diagPointSuccess:"+p.id,System.currentTimeMillis()).putLong("diagPauseUntil",0).apply();Diagnostics.info("taxi","surcharge_updated");Diagnostics.info(Diagnostics.pointArea(p.id),"surcharge_updated");}
      else {String reason="missing_ruble_surcharge";for(Store.Reading r:readings){if(r.status!=null&&r.status.startsWith("Нет базовой цены"))reason="missing_base_price";}Diagnostics.error("taxi",reason,null,0,-1,-1,p.dynamic?"own":"saved");}
      if (best != null) alert(p, best);
      broadcast();
    } catch (TaxiClient.ApiException e) {
      requestHttp=e.status;failedPoint(attempted,e.status,e);
    } catch (Exception e) {
      failedPoint(attempted,0,e);
    } finally {if(requestStarted>0){long elapsed=SystemClock.elapsedRealtime()-requestStarted;Diagnostics.load.completed(SystemClock.elapsedRealtime(),elapsed,requestSuccess);store.prefs.edit().putLong("diagFinishedAt",System.currentTimeMillis()).apply();Diagnostics.detail(Diagnostics.pointArea(attempted),"request_finished","requestId",Diagnostics.request.get(),"rttMs",elapsed,"successes",requestSuccess?1:0,"failures",requestSuccess?0:1);}}
  }

  private void failedPoint(String id,int status,Exception error){
    if(id!=null){boolean active=false;for(Point point:store.points())if(point.id.equals(id)&&point.enabled)active=true;if(!active)return;}
    long elapsed=SystemClock.elapsedRealtime();
    long pause;
    if(id==null){pause=60000;planner.backoff(elapsed,pause);}
    else pause=planner.failed(id,status,elapsed);
    long global=planner.globalPause(elapsed);
    store.prefs.edit().putLong("diagPauseUntil",global>0?System.currentTimeMillis()+global:0).apply();
    Diagnostics.error("taxi",status==0?"request_failed":"http_backoff",error,status,-1,pause/1000,null);
    if(id!=null)Diagnostics.error(Diagnostics.pointArea(id),"retry_scheduled",error,status,-1,pause/1000,null);
    store.prefs.edit().putString("status",global>10000?"Сервис недоступен · повтор позже":"Ошибка точки · остальные обновляются").apply();
    broadcast();
  }

  private void subscribeGps(){
    if(locations==null||stopping||!Access.location(this))return;
    lastGpsSubscribe=SystemClock.elapsedRealtime();
    try{locations.removeUpdates(this);}catch(RuntimeException e){Diagnostics.error("gps","unsubscribe_failed",e,0,-1,-1,null);}
    int subscribed=0;
    for(String provider:locations.getAllProviders()){
      if(LocationManager.PASSIVE_PROVIDER.equals(provider))continue;
      try{
        locations.requestLocationUpdates(provider,15000,0,this,Looper.getMainLooper());
        subscribed++;
        Location last=locations.getLastKnownLocation(provider);if(last!=null)onLocationChanged(last);
      }catch(SecurityException e){Diagnostics.error("gps","permission_denied",e,0,-1,-1,null);}
      catch(RuntimeException e){Diagnostics.error("gps","subscribe_failed",e,0,-1,-1,null);}
    }
    Diagnostics.once("gps",subscribed==0?"no_providers":"subscriptions_refreshed",subscribed==0);
  }

  private void alert(Point p, Store.Reading r) {
    String key = p.id;
    long now = System.currentTimeMillis();
    if (!AlertPolicy.shouldNotify(
        p.notify,
        p.threshold,
        r.rub,
        r.fresh(),
        now,
        store.prefs.getLong("alertAt:" + key, 0),
        store.prefs.getInt("alertRub:" + key, 0))) return;
    Notification n =
        new Notification.Builder(this, Appearance.notificationChannel(this,"alerts","Надбавки"))
            .setSmallIcon(R.drawable.ic_radar)
            .setContentTitle(p.name + " · " + Point.label(r.tariff))
            .setContentText("Надбавка " + r.value())
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    key.hashCode(),
                    new Intent(this, MainActivity.class).putExtra("point", p.id),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE))
            .build();
    if (Build.VERSION.SDK_INT < 33
        || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED) {
      getSystemService(NotificationManager.class)
          .notify(100 + Math.abs(key.hashCode() % 100000), n);
      store.prefs.edit().putLong("alertAt:" + key, now).putInt("alertRub:" + key, r.rub).apply();
    }
  }

  private void broadcast() {
    sendBroadcast(new Intent("dev.raf.radar.REFRESH").setPackage(getPackageName()));
    new Handler(Looper.getMainLooper())
        .post(
            () -> {
              if (overlay != null) overlay.refresh();
            });
  }

  @Override
  public void onLocationChanged(Location l) {
    if (LocationState.accept(store,l)) {
      Diagnostics.once("gps","fix_accepted",false);
      if (store.readings("me").isEmpty()) planner.expedite("me");
      broadcast();
    }
  }

  @Override
  public void onProviderEnabled(String p) {Diagnostics.once("gps","provider_enabled",false);if(!stopping)subscribeGps();}

  @Override
  public void onProviderDisabled(String p) {Diagnostics.once("gps","provider_disabled",true);}

  @Override
  public void onStatusChanged(String p, int s, Bundle b) {}

  @Override
  public void onDestroy() {
    if(store!=null&&store.prefs.getBoolean("diagMonitorExpected",false))Diagnostics.error("monitor","unexpected_stop",null,0,-1,-1,null);
    else Diagnostics.info("monitor","stopped");
    running = false;cueHandler.removeCallbacksAndMessages(null);AppSounds.stopMonitor();
    PresenceClient.stop(this);
    sendBroadcast(new Intent("dev.raf.radar.REFRESH").setPackage(getPackageName()));
    if(store!=null)store.prefs.unregisterOnSharedPreferenceChangeListener(appearanceListener);
    try {
      unregisterReceiver(manual);
    } catch (Exception ignored) {
    }
    if (queue != null) queue.shutdownNow();
    if (locations != null) locations.removeUpdates(this);
    if (overlay != null) overlay.close();
    BrowserFare.releaseIfIdle();
    super.onDestroy();
  }

  @Override
  public IBinder onBind(Intent i) {
    return null;
  }
}

