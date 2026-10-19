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
  public static boolean running = false;
  private Store store;
  private ScheduledExecutorService queue;
  private LocationManager locations;
  private Overlay overlay;
  private final SharedPreferences.OnSharedPreferenceChangeListener appearanceListener=(prefs,key)->{
    if(key!=null&&java.util.Arrays.asList("scale","textScale","opacity","showTariff","dark","overlay").contains(key))
      new Handler(Looper.getMainLooper()).post(()->{if(overlay!=null)overlay.refresh();});
  };
  private int failures = 0;
  private final PollPlanner planner = new PollPlanner();
  private final BroadcastReceiver manual =
      new BroadcastReceiver() {
        public void onReceive(Context c, Intent i) {
          if (queue != null)
            queue.execute(
                () -> {
                  if ("dev.raf.radar.VIEWING".equals(i.getAction())) planner.expedite("me");
                  else planner.refresh();
                  tick();
                });
        }
      };

  @Override
  public void onCreate() {
    super.onCreate();
    if (!Access.monitorReady(this)) { stopSelf(); return; }
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
          locations.requestLocationUpdates(provider, 15000, 20, this);
        }
      } catch (SecurityException ignored) {
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
      stopSelf();
      return START_NOT_STICKY;
    }
    if (overlay != null) overlay.refresh();
    return START_NOT_STICKY;
  }

  private void tick() {
    try {
      if (!Access.monitorReady(this)) { stopSelf(); return; }
      long now = System.currentTimeMillis();
      List<Point> a = store.points();
      List<String> eligible = new ArrayList<>();
      boolean browsing = store.prefs.getLong("viewingUntil", 0) > now;
      String widgetPoint = store.prefs.getBoolean("overlay", false)
          ? store.prefs.getString("overlayPoint", "me") : "";
      for (Point q : a) {
        if (!PollPlanner.needed(q, browsing, widgetPoint)) continue;
        if (q.dynamic) {
          long age = now - store.prefs.getLong("gpsAt", 0);
          if (age < 0 || age > 120000) continue;
          q.lat = Double.longBitsToDouble(store.prefs.getLong("lat", 0));
          q.lon = Double.longBitsToDouble(store.prefs.getLong("lon", 0));
        }
        eligible.add(q.id);
      }
      String id = planner.next(eligible, now, store.prefs.getInt("interval", 120) * 1000L, "me");
      if (id == null) return;
      Point p = null;
      for (Point q : a)
        if (q.id.equals(id)) {
          p = q;
          break;
        }
      if (p == null) return;
      List<Store.Reading> readings = new TaxiClient(this).fetch(p);
      if (p.dynamic) {
        float[] distance = new float[1];
        Location.distanceBetween(
            p.lat,
            p.lon,
            Double.longBitsToDouble(store.prefs.getLong("lat", 0)),
            Double.longBitsToDouble(store.prefs.getLong("lon", 0)),
            distance);
        if (distance[0] > 250) {
          planner.expedite("me");
          return;
        }
      }
      Point current = null;
      for (Point candidate : store.points()) if (candidate.id.equals(p.id)) current = candidate;
      if (current == null || !current.enabled || (!p.dynamic && (current.lat != p.lat || current.lon != p.lon))) return;
      p.notify = current.notify; p.threshold = current.threshold;
      store.readings(p.id, readings);
      store.prefs.edit().putString("status", "Обновлено · " + p.name).apply();
      failures = 0;
      Store.Reading best = null;
      for (Store.Reading r : readings)
        if (current.tariffs.contains(r.tariff) && r.rub != null && r.fresh() && (best == null || r.rub > best.rub)) best = r;
      if (best != null) alert(p, best);
      broadcast();
    } catch (TaxiClient.ApiException e) {
      long pause = e.status == 401 || e.status == 403 || e.status == 429 ? 15 * 60_000L : 60_000L;
      planner.backoff(System.currentTimeMillis(), pause);
      store.prefs.edit().putString("status", e.getMessage()).apply();
      broadcast();
    } catch (Exception e) {
      failures = Math.min(failures + 1, 5);
      planner.backoff(System.currentTimeMillis(), Math.min(900000, 30000L * (1L << failures)));
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
    long age = SystemClock.elapsedRealtimeNanos() - l.getElapsedRealtimeNanos();
    if (age < 0 || age > 120_000_000_000L || l.getAccuracy() > 500) return;
    if (store.prefs.getLong("gpsAt", 0) > 0) {
      float[] distance = new float[1];
      Location.distanceBetween(
          Double.longBitsToDouble(store.prefs.getLong("lat", 0)),
          Double.longBitsToDouble(store.prefs.getLong("lon", 0)),
          l.getLatitude(),
          l.getLongitude(),
          distance);
      if (distance[0] > 250) {
        store.readings("me", Collections.emptyList());
        planner.expedite("me");
      }
    }
    store
        .prefs
        .edit()
        .putLong("lat", Double.doubleToLongBits(l.getLatitude()))
        .putLong("lon", Double.doubleToLongBits(l.getLongitude()))
        .putLong("gpsAt", System.currentTimeMillis())
        .putFloat("accuracy", l.getAccuracy())
        .apply();
    broadcast();
  }

  @Override
  public void onProviderEnabled(String p) {}

  @Override
  public void onProviderDisabled(String p) {}

  @Override
  public void onStatusChanged(String p, int s, Bundle b) {}

  @Override
  public void onDestroy() {
    running = false;
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
