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
  MapGps mapGps;
  List<PresenceClient.Peer> peers=new ArrayList<>();
  Bitmap peerBitmap, nearbyPeerBitmap, offlinePeerBitmap;
  AlertDialog nameDialog,friendsDialog,accountDialog;LinearLayout friendsRows;List<PresenceClient.Peer> friends=new ArrayList<>();RadarUi.RefreshIcon friendsRefreshIcon;boolean directoryError;long nextDirectory;boolean directoryPending;
  long nextPresenceQuery;int presenceEpoch;boolean presenceQuery;

  final StartupSound startupSound = new StartupSound();
  boolean startupSoundPending;
  boolean startAfterAccess, pendingOverlay, resumed, dark;
  RadarLogo pro, brandLogo;
  SenseLogo senseLogo;LinearLayout classicBrand,mapTitle;TextView mapLoading;int senseHeight;
  LineageLogo lineageLogo;
  boolean senseStartPending,senseCommandPending,senseDesired;int senseClickRevision;
  RadarUi.Toggle monitorToggle,overlayToggle;
  final BroadcastReceiver monitorState=new BroadcastReceiver(){public void onReceive(Context c,Intent i){if("dev.raf.radar.PEERS".equals(i.getAction())){friends=PresenceClient.cached(store);peers=new ArrayList<>(friends);renderFriends();}refreshCards();}};
  int fg,bg,surface,muted,currentTab;
  LinearLayout root,cards,mapPanel,pointsPanel,settingsPanel,navigation;
  StatisticsPanel statisticsPanel;
  FrameLayout pages,mapFrame;
  MapView mapView; MapLibreMap map;
  TextView state,friendBadge;boolean socialPending;
  View pointSheet, pointDismiss, pointOverlay;
  final Map<String,Bitmap> pointBitmaps=new HashMap<>();
  Point preview;
  String selected="me";
  Handler handler=new Handler();
  static final String[] TARIFF_IDS={"econom"};
  final Runnable refreshLoop=new Runnable(){public void run(){if(resumed){updateViewing();mapGps.start();refreshCards();refreshPeers();refreshFriends(false);if(currentTab==3&&statisticsPanel!=null)statisticsPanel.visible();handler.postDelayed(this,5000);}}};

  @Override public void onCreate(Bundle saved) {
    setTheme(Appearance.lineage(this)?R.style.AppThemeLineage:R.style.AppThemeDark);
    super.onCreate(saved);MapLibre.getInstance(this);store=new Store(this);access=new Access(this,store);
    mapGps=new MapGps(this,store,this::refreshMarkers);friends=PresenceClient.cached(store);peers=new ArrayList<>(friends);
    startupSoundPending = saved == null;
    if(saved!=null){startAfterAccess=saved.getBoolean("startAfterAccess");pendingOverlay=saved.getBoolean("pendingOverlay");}
    currentTab=saved==null?0:saved.getInt("tab",0);
    String id=getIntent().getStringExtra("point");if(id!=null)selected=id;
    build(saved);if(saved!=null){brandLogo.motion.angle=saved.getFloat("radarSweepAngle",-45);senseLogo.motion.angle=saved.getFloat("senseSweepAngle",-45);}openRequestedSettings(getIntent());
    if(saved==null&&store.prefs.getBoolean("autoUpdate",true))handler.postDelayed(()->Updates.checkAutomatic(this,store),1200);
  }
  @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);openRequestedSettings(intent);}
  private void openRequestedSettings(Intent intent){if(intent.getBooleanExtra("openDiagnostics",false)){intent.removeExtra("openDiagnostics");handler.post(()->{if(!isFinishing()){showTab(2);Diagnostics.ui(this);}});}if(intent.getBooleanExtra("openFriends",false)){intent.removeExtra("openFriends");handler.post(()->{if(!isFinishing()&&AccountClient.active(store)){showTab(0);showFriends();}});}if(intent.getBooleanExtra("widgetSettings",false)){intent.removeExtra("widgetSettings");handler.post(()->{if(!isFinishing())settings();});}}
  int dp(float n){return RadarUi.dp(this,n);}
  TextView text(String value,int size,int color){return RadarUi.text(this,value,size,color,false);}
  GradientDrawable shape(int color,int radius){return RadarUi.shape(this,color,radius,0);}
  LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
  LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
  void pad(View v,int horizontal,int vertical){v.setPadding(dp(horizontal),dp(vertical),dp(horizontal),dp(vertical));}
  TextView heading(String value,int size){TextView t=RadarUi.text(this,value,size,fg,true);t.setTag(R.id.theme_heading,true);if(Appearance.lineage(this)){t.setTypeface(Typeface.create("serif",Typeface.BOLD));t.setTextColor(Appearance.GOLD);}return t;}
  View iconButton(String icon,String description,Runnable action){
    FrameLayout frame=new FrameLayout(this);RadarUi.touch(frame,surface,15,dark?0xFF303238:0xFFE1E3E7);
    ImageView image=RadarUi.icon(this,icon,fg,24);FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(dp(24),dp(24),Gravity.CENTER);frame.addView(image,params);
    frame.setContentDescription(description);frame.setFocusable(true);frame.setOnClickListener(v->action.run());return frame;
  }
  TextView primary(String value,boolean yellow,Runnable action){TextView t=text(value,14,Appearance.lineage(this)?Appearance.TEXT:yellow?Color.BLACK:fg);t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));t.setGravity(Gravity.CENTER);t.setMinHeight(dp(48));RadarUi.touch(t,yellow?RadarUi.YELLOW:(dark?0xFF383C43:0xFFE1E3E7),14,0);t.setOnClickListener(v->{AppSounds.ui(this,AppSounds.Event.CLICK);action.run();});return t;}

  void palette(){dark=true;boolean fantasy=Appearance.lineage(this);fg=fantasy?Appearance.TEXT:0xFFF5F5F7;bg=fantasy?Appearance.INK:RadarUi.BACKGROUND;surface=fantasy?Appearance.PANEL:RadarUi.CARD;muted=fantasy?Appearance.MUTED:0xFF92969F;}

  void build(Bundle saved){
    palette();
    getWindow().setStatusBarColor(bg);getWindow().setNavigationBarColor(bg);
    getWindow().getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
    root=column();root.setBackgroundColor(bg);root.setFitsSystemWindows(true);setContentView(root);
    pages=new FrameLayout(this);root.addView(pages,new LinearLayout.LayoutParams(-1,0,1));
    buildMap(saved);buildPoints();buildSettings();statisticsPanel=new StatisticsPanel(this);pages.addView(statisticsPanel,new FrameLayout.LayoutParams(-1,-1));
    View border=new View(this);border.setBackgroundColor(dark?0xFF25272C:0xFFDDDFE3);root.addView(border,new LinearLayout.LayoutParams(-1,dp(1)));
    navigation=row();navigation.setBackgroundColor(bg);
    rebuildNavigation();
    root.addView(navigation);showTab(currentTab);refreshCards();
    themeMapCaption();
  }
  void buildMap(Bundle saved){
    mapPanel=column();mapFrame=new FrameLayout(this);mapPanel.addView(mapFrame,new LinearLayout.LayoutParams(-1,-1));pages.addView(mapPanel,new FrameLayout.LayoutParams(-1,-1));
    mapView=new MapView(this);mapView.onCreate(saved);mapFrame.addView(mapView,new FrameLayout.LayoutParams(-1,-1));
    pointOverlay=new View(this){@Override protected void onDraw(Canvas c){
      if(map==null)return;
      String own=PresenceClient.ownId(store);
      if(peerBitmap==null)peerBitmap=locationIcon(false,true);
      if(offlinePeerBitmap==null)offlinePeerBitmap=locationIcon(false,false);
      if(nearbyPeerBitmap==null)nearbyPeerBitmap=nearbyPeerIcon();
      LatLng ownLocation=pointLocation(store.widgetPoint());
      PointF ownScreen=ownLocation==null?null:map.getProjection().toScreenLocation(ownLocation);
      for(PresenceClient.Peer peer:peers){
        if(!peer.hasLocation||!peer.retained()||peer.id.equals(own))continue;
        PointF xy=map.getProjection().toScreenLocation(new LatLng(peer.lat,peer.lon));
        // Keep the true coordinate; a wider purple ring remains visible around our gold dot.
        Bitmap peerIcon=!peer.fresh()?offlinePeerBitmap:ownScreen!=null&&Math.hypot(xy.x-ownScreen.x,xy.y-ownScreen.y)<dp(18)?nearbyPeerBitmap:peerBitmap;
        c.drawBitmap(peerIcon,xy.x-peerIcon.getWidth()/2f,xy.y-peerIcon.getHeight()/2f,null);
        drawPersonLabel(c,xy,peer.name,peer.fresh()?peer.locationAge():peer.status());
      }
      for(Point p:store.points()){
        LatLng location=pointLocation(p);if(location==null)continue;
        PointF xy=map.getProjection().toScreenLocation(location);Bitmap icon=pointBitmaps.get(p.id);
        if(icon!=null){PointF label=labelPosition(p,xy,icon);if(!p.dynamic){Paint pin=new Paint(Paint.ANTI_ALIAS_FLAG);pin.setColor(0xB3FFFFFF);pin.setStrokeWidth(dp(1));float edgeY=label.y+(xy.y>=label.y?1:-1)*icon.getHeight()/2f;c.drawLine(xy.x,xy.y,label.x,edgeY,pin);c.drawCircle(xy.x,xy.y,dp(3),pin);}c.drawBitmap(icon,label.x-icon.getWidth()/2f,label.y-icon.getHeight()/2f,null);}
        if(p.dynamic)drawPersonLabel(c,xy,store.prefs.getString("profileName",""),null);
      }
    }};pointOverlay.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    mapFrame.addView(pointOverlay,new FrameLayout.LayoutParams(-1,-1));
    LinearLayout title=column();mapTitle=title;LinearLayout brand=row();
    int logoWidth=Math.min(190,(int)(getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density)-170);
    brandLogo=new RadarLogo(this,false);
    brand.addView(brandLogo,new LinearLayout.LayoutParams(dp(logoWidth),dp(logoWidth/2.48f)));
    pro=new RadarLogo(this,true);pro.setFocusable(true);pro.setClickable(true);pro.setOnClickListener(v->toggleMonitor());
    brand.addView(pro,new LinearLayout.LayoutParams(dp(64),dp(48)));classicBrand=brand;
    FrameLayout logoFrame=new FrameLayout(this);logoFrame.addView(brand,new FrameLayout.LayoutParams(-2,-2));
    int senseWidth=Math.round(0.65f*Math.min(210,(int)(getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density)-100));
    senseHeight=(int)(senseWidth*SenseLogo.HEIGHT/SenseLogo.WIDTH);
    senseLogo=new SenseLogo(this);senseLogo.setOnClickListener(v->toggleSenseMonitor());
    senseLogo.setVisibility(View.VISIBLE);
    classicBrand.setVisibility(View.GONE);
    logoFrame.addView(senseLogo,new FrameLayout.LayoutParams(dp(senseWidth),dp(senseHeight)));title.addView(logoFrame);
    lineageLogo=new LineageLogo(this);lineageLogo.setOnClickListener(v->toggleSenseMonitor());
    logoFrame.addView(lineageLogo,new FrameLayout.LayoutParams(dp(senseWidth),dp(senseWidth*LineageLogo.HEIGHT/LineageLogo.WIDTH)));
    state=text("Мониторинг выключен",10,muted);state.setPadding(0,dp(8),dp(4),dp(10));state.setContentDescription("Состояние мониторинга");title.addView(state);
    FrameLayout.LayoutParams tl=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.START);tl.setMargins(dp(20),dp(18),dp(78),0);mapFrame.addView(title,tl);
    LinearLayout tools=column();String[] icons={"search","locate","person"},names={"Поиск точки","Моя геопозиция","Друзья"};Runnable[] actions={this::addPoint,this::locate,this::showFriends};
    for(int i=0;i<icons.length;i++){View button=iconButton(icons[i],names[i],actions[i]);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(48),dp(48));lp.bottomMargin=dp(8);tools.addView(button,lp);if(i==2&&button instanceof FrameLayout){friendBadge=text("",9,Color.WHITE);friendBadge.setGravity(Gravity.CENTER);friendBadge.setBackground(shape(0xFFAB45FF,10));FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(dp(20),dp(20),Gravity.TOP|Gravity.END);((FrameLayout)button).addView(friendBadge,bp);FriendUi.badge(this);}}
    FrameLayout.LayoutParams tp=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.END);tp.setMargins(0,dp(16),dp(16),0);mapFrame.addView(tools,tp);
    TextView loading=text("Загрузка карты…",12,fg);mapLoading=loading;pad(loading,12,8);loading.setBackground(shape(surface,12));FrameLayout.LayoutParams ll=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL);ll.topMargin=dp(130);mapFrame.addView(loading,ll);
    mapView.addOnDidFinishLoadingMapListener(()->loading.setVisibility(View.GONE));
    mapView.addOnDidFailLoadingMapListener(e->{loading.setVisibility(View.VISIBLE);loading.setText("Карта недоступна · повторить");loading.setOnClickListener(v->applyMapStyle());});
    mapView.getMapAsync(m->{map=m;applyMapStyle();m.getUiSettings().setCompassEnabled(false);
      m.getUiSettings().setAttributionGravity(Gravity.BOTTOM|Gravity.END);m.getUiSettings().setLogoGravity(Gravity.BOTTOM|Gravity.START);
      m.addOnMapLongClickListener(pos->{Point p=new Point(UUID.randomUUID().toString(),"Новая точка",pos.getLatitude(),pos.getLongitude(),false);p.tariffs=new ArrayList<>(Collections.singletonList("econom"));edit(p);return true;});
      m.addOnMapClickListener(pos->{
        Point hit=pointAt(m.getProjection().toScreenLocation(pos));if(hit!=null){edit(hit);return true;}
        hidePreview();return false;
      });
      m.addOnCameraMoveListener(()->pointOverlay.invalidate());
      m.addOnCameraIdleListener(this::refreshPeers);
      double lat=Double.longBitsToDouble(store.prefs.getLong("lat",Double.doubleToLongBits(56.8389))),lon=Double.longBitsToDouble(store.prefs.getLong("lon",Double.doubleToLongBits(60.5975)));
      m.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lat,lon),12.3));
    });
  }
  void applyMapStyle(){
    if(map==null)return;
    try { java.io.InputStream in=getAssets().open(Appearance.lineage(this)?"map-lineage.json":dark?"map-dark.json":"map-light.json");java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)bytes.write(b,0,n);in.close();
      String style=bytes.toString("UTF-8").replace("https://vector.openstreetmap.org/shortbread_v1/tilejson.json",store.prefs.getString("vectorTileJson","https://vector.openstreetmap.org/shortbread_v1/tilejson.json"));
      map.setStyle(new Style.Builder().fromJson(style),s->refreshMarkers());
    }catch(Exception e){toast("Не удалось открыть оформление карты");}
  }
  void buildPoints(){
    pointsPanel=column();pages.addView(pointsPanel,new FrameLayout.LayoutParams(-1,-1));
    LinearLayout header=row();header.setPadding(dp(20),dp(20),dp(16),dp(20));header.addView(heading("Мои точки",24),new LinearLayout.LayoutParams(0,-2,1));
    View plus=iconButton("plus","Добавить точку",this::addPoint);RadarUi.touch(plus,RadarUi.YELLOW,12,0);((ImageView)((FrameLayout)plus).getChildAt(0)).setImageDrawable(new RadarUi.Icon("plus",Appearance.lineage(this)?Appearance.TEXT:Color.BLACK));header.addView(plus,new LinearLayout.LayoutParams(dp(44),dp(44)));pointsPanel.addView(header);
    ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);cards=column();cards.setPadding(dp(20),0,dp(16),dp(16));scroll.addView(cards);pointsPanel.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  }
  void buildSettings(){settingsPanel=column();pages.addView(settingsPanel,new FrameLayout.LayoutParams(-1,-1));
    TextView title=heading("Настройки",24);title.setPadding(dp(20),dp(24),dp(20),dp(20));settingsPanel.addView(title);
    ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);LinearLayout body=settingsContents();scroll.addView(body);settingsPanel.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));}
  void showTab(int tab){
    if(resumed&&tab!=currentTab)AppSounds.ui(this,tab==0?AppSounds.Event.TAB_MAP:tab==1?AppSounds.Event.TAB_POINTS:AppSounds.Event.MENU_OPEN);
    currentTab=tab==3&&store.prefs.getBoolean("experimentalStatistics",false)?3:Math.max(0,Math.min(2,tab));statisticsPanel.setVisibility(currentTab==3?View.VISIBLE:View.GONE);if(currentTab==3)statisticsPanel.visible();mapPanel.setVisibility(currentTab==0?View.VISIBLE:View.GONE);pointsPanel.setVisibility(currentTab==1?View.VISIBLE:View.GONE);settingsPanel.setVisibility(currentTab==2?View.VISIBLE:View.GONE);
    updateNavigation();
    syncBrandLogo();
    if(currentTab==2&&resumed)rebuildSettings();
    updateViewing();if(currentTab==0||currentTab==1){sendBroadcast(new Intent("dev.raf.radar.VIEWING").setPackage(getPackageName()));refreshCards();}
  }
  void syncBrandLogo(){
    if(brandLogo==null)return;
    boolean fantasy=Appearance.lineage(this);
    boolean experimental=true;
    if(!fantasy&&experimental&&!senseLogo.available()){experimental=false;store.prefs.edit().putBoolean("experimentalLogo",false).apply();toast("Не удалось загрузить SENSE. Восстановлен обычный логотип.");}
    if(MonitorService.running)senseStartPending=false;
    if(MonitorService.running==senseDesired)senseCommandPending=false;
    classicBrand.setVisibility(fantasy||experimental?View.GONE:View.VISIBLE);
    senseLogo.setVisibility(!fantasy&&experimental?View.VISIBLE:View.GONE);
    lineageLogo.setVisibility(fantasy?View.VISIBLE:View.GONE);
    lineageLogo.state(MonitorService.running,resumed&&currentTab==0&&fantasy);
    brandLogo.experimental(false);brandLogo.active(MonitorService.running);brandLogo.motionAllowed(false);
    if(pro!=null){pro.experimental(false);pro.motionAllowed(false);pro.setEnabled(!experimental);pro.setClickable(!experimental);}
    senseLogo.state(MonitorService.running,resumed&&currentTab==0&&experimental&&!fantasy);
    if(mapTitle!=null&&mapTitle.getLayoutParams() instanceof FrameLayout.LayoutParams){FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)mapTitle.getLayoutParams();int left=dp(experimental?8:20),top=dp(experimental?8:18);if(lp.leftMargin!=left||lp.topMargin!=top){lp.leftMargin=left;lp.topMargin=top;mapTitle.setLayoutParams(lp);}}
    if(mapLoading!=null){FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)mapLoading.getLayoutParams();int top=dp(experimental?senseHeight+65:130);if(lp.topMargin!=top){lp.topMargin=top;mapLoading.setLayoutParams(lp);}}
  }
  void updateViewing(){store.prefs.edit().putLong("viewingUntil",resumed&&(currentTab==0||currentTab==1)?System.currentTimeMillis()+15000:0).apply();}
  String pointAddress(Point p){return p.dynamic?"Ваше текущее местоположение":p.address.isEmpty()?String.format(Locale.US,"%.5f, %.5f",p.lat,p.lon):p.address;}
  Store.Reading displayReading(Point p){Store.Reading result=null;for(Store.Reading r:store.readings(p.id))if(p.tariffs.contains(r.tariff)&&r.rub!=null){if(result==null||(!result.fresh()&&r.fresh()))result=r;}return result;}
  void refreshCards(){
    if(cards==null)return;
    if(pro!=null)pro.active(MonitorService.running);
    syncBrandLogo();
    syncWorkToggles();FriendUi.badge(this);
    if(state!=null){state.setText(MonitorService.running?"Мониторинг включён":"Мониторинг выключен");state.setTextColor(MonitorService.running?0xFF99D641:muted);}
    cards.removeAllViews();
    for(Point p:store.points()){
      if(p.dynamic)continue;
      LinearLayout line=row();line.setMinimumHeight(dp(76));
      View dot=new View(this);dot.setBackground(shape(p.dynamic?0xFF69AEFF:RadarUi.tariffColor(p.tariffs.isEmpty()?"econom":p.tariffs.get(0)),20));LinearLayout.LayoutParams dotParams=new LinearLayout.LayoutParams(dp(12),dp(12));dotParams.rightMargin=dp(12);line.addView(dot,dotParams);
      LinearLayout labels=column();TextView name=RadarUi.text(this,p.name,15,fg,true);name.setMaxLines(1);name.setEllipsize(android.text.TextUtils.TruncateAt.END);labels.addView(name);
      TextView address=text(pointAddress(p),12,muted);address.setMaxLines(1);address.setEllipsize(android.text.TextUtils.TruncateAt.END);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,-2);ap.topMargin=dp(5);labels.addView(address,ap);
      LinearLayout nameRow=row();labels.removeView(name);nameRow.addView(name,new LinearLayout.LayoutParams(0,-2,1));
      TextView age=text(pointAge(p),10,muted);age.setPadding(dp(8),0,0,0);age.setMaxLines(1);age.setContentDescription("Время обновления: "+p.name+" · "+pointAge(p));nameRow.addView(age);labels.addView(nameRow,0);
      labels.setContentDescription("Настроить точку: "+p.name);labels.setOnClickListener(v->{selected=p.id;editDetails(p);});line.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
      labels.setAlpha(p.enabled?1f:.5f);dot.setAlpha(p.enabled?1f:.35f);
      RadarUi.Toggle toggle=new RadarUi.Toggle(this);toggle.setContentDescription("Обновлять точку: "+p.name);toggle.setChecked(p.enabled);
      toggle.setOnCheckedChangeListener((button,enabled)->{
        for(Point current:store.points())if(current.id.equals(p.id)){current.enabled=enabled;store.update(current);break;}
        refreshCards();
      });
      line.addView(toggle,new LinearLayout.LayoutParams(dp(52),dp(48)));
      cards.addView(line);View divider=new View(this);divider.setBackgroundColor(dark?0xFF23252A:0xFFE0E2E6);cards.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
    }
    refreshMarkers();if(preview!=null)updatePreviewValue();
  }
  void refreshMarkers(){
    if(map==null)return;
    java.util.List<org.maplibre.geojson.Feature> glowPoints=new ArrayList<>();pointBitmaps.clear();
    for(Point p:store.points()){
      double lat=p.lat,lon=p.lon;if(p.dynamic){if(store.prefs.getLong("gpsAt",0)==0)continue;lat=Double.longBitsToDouble(store.prefs.getLong("lat",0));lon=Double.longBitsToDouble(store.prefs.getLong("lon",0));}
      Store.Reading reading=displayReading(p);
      if(p.enabled&&!p.dynamic&&reading!=null&&reading.fresh()){
        org.maplibre.geojson.Feature feature=org.maplibre.geojson.Feature.fromGeometry(org.maplibre.geojson.Point.fromLngLat(lon,lat));
        feature.addStringProperty("color",String.format(Locale.US,"#%06X",PriceLevel.color(reading.rub)&0xFFFFFF));glowPoints.add(feature);
      }
      pointBitmaps.put(p.id,pointIcon(p));
    }
    pointOverlay.invalidate();
    if(map.getStyle()==null||!map.getStyle().isFullyLoaded())return;
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
      PointF xy=map.getProjection().toScreenLocation(loc);Bitmap icon=pointBitmaps.get(p.id);if(icon!=null)xy=labelPosition(p,xy,icon);if(icon!=null&&Math.abs(hit.x-xy.x)<=icon.getWidth()/2f+dp(4)&&Math.abs(hit.y-xy.y)<=icon.getHeight()/2f+dp(4))return p;
    }return null;
  }
  PointF labelPosition(Point p,PointF anchor,Bitmap icon){if(p.dynamic)return anchor;return new PointF(anchor.x,anchor.y-icon.getHeight()/2f-dp(10));}
  List<Store.Reading> mapReadings(Point point){
    List<Store.Reading> rows=new ArrayList<>();
    List<Store.Reading> cached=store.readings(point.id);
    for(String tariff:TariffSelection.ordered(point.tariffs)){
      Store.Reading match=null;for(Store.Reading reading:cached)if(tariff.equals(reading.tariff)){match=reading;break;}
      rows.add(match!=null?match:new Store.Reading(tariff,null,null,0,"Ожидаем данные"));
    }
    return rows;
  }
  String pointAge(Point point){if(!point.enabled)return "Отключена";return ReadingAge.label(store.lastSuccessfulReading(point.id),System.currentTimeMillis());}
  Bitmap pointIcon(Point point){
    if(point.dynamic)return locationIcon(true,LocationState.fresh(store));
    List<Store.Reading> rows=mapReadings(point);
    Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));paint.setTextSize(dp(12));
    int width=dp(point.dynamic?114:56),rowHeight=dp(30),header=point.dynamic?dp(22):0;
    for(Store.Reading reading:rows)width=Math.max(width,Math.round(paint.measureText(RadarUi.value(reading)))+dp(24));
    boolean showAge=store.prefs.getBoolean("mapPointUpdateTime",false);String age=showAge?pointAge(point):"";paint.setTextSize(dp(9));width=Math.max(width,Math.round(paint.measureText(age))+dp(16));paint.setTextSize(dp(12));
    int ageTop=header+rows.size()*rowHeight;
    Bitmap bitmap=Bitmap.createBitmap(width,Math.max(dp(30),ageTop+dp(showAge?22:8)),Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);
    if(point.dynamic){paint.setColor(0xDD273655);canvas.drawRoundRect(0,0,width,header+dp(8),dp(9),dp(9),paint);paint.setColor(Color.WHITE);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(dp(10));canvas.drawText("▲ Моя геопозиция",width/2f,dp(15),paint);paint.setTextSize(dp(12));}
    for(int i=0;i<rows.size();i++){
      Store.Reading reading=rows.get(i);int color=PriceLevel.color(reading.rub);
      int top=header+i*rowHeight;paint.setColor(0xEE15171B);canvas.drawRoundRect(dp(2),top+dp(2),width-dp(2),top+rowHeight-dp(2),dp(8),dp(8),paint);
      if(Appearance.lineage(this)){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1));paint.setColor(Appearance.GOLD);canvas.drawRoundRect(dp(2),top+dp(2),width-dp(2),top+rowHeight-dp(2),dp(4),dp(4),paint);paint.setStyle(Paint.Style.FILL);}
      paint.setColor(color);paint.setTextAlign(Paint.Align.CENTER);float valueCenter=width/2f;paint.setTextSize(dp(12));
      canvas.drawText(RadarUi.value(reading),valueCenter,top+dp(20),paint);
    }
    paint.setTextSize(dp(9));paint.setColor(0xB3FFFFFF);paint.setTextAlign(Paint.Align.CENTER);if(showAge)canvas.drawText(age,width/2f,ageTop+dp(13),paint);
    return bitmap;
  }
  Bitmap locationIcon(boolean own,boolean fresh){
    int size=dp(56),color=own?0xFFFFCC45:fresh?0xFFAB45FF:0xFF92969F;
    Bitmap icon=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(icon);
    Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);float center=size/2f;
    paint.setShader(new RadialGradient(center,center,dp(25),new int[]{Color.argb(fresh?110:35,Color.red(color),Color.green(color),Color.blue(color)),Color.TRANSPARENT},null,Shader.TileMode.CLAMP));
    canvas.drawCircle(center,center,dp(25),paint);paint.setShader(null);
    paint.setColor(0xFF17191F);canvas.drawCircle(center,center,dp(8),paint);
    paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));paint.setColor(color);paint.setAlpha(fresh?255:100);canvas.drawCircle(center,center,dp(8),paint);
    paint.setStyle(Paint.Style.FILL);
    if(Appearance.lineage(this)){Path gem=new Path();float radius=dp(own?6:5);gem.moveTo(center,center-radius);gem.lineTo(center+radius,center);gem.lineTo(center,center+radius);gem.lineTo(center-radius,center);gem.close();canvas.drawPath(gem,paint);
      if(own){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1));for(int i=0;i<4;i++){double a=i*Math.PI/2;canvas.drawLine(center+(float)Math.cos(a)*dp(11),center+(float)Math.sin(a)*dp(11),center+(float)Math.cos(a)*dp(15),center+(float)Math.sin(a)*dp(15),paint);}paint.setStyle(Paint.Style.FILL);}}
    else canvas.drawCircle(center,center,dp(4),paint);return icon;
  }
  Bitmap nearbyPeerIcon(){
    Bitmap icon=locationIcon(false,true);Canvas canvas=new Canvas(icon);
    Paint ring=new Paint(Paint.ANTI_ALIAS_FLAG);ring.setStyle(Paint.Style.STROKE);
    ring.setStrokeWidth(dp(3));ring.setColor(0xFFAB45FF);
    canvas.drawCircle(icon.getWidth()/2f,icon.getHeight()/2f,dp(15),ring);return icon;
  }
  void refreshPeers(){
    if(!resumed||currentTab!=0||map==null||presenceQuery||SystemClock.elapsedRealtime()<nextPresenceQuery)return;
    nextPresenceQuery=SystemClock.elapsedRealtime()+30000;presenceQuery=true;int epoch=presenceEpoch;
    org.maplibre.android.geometry.LatLngBounds b=map.getProjection().getVisibleRegion().latLngBounds;
    PresenceClient.query(this,b.getLonWest(),b.getLatSouth(),b.getLonEast(),b.getLatNorth(),result->{
      presenceQuery=false;if(resumed&&currentTab==0&&presenceEpoch==epoch){if(result!=null)peers=result;pointOverlay.invalidate();}
    });
  }
  void syncScreenAwake(){
    if(resumed&&store.prefs.getBoolean("keepScreenOn",false))getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  }
  void editName(boolean required){
    if(isFinishing()||isDestroyed()||(nameDialog!=null&&nameDialog.isShowing()))return;
    EditText input=new EditText(this);input.setSingleLine(true);input.setText(store.prefs.getString("profileName",""));input.setHint("Ваше имя");input.setTextColor(fg);input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(32)});
    LinearLayout box=column();box.setPadding(dp(24),dp(8),dp(24),0);box.addView(input);
    AlertDialog.Builder builder=new AlertDialog.Builder(this).setTitle(required?"Как вас зовут?":"Изменить имя").setView(box).setCancelable(!required).setPositiveButton("Сохранить",null);
    if(!required)builder.setNegativeButton("Отмена",null);
    nameDialog=builder.create();nameDialog.setOnShowListener(v->nameDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b->{
      String name=input.getText().toString().replaceAll("[\\p{Cc}\\p{Cf}]","").trim().replaceAll("\\s+"," ");
      if(name.isEmpty()){input.setError("Введите имя");return;}
      if(AccountClient.active(store)){org.json.JSONObject body=AccountClient.action("name");try{body.put("name",name);}catch(org.json.JSONException e){throw new IllegalStateException(e);}nameDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);AccountClient.call(this,body,(data,error)->{if(isDestroyed())return;nameDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);if(error!=null){input.setError(error);return;}pointOverlay.invalidate();nameDialog.dismiss();rebuildSettings();});return;}
      store.prefs.edit().putString("profileName",name).apply();PresenceClient.profileChanged(this);pointOverlay.invalidate();nameDialog.dismiss();if(currentTab==2)rebuildSettings();
    }));nameDialog.show();
  }
  void drawPersonLabel(Canvas canvas,PointF xy,String name,String status){
    if(name==null||name.isEmpty())return;Paint label=new Paint(Paint.ANTI_ALIAS_FLAG);label.setTextSize(dp(10));label.setColor(0xB3FFFFFF);label.setTextAlign(Paint.Align.CENTER);
    canvas.drawText(name,xy.x,xy.y+dp(24),label);if(status!=null){label.setTextSize(dp(9));canvas.drawText(status,xy.x,xy.y+dp(37),label);}
  }
  void showFriends(){
    friendsRows=column();ScrollView scroll=new ScrollView(this);scroll.addView(friendsRows);
    friendsDialog=new AlertDialog.Builder(this).setTitle("Друзья").setView(scroll).setNeutralButton("Обновить",null).setPositiveButton("Закрыть",null).create();AppSounds.ui(this,AppSounds.Event.MENU_OPEN);friendsDialog.show();friendsRefreshIcon=new RadarUi.RefreshIcon(this);friendsDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setCompoundDrawables(friendsRefreshIcon,null,null,null);friendsDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setCompoundDrawablePadding(dp(8));friendsDialog.setOnDismissListener(d->{AppSounds.ui(this,AppSounds.Event.MENU_CLOSE);if(friendsRefreshIcon!=null)friendsRefreshIcon.loading(false);});friendsDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->refreshFriends(true));renderFriends();refreshFriends(true);
  }
  void refreshFriends(boolean force){
    if(!resumed||friendsDialog==null||!friendsDialog.isShowing()||directoryPending)return;
    renderFriends();if(!force&&SystemClock.elapsedRealtime()<nextDirectory)return;nextDirectory=SystemClock.elapsedRealtime()+30000;directoryPending=true;renderFriends();
    PresenceClient.directory(this,result->{directoryPending=false;if(!isDestroyed()){directoryError=result==null;if(result!=null){friends=result;PresenceClient.cache(store,result);peers=new ArrayList<>(result);pointOverlay.invalidate();}renderFriends();}});
  }
  void renderFriends(){
    if(friendsRows==null)return;if(friendsDialog!=null&&friendsDialog.isShowing()){android.widget.Button refresh=friendsDialog.getButton(AlertDialog.BUTTON_NEUTRAL);refresh.setEnabled(!directoryPending);refresh.setText(directoryPending?"Обновление…":"Обновить");if(friendsRefreshIcon!=null)friendsRefreshIcon.loading(directoryPending);}friendsRows.removeAllViews();FriendUi.controls(this);FriendUi.badge(this);String own=PresenceClient.ownId(store);int count=0;
    for(PresenceClient.Peer p:friends){if(p.id.equals(own)||!p.retained())continue;count++;LinearLayout line=row();line.setPadding(dp(20),dp(12),dp(20),dp(12));LinearLayout details=column();details.addView(text(p.name+" · ID "+p.id,14,fg));details.addView(text(p.fresh()?p.locationAge():p.status(),11,muted));line.addView(details,new LinearLayout.LayoutParams(0,-2,1));View dot=new View(this);dot.setBackground(shape(p.fresh()?0xFFAB45FF:0xFF92969F,12));line.addView(dot,new LinearLayout.LayoutParams(dp(10),dp(10)));line.setContentDescription(p.name+", "+p.status());line.setFocusable(true);line.setOnLongClickListener(v->{FriendUi.remove(this,p);return true;});line.setOnClickListener(v->{if(!p.hasLocation){toast("Пользователь ещё не передавал координаты");return;}if(map!=null){showTab(0);map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(p.lat,p.lon),15));peers=new ArrayList<>(friends);pointOverlay.invalidate();}friendsDialog.dismiss();});friendsRows.addView(line);}
    if(count>0){TextView hint=text("Удерживайте друга в списке, чтобы удалить",10,muted);pad(hint,20,8);friendsRows.addView(hint);}
    if(directoryError){TextView retry=text("Не удалось обновить · повторить",12,muted);pad(retry,20,12);retry.setOnClickListener(v->refreshFriends(true));friendsRows.addView(retry);}
    if(count==0&&!directoryError){TextView empty=text(directoryPending?"Загрузка…":"Пока нет друзей. Добавьте друга по ID — он должен подтвердить запрос",13,muted);pad(empty,20,20);friendsRows.addView(empty);}
  }
  void toggleSenseMonitor(){
    int revision=++senseClickRevision;
    senseDesired=senseCommandPending?!senseDesired:!(MonitorService.running||startAfterAccess);
    senseCommandPending=true;
    if(senseDesired){senseStartPending=true;pendingOverlay=true;startMonitor();}
    else{
      boolean submitted=(senseStartPending&&!startAfterAccess)||MonitorService.running;
      senseStartPending=false;startAfterAccess=false;pendingOverlay=false;
      store.prefs.edit().putBoolean("overlay",false).apply();
      // Deliver cancellation after onCreate/startForeground, never kill a pending FGS.
      if(submitted)try{startService(new Intent(this,MonitorService.class).setAction("stop"));}
      catch(Exception e){senseCommandPending=false;toast("Не удалось выключить: "+e.getMessage());}
      handler.postDelayed(this::refreshCards,200);
    }
    handler.postDelayed(()->{if(revision==senseClickRevision&&!MonitorService.running&&!startAfterAccess){if(senseDesired&&Appearance.lineage(this))lineageLogo.failure();senseStartPending=false;senseCommandPending=false;}},1500);
  }
  void toggleMonitor(){
    if(MonitorService.running||startAfterAccess)stopMonitor();else{pendingOverlay=true;startMonitor();}
  }
  void stopMonitor(){store.prefs.edit().putBoolean("diagMonitorExpected",false).apply();Diagnostics.info("monitor","user_stop");startAfterAccess=false;pendingOverlay=false;store.prefs.edit().putBoolean("overlay",false).apply();stopService(new Intent(this,MonitorService.class));handler.postDelayed(this::refreshCards,200);}
  boolean syncingToggles;
  void syncWorkToggles(){
    syncingToggles=true;
    if(monitorToggle!=null)monitorToggle.setChecked(MonitorService.running);
    if(overlayToggle!=null)overlayToggle.setChecked(store.prefs.getBoolean("overlay",false)&&Settings.canDrawOverlays(this));
    syncingToggles=false;
  }
  void hidePreview(){if(pointSheet!=null)mapFrame.removeView(pointSheet);if(pointDismiss!=null)mapFrame.removeView(pointDismiss);pointDismiss=null;pointSheet=null;preview=null;previewValue=null;}
  TextView previewValue;
  void updatePreviewValue(){if(previewValue!=null&&preview!=null){Store.Reading reading=displayReading(preview);previewValue.setText("Надбавка "+RadarUi.value(reading));previewValue.setTextColor(PriceLevel.color(reading==null?null:reading.rub));};}
  void edit(Point p){
    if(p.dynamic&&store.prefs.getLong("gpsAt",0)>0){p.lat=Double.longBitsToDouble(store.prefs.getLong("lat",0));p.lon=Double.longBitsToDouble(store.prefs.getLong("lon",0));}
    showTab(0);hidePreview();preview=p;selected=p.id;
    boolean existing=store.points().stream().anyMatch(saved->saved.id.equals(p.id));
    if(!existing)AppSounds.play(this,AppSounds.Event.POINT_CREATE);
    LinearLayout sheet=column();sheet.setPadding(dp(18),dp(10),dp(18),dp(18));GradientDrawable background=shape(dark?0xFF16181B:Color.WHITE,26);background.setStroke(dp(1),dark?0xFF34373D:0xFFDCDDE0);sheet.setBackground(Appearance.lineage(this)?Appearance.frame(this,false):background);sheet.setElevation(dp(10));
    View handle=new View(this);handle.setBackground(shape(dark?0xFF666B73:0xFFC0C3C8,4));LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(dp(32),dp(4));hp.gravity=Gravity.CENTER_HORIZONTAL;hp.bottomMargin=dp(18);sheet.addView(handle,hp);
    sheet.addView(text(String.format(Locale.US,"%.5f, %.5f",p.lat,p.lon),12,muted));
    previewValue=heading("Надбавка "+RadarUi.value(displayReading(p)),22);LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(-1,-2);vp.topMargin=dp(12);vp.bottomMargin=dp(10);sheet.addView(previewValue,vp);
    TextView address=text(pointAddress(p),16,fg);address.setMaxLines(2);sheet.addView(address);
    if(!p.dynamic){final double lat=p.lat,lon=p.lon;resolveAddress(lat,lon,result->{if(preview!=p)return;p.address=result;address.setText(result);cacheAddress(p.id,lat,lon,result);});}
    if(existing&&!p.dynamic&&store.prefs.getBoolean("experimentalStatistics",false)){TextView statistics=primary("Статистика",false,()->PointStatistics.show(this,p));statistics.setContentDescription("Статистика точки");LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(46));sp.topMargin=dp(14);sheet.addView(statistics,sp);}
    LinearLayout buttons=row();LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(50));bp.topMargin=dp(20);sheet.addView(buttons,bp);
    TextView action=primary(existing?"Удалить":"Сохранить",!existing,()->{
      if(existing){if(!p.dynamic){store.remove(p.id);AppSounds.play(this,AppSounds.Event.POINT_DELETE);}}
      else{store.update(p);AppSounds.play(this,AppSounds.Event.POINT_SAVE);}
      hidePreview();refreshCards();
      if(!existing)editDetails(p);
    });
    if(!p.dynamic)buttons.addView(action,new LinearLayout.LayoutParams(-1,-1));else buttons.setVisibility(View.GONE);
    sheet.setClickable(true);sheet.setContentDescription("Карточка локации");
    pointDismiss=new View(this);pointDismiss.setContentDescription("Закрыть карточку локации");pointDismiss.setOnClickListener(v->hidePreview());
    mapFrame.addView(pointDismiss,new FrameLayout.LayoutParams(-1,-1));
    FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);mapFrame.addView(sheet,params);pointSheet=sheet;updatePreviewValue();
  }

  void startMonitor() {
    handler.postDelayed(this::refreshCards,300);
    if (!Access.monitorReady(this) || (pendingOverlay&&!Settings.canDrawOverlays(this))) {
      startAfterAccess = true;
      access.request();
      return;
    }
    startAfterAccess = false;
    if(pendingOverlay){store.prefs.edit().putBoolean("overlay",true).apply();pendingOverlay=false;}
    try {
      startForegroundService(new Intent(this, MonitorService.class).setAction("user_start"));
    } catch (Exception e) {
      senseStartPending=false;senseCommandPending=false;toast("Не удалось включить: " + e.getMessage());
    }
  }

  @Override
  public void onRequestPermissionsResult(int r, String[] p, int[] g) {
    super.onRequestPermissionsResult(r, p, g);
    access.result(r);
  }

  void onAccessChanged() {
    if(mapGps!=null&&resumed)mapGps.start();
    if (startAfterAccess && Access.monitorReady(this)&&(!pendingOverlay||Settings.canDrawOverlays(this))) startMonitor();
  }

  void locate() {
    if (!Access.location(this)) {
      startMonitor();
      return;
    }
    long age = System.currentTimeMillis() - store.prefs.getLong("gpsAt", 0);
    if (age > 120000) {
      toast("Ожидаем свежую геопозицию. Проверьте, что GPS включён.");
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


  void editDetails(Point p) {PointEditor.show(this,p);}

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


  Button action(String value,Runnable run){Button button=new Button(this);button.setText(value);button.setTextColor(fg);button.setTextSize(13);button.setAllCaps(false);button.setMinHeight(dp(48));button.setPadding(dp(12),0,dp(12),0);RadarUi.touch(button,surface,12,0);button.setOnClickListener(v->{AppSounds.ui(this,AppSounds.Event.CLICK);run.run();});return button;}
  void settings(){showTab(2);}
  void rebuildNavigation(){
    if(navigation==null)return;navigation.removeAllViews();
    int[] tabs=store.prefs.getBoolean("experimentalStatistics",false)?new int[]{0,1,3,2}:new int[]{0,1,2};
    String[] labels={"Карта","Точки","Настройки","Статистика"},icons={"map","list","settings","chart"};
    for(int tab:tabs){LinearLayout item=column();item.setTag(tab);item.setGravity(Gravity.CENTER);item.setContentDescription("Вкладка "+labels[tab]);item.setFocusable(true);
      item.addView(RadarUi.icon(this,icons[tab],tab==currentTab?Appearance.accent(this):muted,23));
      TextView label=text(labels[tab],10,tab==currentTab?Appearance.accent(this):muted);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.topMargin=dp(5);item.addView(label,lp);
      item.setOnClickListener(v->showTab(tab));navigation.addView(item,new LinearLayout.LayoutParams(0,dp(66),1));
    }updateNavigation();
  }
  void updateNavigation(){
    if(navigation==null)return;navigation.setBackgroundColor(bg);String[] icons={"map","list","settings","chart"};
    for(int i=0;i<navigation.getChildCount();i++){LinearLayout item=(LinearLayout)navigation.getChildAt(i);int tab=(Integer)item.getTag();item.setSelected(tab==currentTab);int color=tab==currentTab?Appearance.accent(this):muted;((ImageView)item.getChildAt(0)).setImageDrawable(new RadarUi.Icon(icons[tab],color));((TextView)item.getChildAt(1)).setTextColor(color);}
    if(Appearance.lineage(this))navigation.setBackground(Appearance.frame(this,false));
  }
  void themes(){
    LinearLayout body=column();body.setPadding(dp(18),dp(8),dp(18),dp(16));
    AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Темы").setView(body).setNegativeButton("Закрыть",null).create();
    String[] ids={Appearance.STANDARD,Appearance.LINEAGE},names={"Стандартная","Lineage 2"};
    for(int i=0;i<ids.length;i++){
      final String id=ids[i];boolean fantasy=i==1,chosen=Appearance.lineage(this)==fantasy;
      LinearLayout card=row();card.setPadding(dp(12),dp(10),dp(12),dp(10));card.setMinimumHeight(dp(108));card.setBackground(fantasy?Appearance.frame(this,false):shape(RadarUi.CARD,14));
      View previewLogo=fantasy?new LineageLogo(this):new SenseLogo(this);previewLogo.setClickable(false);previewLogo.setFocusable(false);previewLogo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
      card.addView(previewLogo,new LinearLayout.LayoutParams(dp(76),dp(84)));LinearLayout labels=column();labels.setPadding(dp(12),0,0,0);
      TextView title=text(names[i]+(chosen?" · выбрана":""),16,fantasy?Appearance.GOLD:Color.WHITE);labels.addView(title);
      TextView subtitle=text(fantasy?"Пергамент, металл и кристаллы\nБез звуков в этом патче":"Привычное оформление\nПрежние звуки",12,fantasy?Appearance.MUTED:0xFFB4B8C0);subtitle.setPadding(0,dp(8),0,0);labels.addView(subtitle);
      card.addView(labels,new LinearLayout.LayoutParams(0,-2,1));card.setContentDescription("Тема "+names[i]+(chosen?", выбрана":""));card.setFocusable(true);card.setOnClickListener(v->{dialog.dismiss();selectTheme(id);});
      // The entire preview is one accessible target; decorative children never intercept taps.
      previewLogo.setOnTouchListener((v,e)->false);previewLogo.setEnabled(false);
      LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(12);body.addView(card,lp);
    }dialog.setOnDismissListener(d->AppSounds.ui(this,AppSounds.Event.MENU_CLOSE));dialog.show();
  }
  void selectTheme(String id){
    boolean before=Appearance.lineage(this),after=Appearance.LINEAGE.equals(id);if(before==after)return;
    hidePreview();startupSound.stop();AppSounds.stop();
    store.prefs.edit().putString(Appearance.KEY,after?Appearance.LINEAGE:Appearance.STANDARD).apply();
    setTheme(after?R.style.AppThemeLineage:R.style.AppThemeDark);palette();
    root.setBackgroundColor(bg);getWindow().setStatusBarColor(bg);getWindow().setNavigationBarColor(bg);
    Appearance.recolor(root,before,after);updateNavigation();
    pages.removeView(pointsPanel);buildPoints();pointsPanel.setVisibility(currentTab==1?View.VISIBLE:View.GONE);
    rebuildSettings();if(statisticsPanel!=null)statisticsPanel.render();themeMapCaption();peerBitmap=null;nearbyPeerBitmap=null;offlinePeerBitmap=null;refreshCards();applyMapStyle();
    toast(after?"Тема Lineage 2 включена":"Стандартная тема включена");
  }
  void themeMapCaption(){
    if(state==null)return;
    boolean fantasy=Appearance.lineage(this);
    // Center both children on the same axis, even when the status is wider than the emblem.
    if(mapTitle!=null)mapTitle.setGravity(fantasy?Gravity.CENTER_HORIZONTAL:Gravity.START);
    state.setGravity(fantasy?Gravity.CENTER:Gravity.START|Gravity.CENTER_VERTICAL);
    if(fantasy){state.setBackground(Appearance.frame(this,false));state.setPadding(dp(9),dp(7),dp(9),dp(7));}
    else{state.setBackground(null);state.setPadding(0,dp(8),dp(4),dp(10));}
  }
  LinearLayout settingsContents(){
    LinearLayout body=column();body.setPadding(dp(20),0,dp(20),dp(20));
    LinearLayout profile=group(body,"Профиль");
    LinearLayout identityRow=row();identityRow.setMinimumHeight(dp(50));TextView profileName=text("Имя: "+store.prefs.getString("profileName","Не указано"),13,fg);profileName.setSingleLine(true);profileName.setEllipsize(android.text.TextUtils.TruncateAt.END);profileName.setContentDescription("Изменить имя");profileName.setOnClickListener(v->editName(false));profileName.setFocusable(true);identityRow.addView(profileName,new LinearLayout.LayoutParams(0,-2,1));
    TextView identity=text("ID: "+(AccountClient.active(store)?PresenceClient.ownId(store):"—"),13,muted);identity.setPadding(dp(12),0,0,0);identity.setTextIsSelectable(true);identityRow.addView(identity);profile.addView(identityRow);
    if(AccountClient.active(store)){settingAction(profile,"Изменить пароль",()->AccountUi.password(this));settingAction(profile,"Выйти из аккаунта",()->new AlertDialog.Builder(this).setTitle("Выйти?").setMessage("Для возврата потребуются ID и пароль. Сохранённые точки останутся на телефоне.").setNegativeButton("Отмена",null).setPositiveButton("Выйти",(d,w)->{stopMonitor();AccountClient.call(this,AccountClient.action("logout"),(data,error)->{AccountClient.clear(store);friends.clear();peers.clear();rebuildSettings();AccountUi.choose(this);});}).show());}
    else settingAction(profile,"Регистрация или вход",()->AccountUi.choose(this));
    YandexProfile.add(this,profile);
    LinearLayout work=group(body,"Работа приложения");
    monitorToggle=switchRow(work,"Мониторинг",MonitorService.running,0,enabled->{if(enabled)startMonitor();else stopMonitor();});
    settingAction(work,"О приложении",()->new AlertDialog.Builder(this).setTitle("Радар Pro · "+BuildConfig.VERSION_NAME)
        .setMessage("Надбавки Эконома на карте и в компактном виджете. Сохраняйте удобные точки и следите за изменениями рядом.\n\nНадбавки расчётные — сверяйте с Яндекс Про.\n© OpenStreetMap contributors")
        .setPositiveButton("Понятно",null).show());
    LinearLayout widget=group(body,"Виджет");
    overlayToggle=switchRow(widget,"Виджет поверх приложений",store.prefs.getBoolean("overlay",false)&&Settings.canDrawOverlays(this),0,enabled->{
      if(enabled){pendingOverlay=true;startMonitor();}else store.prefs.edit().putBoolean("overlay",false).apply();
    });

    switchRow(widget,"Время обновления",store.prefs.getBoolean("widgetUpdateTime",false),0,enabled->store.prefs.edit().putBoolean("widgetUpdateTime",enabled).apply());
    LinearLayout controls=column();controls.setVisibility(View.GONE);
    settingAction(widget,"Размер и прозрачность",()->{controls.setVisibility(controls.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE);});
    widget.addView(controls);sizeSlider(controls,"Размер виджета","scale",60,160);
    sizeSlider(controls,"Размер текста","textScale",60,250);
    sizeSlider(controls,"Прозрачность","opacity",20,100);
    LinearLayout more=group(body,"Дополнительно");
    settingAction(more,"Темы",this::themes);
    switchRow(more,"Статистика (экспериментальная)",store.prefs.getBoolean("experimentalStatistics",false),0,enabled->{store.prefs.edit().putBoolean("experimentalStatistics",enabled).apply();if(!enabled&&currentTab==3)showTab(2);rebuildNavigation();});
    switchRow(more,"Прогнозирование",store.prefs.getBoolean("forecast",false),0,enabled->{store.prefs.edit().putBoolean("forecast",enabled).apply();refreshCards();});
    switchRow(more,"Время обновления точек",store.prefs.getBoolean("mapPointUpdateTime",false),0,enabled->{store.prefs.edit().putBoolean("mapPointUpdateTime",enabled).apply();refreshCards();});
    switchRow(more,"Делиться местоположением",store.prefs.getBoolean("shareLocation",false),0,enabled->{
      store.prefs.edit().putBoolean("shareLocation",enabled).apply();PresenceClient.sharingChanged(this);
    });
    switchRow(more,"Не выключать экран",store.prefs.getBoolean("keepScreenOn",false),0,enabled->{store.prefs.edit().putBoolean("keepScreenOn",enabled).apply();syncScreenAwake();});
    switchRow(more,"Звук",Appearance.sounds(this),0,enabled->{store.prefs.edit().putBoolean(Appearance.lineage(this)?"lineageSounds":"sounds",enabled).apply();if(!enabled){AppSounds.stop();startupSound.stop();}});
    LinearLayout updates=group(body,"Обновление");
    switchRow(updates,"Автоматическая проверка",store.prefs.getBoolean("autoUpdate",true),0,enabled->store.prefs.edit().putBoolean("autoUpdate",enabled).apply());
    settingAction(updates,"Проверить обновления",()->Updates.check(this,store));
    LinearLayout diagnostic=group(body,"Диагностика");
    switchRow(diagnostic,"Режим диагностики",store.prefs.getBoolean(DiagnosticMode.KEY,false),0,enabled->{DiagnosticMode.set(this,enabled);if(enabled&&!Access.notifications(this))toast("Разрешите уведомления в настройках Android");});
    TextView explanation=text("Подробный журнал за 24 часа и уведомления о сбоях. Отправка по кнопке. При заполнении журнала удаляются самые старые события; число пропусков входит в отчёт.",12,muted);diagnostic.addView(explanation);
    settingAction(diagnostic,"Состояние диагностики",()->Diagnostics.ui(this));
    settingAction(diagnostic,"Журнал диагностики",()->Diagnostics.journalUi(this));
    settingAction(diagnostic,"Отправить диагностику",()->Diagnostics.sendManual(this));
    return body;
  }
  LinearLayout group(LinearLayout body,String title){
    LinearLayout card=column();card.setPadding(dp(14),dp(14),dp(14),dp(4));
    GradientDrawable background=new GradientDrawable(GradientDrawable.Orientation.TL_BR,dark?new int[]{0xFF202225,0xFF181A1D}:new int[]{Color.WHITE,Color.WHITE});background.setCornerRadius(dp(18));card.setBackground(Appearance.lineage(this)?Appearance.frame(this,false):background);
    LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.bottomMargin=dp(14);body.addView(card,params);
    TextView heading=heading(title,16);heading.setPadding(0,0,0,dp(8));card.addView(heading);return card;
  }
  void settingsDivider(LinearLayout group){View line=new View(this);line.setBackgroundColor(dark?0xFF303238:0xFFE8E9EC);group.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));}
  interface BoolChange{void set(boolean enabled);}
  RadarUi.Toggle switchRow(LinearLayout group,String title,boolean checked,int dotColor,BoolChange change){
    LinearLayout r=row();r.setMinimumHeight(dp(48));
    if(dotColor!=0){View dot=new View(this);dot.setBackground(shape(dotColor,12));LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(dp(12),dp(12));params.rightMargin=dp(10);r.addView(dot,params);}
    TextView label=text(title,13,fg);r.addView(label,new LinearLayout.LayoutParams(0,-2,1));RadarUi.Toggle toggle=new RadarUi.Toggle(this);toggle.setContentDescription(title);toggle.setChecked(checked);
    if(change!=null)toggle.setOnCheckedChangeListener((button,value)->{if(!syncingToggles)change.set(value);});r.addView(toggle,new LinearLayout.LayoutParams(dp(52),dp(48)));r.setOnClickListener(v->toggle.performClick());group.addView(r);return toggle;
  }
  void sizeSlider(LinearLayout group,String title,String key,int min,int max){
    LinearLayout line=row();line.setMinimumHeight(dp(42));
    line.addView(text(title,13,fg),new LinearLayout.LayoutParams(0,-2,1));
    int current=Math.max(min,Math.min(max,store.prefs.getInt(key,"opacity".equals(key)?70:100)));
    TextView value=text(current+"%",12,muted);line.addView(value);group.addView(line);
    SeekBar bar=new SeekBar(this);bar.setMax(max-min);bar.setProgress(current-min);bar.setContentDescription(title);
    bar.setProgressTintList(ColorStateList.valueOf(Appearance.accent(this)));bar.setProgressBackgroundTintList(ColorStateList.valueOf(0xFF45484E));bar.setThumbTintList(ColorStateList.valueOf(Appearance.lineage(this)?Appearance.GOLD:Color.WHITE));
    group.addView(bar,new LinearLayout.LayoutParams(-1,dp(40)));
    bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
      public void onProgressChanged(SeekBar b,int progress,boolean user){int n=min+progress;value.setText(n+"%");if(user)store.prefs.edit().putInt(key,n).apply();}
      public void onStartTrackingTouch(SeekBar b){} public void onStopTrackingTouch(SeekBar b){}
    });
  }
  void settingAction(LinearLayout group,String title,Runnable action){LinearLayout line=row();line.setContentDescription(title);line.setMinimumHeight(dp(50));line.addView(text(title,13,fg),new LinearLayout.LayoutParams(0,-2,1));if(title.equals("Размер и прозрачность")){ImageView arrow=RadarUi.icon(this,"right",muted,14);line.addView(arrow);line.setOnClickListener(v->{arrow.animate().rotation(arrow.getRotation()<45?90:0).setDuration(160).start();action.run();});}else line.setOnClickListener(v->{if(!title.equals("Проверить обновления"))AppSounds.ui(this,AppSounds.Event.MENU_OPEN);action.run();});line.setFocusable(true);group.addView(line);}
  void numberDialog(String title,String key,int min,int max,int step,String unit){LinearLayout form=form();slider(form,title,min,max,store.prefs.getInt(key,120),step,value->store.prefs.edit().putInt(key,value).apply(),unit);new AlertDialog.Builder(this).setTitle(title).setView(form).setPositiveButton("Готово",null).show();}
  void mapLayers(){
    List<Point> points=store.points();String[] names=new String[points.size()];for(int i=0;i<names.length;i++)names[i]=points.get(i).name;
    new AlertDialog.Builder(this).setTitle("Тарифы точек").setItems(names,(dialog,index)->editDetails(points.get(index))).setNegativeButton("Отменить",null).show();
  }
  void rebuildSettings(){if(settingsPanel==null)return;settingsPanel.removeAllViews();TextView title=heading("Настройки",24);title.setPadding(dp(20),dp(24),dp(20),dp(20));settingsPanel.addView(title);ScrollView sc=new ScrollView(this);sc.addView(settingsContents());settingsPanel.addView(sc,new LinearLayout.LayoutParams(-1,0,1));}

  void toast(String s) {
    if(s!=null&&(s.startsWith("Не удалось")||s.startsWith("Ошибка")))Diagnostics.once("ui","error_message",true);
    Toast.makeText(this, s, Toast.LENGTH_LONG).show();
  }

  @Override
  protected void onStart() {
    super.onStart();
    mapView.onStart();
    androidx.core.content.ContextCompat.registerReceiver(this,monitorState,peerRefreshFilter(),androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);
  }

  IntentFilter peerRefreshFilter(){IntentFilter filter=new IntentFilter("dev.raf.radar.REFRESH");filter.addAction("dev.raf.radar.PEERS");return filter;}

  @Override
  protected void onResume() {
    super.onResume();
    Diagnostics.foreground=true;Diagnostics.worker.execute(Diagnostics::check);
    mapView.onResume();
    resumed = true;if(currentTab==2)rebuildSettings();syncBrandLogo();syncScreenAwake();PresenceClient.reconcile(this);PresenceClient.screen(this,true);
    if(!AccountClient.active(store))handler.post(()->AccountUi.choose(this));else if(store.prefs.getString("profileName","").isEmpty())handler.post(()->editName(true));
    mapGps.start();nextPresenceQuery=0;
    if (startupSoundPending) {
      startupSoundPending = false;
      startupSound.play(this, Appearance.sounds(this));
    }
    updateViewing();
    sendBroadcast(new Intent("dev.raf.radar.VIEWING").setPackage(getPackageName()));
    handler.post(refreshLoop);
    handler.post(() -> { if (!Updates.resume(this)) access.onResume(); });
  }

  @Override
  protected void onPause() {
    Diagnostics.foreground=false;
    resumed = false;syncBrandLogo();syncScreenAwake();
    presenceEpoch++;mapGps.stop();PresenceClient.screen(this,false);
    store.prefs.edit().putLong("viewingUntil", 0).apply();
    handler.removeCallbacks(refreshLoop);
    mapView.onPause();
    super.onPause();
  }

  @Override
  protected void onStop() {
    startupSound.stop();
    access.onStop();
    unregisterReceiver(monitorState);
    mapView.onStop();
    super.onStop();
  }

  @Override
  protected void onDestroy() {
    startupSound.stop();
    mapGps.stop();
    if(nameDialog!=null)nameDialog.dismiss();if(friendsDialog!=null)friendsDialog.dismiss();
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
    if(brandLogo!=null)b.putFloat("radarSweepAngle",brandLogo.sweepAngle());
    if(senseLogo!=null)b.putFloat("senseSweepAngle",senseLogo.motion.angle);
    b.putInt("tab", currentTab);
    b.putBoolean("startAfterAccess",startAfterAccess);b.putBoolean("pendingOverlay",pendingOverlay);
    mapView.onSaveInstanceState(b);
  }
}


