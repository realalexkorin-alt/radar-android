package dev.raf.radar;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.location.Geocoder;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.maplibre.android.MapLibre;
import org.maplibre.android.annotations.*;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.maps.*;

public final class MainActivity extends Activity {
  Store store;
  LinearLayout root, cards;
  MapView mapView;
  MapLibreMap map;
  TextView state;
  Button monitorButton;
  boolean dark;
  int fg, bg, surface, muted;
  Handler handler = new Handler();
  String selected = "me";
  boolean resumed = false;
  final Map<Long, String> markerPoints = new HashMap<>();
  final Runnable refreshLoop =
      new Runnable() {
        public void run() {
          if (resumed) {
            refreshCards();
            handler.postDelayed(this, 5000);
          }
        }
      };

  @Override
  public void onCreate(Bundle b) {
    super.onCreate(b);
    MapLibre.getInstance(this);
    store = new Store(this);
    selected = getIntent().getStringExtra("point");
    if (selected == null) selected = "me";
    build(b);
  }

  int dp(float v) {
    return (int) (v * getResources().getDisplayMetrics().density);
  }

  TextView text(String t, int size, int color) {
    TextView v = new TextView(this);
    v.setText(t);
    v.setTextSize(size);
    v.setTextColor(color);
    return v;
  }

