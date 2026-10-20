package dev.raf.radar;

import android.content.*;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;

public class EconomyPatchTest {
  Store store;Overlay overlay;Context c;
  @Before public void setup() throws Exception {
    c=InstrumentationRegistry.getInstrumentation().getTargetContext();c.stopService(new Intent(c,MonitorService.class));
    waitFor(()->!MonitorService.running);
    c.getSharedPreferences("radar",0).edit().clear().putBoolean("sounds",false).putBoolean("startupSound",false).putBoolean("autoUpdate",false).commit();
    store=new Store(c);ScreenTest.grantAccess();ScreenTest.shell("appops set dev.raf.radar SYSTEM_ALERT_WINDOW allow");
    Point me=store.points().get(0);me.enabled=false;store.update(me);
  }
  @After public void cleanup(){InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{if(overlay!=null)overlay.close();AppSounds.stop();});c.stopService(new Intent(c,MonitorService.class));}
  interface Ready {boolean get();}
  static void waitFor(Ready ready){long end=android.os.SystemClock.uptimeMillis()+8000;while(!ready.get()){if(android.os.SystemClock.uptimeMillis()>end)throw new AssertionError("State transition timeout");android.os.SystemClock.sleep(80);}}
  @Test public void migrationPreservesSavedDataAndUsesOnlyEconomy() throws Exception {
    String old="[{\"id\":\"home\",\"name\":\"Дом\",\"address\":\"Адрес\",\"lat\":55.7,\"lon\":37.6,\"dynamic\":false,\"enabled\":false,\"notify\":true,\"threshold\":350,\"tariffs\":[\"vip\",\"comfortplus\"]}]";
    store.prefs.edit().remove("economyOnlyV1").putString("points",old).putInt("scale",130).putInt("textScale",220).putInt("ox",27).putBoolean("dark",false).putString("overlayPoint","home").commit();
    Store migrated=new Store(c);Point home=migrated.widgetPoint();assertEquals("home",home.id);assertEquals("Дом",home.name);assertEquals("Адрес",home.address);assertEquals(350,home.threshold);assertFalse(home.enabled);assertTrue(home.notify);
    assertEquals(java.util.Collections.singletonList("econom"),home.tariffs);assertEquals(java.util.Collections.singletonList("econom"),migrated.widgetTariffs());
    assertEquals(130,migrated.prefs.getInt("scale",0));assertEquals(220,migrated.prefs.getInt("textScale",0));assertEquals(27,migrated.prefs.getInt("ox",0));assertTrue(migrated.prefs.getBoolean("dark",false));
    assertEquals(2,migrated.points().size());
  }
  @Test public void proStartsAndStopsMonitorAndOverlay() {
    try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
      scenario.onActivity(a->{assertFalse(a.pro.isSelected());a.pro.performClick();});waitFor(()->MonitorService.running);
      InstrumentationRegistry.getInstrumentation().waitForIdleSync();
      scenario.onActivity(a->{a.refreshCards();assertTrue(a.pro.isSelected());assertTrue(a.store.prefs.getBoolean("overlay",false));});
      ScreenTest.capture("economy-pro-on");
      scenario.onActivity(a->a.pro.performClick());waitFor(()->!MonitorService.running);
      scenario.onActivity(a->{a.refreshCards();assertFalse(a.pro.isSelected());assertFalse(a.store.prefs.getBoolean("overlay",true));});
      ScreenTest.capture("economy-pro-off");
    }
  }
  @Test public void settingsAreCompactAndWidgetControlsExpand(){
    try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
      onView(withContentDescription("Вкладка Настройки")).perform(click());
      onView(withText("Тарифы виджета")).check(doesNotExist());onView(withText("Тарифы точек")).check(doesNotExist());onView(withText("Тёмная тема")).check(doesNotExist());
      onView(withContentDescription("Размер и прозрачность")).perform(scrollTo(),click());
      onView(withContentDescription("Размер текста")).perform(scrollTo()).check(matches(isDisplayed()));
      scenario.onActivity(a->{SeekBar slider=(SeekBar)find(a.root,"Размер текста");assertEquals(190,slider.getMax());});
      ScreenTest.capture("economy-settings");
      onView(withContentDescription("О приложении")).perform(scrollTo(),click());onView(withText("Радар Pro · "+BuildConfig.VERSION_NAME)).check(matches(isDisplayed()));
    }
  }
  @Test public void newCardSavesAndExistingCardDeletesWithoutCancel(){
    try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
      final MainActivity[] activity={null};scenario.onActivity(a->activity[0]=a);
      waitFor(()->activity[0].map!=null);
      Point point=new Point("card","Новая точка",55.7,37.6,false);
      scenario.onActivity(a->{a.edit(point);assertNotNull(a.preview);});
      ScreenTest.capture("economy-new-card");
      onView(withText("Отменить")).check(doesNotExist());onView(withText("Сохранить")).perform(click());
      scenario.onActivity(a->{assertNull(a.preview);assertTrue(a.store.points().stream().anyMatch(p->p.id.equals("card")));a.edit(point);});
      ScreenTest.capture("economy-saved-card");
      onView(withText("Удалить")).perform(click());scenario.onActivity(a->{assertNull(a.preview);assertFalse(a.store.points().stream().anyMatch(p->p.id.equals("card")));});
      scenario.onActivity(a->a.edit(new Point("discard","Временная",55.8,37.7,false)));
      // A real outside tap dismisses independently of map gestures/network.
      onView(withContentDescription("Карточка локации")).check(matches(isDisplayed()));
      scenario.onActivity(a->{if(a.map!=null)a.map.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(new org.maplibre.android.geometry.LatLng(55.8,37.7),12));});
      android.app.UiAutomation automation=InstrumentationRegistry.getInstrumentation().getUiAutomation();
      int width=c.getResources().getDisplayMetrics().widthPixels;
      long t=android.os.SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,width/2f,250,0),up=MotionEvent.obtain(t,t+70,MotionEvent.ACTION_UP,width/2f,250,0);
      automation.injectInputEvent(down,true);automation.injectInputEvent(up,true);down.recycle();up.recycle();
      InstrumentationRegistry.getInstrumentation().waitForIdleSync();
      scenario.onActivity(a->{assertNull(a.preview);assertFalse(a.store.points().stream().anyMatch(p->p.id.equals("discard")));});
    }
  }
  static View find(View view,String description){if(description.equals(view.getContentDescription()))return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=find(group.getChildAt(i),description);if(found!=null)return found;}}return null;}
  View overlayRoot(){try{java.lang.reflect.Field f=Overlay.class.getDeclaredField("root");f.setAccessible(true);return (View)f.get(overlay);}catch(Exception e){throw new AssertionError(e);}}
  @Test public void widgetShowsOnlyPriceColorsAndGrowsWithDigits(){
    try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
      scenario.onActivity(a->{store.prefs.edit().putBoolean("overlay",true).commit();overlay=new Overlay(a,store);});
      for(int rub:new int[]{0,99,100,199,200,299,300,399,400,9999}){
        scenario.onActivity(a->{store.readings("me",java.util.Collections.singletonList(new Store.Reading("econom",rub,null,System.currentTimeMillis(),"test")));overlay.refresh();});
        awaitOverlayLayout(scenario);
        if(rub==0)ScreenTest.capture("economy-widget-zero");
        scenario.onActivity(a->{ViewGroup root=(ViewGroup)overlayRoot();assertEquals(1,root.getChildCount());PriceText price=(PriceText)root.getChildAt(0);
          assertEquals(rub==0?"0":"+"+rub,price.getText().toString());assertEquals(PriceLevel.color(rub),price.getCurrentTextColor());
          android.graphics.Bitmap marker=a.pointIcon(new Point("me","Метка",55.7,37.6,false));
          int wanted=PriceLevel.color(rub);boolean found=false;
          for(int y=0;y<marker.getHeight()&&!found;y++)for(int x=0;x<marker.getWidth();x++){
            int pixel=marker.getPixel(x,y);
            if(Color.alpha(pixel)>200&&Math.abs(Color.red(pixel)-Color.red(wanted))<8&&Math.abs(Color.green(pixel)-Color.green(wanted))<8&&Math.abs(Color.blue(pixel)-Color.blue(wanted))<8){found=true;break;}
          }
          marker.recycle();assertTrue("Map price color at "+rub,found);
          assertTrue("Widget width="+root.getWidth(),root.getWidth()<=a.getResources().getDisplayMetrics().widthPixels);
          assertEquals(0,price.getLayout().getEllipsisCount(0));
          assertTrue("Price="+rub+" line="+price.getLayout().getLineWidth(0)+" width="+price.getWidth()+" padding="+price.getPaddingLeft(),price.getLayout().getLineWidth(0)<=price.getWidth()-price.getPaddingLeft()-price.getPaddingRight()+1);
        });
        if(rub==100||rub==400)ScreenTest.capture("economy-widget-"+rub);
      }
      final int[] large={0};scenario.onActivity(a->large[0]=overlayRoot().getWidth());
      scenario.onActivity(a->{store.readings("me",java.util.Collections.singletonList(new Store.Reading("econom",1,null,System.currentTimeMillis(),"test")));overlay.refresh();});
      awaitOverlayLayout(scenario);scenario.onActivity(a->assertTrue(overlayRoot().getWidth()<large[0]));
      for(int size:new int[]{60,250}){scenario.onActivity(a->{store.prefs.edit().putInt("textScale",size).commit();overlay.refresh();});InstrumentationRegistry.getInstrumentation().waitForIdleSync();ScreenTest.capture("economy-widget-size-"+size);}
    }
  }
  void awaitOverlayLayout(ActivityScenario<MainActivity> scenario){
    java.util.concurrent.CountDownLatch frame=new java.util.concurrent.CountDownLatch(1);
    scenario.onActivity(a->overlayRoot().postOnAnimation(()->overlayRoot().postOnAnimation(frame::countDown)));
    try{assertTrue("Overlay layout frame",frame.await(5,java.util.concurrent.TimeUnit.SECONDS));}catch(InterruptedException e){throw new AssertionError(e);}
    InstrumentationRegistry.getInstrumentation().waitForIdleSync();
  }
  static class CaptureContext extends ContextWrapper {
    Intent opened;CaptureContext(Context c){super(c);}
    @Override public void startActivity(Intent intent){opened=intent;}
  }
  @Test public void widgetAnimatesStopsOnCloseAndDragsWithoutHold(){
    try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
      final CaptureContext[] capture={null};final java.util.concurrent.atomic.AtomicInteger frames=new java.util.concurrent.atomic.AtomicInteger();
      scenario.onActivity(a->{capture[0]=new CaptureContext(a);store.prefs.edit().putBoolean("overlay",true).putInt("ox",20).putInt("oy",200).commit();
        store.readings("me",java.util.Collections.singletonList(new Store.Reading("econom",400,null,System.currentTimeMillis(),"fixture")));
        overlay=new Overlay(capture[0],store);overlay.refresh();
      });
      InstrumentationRegistry.getInstrumentation().waitForIdleSync();
      scenario.onActivity(a->overlayRoot().getViewTreeObserver().addOnDrawListener(()->frames.incrementAndGet()));
      waitFor(()->frames.get()>=3);
      scenario.onActivity(a->{
        long t=android.os.SystemClock.uptimeMillis();View root=overlayRoot();
        for(MotionEvent e:new MotionEvent[]{MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,20,20,0),MotionEvent.obtain(t,t+70,MotionEvent.ACTION_MOVE,100,90,0),MotionEvent.obtain(t,t+100,MotionEvent.ACTION_UP,100,90,0)}){root.dispatchTouchEvent(e);e.recycle();}
        assertNull(capture[0].opened);assertTrue(store.prefs.getInt("ox",0)>20);assertTrue(store.prefs.getInt("oy",0)>200);
        t+=2000;
        for(MotionEvent e:new MotionEvent[]{MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,20,20,0),MotionEvent.obtain(t,t+80,MotionEvent.ACTION_UP,20,20,0)}){root.dispatchTouchEvent(e);e.recycle();}
        assertNotNull(capture[0].opened);assertFalse(capture[0].opened.getBooleanExtra("widgetSettings",true));
        overlay.close();
      });
      InstrumentationRegistry.getInstrumentation().waitForIdleSync();int stopped=frames.get();android.os.SystemClock.sleep(200);assertEquals(stopped,frames.get());
    }
  }
  @Test public void soundVariantsDecodeAndToggleMutesPlayback() throws Exception {
    for(AppSounds.Event event:AppSounds.Event.values())for(int id:AppSounds.variants(event)){
      try(android.content.res.AssetFileDescriptor fd=c.getResources().openRawResourceFd(id);MediaMetadataRetriever media=new MediaMetadataRetriever()){
        media.setDataSource(fd.getFileDescriptor(),fd.getStartOffset(),fd.getLength());assertTrue(Long.parseLong(media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION))>0);
      }
    }
    InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
      assertNotEquals(AppSounds.choose(AppSounds.Event.UPDATE_REPLY),AppSounds.choose(AppSounds.Event.UPDATE_REPLY));
      assertNotEquals(AppSounds.choose(AppSounds.Event.POINT_SAVE),AppSounds.choose(AppSounds.Event.POINT_SAVE));
      AppSounds.play(c,AppSounds.Event.MONITOR_READY);
      try{java.lang.reflect.Field f=AppSounds.class.getDeclaredField("player");f.setAccessible(true);assertNull(f.get(null));}catch(Exception e){throw new AssertionError(e);}
    });
  }
}
