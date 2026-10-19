package dev.raf.radar;

import java.util.*;

/** A single global budget; adding points or tapping refresh cannot bypass it. */
public final class PollPlanner {
  private final Map<String, Long> due = new HashMap<>();
  private long nextGlobal = 0;
  private int cursor = 0;

  public synchronized String next(List<String> eligible, long now, long interval) {
    return next(eligible, now, interval, null);
  }

  public synchronized String next(List<String> eligible, long now, long interval, String priority) {
    if (now < nextGlobal || eligible.isEmpty()) return null;
    if (priority != null && eligible.contains(priority)
        && now >= due.getOrDefault(priority, 0L)) {
      due.put(priority, now + Math.max(30000, interval));
      nextGlobal = now + 15000;
      return priority;
    }
    for (int n = 0; n < eligible.size(); n++) {
      String id = eligible.get(Math.floorMod(cursor++, eligible.size()));
      if (now >= due.getOrDefault(id, 0L)) {
        due.put(id, now + Math.max(30000, interval));
        nextGlobal = now + 15000;
        return id;
      }
    }
    return null;
  }

  public static boolean needed(Point p, boolean browsing, String overlayPoint) {
    return !p.tariffs.isEmpty()
        && (p.dynamic || p.notify || browsing || p.id.equals(overlayPoint));
  }

  public synchronized void expedite(String id) {
    due.remove(id);
  }

  public synchronized void refresh() {
    due.clear();
  }

  public synchronized void backoff(long now, long delay) {
    nextGlobal = Math.max(nextGlobal, now + delay);
  }
}
