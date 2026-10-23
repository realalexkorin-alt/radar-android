package dev.raf.radar;
import android.content.*;import android.graphics.Bitmap;import android.widget.*;
import androidx.test.core.app.ActivityScenario;import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;import static org.junit.Assert.*;import java.util.*;
public class PointAgeTest {
 @Test public void savedPointAgeSurvivesFailureAndAppearsOnMapAndList(){
  Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
  c.getSharedPreferences("radar",0).edit().clear().putBoolean("autoUpdate",false).putBoolean("sounds",false).commit();
  Store s=new Store(c);Point p=new Point("age-fixture","Тестовая точка",55.16,61.40,false);s.update(p);
  long at=System.currentTimeMillis()-120000;s.readings(p.id,Collections.singletonList(new Store.Reading("econom",0,null,at,"fixture")));
  ScreenTest.grantAccess();
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   scenario.onActivity(a->{a.showTab(1);a.refreshCards();assertEquals("Обновлено 2 м назад",a.pointAge(p));
    LinearLayout line=(LinearLayout)a.cards.getChildAt(0);LinearLayout labels=(LinearLayout)line.getChildAt(1);
    assertEquals(a.pointAge(p),((TextView)labels.getChildAt(2)).getText().toString());
    Bitmap icon=a.pointIcon(p);assertTrue(icon.getHeight()>=a.dp(50));icon.recycle();
    a.store.readings(p.id,Collections.singletonList(new Store.Reading("econom",null,null,System.currentTimeMillis(),"Ошибка")));
    assertEquals(at,a.store.lastSuccessfulReading(p.id));assertEquals("Обновлено 2 м назад",a.pointAge(p));
   });ScreenTest.capture("point-update-age-list");
   scenario.recreate();scenario.onActivity(a->assertEquals(at,a.store.lastSuccessfulReading(p.id)));
  }
 }
}
