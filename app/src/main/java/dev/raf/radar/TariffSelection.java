package dev.raf.radar;
import java.util.*;

/** This release monitors Economy only, including legacy saved points. */
final class TariffSelection {
  static final List<String> ALL=Collections.singletonList("econom");
  static List<String> ordered(Collection<String> values) {
    return values.contains("econom")?ALL:Collections.emptyList();
  }
  static List<String> requested(Point point,Collection<String> widget,boolean widgetSource) { return ALL; }
}
