package dev.raf.radar;
import android.content.*;import android.view.*;import androidx.test.core.app.ActivityScenario;import androidx.test.platform.app.InstrumentationRegistry;import org.json.*;import org.junit.Test;import static org.junit.Assert.*;
public class StatisticsDisplayTest {
 JSONObject fixture(String mode)throws Exception{JSONArray slots=new JSONArray();int count=mode.equals("day")?24:mode.equals("week")?7:31;for(int i=0;i<count;i++)slots.put(new JSONObject().put("index",i).put("actual",i%9==8?JSONObject.NULL:Math.round(100+60*Math.sin(i*.7))).put("historical",Math.round(90+40*Math.cos(i*.6))).put("samples",12).put("historyDays",2));return new JSONObject().put("slots",slots).put("areas",new JSONArray().put("2").put("3")).put("timezone","Asia/Yekaterinburg");}
 @Test public void chartsBothThemesAndHiddenByDefault()throws Exception{
  Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();Store s=new Store(c);s.prefs.edit().clear().putBoolean("sounds",false).putBoolean("lineageSounds",false).putBoolean("autoUpdate",false).commit();ScreenTest.grantAccess();
  AccountClient.save(s,new JSONObject().put("user",new JSONObject().put("id",999).put("name","Проверка отображения")).put("session","a".repeat(64)));
  AccountClient.transport=(body,token)->new JSONObject().put("friends",new JSONArray()).put("incoming",new JSONArray());StatisticsPanel.transport=(q,t)->fixture(q.contains("mode=week")?"week":q.contains("mode=month")?"month":"day");
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   scenario.onActivity(a->{assertEquals(3,a.navigation.getChildCount());assertEquals(View.GONE,a.statisticsPanel.getVisibility());a.store.prefs.edit().putBoolean("experimentalStatistics",true).commit();a.rebuildNavigation();assertEquals(4,a.navigation.getChildCount());assertEquals(3,a.navigation.getChildAt(2).getTag());});
   for(String theme:new String[]{Appearance.STANDARD,Appearance.LINEAGE})for(String mode:new String[]{"day","week","month"}){
    scenario.onActivity(a->{a.selectTheme(theme);a.showTab(3);a.statisticsPanel.mode=mode;a.statisticsPanel.data=null;a.statisticsPanel.render();a.statisticsPanel.load(true);});
    ScreenTest.capture("statistics-"+theme+"-"+mode);
    scenario.onActivity(a->{assertEquals(View.VISIBLE,a.statisticsPanel.getVisibility());assertTrue(a.statisticsPanel.chart.getWidth()>0);assertNotNull(a.statisticsPanel.data);assertTrue(a.statisticsPanel.status.getText().toString().contains("Наблюдений"));});
   }
   scenario.onActivity(a->{a.statisticsPanel.data=new JSONObject();a.statisticsPanel.update();});ScreenTest.capture("statistics-empty");
   scenario.onActivity(a->{a.showTab(2);a.store.prefs.edit().putBoolean("experimentalStatistics",false).commit();a.rebuildNavigation();assertEquals(3,a.navigation.getChildCount());});
  }finally{StatisticsPanel.transport=StatisticsPanel::request;AccountClient.transport=AccountClient::request;AccountClient.clear(s);}
 }
}
