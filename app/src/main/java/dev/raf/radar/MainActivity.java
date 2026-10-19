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
  Access access;
  boolean startAfterAccess;
  LinearLayout root, cards, mapPanel, pointsPanel, settingsPanel, navigation;
  FrameLayout pages;
  int currentTab = 0;
  static final String[] TARIFF_IDS = {"econom", "business", "comfortplus", "vip", "child_tariff", "minivan"};
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
            updateViewing();
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
    access = new Access(this, store);
    selected = getIntent().getStringExtra("point");
    if (selected == null) selected = "me";
    currentTab = b == null ? 0 : b.getInt("tab", 0);
    build(b);
    openRequestedSettings(getIntent());
    // Check once per process launch; returning from the package installer must not reopen the prompt.
    if (b == null) handler.postDelayed(() -> Updates.checkAutomatic(this, store), 1200);
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    openRequestedSettings(intent);
  }

  private void openRequestedSettings(Intent intent) {
    if (intent.getBooleanExtra("widgetSettings", false)) {
      intent.removeExtra("widgetSettings");
      handler.post(() -> { if (!isFinishing()) settings(); });
    }
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
    root.setPadding(0, dp(8), 0, 0);
    root.setFitsSystemWindows(true);
    setContentView(root);
    LinearLayout top = new LinearLayout(this);
    top.setGravity(Gravity.CENTER_VERTICAL);
    top.setPadding(dp(18), 0, dp(12), 0);
    TextView title = text("Радар Pro", 26, fg);
    title.setTypeface(null, android.graphics.Typeface.BOLD);
    top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
    top.addView(action("⚙", this::settings));
    root.addView(top);
    state = text("Надбавки и спрос рядом с вами", 12, muted);
    state.setPadding(dp(18), dp(3), dp(18), dp(8));
    root.addView(state);
    pages = new FrameLayout(this);
    root.addView(pages, new LinearLayout.LayoutParams(-1, 0, 1));
    mapPanel = new LinearLayout(this);
    mapPanel.setOrientation(LinearLayout.VERTICAL);
    pages.addView(mapPanel, new FrameLayout.LayoutParams(-1, -1));
    FrameLayout frame = new FrameLayout(this);
    mapPanel.addView(frame, new LinearLayout.LayoutParams(-1, 0, 1));
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
    mapPanel.addView(tools);
    ScrollView scroll = new ScrollView(this);
    cards = new LinearLayout(this);
    cards.setOrientation(LinearLayout.VERTICAL);
    scroll.addView(cards);
    pointsPanel = new LinearLayout(this);
    pointsPanel.setOrientation(LinearLayout.VERTICAL);
    pointsPanel.setPadding(dp(16), 0, dp(16), 0);
    LinearLayout pointHeader = new LinearLayout(this);
    TextView pointTitle = text("Мои точки", 24, fg);
    pointTitle.setTypeface(null, android.graphics.Typeface.BOLD);
    pointHeader.addView(pointTitle, new LinearLayout.LayoutParams(0, dp(56), 1));
    pointHeader.addView(action("＋ Добавить", this::addPoint));
    pointsPanel.addView(pointHeader);
    pointsPanel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    pages.addView(pointsPanel, new FrameLayout.LayoutParams(-1, -1));
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
                access.request();
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
    mapPanel.addView(bottom);
    settingsPanel = new LinearLayout(this);
    settingsPanel.setOrientation(LinearLayout.VERTICAL);
    ScrollView settingsScroll = new ScrollView(this);
    settingsScroll.setFillViewport(true);
    settingsScroll.addView(settingsContents());
    settingsPanel.addView(settingsScroll, new LinearLayout.LayoutParams(-1, -1));
    pages.addView(settingsPanel, new FrameLayout.LayoutParams(-1, -1));
    navigation = new LinearLayout(this);
    navigation.setPadding(dp(8), dp(6), dp(8), dp(6));
    navigation.setBackgroundColor(surface);
    String[] labels = {"Карта", "Точки", "Настройки"};
    for (int i = 0; i < labels.length; i++) {
      final int tab = i;
      Button item = action(labels[i], () -> showTab(tab));
      item.setContentDescription("Вкладка " + labels[i]);
      navigation.addView(item, new LinearLayout.LayoutParams(0, dp(52), 1));
    }
    root.addView(navigation);
    showTab(currentTab);
    refreshCards();
  }

  void showTab(int tab) {
    currentTab = Math.max(0, Math.min(2, tab));
    mapPanel.setVisibility(currentTab == 0 ? View.VISIBLE : View.GONE);
    pointsPanel.setVisibility(currentTab == 1 ? View.VISIBLE : View.GONE);
    settingsPanel.setVisibility(currentTab == 2 ? View.VISIBLE : View.GONE);
    if (navigation != null) for (int i = 0; i < navigation.getChildCount(); i++) {
      Button item = (Button) navigation.getChildAt(i);
      item.setTextColor(i == currentTab ? Color.BLACK : muted);
      item.setBackground(shape(i == currentTab ? 0xFFF7CE46 : surface, 18));
      item.setSelected(i == currentTab);
    }
    updateViewing();
    if (currentTab != 2) {
      sendBroadcast(new Intent("dev.raf.radar.VIEWING").setPackage(getPackageName()));
      refreshCards();
    }
  }

  void updateViewing() {
    store.prefs.edit().putLong("viewingUntil",
        resumed && currentTab != 2 ? System.currentTimeMillis() + 15000 : 0).apply();
  }

  String pointAddress(Point p) {
    if (p.dynamic) return "Следует за вами";
    return p.address.isEmpty() ? String.format(Locale.US, "%.5f, %.5f", p.lat, p.lon) : p.address;
  }

  Set<String> mapTariffs() {
    return store.prefs.getStringSet("mapTariffs", new HashSet<>(Arrays.asList(TARIFF_IDS)));
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
      LinearLayout heading = new LinearLayout(this);
      heading.setGravity(Gravity.CENTER_VERTICAL);
      TextView name = text((p.dynamic ? "◎ " : "● ") + p.name, 17, fg);
      name.setTypeface(null, android.graphics.Typeface.BOLD);
      heading.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
      if (!p.dynamic) {
        Switch active = new Switch(this);
        active.setContentDescription("Мониторинг: " + p.name);
        active.setChecked(p.enabled);
        active.setOnCheckedChangeListener((button, enabled) -> {
          p.enabled = enabled; store.update(p);
          sendBroadcast(new Intent("dev.raf.radar.VIEWING").setPackage(getPackageName()));
          refreshMarkers();
        });
        heading.addView(active);
      }
      box.addView(heading);
      TextView address = text(pointAddress(p), 12, muted);
      address.setPadding(0, dp(6), 0, dp(8));
      box.addView(address);
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
      if (!p.enabled) detail = "Мониторинг точки выключен";
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
                  .icon(pointIcon(p)));
      markerPoints.put(marker.getId(), p.id);
    }
  }

  org.maplibre.android.annotations.Icon pointIcon(Point p) {
    String value = "—";
    for (Store.Reading r : store.readings(p.id)) {
      if (mapTariffs().contains(r.tariff) && p.tariffs.contains(r.tariff)) { value = r.value(); break; }
    }
    String caption = (p.dynamic ? "Я" : p.name) + "  " + value;
    if (caption.length() > 34) caption = caption.substring(0, 31) + "…";
    android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
    paint.setTextSize(dp(13)); paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
    int width = (int) paint.measureText(caption) + dp(24);
    android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(width, dp(46), android.graphics.Bitmap.Config.ARGB_8888);
    android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
    paint.setColor(surface); canvas.drawRoundRect(0, 0, width, dp(34), dp(12), dp(12), paint);
    android.graphics.Path pointer = new android.graphics.Path();
    pointer.moveTo(width / 2f - dp(6), dp(32)); pointer.lineTo(width / 2f, dp(44));
    pointer.lineTo(width / 2f + dp(6), dp(32)); pointer.close(); canvas.drawPath(pointer, paint);
    paint.setColor(!p.enabled ? muted : p.dynamic ? 0xFF4295FF : fg);
    canvas.drawText(caption, dp(12), dp(22), paint);
    return org.maplibre.android.annotations.IconFactory.getInstance(this).fromBitmap(bitmap);
  }

  void startMonitor() {
    if (!Access.monitorReady(this)) {
      startAfterAccess = true;
      access.request();
      return;
    }
    startAfterAccess = false;
    try {
      startForegroundService(new Intent(this, MonitorService.class));
    } catch (Exception e) {
      toast("Не удалось включить: " + e.getMessage());
    }
  }

  @Override
  public void onRequestPermissionsResult(int r, String[] p, int[] g) {
    super.onRequestPermissionsResult(r, p, g);
    access.result(r);
  }

  void onAccessChanged() {
    if (startAfterAccess && Access.monitorReady(this)) startMonitor();
  }

  void locate() {
    if (!Access.location(this)) {
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
    boolean existing = store.points().stream().anyMatch(saved -> saved.id.equals(p.id));
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
      TextView coefficient = text("Кеф: " + pointValues(p), 16, fg);
      coefficient.setPadding(0, dp(8), 0, dp(8));
      form.addView(coefficient);
      TextView address = text(pointAddress(p), 13, muted);
      form.addView(address);
      final double addressLat = p.lat, addressLon = p.lon;
      resolveAddress(addressLat, addressLon, resolved -> {
        if (p.lat != addressLat || p.lon != addressLon) return;
        if (!isFinishing() && address.isAttachedToWindow()) address.setText(resolved);
        p.address = resolved;
        cacheAddress(p.id, addressLat, addressLon, resolved);
      });
      Button move =
          action(
              "Переместить в центр карты",
              () -> {
                if (map != null) {
                  LatLng pos = map.getCameraPosition().target;
                  p.lat = pos.getLatitude();
                  p.lon = pos.getLongitude();
                  coords.setText(String.format(Locale.US, "%.5f, %.5f", p.lat, p.lon));
                  p.address = "";
                  address.setText(pointAddress(p));
                  coefficient.setText("Кеф: —");
                  final double moveLat = p.lat, moveLon = p.lon;
                  resolveAddress(moveLat, moveLon, resolved -> {
                    if (p.lat != moveLat || p.lon != moveLon) return;
                    p.address = resolved; address.setText(resolved);
                    cacheAddress(p.id, moveLat, moveLon, resolved);
                  });
                }
              });
      form.addView(move);
    }
    String[] ids = TARIFF_IDS;
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
            .setNegativeButton("Отменить", null)
            .setNeutralButton(
                p.dynamic ? "В виджет" : existing ? "Удалить" : null,
                (d, w) -> {
                  if (p.dynamic) {
                    store.prefs.edit().putString("overlayPoint", p.id).apply();
                  } else store.remove(p.id);
                  refreshCards();
                })
            .create();
    dialog.setOnShowListener(
        d -> {
          Button saveButton = dialog.getButton(-1);
          if (saveButton.getParent() instanceof LinearLayout) {
            LinearLayout buttons = (LinearLayout) saveButton.getParent();
            buttons.setOrientation(LinearLayout.HORIZONTAL);
            buttons.setPadding(dp(8), 0, dp(8), dp(8));
            for (int index = 0; index < buttons.getChildCount(); index++) {
              View child = buttons.getChildAt(index);
              if (child instanceof Button) {
                Button button = (Button) child;
                button.setAllCaps(false); button.setTextSize(12);
                button.setMinWidth(0); button.setMinimumWidth(0);
                button.setPadding(dp(4), 0, dp(4), 0);
                button.setSingleLine(false); button.setMaxLines(2);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(52), 1);
                params.setMargins(dp(2), 0, dp(2), 0);
                button.setLayoutParams(params);
              } else child.setVisibility(View.GONE);
            }
          }
          dialog.getButton(-1).setBackground(shape(0xFFF7CE46, 12));
          dialog.getButton(-1).setTextColor(Color.BLACK);
          dialog.getButton(-2).setTextColor(fg);
          dialog.getButton(-2).setBackground(shape(surface, 12));
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
                    });
        });
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

  String pointValues(Point p) {
    StringBuilder value = new StringBuilder();
    for (Store.Reading r : store.readings(p.id)) {
      if (!p.tariffs.contains(r.tariff) || !mapTariffs().contains(r.tariff)) continue;
      if (value.length() > 0) value.append(" · ");
      value.append(Point.label(r.tariff)).append(" ").append(r.value());
    }
    return value.length() == 0 ? "—" : value.toString();
  }

  void cacheAddress(String id, double lat, double lon, String address) {
    for (Point saved : store.points()) if (saved.id.equals(id) && saved.lat == lat && saved.lon == lon) {
      saved.address = address; store.update(saved); return;
    }
  }

  interface AddressResult { void ready(String address); }
  void resolveAddress(double lat, double lon, AddressResult result) {
    if (!Geocoder.isPresent()) return;
    new Thread(() -> {
      try {
        List<android.location.Address> matches = new Geocoder(this, new Locale("ru")).getFromLocation(lat, lon, 1);
        if (matches == null || matches.isEmpty()) return;
        String address = matches.get(0).getAddressLine(0);
        if (address != null) handler.post(() -> { if (!isFinishing() && !isDestroyed()) result.ready(address); });
      } catch (Exception ignored) { }
    }, "radar-address").start();
  }

  void settings() { showTab(2); }

  LinearLayout settingsContents() {
    LinearLayout f = form();
    f.addView(text("Настройки", 24, fg));
    section(f, "Основные");
    f.addView(text("Радар Pro " + BuildConfig.VERSION_NAME, 14, muted));
    f.addView(text("Надбавка ₽ — расчётная · сверяйте с Яндекс Про", 12, muted));
    Switch theme = new Switch(this);
    theme.setText("Тёмная тема");
    theme.setTextColor(fg);
    theme.setChecked(dark);
    theme.setOnCheckedChangeListener((b, v) -> { store.prefs.edit().putBoolean("dark", v).apply(); });
    f.addView(theme);
    section(f, "Внешний вид и виджет");
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
    Switch showTariff = new Switch(this);
    showTariff.setText("Показывать название тарифа в виджете");
    showTariff.setTextColor(fg);
    showTariff.setChecked(store.prefs.getBoolean("showTariff", true));
    showTariff.setOnCheckedChangeListener((b, value) -> store.prefs.edit().putBoolean("showTariff", value).apply());
    f.addView(showTariff);
    section(f, "Тарифы на карте");
    Set<String> selectedTariffs = new HashSet<>(mapTariffs());
    for (String tariff : TARIFF_IDS) {
      CheckBox check = new CheckBox(this);
      check.setText(Point.label(tariff)); check.setTextColor(fg);
      check.setChecked(selectedTariffs.contains(tariff));
      check.setOnCheckedChangeListener((button, enabled) -> {
        if (enabled) selectedTariffs.add(tariff); else selectedTariffs.remove(tariff);
        if (selectedTariffs.isEmpty()) { selectedTariffs.add(tariff); button.setChecked(true); toast("Оставьте хотя бы один тариф"); }
        store.prefs.edit().putStringSet("mapTariffs", new HashSet<>(selectedTariffs)).apply();
        refreshMarkers();
      });
      f.addView(check);
    }
    section(f, "Мониторинг");
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
            "Моя геопозиция — первой. Остальные точки обновляются при открытой карте,"
                + " в виджете или с включёнными уведомлениями. Не чаще одного запроса за 15 с.",
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
    section(f, "Обновление");
    f.addView(text("Проверка обновлений при каждом запуске. Канал GitHub подключён.", 13, muted));
    f.addView(
        action(
            "Проверить обновления",
            () -> {
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
    f.addView(action("Применить оформление", this::recreate));
    return f;
  }

  void section(LinearLayout container, String title) {
    TextView heading = text(title, 18, fg);
    heading.setTypeface(null, android.graphics.Typeface.BOLD);
    heading.setPadding(0, dp(24), 0, dp(10));
    container.addView(heading);
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
    updateViewing();
    sendBroadcast(new Intent("dev.raf.radar.VIEWING").setPackage(getPackageName()));
    handler.post(refreshLoop);
    handler.post(() -> { if (!Updates.resume(this)) access.onResume(); });
  }

  @Override
  protected void onPause() {
    resumed = false;
    store.prefs.edit().putLong("viewingUntil", 0).apply();
    handler.removeCallbacks(refreshLoop);
    mapView.onPause();
    super.onPause();
  }

  @Override
  protected void onStop() {
    access.onStop();
    mapView.onStop();
    super.onStop();
  }

  @Override
  protected void onDestroy() {
    Updates.detach(this);
    if (access != null) access.close();
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
    b.putInt("tab", currentTab);
    mapView.onSaveInstanceState(b);
  }
}
