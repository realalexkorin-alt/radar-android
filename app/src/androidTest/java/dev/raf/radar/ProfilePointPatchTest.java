package dev.raf.radar;
import android.content.*;import android.graphics.*;import android.view.*;import android.widget.*;
import androidx.test.core.app.ActivityScenario;import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;import org.junit.Test;import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.*;import static androidx.test.espresso.matcher.ViewMatchers.*;import static androidx.test.espresso.action.ViewActions.*;
public class ProfilePointPatchTest {
 void prepare(String theme)throws Exception{
  Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();c.stopService(new Intent(c,MonitorService.class));
  Store s=new Store(c);s.prefs.edit().clear().putBoolean("economyOnlyV1",true).putBoolean("dark",true).putBoolean("autoUpdate",false).putBoolean("sounds",false).putString(Appearance.KEY,theme).commit();
  AccountClient.save(s,new JSONObject().put("user",new JSONObject().put("id",999).put("name","Проверка")).put("session","a".repeat(64)));
  AccountClient.transport=(body,token)->new JSONObject().put("friends",new JSONArray()).put("incoming",new JSONArray());ScreenTest.grantAccess();
 }
 @Test public void profileAndPointButtonsAndFixedMarkersInBothThemes()throws Exception{
  for(String theme:new String[]{Appearance.STANDARD,Appearance.LINEAGE}){
   prepare(theme);
   try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
    s.onActivity(a->{
     Point p=new Point("fixture","Рабочая точка",55,61,false);p.address="Челябинск";a.store.update(p);
     a.store.readings(p.id,java.util.Collections.singletonList(new Store.Reading("econom",0,null,System.currentTimeMillis()-65000,"fixture")));
     a.store.prefs.edit().putBoolean("mapPointUpdateTime",false).putBoolean("forecast",false).commit();Bitmap before=a.pointIcon(p);
     a.store.prefs.edit().putBoolean("forecast",true).putInt("trend:"+p.id,1).commit();Bitmap after=a.pointIcon(p);
     assertEquals(before.getWidth(),after.getWidth());assertEquals(before.getHeight(),after.getHeight());
     for(int x=before.getWidth()/2-a.dp(5);x<before.getWidth()/2+a.dp(5);x++)for(int y=a.dp(10);y<a.dp(23);y++)assertEquals(before.getPixel(x,y),after.getPixel(x,y));
     before.recycle();after.recycle();a.showTab(1);assertTrue(SettingsPolishTest.contains(a.cards,"Обновлено 1 м назад"));
    });ScreenTest.capture("points-"+theme);
    s.onActivity(a->{a.showTab(2);assertTrue(SettingsPolishTest.contains(a.settingsContents(),"Яндекс авторизация"));});ScreenTest.capture("profile-"+theme);
   }finally{AccountClient.transport=AccountClient::request;}
  }
 }
 @Test public void savingMapPointImmediatelyOpensItsSettings()throws Exception{
  prepare(Appearance.STANDARD);
  try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
   s.onActivity(a->a.edit(new Point("new-fixture","Новая точка",55,61,false)));
   onView(withText("Сохранить")).perform(click());
   onView(withText("Настройка точки")).check(androidx.test.espresso.assertion.ViewAssertions.matches(isDisplayed()));
   onView(withHint("Название точки")).perform(replaceText("Дом"),closeSoftKeyboard());
   ScreenTest.capture("new-point-settings");
   onView(withText("Сохранить")).perform(click());
   s.onActivity(a->{boolean found=false;for(Point p:a.store.points())if("new-fixture".equals(p.id)){found=true;assertEquals("Дом",p.name);}assertTrue(found);});
  }finally{AccountClient.transport=AccountClient::request;}
 }
}
