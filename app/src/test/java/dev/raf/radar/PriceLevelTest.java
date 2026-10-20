package dev.raf.radar;
import org.junit.Test;
import static org.junit.Assert.*;
public class PriceLevelTest {
  @Test public void exactBoundaries(){
    int[] values={0,99,100,199,200,299,300,399,400,500,9999};
    int[] levels={0,0,1,1,2,2,3,3,4,4,4};
    for(int i=0;i<values.length;i++)assertEquals("rub="+values[i],levels[i],PriceLevel.of(values[i]));
    assertEquals(0,PriceLevel.of(null));assertEquals(0xFFFFFFFF,PriceLevel.color(null));
    assertEquals("Катастрофа",PriceLevel.label(400));
  }
}
