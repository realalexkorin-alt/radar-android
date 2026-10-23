package dev.raf.radar;
import org.junit.Test;import static org.junit.Assert.*;
public class ReadingAgeTest {
 @Test public void unknownAndElapsedUnits(){
  long now=2000000000000L;
  assertEquals("Нет обновлений",ReadingAge.label(0,now));
  assertEquals("Обновлено 12 с назад",ReadingAge.label(now-12000,now));
  assertEquals("Обновлено 1 м назад",ReadingAge.label(now-60000,now));
  assertEquals("Обновлено 2 ч назад",ReadingAge.label(now-7200000,now));
  assertEquals("Обновлено 5 д назад",ReadingAge.label(now-432000000,now));
  assertEquals("Обновлено 0 с назад",ReadingAge.label(now+1000,now));
 }
}
