package dev.raf.radar;
import java.time.*;
import java.time.format.DateTimeFormatter;
/** Server Retry-After takes precedence over the normal ten-second interval. */
final class RateLimitPolicy {
 static long delay(String value,long now,int failures){
  if(value!=null)try{
   String v=value.trim();
   long delay=v.matches("[0-9]{1,9}")?Math.multiplyExact(Long.parseLong(v),1000):ZonedDateTime.parse(v,DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()-now;
   if(delay>0)return Math.max(1000,delay);
  }catch(Exception ignored){}
  return 10000;
 }
}
