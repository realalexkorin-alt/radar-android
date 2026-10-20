package dev.raf.radar;

import android.content.*;
import android.graphics.*;
import android.location.Location;
import android.os.SystemClock;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.*;
import org.json.JSONObject;
import org.junit.*;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;

public class PresencePatchTest {
  Context c;Store store;
  @Before public void setup(){
    c=InstrumentationRegistry.getInstrumentation().getTargetContext();c.stopService(new Intent(c,MonitorService.class));EconomyPatchTest.waitFor(()->!MonitorService.running);
    c.getSharedPreferences("radar",0).edit().clear().putBoolean("autoUpdate",false).putBoolean("sounds",false).commit();store=new Store(c);ScreenTest.grantAccess();
  }
  @After public void stop(){c.stopService(new Intent(c,MonitorService.class));}
  @Test public void migrationLocksWidgetToGpsAndPreservesPoints(){
    Point saved=new Point("saved","Дом",55,61,false);saved.threshold=350;saved.notify=true;store.update(saved);
    store.prefs.edit().putString("overlayPoint","saved").remove("gpsWidgetV1").putInt("scale",130).commit();
    store=new Store(c);assertEquals("me",store.widgetPoint().id);assertEquals("me",store.prefs.getString("overlayPoint",""));
    assertFalse(store.prefs.getBoolean("shareLocation",false));assertEquals(130,store.prefs.getInt("scale",0));
    assertTrue(store.points().stream().anyMatch(p->p.id.equals("saved")&&p.threshold==350&&p.notify));
    store.prefs.edit().putString("overlayPoint","saved").commit();assertEquals("me",new Store(c).widgetPoint().id);
  }
  @Test public void widgetUsesOnlyFreshGpsRubles(){
    long now=System.currentTimeMillis();store.prefs.edit().putLong("gpsAt",now).commit();
    store.readings("saved",Collections.singletonList(new Store.Reading("econom",500,null,now,"fixture")));
    store.readings("me",Collections.singletonList(new Store.Reading("econom",140,null,now,"fixture")));
    assertEquals(Integer.valueOf(140),store.widgetReading().rub);
    store.prefs.edit().putLong("gpsAt",now-120001).commit();assertNull(store.widgetReading());
    store.prefs.edit().putLong("gpsAt",now).commit();store.readings("me",Collections.singletonList(new Store.Reading("econom",null,2.5,now,"legacy")));
    assertNull(store.widgetReading());assertEquals("—",RadarUi.value(store.readings("me").get(0)));assertEquals("—",store.readings("me").get(0).value());
  }
  @Test public void gpsCacheRejectsStaleFixAndPreservesOriginalAge(){
    Location l=new Location("gps");l.setLatitude(55);l.setLongitude(61);l.setAccuracy(10);l.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos()-60_000_000_000L);
    assertTrue(LocationState.accept(store,l));long age=System.currentTimeMillis()-store.prefs.getLong("gpsAt",0);assertTrue(age>=60000&&age<62000);
    l.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos()-121_000_000_000L);assertFalse(LocationState.accept(store,l));
    l.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());l.setAccuracy(501);assertFalse(LocationState.accept(store,l));
  }
  @Test public void sharingDefaultOffSurvivesRecreationAndMarkersHaveDistinctColors() throws Exception {
    try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
      onView(withContentDescription("Вкладка Настройки")).perform(click());
      onView(withContentDescription("Делиться местоположением")).perform(scrollTo(),click());
      assertTrue(store.prefs.getBoolean("shareLocation",false));assertFalse(MonitorService.running);
      // PRO is off, so this opt-in must not create a publication credential or send GPS.
      assertFalse(store.prefs.contains("presenceToken"));scenario.recreate();assertTrue(store.prefs.getBoolean("shareLocation",false));
      onView(withContentDescription("Вкладка Настройки")).perform(click());
      onView(withContentDescription("Делиться местоположением")).perform(scrollTo(),click());assertFalse(store.prefs.getBoolean("shareLocation",true));
      scenario.onActivity(a->{Bitmap own=a.locationIcon(true,true),peer=a.locationIcon(false,true);int x=own.getWidth()/2,y=own.getHeight()/2;
        int gold=own.getPixel(x,y),purple=peer.getPixel(x,y);assertTrue(Color.red(gold)>Color.blue(gold));assertTrue(Color.blue(purple)>Color.green(purple));
        assertEquals(0,Color.alpha(own.getPixel(0,0)));own.recycle();peer.recycle();});
      onView(withContentDescription("Вкладка Карта")).perform(click());
      final MainActivity[] activity={null};scenario.onActivity(a->activity[0]=a);EconomyPatchTest.waitFor(()->activity[0].map!=null);
      scenario.onActivity(a->{
        a.store.prefs.edit().putLong("lat",Double.doubleToLongBits(55.16)).putLong("lon",Double.doubleToLongBits(61.40)).putLong("gpsAt",System.currentTimeMillis()).commit();
        try{a.peers=new ArrayList<>(Collections.singletonList(new PresenceClient.Peer(new JSONObject().put("id","fixture-peer").put("lat",55.1608).put("lon",61.402).put("observedAt",System.currentTimeMillis()))));}catch(Exception e){throw new AssertionError(e);}
        a.presenceEpoch++;a.nextPresenceQuery=Long.MAX_VALUE;
        a.map.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(new org.maplibre.android.geometry.LatLng(55.16,61.40),15));a.refreshMarkers();
        assertNotNull(a.pointBitmaps.get("me"));
        Bitmap overlay=Bitmap.createBitmap(a.pointOverlay.getWidth(),a.pointOverlay.getHeight(),Bitmap.Config.ARGB_8888);a.pointOverlay.draw(new Canvas(overlay));
        PointF own=a.map.getProjection().toScreenLocation(new org.maplibre.android.geometry.LatLng(55.16,61.40));
        PointF peer=a.map.getProjection().toScreenLocation(new org.maplibre.android.geometry.LatLng(55.1608,61.402));
        int gold=overlay.getPixel(Math.round(own.x),Math.round(own.y)),purple=overlay.getPixel(Math.round(peer.x),Math.round(peer.y));
        assertTrue("Own GPS marker drawn",Color.alpha(gold)>200&&Color.red(gold)>Color.blue(gold));
        assertTrue("Peer marker drawn",Color.alpha(purple)>200&&Color.blue(purple)>Color.green(purple));overlay.recycle();
      });ScreenTest.capture("presence-gold-purple-map");
    }
  }
  @Test public void coincidentPeerRemainsVisibleAroundOwnMarker() throws Exception {
    try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
      final MainActivity[] activity={null};scenario.onActivity(a->activity[0]=a);
      EconomyPatchTest.waitFor(()->activity[0].map!=null);
      scenario.onActivity(a->{
        a.store.prefs.edit().putLong("lat",Double.doubleToLongBits(55.16)).putLong("lon",Double.doubleToLongBits(61.40)).putLong("gpsAt",System.currentTimeMillis()).commit();
        try{a.peers=new ArrayList<>(Collections.singletonList(new PresenceClient.Peer(new JSONObject().put("id","fixture-colocated").put("lat",55.16).put("lon",61.40).put("observedAt",System.currentTimeMillis()))));}catch(Exception e){throw new AssertionError(e);}
        a.presenceEpoch++;a.nextPresenceQuery=Long.MAX_VALUE;
        a.map.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(new org.maplibre.android.geometry.LatLng(55.16,61.40),12.3));a.refreshMarkers();
        Bitmap overlay=Bitmap.createBitmap(a.pointOverlay.getWidth(),a.pointOverlay.getHeight(),Bitmap.Config.ARGB_8888);a.pointOverlay.draw(new Canvas(overlay));
        PointF xy=a.map.getProjection().toScreenLocation(new org.maplibre.android.geometry.LatLng(55.16,61.40));
        int gold=overlay.getPixel(Math.round(xy.x),Math.round(xy.y));
        int purple=overlay.getPixel(Math.round(xy.x+a.dp(15)),Math.round(xy.y));
        assertTrue("Own gold center survives overlap",Color.red(gold)>Color.blue(gold));
        assertTrue("Colocated peer purple ring survives overlap",Color.alpha(purple)>200&&Color.blue(purple)>Color.red(purple)&&Color.blue(purple)>Color.green(purple));overlay.recycle();
      });ScreenTest.capture("presence-colocated-gold-purple");
    }
  }
  @Test public void androidHttpsPublishesAndWithdrawsTwoSyntheticClients() throws Exception {
    String a=java.util.UUID.randomUUID().toString().replace("-","")+java.util.UUID.randomUUID().toString().replace("-","");
    String b=java.util.UUID.randomUUID().toString().replace("-","")+java.util.UUID.randomUUID().toString().replace("-","");
    long revision=System.currentTimeMillis();String endpoint=PresenceClient.ENDPOINT;
    String query=endpoint+"?west=-123.45679&south=-47.12346&east=-123.45677&north=-47.12344";
    try{
      JSONObject body=new JSONObject().put("lat",-47.12345).put("lon",-123.45678).put("accuracy",10).put("observedAt",System.currentTimeMillis()).put("revision",++revision);
      String first=new JSONObject(PresenceClient.request("PUT",endpoint,body,a)).getString("id");
      body.put("revision",++revision).put("observedAt",System.currentTimeMillis());
      String second=new JSONObject(PresenceClient.request("PUT",endpoint,body,b)).getString("id");
      org.json.JSONArray points=new JSONObject(PresenceClient.request("GET",query,null,null)).getJSONArray("points");
      Set<String> ids=new HashSet<>();for(int i=0;i<points.length();i++)ids.add(points.getJSONObject(i).getString("id"));
      assertTrue("First Android HTTPS publication visible",ids.contains(first));assertTrue("Second Android HTTPS publication visible",ids.contains(second));
      PresenceClient.request("DELETE",endpoint,new JSONObject().put("revision",++revision),a);
      points=new JSONObject(PresenceClient.request("GET",query,null,null)).getJSONArray("points");
      ids.clear();for(int i=0;i<points.length();i++)ids.add(points.getJSONObject(i).getString("id"));
      assertFalse("Withdrawal removes own fixture",ids.contains(first));assertTrue(ids.contains(second));
    }finally{
      try{PresenceClient.request("DELETE",endpoint,new JSONObject().put("revision",++revision),a);}finally{PresenceClient.request("DELETE",endpoint,new JSONObject().put("revision",++revision),b);}
    }
  }
  @Test public void peerExpiryAndOwnIdentityAreStable() throws Exception {
    long now=System.currentTimeMillis();PresenceClient.Peer peer=new PresenceClient.Peer(new JSONObject().put("id","peer").put("lat",55).put("lon",61).put("observedAt",now));assertTrue(peer.fresh());
    PresenceClient.Peer old=new PresenceClient.Peer(new JSONObject().put("id","peer").put("lat",55).put("lon",61).put("observedAt",now-120001));assertFalse(old.fresh());
    store.prefs.edit().putString("presenceToken",String.join("",Collections.nCopies(64,"a"))).commit();assertEquals(64,PresenceClient.ownId(store).length());assertFalse(PresenceClient.ownId(store).equals(store.prefs.getString("presenceToken","")));
  }
}
