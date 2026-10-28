package dev.raf.radar;
import android.content.*;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import static org.junit.Assert.*;
public class YandexUiTest {
 Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
 boolean contains(View v,String text){if(v instanceof TextView&&((TextView)v).getText().toString().contains(text))return true;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)if(contains(g.getChildAt(i),text))return true;}return false;}
 @Test public void sessionStatesAreReadableInBothThemesAndLightMode(){
  for(String variant:new String[]{"dark","light","lineage"}){
   Context c=context();c.getSharedPreferences("radar",0).edit().clear().putBoolean("dark",!"light".equals(variant)).putString(Appearance.KEY,"lineage".equals(variant)?Appearance.LINEAGE:Appearance.STANDARD).commit();
   try(ActivityScenario<LoginActivity> scenario=ActivityScenario.launch(LoginActivity.class)){
    scenario.onActivity(a->assertTrue(contains(a.getWindow().getDecorView(),"Нет подключённых сессий")));ScreenTest.capture("yandex-"+variant+"-empty");
    c.getSharedPreferences("radar",0).edit().putString("yandexSession","display-fixture").putLong("yandexSessionAt",System.currentTimeMillis()).commit();
    scenario.onActivity(a->{a.showSessions();assertTrue(contains(a.getWindow().getDecorView(),"Основная сессия"));assertTrue(contains(a.getWindow().getDecorView(),"Ожидает проверки"));});ScreenTest.capture("yandex-"+variant+"-session");
    scenario.onActivity(a->a.showResult(true,"Доступ подтверждён. Надбавка получена — можно вернуться к мониторингу."));ScreenTest.capture("yandex-"+variant+"-success");
    scenario.onActivity(a->a.showResult(false,"Не удалось проверить вход. Проверьте интернет и повторите попытку."));ScreenTest.capture("yandex-"+variant+"-failure");
   }
  }
 }
 @Test public void deletionRemovesLocalSessionAndSuppressesRecapture()throws Exception{
  Context c=context();c.getSharedPreferences("radar",0).edit().clear().putString("yandexSession","fixture").putLong("yandexSessionAt",System.currentTimeMillis()).putLong("yandexVerifiedAt",System.currentTimeMillis()).commit();
  try(ActivityScenario<LoginActivity> scenario=ActivityScenario.launch(LoginActivity.class)){
   scenario.onActivity(LoginActivity::confirmDelete);
   androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText("Удалить")).perform(androidx.test.espresso.action.ViewActions.click());
   for(int i=0;i<40&&YandexSession.saved(c);i++)android.os.SystemClock.sleep(50);
   assertFalse(YandexSession.saved(c));assertTrue(c.getSharedPreferences("radar",0).getBoolean("yandexRemoved",false));assertTrue(YandexSession.headers(c).isEmpty());
   assertFalse(YandexSession.capture(c,TaxiClient.ENDPOINT,java.util.Collections.singletonMap("X-Csrf-Token","fixture")));
  }
 }
}
