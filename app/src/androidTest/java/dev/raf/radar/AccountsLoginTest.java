package dev.raf.radar;
import android.content.Context;import androidx.test.core.app.ActivityScenario;import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;import org.junit.Test;import static org.junit.Assert.*;import static androidx.test.espresso.Espresso.onView;import static androidx.test.espresso.action.ViewActions.*;import static androidx.test.espresso.matcher.ViewMatchers.*;
public class AccountsLoginTest {
 @Test public void encryptedSessionSurvivesStoreRecreation()throws Exception{Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();Store s=new Store(c);s.prefs.edit().clear().commit();String token="a".repeat(64);AccountClient.save(s,new JSONObject().put("user",new JSONObject().put("id",999).put("name","Тест")).put("session",token));assertEquals(token,AccountClient.session(new Store(c)));assertFalse(s.prefs.getString("accountSession","").contains(token));AccountClient.clear(s);assertNull(AccountClient.session(s));}
 @Test public void explicitLoginRegistrationAndRecoveryForms(){Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();c.getSharedPreferences("radar",0).edit().clear().putBoolean("autoUpdate",false).putBoolean("sounds",false).commit();ScreenTest.grantAccess();try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
  onView(withText("Регистрация")).perform(click());onView(withHint("ID 1–999 · пусто = случайный")).perform(typeText("777"));onView(withText("Зарегистрироваться")).perform(click());onView(withText("Пароль от 8 до 128 символов")).check(androidx.test.espresso.assertion.ViewAssertions.matches(isDisplayed()));
  onView(withText("Назад")).perform(click());onView(withText("Вход")).perform(click());onView(withText("Забыл пароль")).perform(click());onView(withHint("Код восстановления · 16 цифр")).check(androidx.test.espresso.assertion.ViewAssertions.matches(isDisplayed()));ScreenTest.capture("account-recovery-form");
 }}
}
