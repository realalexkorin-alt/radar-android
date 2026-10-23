package dev.raf.radar;
import android.content.Context;import androidx.test.core.app.ActivityScenario;import androidx.test.platform.app.InstrumentationRegistry;import org.json.*;import org.junit.Test;import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.*;import static androidx.test.espresso.action.ViewActions.*;import static androidx.test.espresso.matcher.ViewMatchers.*;import static androidx.test.espresso.assertion.ViewAssertions.*;
public class FriendsUiTest {
 @Test public void badgeRequestsAndAddById()throws Exception{
  Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();Store s=new Store(c);s.prefs.edit().clear().putBoolean("autoUpdate",false).putBoolean("sounds",false).commit();
  JSONArray incoming=new JSONArray().put(new JSONObject().put("id",222).put("name","Никита")).put(new JSONObject().put("id",333).put("name","Друг"));
  JSONObject state=new JSONObject().put("user",new JSONObject().put("id",111).put("name","Тест")).put("session","a".repeat(64)).put("friends",new JSONArray()).put("incoming",incoming).put("outgoing",new JSONArray().put(new JSONObject().put("id",444).put("name","Коллега")));
  AccountClient.save(s,state);ScreenTest.grantAccess();
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   scenario.onActivity(a->{assertEquals("2",a.friendBadge.getText().toString());a.directoryPending=true;a.showFriends();a.nextDirectory=Long.MAX_VALUE;});
   onView(withContentDescription("Вас хочет добавить в друзья Никита · ID 222")).perform(click());onView(withText("Принять")).check(matches(isDisplayed()));androidx.test.espresso.Espresso.pressBack();
   onView(withText("＋ Добавить друга по ID")).perform(click());onView(withHint("ID 1–999")).perform(typeText("111"));onView(withText("Отправить запрос")).perform(click());onView(withHint("ID 1–999")).check(matches(hasErrorText("Нельзя добавить себя")));androidx.test.espresso.Espresso.pressBack();ScreenTest.capture("friends-requests-badge");
  }finally{AccountClient.clear(s);}
 }
}
