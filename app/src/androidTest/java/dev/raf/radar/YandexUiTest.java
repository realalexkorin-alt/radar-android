package dev.raf.radar;
import android.view.*;import android.webkit.*;import android.content.*;
import androidx.test.core.app.ActivityScenario;import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;import static org.junit.Assert.*;
public class YandexUiTest {
 WebView web(View v){if(v instanceof WebView)return (WebView)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){WebView w=web(g.getChildAt(i));if(w!=null)return w;}}return null;}
 @Test public void serviceMapIsNeverShownAfterOfficialLogin(){
  Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
  c.getSharedPreferences("radar",0).edit().clear().putBoolean("economyOnlyV1",true).commit();
  try(ActivityScenario<LoginActivity> s=ActivityScenario.launch(LoginActivity.class)){
   s.onActivity(a->{assertNotNull(BrowserFare.pageObserver);BrowserFare.pageObserver.accept("https://taxi.yandex.ru/");assertEquals(View.GONE,web(a.getWindow().getDecorView()).getVisibility());assertFalse(SettingsPolishTest.contains(a.getWindow().getDecorView(),"Выберите маршрут"));});
   ScreenTest.capture("login-no-map");
  }
 }
 @Test public void failedCheckStaysOnLoginWithoutAResultPage(){
  try(ActivityScenario<LoginActivity> s=ActivityScenario.launch(LoginActivity.class)){
   s.onActivity(a->{a.showResult(false,"fixture");assertNotNull(web(a.getWindow().getDecorView()));assertFalse(SettingsPolishTest.contains(a.getWindow().getDecorView(),"К списку сессий"));});
  }
 }
}
