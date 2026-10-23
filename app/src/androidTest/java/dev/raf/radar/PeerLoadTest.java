package dev.raf.radar;
import android.content.*;import androidx.test.core.app.ActivityScenario;import androidx.test.platform.app.InstrumentationRegistry;
import java.util.*;import org.json.*;import org.junit.Test;import static org.junit.Assert.*;
public class PeerLoadTest {
 @Test public void cacheSurvivesActivityRecreationAndFailedRefresh()throws Exception{
  Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();c.getSharedPreferences("radar",0).edit().clear().putBoolean("autoUpdate",false).putBoolean("sounds",false).commit();Store s=new Store(c);
  PresenceClient.Peer p=new PresenceClient.Peer(new JSONObject().put("id","fixture-peer").put("name","Друг").put("lat",55.16).put("lon",61.4).put("observedAt",System.currentTimeMillis()-3600000).put("lastSeen",System.currentTimeMillis()-180000).put("offline",true));
  PresenceClient.cache(s,Collections.singletonList(p));assertEquals(1,PresenceClient.cached(new Store(c)).size());ScreenTest.grantAccess();
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   scenario.onActivity(a->{if(a.nameDialog!=null)a.nameDialog.dismiss();assertEquals("Друг",a.friends.get(0).name);a.directoryPending=true;a.showFriends();a.directoryError=true;a.directoryPending=false;a.nextDirectory=Long.MAX_VALUE;a.renderFriends();assertEquals(2,a.friendsRows.getChildCount());assertTrue(a.friends.get(0).locationAge().contains("1 ч"));assertTrue(a.friendsDialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).isEnabled());});
   ScreenTest.capture("friends-cached-offline-refresh");scenario.recreate();scenario.onActivity(a->assertEquals("Друг",a.friends.get(0).name));
  }
 }
 @Test public void twoAndroidHttpsClientsAppearAndWithdraw()throws Exception{
  String a=UUID.randomUUID().toString().replace("-","")+UUID.randomUUID().toString().replace("-","");String b=UUID.randomUUID().toString().replace("-","")+UUID.randomUUID().toString().replace("-","");long rev=System.currentTimeMillis();String endpoint=PresenceClient.ENDPOINT;
  String query=endpoint+"?west=-123.45679&south=-47.12346&east=-123.45677&north=-47.12344";
  try{
   JSONObject body=new JSONObject().put("name","Проверка связи").put("lat",-47.12345).put("lon",-123.45678).put("accuracy",10).put("observedAt",System.currentTimeMillis()).put("revision",++rev);
   String first=new JSONObject(PresenceClient.request("PUT",endpoint,body,a)).getString("id");body.put("revision",++rev).put("observedAt",System.currentTimeMillis());String second=new JSONObject(PresenceClient.request("PUT",endpoint,body,b)).getString("id");
   JSONArray rows=new JSONObject(PresenceClient.request("GET",query,null,null)).getJSONArray("points");Set<String> ids=new HashSet<>();for(int i=0;i<rows.length();i++){PresenceClient.Peer p=new PresenceClient.Peer(rows.getJSONObject(i));ids.add(p.id);}assertTrue(ids.contains(first));assertTrue(ids.contains(second));
   PresenceClient.request("POST",endpoint,new JSONObject().put("revision",++rev).put("offline",true),b);
   rows=new JSONObject(PresenceClient.request("GET",query,null,null)).getJSONArray("points");boolean retained=false;for(int i=0;i<rows.length();i++){PresenceClient.Peer p=new PresenceClient.Peer(rows.getJSONObject(i));if(p.id.equals(second))retained=p.offline&&p.retained();}assertTrue(retained);
  }finally{try{PresenceClient.request("DELETE",endpoint,new JSONObject().put("revision",++rev),a);}finally{PresenceClient.request("DELETE",endpoint,new JSONObject().put("revision",++rev),b);}}
 }
}
