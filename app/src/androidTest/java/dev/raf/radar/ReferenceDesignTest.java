package dev.raf.radar;

import android.content.*;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;

/** Fixture data is confined to the emulator; it is never populated by production code. */
public class ReferenceDesignTest {
  @Before public void setup(){Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();c.stopService(new Intent(c,MonitorService.class));new Store(c).prefs.edit().clear().commit();ScreenTest.grantAccess();}
  void seed(MainActivity a){
    long now=System.currentTimeMillis();a.store.prefs.edit().putLong("lat",Double.doubleToLongBits(55.7558)).putLong("lon",Double.doubleToLongBits(37.6173)).putLong("gpsAt",now).putInt("opacity",85).putString("overlayPoint","me").apply();
    Point me=a.store.points().get(0);me.tariffs=java.util.Collections.singletonList("econom");a.store.update(me);a.store.readings("me",java.util.Collections.singletonList(new Store.Reading("econom",120,null,now,"fixture")));
    String[] names={"Дом","ТЦ Европейский","Шереметьево","Центр","Москва-Сити","Внуково","ТЦ Авиапарк","Красная площадь"};
    String[] addresses={"ул. Ленина, 25","пл. Киевского Вокзала, 2","Международное ш., вл. 1","ул. Тверская, 12","Пресненская наб., 10","2-я Рейсовая ул., 2","Ходынский б-р, 4","ул. Ильинка, 5"};
    for(int i=0;i<names.length;i++){
      Point p=new Point("ref-"+i,names[i],55.7558+(i%3-1)*.007,37.6173+(i%4-1.5)*.012,false);p.address=addresses[i];p.enabled=i!=2&&i!=5&&i!=7;p.tariffs=java.util.Collections.singletonList(i%2==0?"comfortplus":"business");a.store.update(p);
      a.store.readings(p.id,java.util.Collections.singletonList(new Store.Reading(p.tariffs.get(0),new int[]{120,70,95,120}[i%4],null,now,"fixture")));
    }
    a.store.prefs.edit().putStringSet("mapTariffs",new java.util.LinkedHashSet<>(java.util.Arrays.asList("econom","business","comfortplus"))).apply();a.rebuildSettings();a.refreshCards();
  }
  @Test public void referenceScreensAndCompactWidget() throws Exception {
    try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
      scenario.onActivity(a->{seed(a);if(a.map!=null)a.map.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(new org.maplibre.android.geometry.LatLng(55.7558,37.6173),12.8));});
      // Wait for actual style rendering, not merely Activity idle. A missing marker must fail QA.
      android.os.SystemClock.sleep(2500);
      scenario.onActivity(a->{
        assertNotNull("Map must be ready",a.map);
        a.store.prefs.edit().putStringSet("mapTariffs",java.util.Collections.singleton("business")).commit();a.refreshMarkers();
        a.map.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(new org.maplibre.android.geometry.LatLng(55.7488,37.6353),12.8));
      });
      android.os.SystemClock.sleep(500);
      scenario.onActivity(a->{
        android.graphics.PointF pos=a.map.getProjection().toScreenLocation(new org.maplibre.android.geometry.LatLng(55.7488,37.6353));
        assertEquals("Saved point must be tappable","ref-3",a.pointAt(pos).id);
        android.graphics.Bitmap icon=a.pointBitmaps.get("ref-3");assertNotNull(icon);
        int pixel=icon.getPixel(aSampleX(icon),aSampleY(icon));
        assertTrue("Marker image must retain orange RGB, not a white silhouette",android.graphics.Color.red(pixel)>200&&android.graphics.Color.blue(pixel)<100);
      });
      final android.graphics.PointF[] position={null};scenario.onActivity(a->{
        position[0]=a.map.getProjection().toScreenLocation(new org.maplibre.android.geometry.LatLng(55.7488,37.6353));
        int[] offset=new int[2];a.mapFrame.getLocationOnScreen(offset);android.graphics.Bitmap icon=a.pointBitmaps.get("ref-3");position[0].offset(offset[0]-icon.getWidth()/2f+aSampleX(icon),offset[1]-icon.getHeight()/2f+aSampleY(icon));
      });
      android.graphics.Bitmap screen=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
      int px=Math.round(position[0].x),py=Math.round(position[0].y);
      assertTrue("Marker must be inside screenshot",px>=0&&px<screen.getWidth()&&py>=0&&py<screen.getHeight());
      int markerPixel=screen.getPixel(px,py);
      assertTrue("Actual screen must display orange marker",android.graphics.Color.red(markerPixel)>200&&android.graphics.Color.blue(markerPixel)<100);
      ScreenTest.capture("reference-map-016");
      scenario.onActivity(a->{Point p=a.store.points().stream().filter(x->x.id.equals("ref-3")).findFirst().get();a.edit(p);});
      onView(withText("Сохранить")).check(matches(isDisplayed()));ScreenTest.capture("reference-card-016");onView(withText("Отменить")).perform(click());
      onView(withContentDescription("Вкладка Точки")).perform(click());onView(withText("Дом")).check(matches(isDisplayed()));ScreenTest.capture("reference-points-016");
      onView(withContentDescription("Вкладка Настройки")).perform(click());onView(withText("Размер виджета")).check(matches(isDisplayed()));ScreenTest.capture("reference-settings-016");
      onView(withContentDescription("Вкладка Карта")).perform(click());
      final Overlay[] overlay={null};scenario.onActivity(a->{a.store.prefs.edit().putBoolean("overlay",true).apply();overlay[0]=new Overlay(a,a.store);overlay[0].refresh();});
      ScreenTest.capture("reference-widget-016");
      scenario.onActivity(a->{View root=root(overlay[0]);assertTrue(root.getWidth()<=a.getResources().getDisplayMetrics().widthPixels);find(root,"Раскрыть меню виджета").performClick();});
      ScreenTest.capture("reference-widget-expanded-016");
      scenario.onActivity(a->{find(menu(overlay[0]),"Закрыть виджет").performClick();assertFalse(a.store.prefs.getBoolean("overlay",true));});
    }
  }
  @Test public void settingsAndBottomSheetPreserveState(){
    try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
      onView(withContentDescription("Вкладка Настройки")).perform(click());
      onView(withContentDescription("Размер виджета")).perform(new androidx.test.espresso.ViewAction(){
        public org.hamcrest.Matcher<View> getConstraints(){return isAssignableFrom(SeekBar.class);}
        public String getDescription(){return "Move widget size slider to 130%";}
        public void perform(androidx.test.espresso.UiController ui,View view){SeekBar bar=(SeekBar)view;int x=bar.getPaddingLeft()+Math.round((bar.getWidth()-bar.getPaddingLeft()-bar.getPaddingRight())*.7f);long t=android.os.SystemClock.uptimeMillis();bar.dispatchTouchEvent(MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,x,bar.getHeight()/2f,0));bar.dispatchTouchEvent(MotionEvent.obtain(t,t+20,MotionEvent.ACTION_UP,x,bar.getHeight()/2f,0));ui.loopMainThreadUntilIdle();}
      });
      scenario.onActivity(a->assertEquals(130,a.store.prefs.getInt("scale",0)));
      onView(withText("Автоматическая проверка")).perform(scrollTo(),click());scenario.onActivity(a->assertFalse(a.store.prefs.getBoolean("autoUpdate",true)));
      scenario.recreate();scenario.onActivity(a->{assertEquals(2,a.currentTab);assertEquals(130,a.store.prefs.getInt("scale",0));assertFalse(a.store.prefs.getBoolean("autoUpdate",true));});
      onView(withContentDescription("Вкладка Карта")).perform(click());
      scenario.onActivity(a->{Point p=new Point("new","Проверка",55.75,37.61,false);a.edit(p);assertNotNull(a.pointSheet);});
      onView(withText("Сохранить")).perform(click());scenario.recreate();scenario.onActivity(a->{assertTrue(a.store.points().stream().anyMatch(p->p.id.equals("new")));assertNull(a.pointSheet);});
    }
  }
  static int aSampleX(android.graphics.Bitmap icon){return icon.getWidth()/2;}
  static int aSampleY(android.graphics.Bitmap icon){return 5;}
  static View menu(Overlay o){try{java.lang.reflect.Field f=Overlay.class.getDeclaredField("menu");f.setAccessible(true);return (View)f.get(o);}catch(Exception e){throw new AssertionError(e);}}
  static View root(Overlay o){try{java.lang.reflect.Field f=Overlay.class.getDeclaredField("root");f.setAccessible(true);return (View)f.get(o);}catch(Exception e){throw new AssertionError(e);}}
  static View find(View view,String description){if(description.equals(view.getContentDescription()))return view;if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++){View v=find(g.getChildAt(i),description);if(v!=null)return v;}}return null;}
}
