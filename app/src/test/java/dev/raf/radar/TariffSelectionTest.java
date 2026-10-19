package dev.raf.radar;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class TariffSelectionTest {
  @Test public void widgetSourceFetchesWidgetTariffsWithoutChangingPointSelection(){
    Point point=new Point("p","p",55,61,false);point.tariffs=Collections.singletonList("econom");
    assertEquals(Arrays.asList("econom","vip"),TariffSelection.requested(point,Collections.singleton("vip"),true));
    assertEquals(Collections.singletonList("econom"),point.tariffs);
    assertEquals(Collections.singletonList("econom"),TariffSelection.requested(point,Collections.singleton("vip"),false));
  }
  @Test public void otherPointsDoNotFetchWidgetTariffs(){
    Point point=new Point("p","p",55,61,false);point.tariffs=Arrays.asList("minivan","comfortplus");
    assertEquals(Arrays.asList("business","comfortplus","minivan"),TariffSelection.requested(point,Collections.singleton("business"),true));
    assertEquals(Arrays.asList("comfortplus","minivan"),TariffSelection.requested(point,Collections.singleton("business"),false));
  }
  @Test public void orderIsEconomComfortComfortPlusThenRemainingTariffs(){
    assertEquals(Arrays.asList("econom","business","comfortplus","vip"),TariffSelection.ordered(Arrays.asList("vip","comfortplus","bad","business","econom","econom")));
  }
}
