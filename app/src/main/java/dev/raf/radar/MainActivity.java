package dev.raf.radar;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.location.Geocoder;
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
  Store store; Access access;
  final StartupSound startupSound = new StartupSound();
  boolean startupSoundPending;
  boolean startAfterAccess, resumed, dark;
  int fg,bg,surface,muted,currentTab;
  LinearLayout root,cards,mapPanel,pointsPanel,settingsPanel,navigation;
  FrameLayout pages,mapFrame;
  MapView mapView; MapLibreMap map;
  TextView state;
  View pointSheet, pointOverlay;
  final Map<String,Bitmap> pointBitmaps=new HashMap<>();
  Point preview;
  String selected="me";
  Handler handler=new Handler();
  static final String[] TARIFF_IDS={"econom","business","comfortplus","vip","child_tariff","minivan"};
  final Runnable refreshLoop=new Runnable(){public void run(){if(resumed){updateViewing();refreshCards();handler.postDelayed(this,5000);}}};

  @Override public void onCreate(Bundle saved) {
    setTheme(getSharedPreferences("radar",MODE_PRIVATE).getBoolean("dark",true)?R.style.AppThemeDark:R.style.AppTheme);
    super.onCreate(saved);MapLibre.getInstance(this);store=new Store(this);access=new Access(this,store);
    startupSoundPending = saved == null;
    currentTab=saved==null?0:saved.getInt("tab",0);
    String id=getIntent().getStringExtra("point");if(id!=null)selected=id;
    build(saved);openRequestedSettings(getIntent());
    if(saved==null&&store.prefs.getBoolean("autoUpdate",true))handler.postDelayed(()->Updates.checkAutomatic(this,store),1200);
  }
  @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);openRequestedSettings(intent);}
  private void openRequestedSettings(Intent intent){if(intent.getBooleanExtra("widgetSettings",false)){intent.removeExtra("widgetSettings");handler.post(()->{if(!isFinishing())settings();});}}
  int dp(float n){return RadarUi.dp(this,n);}
  TextView text(String value,int size,int color){return RadarUi.text(this,value,size,color,false);}
  GradientDrawable shape(int color,int radius){return RadarUi.shape(this,color,radius,0);}
  LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
  LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
  void pad(View v,int horizontal,int vertical){v.setPadding(dp(horizontal),dp(vertical),dp(horizontal),dp(vertical));}
  TextView heading(String value,int size){TextView t=RadarUi.text(this,value,size,fg,true);return t;}
  View iconButton(String icon,String description,Runnable action){
    FrameLayout frame=new FrameLayout(this);RadarUi.touch(frame,surface,15,dark?0xFF303238:0xFFE1E3E7);
    ImageView image=RadarUi.icon(this,icon,fg,24);FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(dp(24),dp(24),Gravity.CENTER);frame.addView(image,params);
    frame.setContentDescription(description);frame.setFocusable(true);frame.setOnClickListener(v->action.run());return frame;
  }
  TextView primary(String value,boolean yellow,Runnable action){TextView t=text(value,14,yellow?Color.BLACK:fg);t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));t.setGravity(Gravity.CENTER);t.setMinHeight(dp(48));RadarUi.touch(t,yellow?RadarUi.YELLOW:(dark?0xFF383C43:0xFFE1E3E7),14,0);t.setOnClickListener(v->action.run());return t;}

  void build(Bundle saved){
    dark=store.prefs.getBoolean("dark",true);fg=dark?0xFFF5F5F7:0xFF15171B;bg=dark?RadarUi.BACKGROUND:0xFFF2F3F5;
    surface=dark?RadarUi.CARD:Color.WHITE;muted=dark?0xFF92969F:0xFF676D76;
    getWindow().setStatusBarColor(bg);getWindow().setNavigationBarColor(bg);
    getWindow().getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
    root=column();root.setBackgroundColor(bg);root.setFitsSystemWindows(true);setContentView(root);
    pages=new FrameLayout(this);root.addView(pages,new LinearLayout.LayoutParams(-1,0,1));
    buildMap(saved);buildPoints();buildSettings();
    View border=new View(this);border.setBackgroundColor(dark?0xFF25272C:0xFFDDDFE3);root.addView(border,new LinearLayout.LayoutParams(-1,dp(1)));
    navigation=row();navigation.setBackgroundColor(bg);
    String[] labels={"Карта","Точки","Настройки"},icons={"map","list","settings"};
    for(int i=0;i<labels.length;i++){
      final int tab=i;LinearLayout item=column();item.setGravity(Gravity.CENTER);item.setContentDescription("Вкладка "+labels[i]);item.setFocusable(true);
      item.addView(RadarUi.icon(this,icons[i],i==currentTab?RadarUi.YELLOW:muted,23));
      TextView label=text(labels[i],10,i==currentTab?RadarUi.YELLOW:muted);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.topMargin=dp(5);item.addView(label,lp);
      item.setOnClickListener(v->showTab(tab));navigation.addView(item,new LinearLayout.LayoutParams(0,dp(66),1));
    }
    root.addView(navigation);showTab(currentTab);refreshCards();
  }
  void buildMap(Bundle saved){
    mapPanel=column();mapFrame=new FrameLayout(this);mapPanel.addView(mapFrame,new LinearLayout.LayoutParams(-1,-1));pages.addView(mapPanel,new FrameLayout.LayoutParams(-1,-1));
    mapView=new MapView(this);mapView.onCreate(saved);mapFrame.addView(mapView,new FrameLayout.LayoutParams(-1,-1));
    pointOverlay=new View(this){@Override protected void onDraw(Canvas c){
      if(map==null)return;
      for(Point p:store.points()){
        LatLng location=pointLocation(p);if(location==null)continue;
        PointF xy=map.getProjection().toScreenLocation(location);Bitmap icon=pointBitmaps.get(p.id);
        if(icon!=null)c.drawBitmap(icon,xy.x-icon.getWidth()/2f,xy.y-icon.getHeight()/2f,null);
      }
    }};pointOverlay.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    mapFrame.addView(pointOverlay,new FrameLayout.LayoutParams(-1,-1));
    LinearLayout title=column();title.addView(heading("Радар Pro",24));
    state=text("Мониторинг выключен · начать",10,muted);state.setPadding(0,dp(8),dp(4),dp(10));state.setContentDescription("Управление мониторингом");state.setOnClickListener(v->toggleMonitor());title.addView(state);
    FrameLayout.LayoutParams tl=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.START);tl.setMargins(dp(20),dp(18),dp(78),0);mapFrame.addView(title,tl);
    LinearLayout tools=column();String[] icons={"search","layers","locate"},names={"Поиск точки","Слои карты","Моя геопозиция"};Runnable[] actions={this::addPoint,this::mapLayers,this::locate};
    for(int i=0;i<icons.length;i++){View button=iconButton(icons[i],names[i],actions[i]);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(48),dp(48));lp.bottomMargin=dp(8);tools.addView(button,lp);}
    FrameLayout.LayoutParams tp=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.END);tp.setMargins(0,dp(16),dp(16),0);mapFrame.addView(tools,tp);
    TextView loading=text("Загрузка карты…",12,fg);pad(loading,12,8);loading.setBackground(shape(surface,12));FrameLayout.LayoutParams ll=new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER);mapFrame.addView(loading,ll);
    mapView.addOnDidFinishLoadingMapListener(()->loading.setVisibility(View.GONE));
    mapView.addOnDidFailLoadingMapListener(e->{loading.setVisibility(View.VISIBLE);loading.setText("Карта недоступна · повторить");loading.setOnClickListener(v->applyMapStyle());});
    mapView.getMapAsync(m->{map=m;applyMapStyle();m.getUiSettings().setCompassEnabled(false);
      m.getUiSettings().setAttributionGravity(Gravity.BOTTOM|Gravity.END);m.getUiSettings().setLogoGravity(Gravity.BOTTOM|Gravity.START);
      m.addOnMapLongClickListener(pos->{Point p=new Point(UUID.randomUUID().toString(),"Новая точка",pos.getLatitude(),pos.getLongitude(),false);p.tariffs=new ArrayList<>(mapTariffs());edit(p);return true;});
      m.addOnMapClickListener(pos->{
        Point hit=pointAt(m.getProjection().toScreenLocation(pos));if(hit!=null){edit(hit);return true;}
        hidePreview();return false;
      });
      m.addOnCameraMoveListener(()->pointOverlay.invalidate());
      double lat=Double.longBitsToDouble(store.prefs.getLong("lat",Double.doubleToLongBits(56.8389))),lon=Double.longBitsToDouble(store.prefs.getLong("lon",Double.doubleToLongBits(60.5975)));
      m.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lat,lon),12.3));
    });
  }
  void applyMapStyle(){
    if(map==null)return;
    try { java.io.InputStream in=getAssets().open(dark?"map-dark.json":"map-light.json");java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)bytes.write(b,0,n);in.close();
      String style=bytes.toString("UTF-8").replace("https://vector.openstreetmap.org/shortbread_v1/tilejson.json",store.prefs.getString("vectorTileJson","https://vector.openstreetmap.org/shortbread_v1/tilejson.json"));
      map.setStyle(new Style.Builder().fromJson(style),s->refreshMarkers());
    }catch(Exception e){toast("Не удалось открыть оформление карты");}
  }
  void buildPoints(){
    pointsPanel=column();pages.addView(pointsPanel,new FrameLayout.LayoutParams(-1,-1));
    LinearLayout header=row();header.setPadding(dp(20),dp(20),dp(16),dp(20));header.addView(heading("Мои точки",24),new LinearLayout.LayoutParams(0,-2,1));
    View plus=iconButton("plus","Добавить точку",this::addPoint);plus.setBackground(shape(RadarUi.YELLOW,12));((ImageView)((FrameLayout)plus).getChildAt(0)).setImageDrawable(new RadarUi.Icon("plus",Color.BLACK));header.addView(plus,new LinearLayout.LayoutParams(dp(44),dp(44)));pointsPanel.addView(header);
    ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);cards=column();cards.setPadding(dp(20),0,dp(16),dp(16));scroll.addView(cards);pointsPanel.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  }
  void buildSettings(){settingsPanel=column();pages.addView(settingsPanel,new FrameLayout.LayoutParams(-1,-1));
    TextView title=heading("Настройки",24);title.setPadding(dp(20),dp(24),dp(20),dp(20));settingsPanel.addView(title);
    ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);LinearLayout body=settingsContents();scroll.addView(body);settingsPanel.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));}
  void showTab(int tab){
    currentTab=Math.max(0,Math.min(2,tab));mapPanel.setVisibility(currentTab==0?View.VISIBLE:View.GONE);pointsPanel.setVisibility(currentTab==1?View.VISIBLE:View.GONE);settingsPanel.setVisibility(currentTab==2?View.VISIBLE:View.GONE);
    if(navigation!=null){String[] icons={"map","list","settings"};for(int i=0;i<3;i++){LinearLayout item=(LinearLayout)navigation.getChildAt(i);item.setSelected(i==currentTab);int color=i==currentTab?RadarUi.YELLOW:muted;((ImageView)item.getChildAt(0)).setImageDrawable(new RadarUi.Icon(icons[i],color));((TextView)item.getChildAt(1)).setTextColor(color);}}
    if(currentTab==2&&resumed)rebuildSettings();
    updateViewing();if(currentTab!=2){sendBroadcast(new Intent("dev.raf.radar.VIEWING").setPackage(getPackageName()));refreshCards();}
  }
  void updateViewing(){store.prefs.edit().putLong("viewingUntil",resumed&&currentTab!=2?System.currentTimeMillis()+15000:0).apply();}
  String pointAddress(Point p){return p.dynamic?"Ваше текущее местоположение":p.address.isEmpty()?String.format(Locale.US,"%.5f, %.5f",p.lat,p.lon):p.address;}
  Set<String> mapTariffs(){return new LinkedHashSet<>(TariffSelection.ordered(store.prefs.getStringSet("mapTariffs",new LinkedHashSet<>(Arrays.asList("econom","business")))));}
  Store.Reading displayReading(Point p){Store.Reading result=null;for(Store.Reading r:store.readings(p.id))if(mapTariffs().contains(r.tariff)&&(r.rub!=null||r.ratio!=null)){if(result==null||(!result.fresh()&&r.fresh()))result=r;}return result;}
  void refreshCards(){
    if(cards==null)return;
    if(state!=null){state.setText(MonitorService.running?"Мониторинг включён · остановить":"Мониторинг выключен · начать");state.setTextColor(MonitorService.running?0xFF99D641:muted);}
    cards.removeAllViews();
    for(Point p:store.points()){
      if(p.dynamic)continue;
      LinearLayout line=row();line.setMinimumHeight(dp(76));
      View dot=new View(this);dot.setBackground(shape(p.dynamic?0xFF69AEFF:RadarUi.tariffColor(p.tariffs.isEmpty()?"econom":p.tariffs.get(0)),20));LinearLayout.LayoutParams dotParams=new LinearLayout.LayoutParams(dp(12),dp(12));dotParams.rightMargin=dp(12);line.addView(dot,dotParams);
      LinearLayout labels=column();TextView name=RadarUi.text(this,p.name,15,fg,true);name.setMaxLines(1);name.setEllipsize(android.text.TextUtils.TruncateAt.END);labels.addView(name);
      TextView address=text(pointAddress(p),12,muted);address.setMaxLines(1);address.setEllipsize(android.text.TextUtils.TruncateAt.END);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,-2);ap.topMargin=dp(5);labels.addView(address,ap);
      labels.setContentDescription("Настроить точку: "+p.name);labels.setOnClickListener(v->{selected=p.id;editDetails(p);});line.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
      if(!p.dynamic){RadarUi.Toggle active=new RadarUi.Toggle(this);active.setContentDescription("Мониторинг: "+p.name);active.setChecked(p.enabled);active.setOnCheckedChangeListener((button,enabled)->{p.enabled=enabled;store.update(p);refreshMarkers();});line.addView(active,new LinearLayout.LayoutParams(dp(56),dp(48)));}
      else{View gear=iconButton("settings","Настроить геопозицию",()->editDetails(p));gear.setBackgroundColor(Color.TRANSPARENT);line.addView(gear,new LinearLayout.LayoutParams(dp(48),dp(48)));}
      cards.addView(line);View divider=new View(this);divider.setBackgroundColor(dark?0xFF23252A:0xFFE0E2E6);cards.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
    }
    refreshMarkers();if(preview!=null)updatePreviewValue();
  }
  void refreshMarkers(){
    if(map==null||map.getStyle()==null||!map.getStyle().isFullyLoaded())return;
    java.util.List<org.maplibre.geojson.Feature> glowPoints=new ArrayList<>();pointBitmaps.clear();
    for(Point p:store.points()){
      double lat=p.lat,lon=p.lon;if(p.dynamic){if(store.prefs.getLong("gpsAt",0)==0)continue;lat=Double.longBitsToDouble(store.prefs.getLong("lat",0));lon=Double.longBitsToDouble(store.prefs.getLong("lon",0));}
      Store.Reading reading=displayReading(p);
      if(!p.dynamic&&p.enabled&&reading!=null&&reading.fresh()){
        org.maplibre.geojson.Feature feature=org.maplibre.geojson.Feature.fromGeometry(org.maplibre.geojson.Point.fromLngLat(lon,lat));
        feature.addStringProperty("color",String.format(Locale.US,"#%06X",RadarUi.tariffColor(reading.tariff)&0xFFFFFF));glowPoints.add(feature);
      }
      pointBitmaps.put(p.id,pointIcon(p));
    }
    org.maplibre.android.style.sources.GeoJsonSource source=map.getStyle().getSourceAs("reading-points");
    org.maplibre.geojson.FeatureCollection data=org.maplibre.geojson.FeatureCollection.fromFeatures(glowPoints);
    if(source==null){
      source=new org.maplibre.android.style.sources.GeoJsonSource("reading-points",data);map.getStyle().addSource(source);
      org.maplibre.android.style.layers.CircleLayer glow=new org.maplibre.android.style.layers.CircleLayer("reading-glow","reading-points").withProperties(
        org.maplibre.android.style.layers.PropertyFactory.circleColor(org.maplibre.android.style.expressions.Expression.get("color")),
        org.maplibre.android.style.layers.PropertyFactory.circleRadius(40f),org.maplibre.android.style.layers.PropertyFactory.circleOpacity(.3f),org.maplibre.android.style.layers.PropertyFactory.circleBlur(.8f));
      map.getStyle().addLayerBelow(glow,"cities");
    }else source.setGeoJson(data);
    pointOverlay.invalidate();
  }
  LatLng pointLocation(Point p){
    if(!p.dynamic)return new LatLng(p.lat,p.lon);
    if(store.prefs.getLong("gpsAt",0)==0)return null;
    return new LatLng(Double.longBitsToDouble(store.prefs.getLong("lat",0)),Double.longBitsToDouble(store.prefs.getLong("lon",0)));
  }
  Point pointAt(PointF hit){
    java.util.List<Point> points=store.points();for(int i=points.size()-1;i>=0;i--){Point p=points.get(i);LatLng loc=pointLocation(p);if(loc==null)continue;
      PointF xy=map.getProjection().toScreenLocation(loc);Bitmap icon=pointBitmaps.get(p.id);if(icon!=null&&Math.abs(hit.x-xy.x)<=icon.getWidth()/2f+dp(4)&&Math.abs(hit.y-xy.y)<=icon.getHeight()/2f+dp(4))return p;
    }return null;
  }
  List<Store.Reading> mapReadings(Point point){
    List<Store.Reading> rows=new ArrayList<>();
    List<Store.Reading> cached=store.readings(point.id);
    for(String tariff:mapTariffs()){
      Store.Reading match=null;for(Store.Reading reading:cached)if(tariff.equals(reading.tariff)){match=reading;break;}
      rows.add(match!=null?match:new Store.Reading(tariff,null,null,0,"Ожидаем данные"));
    }
    return rows;
  }
  Bitmap pointIcon(Point point){
    List<Store.Reading> rows=mapReadings(point);
    Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));paint.setTextSize(dp(12));
    int width=dp(90),rowHeight=dp(30),header=point.dynamic?dp(22):0;
    for(Store.Reading reading:rows)width=Math.max(width,Math.round(paint.measureText(Point.label(reading.tariff)+"  "+RadarUi.value(reading)))+dp(24));
    Bitmap bitmap=Bitmap.createBitmap(width,Math.max(dp(30),header+rows.size()*rowHeight+dp(8)),Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);
    if(point.dynamic){paint.setColor(0xDD273655);canvas.drawRoundRect(0,0,width,header+dp(8),dp(9),dp(9),paint);paint.setColor(Color.WHITE);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(dp(10));canvas.drawText("▲ Моя геопозиция",width/2f,dp(15),paint);paint.setTextSize(dp(12));}
    for(int i=0;i<rows.size();i++){
      Store.Reading reading=rows.get(i);int color=point.enabled&&reading.fresh()?RadarUi.tariffColor(reading.tariff):0xFF747983;
      int top=header+i*rowHeight;paint.setColor(color);canvas.drawRoundRect(dp(2),top+dp(2),width-dp(2),top+rowHeight-dp(2),dp(8),dp(8),paint);
      paint.setColor(Color.WHITE);paint.setTextAlign(Paint.Align.CENTER);canvas.drawText(Point.label(reading.tariff)+"  "+RadarUi.value(reading),width/2f,top+dp(20),paint);
    }
    return bitmap;
  }
  void toggleMonitor(){if(MonitorService.running)stopService(new Intent(this,MonitorService.class));else startMonitor();handler.postDelayed(this::refreshCards,350);}
  void hidePreview(){if(pointSheet!=null)mapFrame.removeView(pointSheet);pointSheet=null;preview=null;}
  TextView previewValue;
  void updatePreviewValue(){if(previewValue!=null&&preview!=null)previewValue.setText("Кеф "+RadarUi.value(displayReading(preview)));}
  void edit(Point p){
    if(p.dynamic&&store.prefs.getLong("gpsAt",0)>0){p.lat=Double.longBitsToDouble(store.prefs.getLong("lat",0));p.lon=Double.longBitsToDouble(store.prefs.getLong("lon",0));}
    showTab(0);hidePreview();preview=p;selected=p.id;
    LinearLayout sheet=column();sheet.setPadding(dp(18),dp(10),dp(18),dp(18));GradientDrawable background=shape(dark?0xFF16181B:Color.WHITE,26);background.setStroke(dp(1),dark?0xFF34373D:0xFFDCDDE0);sheet.setBackground(background);sheet.setElevation(dp(10));
    View handle=new View(this);handle.setBackground(shape(dark?0xFF666B73:0xFFC0C3C8,4));LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(dp(32),dp(4));hp.gravity=Gravity.CENTER_HORIZONTAL;hp.bottomMargin=dp(18);sheet.addView(handle,hp);
    sheet.addView(text(String.format(Locale.US,"%.5f, %.5f",p.lat,p.lon),12,muted));
    previewValue=heading("Кеф "+RadarUi.value(displayReading(p)),22);LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(-1,-2);vp.topMargin=dp(12);vp.bottomMargin=dp(10);sheet.addView(previewValue,vp);
    TextView address=text(pointAddress(p),16,fg);address.setMaxLines(2);sheet.addView(address);
    if(!p.dynamic){final double lat=p.lat,lon=p.lon;resolveAddress(lat,lon,result->{if(preview!=p)return;p.address=result;address.setText(result);cacheAddress(p.id,lat,lon,result);});}
    LinearLayout buttons=row();LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(50));bp.topMargin=dp(20);sheet.addView(buttons,bp);
    TextView save=primary("Сохранить",true,()->{store.update(p);store.prefs.edit().putString("overlayPoint",p.id).apply();hidePreview();refreshCards();});
    TextView cancel=primary("Отменить",false,this::hidePreview);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,-1,1);sp.rightMargin=dp(8);buttons.addView(save,sp);buttons.addView(cancel,new LinearLayout.LayoutParams(0,-1,1));
    FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);mapFrame.addView(sheet,params);pointSheet=sheet;
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


  void editDetails(Point p) {
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
      if (!p.tariffs.contains(r.tariff)) continue;
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


  Button action(String value,Runnable run){Button button=new Button(this);button.setText(value);button.setTextColor(fg);button.setTextSize(13);button.setAllCaps(false);button.setMinHeight(dp(48));button.setPadding(dp(12),0,dp(12),0);RadarUi.touch(button,surface,12,0);button.setOnClickListener(v->run.run());return button;}
  void settings(){showTab(2);}
  LinearLayout settingsContents(){
    LinearLayout body=column();body.setPadding(dp(14),0,dp(14),dp(24));
    LinearLayout widget=group(body,"Виджет");
    sizeSlider(widget,"Размер виджета","scale",60,160);
    settingsDivider(widget);
    LinearLayout opacityLabel=row();opacityLabel.addView(text("Прозрачность",13,fg),new LinearLayout.LayoutParams(0,-2,1));TextView opacityValue=text(store.prefs.getInt("opacity",70)+"%",12,muted);opacityLabel.addView(opacityValue);LinearLayout.LayoutParams op=new LinearLayout.LayoutParams(-1,dp(42));widget.addView(opacityLabel,op);
    SeekBar opacity=new SeekBar(this);opacity.setMax(80);opacity.setProgress(store.prefs.getInt("opacity",70)-20);opacity.setProgressTintList(ColorStateList.valueOf(RadarUi.YELLOW));opacity.setProgressBackgroundTintList(ColorStateList.valueOf(0xFF45484E));opacity.setThumbTintList(ColorStateList.valueOf(Color.WHITE));opacity.setContentDescription("Прозрачность виджета");widget.addView(opacity,new LinearLayout.LayoutParams(-1,dp(30)));
    opacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int progress,boolean user){int value=progress+20;opacityValue.setText(value+"%");if(user)store.prefs.edit().putInt("opacity",value).apply();}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});
    settingsDivider(widget);sizeSlider(widget,"Размер текста","textScale",60,160);
    settingsDivider(widget);switchRow(widget,"Показывать тариф",store.prefs.getBoolean("showTariff",true),0,value->store.prefs.edit().putBoolean("showTariff",value).apply());
    settingAction(widget,"Тарифы виджета",()->{String id=store.prefs.getString("overlayPoint","me");for(Point p:store.points())if(p.id.equals(id)){editDetails(p);return;}});
    LinearLayout tariffs=group(body,"Тарифы на карте");TextView caption=text("Все выбранные тарифы отображаются на каждой точке.\nТарифы виджета и уведомлений задаются в настройках точки.",11,muted);caption.setPadding(0,0,0,dp(8));tariffs.addView(caption);
    for(String tariff:TARIFF_IDS){RadarUi.Toggle toggle=switchRow(tariffs,Point.label(tariff),mapTariffs().contains(tariff),RadarUi.tariffColor(tariff),null);toggle.setContentDescription("Тариф на карте: "+Point.label(tariff));toggle.setOnCheckedChangeListener((button,enabled)->{
      Set<String> selected=new LinkedHashSet<>(mapTariffs());if(enabled)selected.add(tariff);else selected.remove(tariff);
      if(selected.isEmpty()){button.setChecked(true);toast("Оставьте хотя бы один тариф");return;}
      store.prefs.edit().putStringSet("mapTariffs",selected).apply();refreshMarkers();
    });}
    LinearLayout updates=group(body,"Обновление");switchRow(updates,"Автоматическая проверка",store.prefs.getBoolean("autoUpdate",true),0,value->store.prefs.edit().putBoolean("autoUpdate",value).apply());
    TextView explain=text("Приложение проверяет обновления\nпри каждом запуске",11,muted);explain.setPadding(0,0,0,dp(12));updates.addView(explain);
    settingsDivider(updates);settingAction(updates,"Проверить обновления",()->Updates.check(this,store));
    LinearLayout work=group(body,"Работа приложения");
    settingAction(work,"Моя геопозиция",()->{for(Point p:store.points())if(p.dynamic){editDetails(p);break;}});
    switchRow(work,"Мониторинг",MonitorService.running,0,enabled->{if(enabled)startMonitor();else stopService(new Intent(this,MonitorService.class));handler.postDelayed(this::refreshCards,300);});
    switchRow(work,"Виджет поверх приложений",store.prefs.getBoolean("overlay",false),0,enabled->{if(enabled&&!Settings.canDrawOverlays(this)){access.request();return;}store.prefs.edit().putBoolean("overlay",enabled).putString("overlayPoint",selected).apply();if(enabled)startMonitor();});
    settingAction(work,"Интервал обновления",()->numberDialog("Интервал каждой точки","interval",30,600,30," с"));
    settingAction(work,"Разрешения приложения",()->access.request());
    LinearLayout more=group(body,"Дополнительно");switchRow(more,"Звук при запуске",store.prefs.getBoolean("startupSound",true),0,value->store.prefs.edit().putBoolean("startupSound",value).apply());switchRow(more,"Тёмная тема",dark,0,value->store.prefs.edit().putBoolean("dark",value).apply());
    settingAction(more,"Применить оформление",this::recreate);
    settingAction(more,"Вход через Яндекс",()->startActivity(new Intent(this,LoginActivity.class)));
    settingAction(more,"Удалить сессию Яндекса",()->{android.webkit.CookieManager.getInstance().removeAllCookies(done->toast("Сессия удалена"));android.webkit.WebStorage.getInstance().deleteAllData();});
    TextView version=text("Радар Pro · "+BuildConfig.VERSION_NAME,11,muted);version.setGravity(Gravity.CENTER);version.setPadding(0,dp(8),0,dp(12));body.addView(version);
    TextView info=text("Надбавки в ₽ — расчётные. Сверяйте с Яндекс Про.\n© OpenStreetMap contributors · Нет рекламы и аналитики.",10,muted);info.setGravity(Gravity.CENTER);body.addView(info);
    return body;
  }
  LinearLayout group(LinearLayout body,String title){
    LinearLayout card=column();card.setPadding(dp(14),dp(14),dp(14),dp(4));
    GradientDrawable background=new GradientDrawable(GradientDrawable.Orientation.TL_BR,dark?new int[]{0xFF202225,0xFF181A1D}:new int[]{Color.WHITE,Color.WHITE});background.setCornerRadius(dp(18));card.setBackground(background);
    LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.bottomMargin=dp(14);body.addView(card,params);
    TextView heading=heading(title,16);heading.setPadding(0,0,0,dp(8));card.addView(heading);return card;
  }
  void settingsDivider(LinearLayout group){View line=new View(this);line.setBackgroundColor(dark?0xFF303238:0xFFE8E9EC);group.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));}
  interface BoolChange{void set(boolean enabled);}
  RadarUi.Toggle switchRow(LinearLayout group,String title,boolean checked,int dotColor,BoolChange change){
    LinearLayout r=row();r.setMinimumHeight(dp(48));
    if(dotColor!=0){View dot=new View(this);dot.setBackground(shape(dotColor,12));LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(dp(12),dp(12));params.rightMargin=dp(10);r.addView(dot,params);}
    TextView label=text(title,13,fg);r.addView(label,new LinearLayout.LayoutParams(0,-2,1));RadarUi.Toggle toggle=new RadarUi.Toggle(this);toggle.setContentDescription(title);toggle.setChecked(checked);
    if(change!=null)toggle.setOnCheckedChangeListener((button,value)->change.set(value));r.addView(toggle,new LinearLayout.LayoutParams(dp(52),dp(48)));r.setOnClickListener(v->toggle.performClick());group.addView(r);return toggle;
  }
  void sizeSlider(LinearLayout group,String title,String key,int min,int max){
    LinearLayout line=row();line.setMinimumHeight(dp(42));
    line.addView(text(title,13,fg),new LinearLayout.LayoutParams(0,-2,1));
    int current=Math.max(min,Math.min(max,store.prefs.getInt(key,100)));
    TextView value=text(current+"%",12,muted);line.addView(value);group.addView(line);
    SeekBar bar=new SeekBar(this);bar.setMax(max-min);bar.setProgress(current-min);bar.setContentDescription(title);
    bar.setProgressTintList(ColorStateList.valueOf(RadarUi.YELLOW));bar.setProgressBackgroundTintList(ColorStateList.valueOf(0xFF45484E));bar.setThumbTintList(ColorStateList.valueOf(Color.WHITE));
    group.addView(bar,new LinearLayout.LayoutParams(-1,dp(40)));
    bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
      public void onProgressChanged(SeekBar b,int progress,boolean user){int n=min+progress;value.setText(n+"%");if(user)store.prefs.edit().putInt(key,n).apply();}
      public void onStartTrackingTouch(SeekBar b){} public void onStopTrackingTouch(SeekBar b){}
    });
  }
  void settingAction(LinearLayout group,String title,Runnable action){LinearLayout line=row();line.setContentDescription(title);line.setMinimumHeight(dp(50));line.addView(text(title,13,fg),new LinearLayout.LayoutParams(0,-2,1));line.addView(RadarUi.icon(this,"right",muted,14));line.setOnClickListener(v->action.run());line.setFocusable(true);group.addView(line);}
  void numberDialog(String title,String key,int min,int max,int step,String unit){LinearLayout form=form();slider(form,title,min,max,store.prefs.getInt(key,120),step,value->store.prefs.edit().putInt(key,value).apply(),unit);new AlertDialog.Builder(this).setTitle(title).setView(form).setPositiveButton("Готово",null).show();}
  void mapLayers(){String[] labels=new String[TARIFF_IDS.length];boolean[] checked=new boolean[TARIFF_IDS.length];Set<String> selected=new LinkedHashSet<>(mapTariffs());for(int i=0;i<labels.length;i++){labels[i]=Point.label(TARIFF_IDS[i]);checked[i]=selected.contains(TARIFF_IDS[i]);}
    new AlertDialog.Builder(this).setTitle("Тарифы на карте").setMultiChoiceItems(labels,checked,(dialog,index,on)->{if(on)selected.add(TARIFF_IDS[index]);else selected.remove(TARIFF_IDS[index]);}).setPositiveButton("Применить",(d,w)->{if(selected.isEmpty()){toast("Оставьте хотя бы один тариф");return;}store.prefs.edit().putStringSet("mapTariffs",selected).apply();refreshMarkers();rebuildSettings();}).setNegativeButton("Отменить",null).show();}
  void rebuildSettings(){if(settingsPanel==null)return;settingsPanel.removeAllViews();TextView title=heading("Настройки",24);title.setPadding(dp(20),dp(24),dp(20),dp(20));settingsPanel.addView(title);ScrollView sc=new ScrollView(this);sc.addView(settingsContents());settingsPanel.addView(sc,new LinearLayout.LayoutParams(-1,0,1));}

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
    if (startupSoundPending) {
      startupSoundPending = false;
      startupSound.play(this, store.prefs.getBoolean("startupSound", true));
    }
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
    startupSound.stop();
    access.onStop();
    mapView.onStop();
    super.onStop();
  }

  @Override
  protected void onDestroy() {
    startupSound.stop();
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
