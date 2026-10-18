package dev.raf.radar;

/** One cooldown per point AND tariff. Only fresh, verified RUB amounts enter here. */
public final class AlertPolicy {
  public static final long COOLDOWN = 30 * 60_000L;

  public static boolean shouldNotify(
      boolean enabled,
      int threshold,
      Integer amount,
      boolean fresh,
      long now,
      long lastAt,
      int lastAmount) {
    if (!enabled || !fresh || amount == null || amount < 0 || amount < threshold) return false;
    if (lastAt == 0) return true;
    if (now < lastAt) return false;
    return now - lastAt >= COOLDOWN || amount - lastAmount >= 50;
  }
}