  GradientDrawable shape(int c, int radius) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(c);
    d.setCornerRadius(dp(radius));
    return d;
  }

  void build(Bundle b) {
    dark = store.prefs.getBoolean("dark", true);
    fg = Color.parseColor(dark ? "#F5F5F6" : "#1B1D21");
    bg = Color.parseColor(dark ? "#101216" : "#F2F3F5");
    surface = Color.parseColor(dark ? "#202329" : "#FFFFFF");
    muted = Color.parseColor(dark ? "#A1A6AF" : "#646B75");
    getWindow().setStatusBarColor(bg);
    getWindow().setNavigationBarColor(bg);
    getWindow()
        .getDecorView()
        .setSystemUiVisibility(dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
    root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(bg);
    root.setPadding(dp(14), dp(8), dp(14), dp(8));
    root.setFitsSystemWindows(true);
    setContentView(root);
    LinearLayout top = new LinearLayout(this);
    top.setGravity(Gravity.CENTER_VERTICAL);
    TextView title = text("Радар", 28, fg);
    title.setTypeface(null, android.graphics.Typeface.BOLD);
    top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
    top.addView(action("⚙", () -> settings()));
    root.addView(top);
    state = text("Надбавки и спрос рядом с вами", 12, muted);
    state.setPadding(0, dp(3), 0, dp(8));
    root.addView(state);
    FrameLayout frame = new FrameLayout(this);
    root.addView(frame, new LinearLayout.LayoutParams(-1, 0, 1));
    mapView = new MapView(this);
    mapView.onCreate(b);
    frame.addView(mapView, new FrameLayout.LayoutParams(-1, -1));
    TextView loading = text("Загрузка карты…", 14, fg);
    loading.setGravity(Gravity.CENTER);
    loading.setPadding(dp(12), dp(8), dp(12), dp(8));
    loading.setBackground(shape(surface, 14));
    FrameLayout.LayoutParams loadingParams = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER);
    frame.addView(loading, loadingParams);
    mapView.addOnDidFinishLoadingMapListener(() -> loading.setVisibility(View.GONE));
    mapView.addOnDidFailLoadingMapListener(
        error -> loading.setText("Карта недоступна · проверьте связь"));
    TextView guidance = text("Удерживайте карту, чтобы добавить точку", 11, Color.BLACK);
    guidance.setPadding(dp(10), dp(5), dp(10), dp(5));
    guidance.setBackground(shape(0xE6F7CE46, 12));
    FrameLayout.LayoutParams gl =
        new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
    gl.topMargin = dp(10);
    frame.addView(guidance, gl);
    mapView.getMapAsync(
        m -> {
          map = m;
          String style =
              "{\"version\":8,\"sources\":{\"osm\":{\"type\":\"raster\",\"tiles\":[\"https://tile.openstreetmap.org/{z}/{x}/{y}.png\"],\"tileSize\":256,\"attribution\":\"©"
                  + " OpenStreetMap"
                  + " contributors\"}},\"layers\":[{\"id\":\"map\",\"type\":\"raster\",\"source\":\"osm\",\"paint\":{\"raster-brightness-max\":"
                  + (dark ? "0.55" : "1")
                  + ",\"raster-saturation\":"
                  + (dark ? "-0.8" : "-0.15")
                  + "}}]}";
          m.setStyle(
              new Style.Builder().fromJson(style),
              s -> {
                refreshMarkers();
              });
          m.getUiSettings().setCompassEnabled(true);
          m.addOnMapLongClickListener(
              pos -> {
                edit(
                    new Point(
                        UUID.randomUUID().toString(),
                        "Новая точка",
                        pos.getLatitude(),
                        pos.getLongitude(),
                        false));
                return true;
              });
          m.setOnMarkerClickListener(
              marker -> {
                for (Point p : store.points())
                  if (p.id.equals(markerPoints.get(marker.getId()))) {
                    selected = p.id;
                    edit(p);
                    break;
                  }
                return true;
              });
          double
              lat =
                  Double.longBitsToDouble(
                      store.prefs.getLong("lat", Double.doubleToLongBits(56.8389))),
              lon =
                  Double.longBitsToDouble(
                      store.prefs.getLong("lon", Double.doubleToLongBits(60.5975)));
          m.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lat, lon), 12));
        });
    LinearLayout tools = new LinearLayout(this);
    tools.addView(action("◎ Я", this::locate));
    tools.addView(action("＋ Точка", this::addPoint));
    tools.addView(
        action(
            "↻",
            () -> {
              if (!MonitorService.running) startMonitor();
              else sendBroadcast(new Intent("dev.raf.radar.MANUAL").setPackage(getPackageName()));
            }));
    root.addView(tools);
    ScrollView scroll = new ScrollView(this);
    cards = new LinearLayout(this);
    cards.setOrientation(LinearLayout.VERTICAL);
    scroll.addView(cards);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, dp(190)));
    LinearLayout bottom = new LinearLayout(this);
    Button start =
        action(
            MonitorService.running ? "Остановить" : "Начать мониторинг",
            () -> {
              if (MonitorService.running) {
                stopService(new Intent(this, MonitorService.class));
              } else startMonitor();
              handler.postDelayed(
                  () -> {
                    if (!isFinishing()) recreate();
                  },
                  400);
            });
    monitorButton = start;
    start.setBackground(shape(Color.rgb(247, 206, 70), 16));
    start.setTextColor(Color.BLACK);
    bottom.addView(start, new LinearLayout.LayoutParams(0, dp(50), 1));
    bottom.addView(
        action(
            "Виджет",
            () -> {
              if (!Settings.canDrawOverlays(this)) {
                startActivity(
                    new Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
                return;
              }
              store
                  .prefs
                  .edit()
                  .putBoolean("overlay", !store.prefs.getBoolean("overlay", false))
                  .putString("overlayPoint", selected)
                  .apply();
              startMonitor();
            }));
    root.addView(bottom);
    refreshCards();
  }

  Button action(String label, Runnable r) {
    Button b = new Button(this);
    b.setText(label);
    b.setTextColor(fg);
    b.setTextSize(13);
    b.setAllCaps(false);
    b.setMinWidth(dp(44));
    b.setMinimumWidth(dp(44));
    b.setBackground(shape(surface, 14));
    b.setOnClickListener(v -> r.run());
    return b;
  }

  void refreshCards() {
    if (cards == null) return;
    if (monitorButton != null)
      monitorButton.setText(MonitorService.running ? "Остановить" : "Начать мониторинг");
    state.setText(
        MonitorService.running
            ? store.prefs.getString("status", "Мониторинг · ожидание GPS")
            : "Мониторинг выключен · данные не обновляются");
    cards.removeAllViews();
    for (Point p : store.points()) {
      LinearLayout box = new LinearLayout(this);
      box.setOrientation(LinearLayout.VERTICAL);
      box.setPadding(dp(12), dp(9), dp(12), dp(9));
      box.setBackground(shape(surface, 16));
      LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, -2);
      bl.bottomMargin = dp(7);
      cards.addView(box, bl);
      TextView name = text((p.dynamic ? "◎ " : "● ") + p.name + (p.notify ? "  ♧" : ""), 15, fg);
      name.setTypeface(null, android.graphics.Typeface.BOLD);
      box.addView(name);
      List<Store.Reading> readings = store.readings(p.id);
      StringBuilder values = new StringBuilder();
      for (String t : p.tariffs) {
        Store.Reading r = null;
        for (Store.Reading x : readings) if (t.equals(x.tariff)) r = x;
        if (values.length() > 0) values.append("    ");
        values.append(Point.label(t)).append(" ").append(r == null ? "—" : r.value());
      }
      box.addView(text(values.toString(), 15, fg));
      String detail = "Нет данных · включите мониторинг";
      if (!readings.isEmpty()) {
        Store.Reading r = readings.get(0);
        long sec = Math.max(0, (System.currentTimeMillis() - r.time) / 1000);
        detail = (r.fresh() ? "Обновлено " : "Устарело · ") + sec + " с назад · × не равно ₽";
      } else if (p.dynamic) detail = "Следует за вами · требуется геопозиция";
      box.addView(text(detail, 10, muted));
      box.setOnClickListener(
          v -> {
            selected = p.id;
            edit(p);
          });
    }
    refreshMarkers();
  }

  void refreshMarkers() {
    if (map == null || map.getStyle() == null) return;
    map.clear();
    markerPoints.clear();
    for (Point p : store.points()) {
      double lat = p.lat, lon = p.lon;
      if (p.dynamic) {
        if (store.prefs.getLong("gpsAt", 0) == 0) continue;
        lat = Double.longBitsToDouble(store.prefs.getLong("lat", 0));
        lon = Double.longBitsToDouble(store.prefs.getLong("lon", 0));
      }
      Marker marker =
          map.addMarker(
              new MarkerOptions()
                  .position(new LatLng(lat, lon))
                  .title(p.name)
                  .snippet(
                      p.dynamic
                          ? "Точность ±" + Math.round(store.prefs.getFloat("accuracy", 0)) + " м"
                          : "Нажмите для настройки")
                  .icon(pointIcon(p.dynamic)));
      markerPoints.put(marker.getId(), p.id);
    }
  }

  org.maplibre.android.annotations.Icon pointIcon(boolean me) {
    android.graphics.Bitmap b =
        android.graphics.Bitmap.createBitmap(
            dp(30), dp(30), android.graphics.Bitmap.Config.ARGB_8888);
    android.graphics.Canvas canvas = new android.graphics.Canvas(b);
    android.graphics.Paint paint =
        new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
    paint.setColor(Color.WHITE);
    canvas.drawCircle(dp(15), dp(15), dp(12), paint);
    paint.setColor(
        me
            ? (System.currentTimeMillis() - store.prefs.getLong("gpsAt", 0) > 120000
                ? Color.GRAY
                : Color.rgb(70, 145, 255))
            : Color.rgb(247, 206, 70));
    canvas.drawCircle(dp(15), dp(15), dp(9), paint);
    return org.maplibre.android.annotations.IconFactory.getInstance(this).fromBitmap(b);
  }

  void startMonitor() {
    if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        != PackageManager.PERMISSION_GRANTED) {
      ArrayList<String> a =
          new ArrayList<>(
              Arrays.asList(
                  Manifest.permission.ACCESS_FINE_LOCATION,
                  Manifest.permission.ACCESS_COARSE_LOCATION));
      if (Build.VERSION.SDK_INT >= 33) a.add(Manifest.permission.POST_NOTIFICATIONS);
      requestPermissions(a.toArray(new String[0]), 10);
      return;
    }
    if (Build.VERSION.SDK_INT >= 33
        && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED)
      requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 11);
    try {
      startForegroundService(new Intent(this, MonitorService.class));
    } catch (Exception e) {
      toast("Не удалось включить: " + e.getMessage());
    }
  }

  @Override
  public void onRequestPermissionsResult(int r, String[] p, int[] g) {
    super.onRequestPermissionsResult(r, p, g);
    if (r == 10
        && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) startMonitor();
  }

  void locate() {
    if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        != PackageManager.PERMISSION_GRANTED) {
      startMonitor();
      return;
    }
    long age = System.currentTimeMillis() - store.prefs.getLong("gpsAt", 0);
    if (age > 120000) {
      toast("Ожидаем свежую геопозицию. Включите мониторинг и GPS.");
      return;
    }
    if (map != null)
      map.animateCamera(
          CameraUpdateFactory.newLatLngZoom(
              new LatLng(
                  Double.longBitsToDouble(store.prefs.getLong("lat", 0)),
                  Double.longBitsToDouble(store.prefs.getLong("lon", 0))),
              14));
  }

  void addPoint() {
    LinearLayout form = new LinearLayout(this);
    form.setOrientation(LinearLayout.VERTICAL);
    EditText query = new EditText(this);
    query.setHint("Адрес или широта, долгота");
    form.addView(query);
    new AlertDialog.Builder(this)
        .setTitle("Добавить точку")
        .setView(form)
        .setMessage("Или удерживайте нужное место на карте.")
        .setPositiveButton(
            "Найти",
            (d, w) ->
                new Thread(
                        () -> {
                          try {
                            String q = query.getText().toString().trim();
                            double lat, lon;
                            if (q.matches("[-+0-9.]+\\s*,\\s*[-+0-9.]+")) {
                              String[] v = q.split(",");
                              lat = Double.parseDouble(v[0].trim());
                              lon = Double.parseDouble(v[1].trim());
                            } else {
                              List<android.location.Address> a =
                                  new Geocoder(this, new Locale("ru")).getFromLocationName(q, 1);
                              if (a == null || a.isEmpty()) throw new Exception();
                              lat = a.get(0).getLatitude();
                              lon = a.get(0).getLongitude();
                            }
                            if (!Double.isFinite(lat)
                                || !Double.isFinite(lon)
                                || Math.abs(lat) > 90
                                || Math.abs(lon) > 180) throw new Exception();
                            Point p = new Point(UUID.randomUUID().toString(), q, lat, lon, false);
                            runOnUiThread(
                                () -> {
                                  if (map != null)
                                    map.animateCamera(
                                        CameraUpdateFactory.newLatLngZoom(
                                            new LatLng(p.lat, p.lon), 14));
                                  edit(p);
                                });
                          } catch (Exception e) {
                            runOnUiThread(
                                () ->
                                    toast(
                                        "Адрес не найден. Укажите координаты или выберите место на"
                                            + " карте."));
                          }
                        })
                    .start())
        .setNegativeButton("Отмена", null)
        .show();
  }

  void edit(Point p) {
    LinearLayout form = form();
    EditText name = new EditText(this);
    name.setTextColor(fg);
    name.setHintTextColor(muted);
    name.setText(p.name);
    name.setEnabled(!p.dynamic);
    form.addView(name);
    if (!p.dynamic) {
      TextView coords = text(String.format(Locale.US, "%.5f, %.5f", p.lat, p.lon), 12, muted);
      form.addView(coords);
      Button move =
          action(
              "Переместить в центр карты",
              () -> {
                if (map != null) {
                  LatLng pos = map.getCameraPosition().target;
                  p.lat = pos.getLatitude();
                  p.lon = pos.getLongitude();
                  coords.setText(String.format(Locale.US, "%.5f, %.5f", p.lat, p.lon));
                }
              });
      form.addView(move);
    }
    String[] ids = {"econom", "business", "comfortplus", "vip", "child_tariff", "minivan"};
    ArrayList<CheckBox> checks = new ArrayList<>();
    for (String id : ids) {
      CheckBox cb = new CheckBox(this);
      cb.setText(Point.label(id));
      cb.setTextColor(fg);
      cb.setChecked(p.tariffs.contains(id));
      form.addView(cb);
      checks.add(cb);
    }
    Switch on = new Switch(this);
    on.setText("Уведомлять");
    on.setTextColor(fg);
    on.setChecked(p.notify);
    form.addView(on);
    final int[] threshold = {p.threshold};
    slider(form, "Уведомлять при", 0, 1000, p.threshold, 50, v -> threshold[0] = v, " ₽");
    form.addView(
        text(
            "Пауза 30 минут. Рост ещё на 50 ₽ — сразу.\n"
                + "Уведомления используют расчётную надбавку в ₽; по множителю × не"
                + " отправляются.",
            12,
            muted));
    ScrollView sc = new ScrollView(this);
    sc.addView(form);
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle(p.dynamic ? "Моя геопозиция" : "Настройка точки")
            .setView(sc)
            .setPositiveButton("Сохранить", null)
            .setNegativeButton("Отмена", null)
            .setNeutralButton(
                p.dynamic ? "В виджет" : "Удалить",
                (d, w) -> {
                  if (p.dynamic) {
                    store.prefs.edit().putString("overlayPoint", p.id).apply();
                  } else store.remove(p.id);
                  refreshCards();
                })
            .create();
    dialog.setOnShowListener(
        d ->
            dialog
                .getButton(-1)
                .setOnClickListener(
                    v -> {
                      ArrayList<String> ts = new ArrayList<>();
                      for (int i = 0; i < ids.length; i++)
                        if (checks.get(i).isChecked()) ts.add(ids[i]);
                      if (ts.isEmpty()) {
                        toast("Выберите хотя бы один тариф");
                        return;
                      }
                      p.name = p.dynamic ? "Моя геопозиция" : name.getText().toString().trim();
                      if (p.name.isEmpty()) {
                        toast("Укажите название");
                        return;
                      }
                      p.tariffs = ts;
                      p.notify = on.isChecked();
                      p.threshold = threshold[0];
                      store.update(p);
                      selected = p.id;
                      store.prefs.edit().putString("overlayPoint", p.id).apply();
                      refreshCards();
                      dialog.dismiss();
                    }));
    dialog.show();
  }

  LinearLayout form() {
    LinearLayout f = new LinearLayout(this);
    f.setOrientation(LinearLayout.VERTICAL);
    f.setPadding(dp(20), dp(10), dp(20), dp(10));
    f.setBackgroundColor(bg);
    return f;
  }

  interface NumberChange {
    void set(int v);
  }

  void slider(
      LinearLayout f,
      String label,
      int min,
      int max,
      int initial,
      int step,
      NumberChange change,
      String unit) {
    TextView t = text(label + ": " + initial + unit, 14, fg);
    f.addView(t);
    SeekBar bar = new SeekBar(this);
    bar.setMax((max - min) / step);
    bar.setProgress((initial - min) / step);
    f.addView(bar);
    bar.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onProgressChanged(SeekBar b, int v, boolean user) {
            int n = min + v * step;
            t.setText(label + ": " + n + unit);
            if (user) change.set(n);
          }

          public void onStartTrackingTouch(SeekBar b) {}

          public void onStopTrackingTouch(SeekBar b) {}
        });
  }

  void settings() {
    LinearLayout f = form();
    f.addView(text("Радар " + BuildConfig.VERSION_NAME, 20, fg));
    f.addView(text("Надбавка ₽ — расчётная · сверяйте с Яндекс Про", 12, muted));
    Switch theme = new Switch(this);
    theme.setText("Тёмная тема");
    theme.setTextColor(fg);
    theme.setChecked(dark);
    theme.setOnCheckedChangeListener((b, v) -> store.prefs.edit().putBoolean("dark", v).apply());
    f.addView(theme);
    slider(
        f,
        "Прозрачность фона виджета",
        20,
        100,
        store.prefs.getInt("opacity", 90),
        5,
        v -> store.prefs.edit().putInt("opacity", v).apply(),
        "%");
    slider(
        f,
        "Масштаб виджета",
        60,
        160,
        store.prefs.getInt("scale", 100),
        10,
        v -> store.prefs.edit().putInt("scale", v).apply(),
        "%");
    slider(
        f,
        "Размер текста",
        70,
        150,
        store.prefs.getInt("textScale", 100),
        10,
        v -> store.prefs.edit().putInt("textScale", v).apply(),
        "%");
    Switch lock = new Switch(this);
    lock.setText("Зафиксировать виджет");
    lock.setTextColor(fg);
    lock.setChecked(store.prefs.getBoolean("locked", false));
    lock.setOnCheckedChangeListener((b, v) -> store.prefs.edit().putBoolean("locked", v).apply());
    f.addView(lock);
    slider(
        f,
        "Интервал каждой точки",
        30,
        600,
        store.prefs.getInt("interval", 120),
        30,
        v -> store.prefs.edit().putInt("interval", v).apply(),
        " с");
    f.addView(
        text(
            "Общая очередь: не чаще одного запроса за 15 с. Для 30 точек полный круг займёт не"
                + " менее 7,5 минут.",
            12,
            muted));
    f.addView(
        action(
            "Вход через Яндекс · экспериментально",
            () -> startActivity(new Intent(this, LoginActivity.class))));
    f.addView(
        action(
            "Удалить сессию Яндекса",
            () -> {
              android.webkit.CookieManager.getInstance()
                  .removeAllCookies(done -> toast("Сессия удалена"));
              android.webkit.WebStorage.getInstance().deleteAllData();
            }));
    EditText repo = new EditText(this);
    repo.setTextColor(fg);
    repo.setHintTextColor(muted);
    repo.setSingleLine();
    repo.setHint("GitHub: владелец/репозиторий");
    repo.setText(store.prefs.getString("repo", Updates.DEFAULT_REPO));
    f.addView(repo);
    f.addView(
        action(
            "Проверить обновления",
            () -> {
              store.prefs.edit().putString("repo", repo.getText().toString().trim()).apply();
              Updates.check(this, store);
            }));
    f.addView(
        text(
            "Карта: © OpenStreetMap contributors.\n"
                + "Надбавка ₽: текущая цена минус база города, округление до 10 ₽. Это оценка,"
                + " совпадение с Яндекс Про не подтверждено.\n"
                + "Данные хранятся на устройстве. Нет рекламы и аналитики.",
            12,
            muted));
    ScrollView sc = new ScrollView(this);
    sc.addView(f);
    new AlertDialog.Builder(this)
        .setTitle("Настройки")
        .setView(sc)
        .setPositiveButton(
            "Готово",
            (d, w) -> {
              store.prefs.edit().putString("repo", repo.getText().toString().trim()).apply();
              recreate();
            })
        .show();
  }

  void toast(String s) {
    Toast.makeText(this, s, Toast.LENGTH_LONG).show();
  }

  @Override
  protected void onStart() {
    super.onStart();
    mapView.onStart();
  }

  @Override
  protected void onResume() {
    super.onResume();
    mapView.onResume();
    resumed = true;
    handler.post(refreshLoop);
  }

  @Override
  protected void onPause() {
    resumed = false;
    handler.removeCallbacks(refreshLoop);
    mapView.onPause();
    super.onPause();
  }

  @Override
  protected void onStop() {
    mapView.onStop();
    super.onStop();
  }

  @Override
  protected void onDestroy() {
    handler.removeCallbacksAndMessages(null);
    if (mapView != null) mapView.onDestroy();
    super.onDestroy();
  }

  @Override
  public void onLowMemory() {
    super.onLowMemory();
    mapView.onLowMemory();
  }

  @Override
  protected void onSaveInstanceState(Bundle b) {
    super.onSaveInstanceState(b);
    mapView.onSaveInstanceState(b);
  }
}
