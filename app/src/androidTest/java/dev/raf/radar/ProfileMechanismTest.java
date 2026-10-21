package dev.raf.radar;
import android.content.*;import android.graphics.*;import android.os.SystemClock;import android.view.*;import android.widget.*;
import androidx.lifecycle.Lifecycle;import androidx.test.core.app.ActivityScenario;import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;import java.util.*;import org.junit.Test;import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;import static androidx.test.espresso.action.ViewActions.*;import static androidx.test.espresso.matcher.ViewMatchers.*;
public class ProfileMechanismTest {
 void setup(boolean name){Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();c.stopService(new Intent(c,MonitorService.class));EconomyPatchTest.waitFor(()->!MonitorService.running);c.getSharedPreferences("radar",0).edit().clear().putBoolean("autoUpdate",false).putBoolean("sounds",false).putBoolean("membersV2",true).putString("profileName",name?"Никита":"").commit();ScreenTest.grantAccess();}
 @Test public void registrationPersistentIdentityAndVisibleOnlyScreenFlag()throws Exception{
  setup(false);String[] id={null};
  try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
   onView(withHint("Ваше имя")).perform(typeText("Nikita"),closeSoftKeyboard());onView(withText("Сохранить")).perform(click());
   s.onActivity(a->{id[0]=PresenceClient.ownId(a.store);assertEquals(64,id[0].length());assertEquals("Nikita",a.store.prefs.getString("profileName",""));assertEquals(View.VISIBLE,a.senseLogo.getVisibility());assertEquals(View.GONE,a.classicBrand.getVisibility());a.store.prefs.edit().putBoolean("keepScreenOn",true).commit();a.syncScreenAwake();assertTrue((a.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)!=0);a.store.prefs.edit().putString("profileName","Новое имя").commit();});
   MainActivity[] ref={null};s.onActivity(a->ref[0]=a);s.moveToState(Lifecycle.State.STARTED);InstrumentationRegistry.getInstrumentation().runOnMainSync(()->assertEquals(0,ref[0].getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
   s.moveToState(Lifecycle.State.RESUMED);s.recreate();s.onActivity(a->{assertEquals(id[0],PresenceClient.ownId(a.store));assertEquals("Новое имя",a.store.prefs.getString("profileName",""));assertTrue((a.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)!=0);});
   onView(withContentDescription("Вкладка Настройки")).perform(click());onView(withContentDescription("Не выключать экран")).perform(scrollTo(),click());s.onActivity(a->assertEquals(0,a.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
  }
 }
 @Test public void mainLogoReversibleIntactMechanismAndFriends()throws Exception{
  setup(true);Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
  try(ActivityScenario<MainActivity>s=ActivityScenario.launch(MainActivity.class)){
   MainActivity[] ref={null};s.onActivity(a->{ref[0]=a;a.presenceEpoch++;a.nextPresenceQuery=Long.MAX_VALUE;assertFalse(a.pro.isClickable());assertFalse(a.senseLogo.inButton(a.senseLogo.getWidth()*.5f,a.senseLogo.getHeight()*.9f));try{SenseSmokeTest.scene("new-sense-closed",a.senseLogo,0);SenseSmokeTest.scene("new-sense-doors",a.senseLogo,.4f);SenseSmokeTest.scene("new-sense-open",a.senseLogo,.65f);SenseSmokeTest.scene("new-sense-working",a.senseLogo,1);}catch(Exception e){throw new AssertionError(e);}SenseSmokeTest.tap(a.senseLogo,SenseLogo.CX,SenseLogo.CY);SenseSmokeTest.tap(a.senseLogo,SenseLogo.CX,SenseLogo.CY);});
   SystemClock.sleep(400);s.onActivity(a->assertFalse(MonitorService.running));
   s.onActivity(a->SenseSmokeTest.tap(a.senseLogo,SenseLogo.CX,SenseLogo.CY));EconomyPatchTest.waitFor(()->MonitorService.running);SystemClock.sleep(2300);s.onActivity(a->{a.refreshCards();assertEquals(1,a.senseLogo.motion.progress,.001);assertTrue(a.senseLogo.animationRunning());});
   SenseSmokeTest.capture("new-sense-map-working");s.moveToState(Lifecycle.State.STARTED);InstrumentationRegistry.getInstrumentation().runOnMainSync(()->assertFalse(ref[0].senseLogo.animationRunning()));s.moveToState(Lifecycle.State.RESUMED);s.onActivity(a->{assertEquals(1,a.senseLogo.motion.progress,.001);SenseSmokeTest.tap(a.senseLogo,SenseLogo.CX,SenseLogo.CY);});EconomyPatchTest.waitFor(()->!MonitorService.running);SystemClock.sleep(1800);s.onActivity(a->{a.refreshCards();assertEquals(0,a.senseLogo.motion.progress,.001);assertEquals(0,a.senseLogo.motion.pro,.001);assertFalse(a.store.prefs.getBoolean("overlay",true));});
   EconomyPatchTest.waitFor(()->ref[0].map!=null);
   s.onActivity(a->{try{a.directoryPending=true;long now=System.currentTimeMillis();PresenceClient.Peer on=new PresenceClient.Peer(new JSONObject().put("id","a".repeat(64)).put("name","Друг онлайн").put("lat",55.16).put("lon",61.40).put("observedAt",now).put("lastSeen",now));PresenceClient.Peer off=new PresenceClient.Peer(new JSONObject().put("id","b".repeat(64)).put("name","Никита офлайн").put("lat",55.17).put("lon",61.41).put("observedAt",now-3600000).put("lastSeen",now-3600000));assertTrue(on.fresh());assertFalse(off.fresh());assertTrue(off.retained());assertTrue(off.status().contains("ч назад"));a.friends=new ArrayList<>(Arrays.asList(on,off));a.peers=new ArrayList<>(a.friends);a.showFriends();assertEquals(2,a.friendsRows.getChildCount());}catch(Exception e){throw new AssertionError(e);}});
   SystemClock.sleep(250);SenseSmokeTest.capture("new-friends-online-offline");s.onActivity(a->{a.friendsRows.getChildAt(1).performClick();assertFalse(a.friendsDialog.isShowing());});
   SystemClock.sleep(500);SenseSmokeTest.capture("new-peer-map-labels");
  }finally{c.stopService(new Intent(c,MonitorService.class));}
 }
}
