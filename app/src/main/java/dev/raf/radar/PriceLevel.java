package dev.raf.radar;

/** Ruble surcharge boundaries shared by map prices and the overlay. */
final class PriceLevel {
  static int of(Integer rub) { return rub==null||rub<100?0:rub<200?1:rub<300?2:rub<400?3:4; }
  static int color(Integer rub) { return new int[]{0xFFFFFFFF,0xFF2488FF,0xFFB43DFF,0xFFFFCB38,0xFFFF2438}[of(rub)]; }
  static String label(Integer rub) { return new String[]{"Обычный","Редкий","Эпик","Легендарный","Катастрофа"}[of(rub)]; }
}
