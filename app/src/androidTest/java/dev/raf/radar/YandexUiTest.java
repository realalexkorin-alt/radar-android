package dev.raf.radar;
import android.view.*;import android.webkit.*;import android.content.*;
import androidx.test.core.app.ActivityScenario;import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;import static org.junit.Assert.*;
public class YandexUiTest {
 WebView web(View v){if(v instanceof WebView)return (WebView)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){WebView w=web(g.getChildAt(i));if(w!=null)return w;}}return null;}
 @Test public void signInHasOwnBrowserAndAVisibleReturnPanel(){
  Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
  c.getSharedPreferences("radar",0).edit().clear().putBoolean("economyOnlyV1",true).commit();
  try(ActivityScenario<LoginActivity> s=ActivityScenario.launch(LoginActivity.class)){
   s.onActivity(a->{
    assertNotSame(BrowserFare.view(a),web(a.getWindow().getDecorView()));
    a.authPage("https://taxi.yandex.ru/",false);
    assertEquals(View.INVISIBLE,web(a.getWindow().getDecorView()).getVisibility());
    assertTrue(SettingsPolishTest.contains(a.getWindow().getDecorView(),"Аккаунт выбран"));
   });ScreenTest.capture("login-return-panel");
  }
 }
 @Test public void failedCheckAllowsImmediateRetryWithoutMinuteTimer(){
  Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();Store store=new Store(c);
  store.prefs.edit().putLong("yandexTestAt",System.currentTimeMillis()).commit();
  try(ActivityScenario<LoginActivity> s=ActivityScenario.launch(LoginActivity.class)){
   s.onActivity(a->{
    a.showResult(false,"fixture");a.probe();
    assertFalse(SettingsPolishTest.contains(a.getWindow().getDecorView(),"Повторная проверка будет доступна"));
    assertFalse(SettingsPolishTest.contains(a.getWindow().getDecorView(),"К списку сессий"));
   });
  }
 }
}
