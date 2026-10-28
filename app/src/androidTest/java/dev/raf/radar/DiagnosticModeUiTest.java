package dev.raf.radar;
import android.app.*;import android.content.*;import android.view.*;import android.widget.*;import androidx.test.core.app.ActivityScenario;import androidx.test.platform.app.InstrumentationRegistry;import org.junit.Test;import static org.junit.Assert.*;
public class DiagnosticModeUiTest {
 View toggle(View v){if("Режим диагностики".contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=toggle(((ViewGroup)v).getChildAt(i));if(found!=null)return found;}return null;}
 @Test public void modeAndNotificationsWorkInBothThemes()throws Exception{
  Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();Store store=new Store(c);ScreenTest.grantAccess();
  for(String theme:new String[]{Appearance.STANDARD,Appearance.LINEAGE}){
   store.prefs.edit().putString(Appearance.KEY,theme).putBoolean("autoUpdate",false).putBoolean("sounds",false).putBoolean("lineageSounds",false).putBoolean(DiagnosticMode.KEY,false).putInt("accountId",999).putString("profileName","Fixture").commit();
   try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
    scenario.onActivity(a->{a.showTab(2);a.rebuildSettings();View mode=toggle(a.settingsPanel);assertNotNull(mode);mode.performClick();assertTrue(DiagnosticMode.enabled());assertTrue(SettingsPolishTest.contains(a.settingsPanel,"Журнал диагностики"));assertTrue(SettingsPolishTest.contains(a.settingsPanel,"Отправить диагностику"));});
    Diagnostics.worker.submit(()->{DiagnosticMode.notified.clear();DiagnosticMode.alert("taxi","request_failed","error",0);assertTrue(java.util.Arrays.stream(c.getSystemService(NotificationManager.class).getActiveNotifications()).anyMatch(n->"diagnostics".equals(n.getTag())));}).get(10,java.util.concurrent.TimeUnit.SECONDS);
    scenario.onActivity(a->{ScrollView scroll=(ScrollView)a.settingsPanel.getChildAt(1);scroll.fullScroll(View.FOCUS_DOWN);});ScreenTest.capture("diagnostic-mode-"+theme);
    scenario.onActivity(a->{toggle(a.settingsPanel).performClick();assertFalse(DiagnosticMode.enabled());});
    assertFalse(java.util.Arrays.stream(c.getSystemService(NotificationManager.class).getActiveNotifications()).anyMatch(n->"diagnostics".equals(n.getTag())));
   }
  }
 }
}
