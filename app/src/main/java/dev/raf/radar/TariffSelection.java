package dev.raf.radar;
import java.util.*;

/** Point map/alert tariffs and independent widget tariffs share only a data source. */
final class TariffSelection {
  static final List<String> ALL = Collections.unmodifiableList(Arrays.asList(
      "econom", "business", "comfortplus", "vip", "child_tariff", "minivan"));
  static List<String> ordered(Collection<String> values) {
    List<String> result = new ArrayList<>();
    for (String tariff : ALL) if (values.contains(tariff)) result.add(tariff);
    return result;
  }
  static List<String> requested(Point point, Collection<String> widget, boolean widgetSource) {
    Set<String> result = new LinkedHashSet<>(point.tariffs);
    if (widgetSource) result.addAll(widget);
    return ordered(result);
  }
}
