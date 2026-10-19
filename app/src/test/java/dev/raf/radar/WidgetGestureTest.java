package dev.raf.radar;
import static org.junit.Assert.*;
import org.junit.Test;

public class WidgetGestureTest {
  private WidgetGesture gesture() { return new WidgetGesture(8, 500); }
  @Test public void tap() {
    WidgetGesture g = gesture(); g.down(10, 10, 1000);
    assertEquals(WidgetGesture.TAP, g.up(12, 11, 1100));
  }
  @Test public void holdOpensSettingsOnlyOnRelease() {
    WidgetGesture g = gesture(); g.down(10, 10, 1000);
    assertFalse(g.move(11, 11, 1700));
    assertEquals(WidgetGesture.SETTINGS, g.up(11, 11, 1800));
  }
  @Test public void holdAndDragNeverOpensSettings() {
    WidgetGesture g = gesture(); g.down(10, 10, 1000);
    assertTrue(g.move(80, 90, 1600));
    assertEquals(WidgetGesture.DRAG, g.up(80, 90, 1700));
  }
  @Test public void quickSwipeDoesNotOpenApp() {
    WidgetGesture g = gesture(); g.down(10, 10, 1000);
    assertFalse(g.move(80, 90, 1100));
    assertEquals(WidgetGesture.NONE, g.up(80, 90, 1200));
  }
  @Test public void cancelledHoldDoesNothing() {
    WidgetGesture g = gesture(); g.down(10, 10, 1000); g.cancel();
    assertEquals(WidgetGesture.NONE, g.up(10, 10, 1800));
  }
  @Test public void returnAfterDragStillDoesNotClick() {
    WidgetGesture g = gesture(); g.down(10, 10, 1000); g.move(80, 90, 1600);
    assertEquals(WidgetGesture.DRAG, g.up(10, 10, 1700));
  }
  @Test public void nextTouchHasIndependentState() {
    WidgetGesture g = gesture(); g.down(0, 0, 1000); g.move(80, 90, 1600);
    g.up(80, 90, 1700); g.down(10, 10, 2000);
    assertEquals(WidgetGesture.TAP, g.up(10, 10, 2100));
  }
}
