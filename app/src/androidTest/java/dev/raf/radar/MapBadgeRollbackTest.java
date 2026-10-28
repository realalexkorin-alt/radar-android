package dev.raf.radar;
import android.content.*;import android.graphics.*;import android.view.*;import android.widget.*;
import androidx.test.core.app.ActivityScenario;import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;import org.junit.Test;import static org.junit.Assert.*;
public class MapBadgeRollbackTest {
 @Test public void oldBadgesFitAgeAndIgnoreForecastInBothThemes()throws Exception{
  Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();c.stopService(new Intent(c,MonitorService.class));
  for(String theme:new String[]{Appearance.STANDARD,Appearance.LINEAGE}){
   Store store=new Store(c);store.prefs.edit().clear().putBoolean("economyOnlyV1",true).putBoolean("dark",true).putBoolean("autoUpdate",false).putBoolean("sounds",false).putString(Appearance.KEY,theme).commit();
   AccountClient.save(store,new JSONObject().put("user",new JSONObject().put("id",999).put("name","Проверка")).put("session","a".repeat(64)));
   AccountClient.transport=(body,token)->new JSONObject().put("friends",new JSONArray()).put("incoming",new JSONArray());ScreenTest.grantAccess();
   try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
    scenario.onActivity(a->{
     LinearLayout examples=a.column();examples.setPadding(a.dp(24),a.dp(24),a.dp(24),a.dp(24));examples.setBackgroundColor(Appearance.lineage(a)?Appearance.INK:RadarUi.BACKGROUND);
     examples.addView(a.heading("Точки · без стрелок",24));
     for(int value:new int[]{0,80,2500}){
      Point p=new Point("fixture","Точка",55,61,false);a.store.readings(p.id,java.util.Collections.singletonList(new Store.Reading("econom",value,null,System.currentTimeMillis()-65000,"fixture")));
      a.store.prefs.edit().putBoolean("mapPointUpdateTime",true).putBoolean("forecast",false).commit();Bitmap before=a.pointIcon(p);
      a.store.prefs.edit().putBoolean("forecast",true).putInt("trend:"+p.id,1).commit();Bitmap after=a.pointIcon(p);
      assertTrue(before.sameAs(after));
      Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setTextSize(a.dp(9));assertTrue(before.getWidth()>=paint.measureText(a.pointAge(p))+a.dp(16)-1);
      ImageView image=new ImageView(a);image.setImageBitmap(after);image.setScaleType(ImageView.ScaleType.CENTER);examples.addView(image,new LinearLayout.LayoutParams(-1,a.dp(90)));before.recycle();
     }
     a.setContentView(examples);
    });ScreenTest.capture("map-badges-"+theme);
   }finally{AccountClient.transport=AccountClient::request;}
  }
 }
}
