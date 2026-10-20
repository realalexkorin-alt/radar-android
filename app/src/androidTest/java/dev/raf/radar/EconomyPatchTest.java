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
      onView(withText("Отменить")).check(doesNotExist());onView(withText("Сохранить")).perform(click());
      scenario.onActivity(a->{assertNull(a.preview);assertTrue(a.store.points().stream().anyMatch(p->p.id.equals("card")));a.edit(point);});
      onView(withText("Удалить")).perform(click());scenario.onActivity(a->{assertNull(a.preview);assertFalse(a.store.points().stream().anyMatch(p->p.id.equals("card")));});
      scenario.onActivity(a->a.edit(new Point("discard","Временная",55.8,37.7,false)));
      // A real outside tap reaches the map and dismisses the sheet.
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
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        scenario.onActivity(a->{ViewGroup root=(ViewGroup)overlayRoot();assertEquals(1,root.getChildCount());PriceText price=(PriceText)root.getChildAt(0);
          assertEquals(rub==0?"0":"+"+rub,price.getText().toString());assertEquals(PriceLevel.color(rub),price.getCurrentTextColor());
          assertTrue(root.getWidth()<=a.getResources().getDisplayMetrics().widthPixels);assertEquals(0,price.getLayout().getEllipsisCount(0));assertTrue(price.getLayout().getLineWidth(0)<=price.getWidth()-price.getPaddingLeft()-price.getPaddingRight()+1);
        });
        if(rub==100||rub==400)ScreenTest.capture("economy-widget-"+rub);
      }
      final int[] large={0};scenario.onActivity(a->large[0]=overlayRoot().getWidth());
      scenario.onActivity(a->{store.readings("me",java.util.Collections.singletonList(new Store.Reading("econom",1,null,System.currentTimeMillis(),"test")));overlay.refresh();});
      InstrumentationRegistry.getInstrumentation().waitForIdleSync();scenario.onActivity(a->assertTrue(overlayRoot().getWidth()<large[0]));
      for(int size:new int[]{60,250}){scenario.onActivity(a->{store.prefs.edit().putInt("textScale",size).commit();overlay.refresh();});InstrumentationRegistry.getInstrumentation().waitForIdleSync();ScreenTest.capture("economy-widget-size-"+size);}
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
