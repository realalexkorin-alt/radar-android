package dev.raf.radar;

/** Age of the last successful surcharge response, independent of GPS freshness. */
final class ReadingAge {
  static String label(long updated,long now){
    if(updated<=0)return "Нет обновлений";
    long age=Math.max(0,(now-updated)/1000);String unit="с";
    if(age>=86400){age/=86400;unit="д";}else if(age>=3600){age/=3600;unit="ч";}else if(age>=60){age/=60;unit="м";}
    return "Обновлено "+age+" "+unit+" назад";
  }
}
