package dev.raf.radar;
import android.content.Context;import androidx.test.platform.app.InstrumentationRegistry;import org.junit.Test;import static org.junit.Assert.*;import java.util.*;
public class TrendTest {
 @Test public void trendUsesOnlyNewSuccessfulReadingsAndSurvivesRestart(){Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();Store s=new Store(c);s.prefs.edit().clear().commit();long now=System.currentTimeMillis();save(s,"point",200,now);assertEquals(0,s.prefs.getInt("trend:point",9));save(s,"point",250,now+1);assertEquals(1,new Store(c).prefs.getInt("trend:point",0));save(s,"point",null,now+2);assertEquals(1,s.prefs.getInt("trend:point",0));save(s,"point",0,now+3);assertEquals(-1,s.prefs.getInt("trend:point",0));save(s,"point",300,now-1);assertEquals(-1,s.prefs.getInt("trend:point",0));save(s,"point",0,now+4);assertEquals(0,s.prefs.getInt("trend:point",9));save(s,"other",500,now+5);assertEquals(0,s.prefs.getInt("trend:other",9));}
 static void save(Store s,String id,Integer rub,long time){s.readings(id,Collections.singletonList(new Store.Reading("econom",rub,null,time,"fixture")));}
}
