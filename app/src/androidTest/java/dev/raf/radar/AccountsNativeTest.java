package dev.raf.radar;
import org.json.*;import org.junit.Test;import static org.junit.Assert.*;import java.util.UUID;
public class AccountsNativeTest {
 @Test public void androidPrivateFriendExchangeAndCleanup()throws Exception{
  String password=UUID.randomUUID().toString();JSONObject a=null,b=null;
  try{
   a=AccountClient.request(new JSONObject().put("action","register").put("name","Android synthetic A").put("password",password),null);b=AccountClient.request(new JSONObject().put("action","register").put("name","Android synthetic B").put("password",password),null);
   String at=a.getString("session"),bt=b.getString("session");int aid=a.getJSONObject("user").getInt("id"),bid=b.getJSONObject("user").getInt("id");
   AccountClient.request(new JSONObject().put("action","request").put("target",bid),at);JSONObject state=AccountClient.request(AccountClient.action("poll"),bt);assertEquals(1,state.getJSONArray("incoming").length());assertEquals(0,state.getJSONArray("friends").length());
   AccountClient.request(new JSONObject().put("action","accept").put("target",aid),bt);
   AccountClient.request(new JSONObject().put("action","position").put("lat",-47.12345).put("lon",-123.45678).put("accuracy",8).put("observedAt",System.currentTimeMillis()),at);
   state=AccountClient.request(AccountClient.action("poll"),bt);assertEquals(-47.12345,state.getJSONArray("friends").getJSONObject(0).getDouble("lat"),0.000001);assertTrue(AccountClient.peers(state).get(0).hasLocation);
   AccountClient.request(AccountClient.action("offline"),at);state=AccountClient.request(AccountClient.action("poll"),bt);assertTrue(AccountClient.peers(state).get(0).offline);assertTrue(AccountClient.peers(state).get(0).retained());
   AccountClient.request(new JSONObject().put("action","remove").put("target",aid),bt);assertEquals(0,AccountClient.request(AccountClient.action("poll"),bt).getJSONArray("friends").length());
  }finally{try{if(a!=null)AccountClient.request(new JSONObject().put("action","delete-account").put("password",password),a.getString("session"));}finally{if(b!=null)AccountClient.request(new JSONObject().put("action","delete-account").put("password",password),b.getString("session"));}}
 }
}
