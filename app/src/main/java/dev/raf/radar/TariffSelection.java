package dev.raf.radar;
import java.util.*;

/** Map filters and point/widget/alert tariffs have separate, explicit scopes. */
final class TariffSelection {
  static final List<String> ALL = Collections.unmodifiableList(Arrays.asList(
      "econom", "business", "comfortplus", "vip", "child_tariff", "minivan"));
  static List<String> ordered(Collection<String> values) {
    List<String> result = new ArrayList<>();
    for (String tariff : ALL) if (values.contains(tariff)) result.add(tariff);
    return result;
  }
  static List<String> requested(Point point, Collection<String> map, boolean browsing) {
    Set<String> result = new LinkedHashSet<>(point.tariffs);
    if (browsing) result.addAll(map);
    return ordered(result);
  }
}
