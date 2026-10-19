package dev.raf.radar;
import android.content.*;
import android.widget.*;
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
  @Before public void reset(){
    Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();c.stopService(new Intent(c,MonitorService.class));
    new Store(c).prefs.edit().clear().putBoolean("autoUpdate",false).putBoolean("startupSound",false).commit();ScreenTest.grantAccess();
  }
  @Test public void physicalSwitchTapsPersistAndLastTariffCannotBeRemoved(){
    try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
      onView(withContentDescription("Вкладка Настройки")).perform(click());
      onView(withContentDescription("Тариф на карте: Бизнес")).perform(scrollTo(),click());
      onView(withContentDescription("Тариф на карте: Бизнес")).check(matches(isChecked()));
      onView(withContentDescription("Тариф на карте: Эконом")).perform(scrollTo(),click());
      onView(withContentDescription("Тариф на карте: Комфорт")).perform(scrollTo(),click());
      scenario.onActivity(a->assertEquals(Collections.singleton("vip"),a.mapTariffs()));
      scenario.recreate();
      onView(withContentDescription("Тариф на карте: Бизнес")).perform(scrollTo()).check(matches(isChecked()));
      onView(withContentDescription("Тариф на карте: Бизнес")).perform(click()).check(matches(isChecked()));
      scenario.onActivity(a->assertEquals(Collections.singleton("vip"),a.mapTariffs()));
      ScreenTest.capture("tariffs-settings-019");
    }
  }
  @Test public void mapUsesEverySelectedTariffIndependentlyOfPointWidgetSelection(){
    try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
      scenario.onActivity(a->{
        Point p=new Point("tariff-test","Дом",55.75,37.61,false);p.tariffs=Collections.singletonList("econom");a.store.update(p);
        a.store.prefs.edit().putStringSet("mapTariffs",new HashSet<>(Arrays.asList("business","vip"))).commit();
        long now=System.currentTimeMillis();a.store.readings(p.id,Arrays.asList(new Store.Reading("econom",120,null,now,"fixture"),new Store.Reading("business",70,null,now,"fixture"),new Store.Reading("vip",95,null,now,"fixture")));
        List<Store.Reading> rows=a.mapReadings(p);
        assertEquals(2,rows.size());assertEquals("business",rows.get(0).tariff);assertEquals("vip",rows.get(1).tariff);
        assertEquals(Integer.valueOf(70),rows.get(0).rub);assertEquals(Integer.valueOf(95),rows.get(1).rub);
        assertEquals("Эконом +120 ₽",a.pointValues(p));
        a.store.prefs.edit().putStringSet("mapTariffs",Collections.singleton("minivan")).commit();
        assertEquals("minivan",a.mapReadings(p).get(0).tariff);assertNull(a.mapReadings(p).get(0).rub);
        assertEquals("Эконом +120 ₽",a.pointValues(p));
      });
    }
  }
  @Test public void layersDialogAndSettingsUseSameSelection(){
    try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
      onView(withContentDescription("Слои карты")).perform(click());
      onView(withText("Бизнес")).perform(click());onView(withText("Применить")).perform(click());
      onView(withContentDescription("Вкладка Настройки")).perform(click());
      onView(withContentDescription("Тариф на карте: Бизнес")).perform(scrollTo()).check(matches(isChecked()));
      scenario.recreate();scenario.onActivity(a->assertTrue(a.mapTariffs().contains("vip")));
    }
  }
  @Test public void pointCheckboxesSaveAndSurviveRecreation(){
    try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
      scenario.onActivity(a->{Point p=new Point("settings-test","Дом",55.75,37.61,false);p.tariffs=new ArrayList<>(Collections.singletonList("econom"));a.store.update(p);a.editDetails(p);});
      onView(withText("Эконом")).perform(scrollTo(),click());
      onView(withText("Бизнес")).perform(scrollTo(),click());
      onView(withText("Сохранить")).perform(click());
      scenario.recreate();scenario.onActivity(a->{
        Point p=a.store.points().stream().filter(x->x.id.equals("settings-test")).findFirst().get();
        assertEquals(Collections.singletonList("vip"),p.tariffs);
        assertEquals("settings-test",a.store.prefs.getString("overlayPoint",""));
      });
    }
  }
}
