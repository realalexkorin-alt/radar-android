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
    if(key!=null&&java.util.Arrays.asList("scale","textScale","opacity","widgetUpdateTime","showTariff","dark","overlay","points","overlayPoint","widgetTariffs").contains(key))
      new Handler(Looper.getMainLooper()).post(()->{if(overlay!=null)overlay.refresh();});
    if(key!=null&&java.util.Arrays.asList("points","widgetTariffs","overlayPoint","overlay").contains(key)&&queue!=null)
      queue.execute(()->{planner.refresh();tick();});
  };
  private int failures = 0;
  private boolean announced,stopping;
  private final android.os.Handler cueHandler=new android.os.Handler(android.os.Looper.getMainLooper());
  private final BroadcastReceiver manual =
      new BroadcastReceiver() {
        public void onReceive(Context c, Intent i) {
          if (queue != null)
            queue.execute(
                () -> {
                  planner.refresh();
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
    if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        == PackageManager.PERMISSION_GRANTED) {
      try {
        for (String provider : locations.getProviders(true)) {
          Location last = locations.getLastKnownLocation(provider);
          if (last != null) onLocationChanged(last);
          locations.requestLocationUpdates(provider, 15000, 0, this);
        }
      } catch (SecurityException e) {Diagnostics.error("gps","permission_denied",e,0,-1,-1,null);
      }
    }
    overlay = new Overlay(this, store);
    store.prefs.registerOnSharedPreferenceChangeListener(appearanceListener);
    queue = Executors.newSingleThreadScheduledExecutor();
    queue.scheduleWithFixedDelay(this::tick, 1, 5, TimeUnit.SECONDS);
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
      long interval=store.prefs.getInt("interval",120)*1000L;
      String id = planner.next(eligible, now, interval, "me", browsing||!widgetPoint.isEmpty()?10000L:interval);
      if (id == null) {Diagnostics.once("monitor","queue_wait",false);return;}
      Point p = null;
      for (Point q : a)
        if (q.id.equals(id)) {
          p = q;
          break;
        }
      if (p == null) return;
      Point request=Point.from(p.json());
      request.tariffs=TariffSelection.requested(p,store.widgetTariffs(),p.id.equals(widgetPoint));
      store.prefs.edit().putLong("diagAttemptAt",now).apply();
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
          planner.expedite("me");
          return;
        }
      }
      Point current = null;
      for (Point candidate : store.points()) if (candidate.id.equals(p.id)) current = candidate;
      if (current == null || !current.enabled || (!p.dynamic && (current.lat != p.lat || current.lon != p.lon))) return;
      List<String> stillNeeded=TariffSelection.requested(current,store.widgetTariffs(),store.prefs.getBoolean("overlay",false)&&current.id.equals(store.widgetPoint().id));
      if(!request.tariffs.containsAll(stillNeeded)){planner.expedite(p.id);return;}
      p.notify = current.notify; p.threshold = current.threshold;
      store.readings(p.id, readings);
      if(p.dynamic)store.prefs.edit().putLong("readQueryLat",Double.doubleToLongBits(request.lat)).putLong("readQueryLon",Double.doubleToLongBits(request.lon)).apply();
      store.prefs.edit().putString("status", "Обновлено · " + p.name).apply();
      failures = 0;
      Store.Reading best = null;
      for (Store.Reading r : readings)
        if (current.tariffs.contains(r.tariff) && r.rub != null && r.fresh() && (best == null || r.rub > best.rub)) best = r;
      boolean valid=false;for(Store.Reading r:readings)if(r.rub!=null&&r.fresh())valid=true;
      if(valid){store.prefs.edit().putLong("diagSuccessAt",System.currentTimeMillis()).putLong("diagPauseUntil",0).apply();Diagnostics.info("taxi","surcharge_updated");}
      else Diagnostics.error("taxi","missing_ruble_surcharge",null,0,-1,-1,p.dynamic?"own":"saved");
      if (best != null) alert(p, best);
      broadcast();
    } catch (TaxiClient.ApiException e) {
      long pause = e.status == 401 || e.status == 403 || e.status == 429 ? 15 * 60_000L : 60_000L;
      planner.backoff(System.currentTimeMillis(), pause);
      store.prefs.edit().putLong("diagPauseUntil",System.currentTimeMillis()+pause).apply();
      Diagnostics.error("taxi","http_backoff",e,e.status,-1,pause/1000,null);
      store.prefs.edit().putString("status", e.getMessage()).apply();
      broadcast();
    } catch (Exception e) {
      failures = Math.min(failures + 1, 5);
      long pause=Math.min(900000,30000L*(1L<<failures));
      planner.backoff(System.currentTimeMillis(),pause);
      store.prefs.edit().putLong("diagPauseUntil",System.currentTimeMillis()+pause).apply();
      Diagnostics.error("taxi","request_failed",e,0,-1,pause/1000,null);
      store.prefs.edit().putString("status", "Нет обновления · повтор позже").apply();
      broadcast();
    }
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
        new Notification.Builder(this, "alerts")
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
  public void onProviderEnabled(String p) {}

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
    super.onDestroy();
  }

  @Override
  public IBinder onBind(Intent i) {
    return null;
  }
}
