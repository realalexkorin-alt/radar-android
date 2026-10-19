package dev.raf.radar;
import android.content.*;
import android.graphics.Rect;
import android.view.accessibility.*;
import android.accessibilityservice.AccessibilityServiceInfo;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import java.util.*;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;

public class TariffSettingsTest {
  Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
  @Before public void reset(){
    Context c=context();c.stopService(new Intent(c,MonitorService.class));
    new Store(c).prefs.edit().clear().putBoolean("autoUpdate",false).putBoolean("startupSound",false).commit();ScreenTest.grantAccess();
  }
  @After public void stop(){context().stopService(new Intent(context(),MonitorService.class));}
  @Test public void widgetSwitchesAreIndependentAndPersist(){
    try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
      scenario.onActivity(a->a.store.widgetTariffs(Collections.singleton("econom")));
      onView(withContentDescription("Вкладка Настройки")).perform(click());
      onView(withContentDescription("Тариф виджета: Комфорт")).perform(scrollTo(),click());
      onView(withContentDescription("Тариф виджета: Комфорт+")).perform(scrollTo(),click());
      scenario.onActivity(a->{assertEquals(Arrays.asList("econom","business","comfortplus"),a.store.widgetTariffs());assertEquals(Arrays.asList("econom","business","comfortplus"),a.store.points().get(0).tariffs);});
      scenario.recreate();
      onView(withContentDescription("Тариф виджета: Комфорт+")).perform(scrollTo()).check(matches(isChecked()));
      onView(withContentDescription("Тариф виджета: Эконом")).perform(scrollTo(),click());
      onView(withContentDescription("Тариф виджета: Комфорт")).perform(scrollTo(),click());
      onView(withContentDescription("Тариф виджета: Комфорт+")).perform(scrollTo(),click()).check(matches(isChecked()));
      scenario.onActivity(a->assertEquals(Collections.singletonList("comfortplus"),a.store.widgetTariffs()));
      ScreenTest.capture("widget-tariff-settings-010");
    }
  }
  @Test public void eachMapPointUsesItsOwnTariffsAndIgnoresLegacyGlobalFilter(){
    try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
      scenario.onActivity(a->{
        Point one=new Point("one","Дом",55.75,37.61,false);one.tariffs=Collections.singletonList("econom");a.store.update(one);
        Point two=new Point("two","Работа",55.77,37.65,false);two.tariffs=Arrays.asList("business","comfortplus");a.store.update(two);
        a.store.widgetTariffs(Arrays.asList("vip","minivan"));a.store.prefs.edit().putStringSet("mapTariffs",Collections.singleton("minivan")).commit();
        assertEquals(1,a.mapReadings(one).size());assertEquals("econom",a.mapReadings(one).get(0).tariff);
        assertEquals(2,a.mapReadings(two).size());assertEquals("business",a.mapReadings(two).get(0).tariff);assertEquals("comfortplus",a.mapReadings(two).get(1).tariff);
        assertTrue(a.pointIcon(two).getHeight()>a.pointIcon(one).getHeight());
        assertEquals(Arrays.asList("vip","minivan"),a.store.widgetTariffs());
      });
      scenario.recreate();scenario.onActivity(a->{assertEquals(Collections.singletonList("econom"),a.store.points().stream().filter(p->p.id.equals("one")).findFirst().get().tariffs);assertEquals(Arrays.asList("vip","minivan"),a.store.widgetTariffs());});
    }
  }
  @Test public void savingPointDoesNotChangeWidgetTariffsOrSource(){
    try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
      scenario.onActivity(a->{a.store.widgetTariffs(Collections.singleton("vip"));Point p=new Point("saved","Дом",55.75,37.61,false);p.tariffs=new ArrayList<>(Collections.singletonList("econom"));a.store.update(p);a.editDetails(p);});
      onView(withText("Эконом")).perform(scrollTo(),click());onView(withText("Комфорт+")).perform(scrollTo(),click());onView(withText("Сохранить")).perform(click());
      scenario.recreate();scenario.onActivity(a->{Point p=a.store.points().stream().filter(x->x.id.equals("saved")).findFirst().get();assertEquals(Collections.singletonList("comfortplus"),p.tariffs);assertEquals("comfortplus",a.mapReadings(p).get(0).tariff);assertEquals(Collections.singletonList("vip"),a.store.widgetTariffs());assertEquals("me",a.store.widgetPoint().id);});
    }
  }
  @Test public void migrationPreservesExistingWidgetThenNeverTracksPointTariffs(){
    Store store=new Store(context());Point p=new Point("old","Старая точка",55,61,false);p.tariffs=Collections.singletonList("econom");store.update(p);
    store.prefs.edit().putString("overlayPoint","old").remove("widgetTariffs").commit();
    store=new Store(context());assertEquals(Collections.singletonList("econom"),store.widgetTariffs());
    p.tariffs=Collections.singletonList("vip");store.update(p);assertEquals(Collections.singletonList("econom"),new Store(context()).widgetTariffs());
    store.remove("old");assertEquals("me",store.widgetPoint().id);assertEquals(Collections.singletonList("econom"),store.widgetTariffs());
  }
  Rect widgetRow(String text){
    android.app.UiAutomation automation=InstrumentationRegistry.getInstrumentation().getUiAutomation();
    AccessibilityServiceInfo info=automation.getServiceInfo();info.flags|=AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;automation.setServiceInfo(info);
    for(AccessibilityWindowInfo window:automation.getWindows()){
      AccessibilityNodeInfo root=window.getRoot();if(root==null)continue;
      for(AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(text))if(text.contentEquals(node.getText()==null?"":node.getText())){Rect bounds=new Rect();node.getBoundsInScreen(bounds);return bounds;}
    }
    return null;
  }
  Rect waitRow(String text){long end=android.os.SystemClock.uptimeMillis()+8000;Rect found;do{found=widgetRow(text);if(found!=null)return found;android.os.SystemClock.sleep(200);}while(android.os.SystemClock.uptimeMillis()<end);throw new AssertionError("Live widget row missing: "+text);}
  @Test public void liveWidgetUpdatesFromSettingsAndStacksRowsVertically(){
    try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
      scenario.onActivity(a->{
        Point me=a.store.points().get(0);me.tariffs=Collections.singletonList("vip");me.enabled=false;a.store.update(me);
        a.store.widgetTariffs(Collections.singleton("econom"));long now=System.currentTimeMillis();
        a.store.readings(me.id,Arrays.asList(new Store.Reading("econom",120,null,now,"fixture"),new Store.Reading("business",70,null,now,"fixture"),new Store.Reading("comfortplus",50,null,now,"fixture")));
        a.store.prefs.edit().putBoolean("overlay",true).putInt("oy",180).commit();a.startForegroundService(new Intent(a,MonitorService.class));
      });
      waitRow("Эконом  +120");
      onView(withContentDescription("Вкладка Настройки")).perform(click());
      onView(withContentDescription("Тариф виджета: Комфорт")).perform(scrollTo(),click());
      onView(withContentDescription("Тариф виджета: Комфорт+")).perform(scrollTo(),click());
      Rect economy=waitRow("Эконом  +120"),comfort=waitRow("Комфорт  +70"),plus=waitRow("Комфорт+  +50");
      assertTrue(economy.bottom<=comfort.top);assertTrue(comfort.bottom<=plus.top);assertEquals(economy.left,comfort.left);assertEquals(comfort.left,plus.left);
      scenario.onActivity(a->assertEquals(Collections.singletonList("vip"),a.store.points().get(0).tariffs));
      ScreenTest.capture("widget-vertical-independent-010");
    }
  }
}
