package dev.raf.radar;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class TariffSelectionTest {
  @Test public void legacyPointAndWidgetNeverRequestOtherTariffs(){
    Point point=new Point("p","p",55,61,false);point.tariffs=Arrays.asList("vip","business");
    assertEquals(Collections.singletonList("econom"),TariffSelection.requested(point,Collections.singleton("minivan"),true));
    assertEquals(Collections.singletonList("econom"),TariffSelection.requested(point,Collections.emptyList(),false));
  }
  @Test public void persistedPointKeepsPropertiesAndMovesToEconomy() throws Exception {
    Point point=new Point("p","Дом",55,61,false);point.tariffs=Collections.singletonList("vip");point.address="Адрес";point.threshold=350;point.notify=true;point.enabled=false;
    Point restored=Point.from(point.json());assertEquals(Collections.singletonList("econom"),restored.tariffs);
    assertEquals("Дом",restored.name);assertEquals("Адрес",restored.address);assertEquals(350,restored.threshold);assertTrue(restored.notify);assertFalse(restored.enabled);
  }
}
