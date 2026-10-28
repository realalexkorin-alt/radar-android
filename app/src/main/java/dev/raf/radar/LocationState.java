package dev.raf.radar;

import android.location.Location;
import android.os.SystemClock;
import java.util.Collections;

/** Shared foreground/service GPS cache; the fix's original age is preserved. */
final class LocationState {
  static boolean fresh(Store s) {
    long age=System.currentTimeMillis()-s.prefs.getLong("gpsAt",0);
    return s.prefs.getLong("gpsAt",0)>0 && age>=0 && age<120000;
  }
  static boolean readingCurrent(Store s){
    if(!s.prefs.contains("readQueryLat"))return false;float[] distance=new float[1];
    Location.distanceBetween(Double.longBitsToDouble(s.prefs.getLong("readQueryLat",0)),Double.longBitsToDouble(s.prefs.getLong("readQueryLon",0)),Double.longBitsToDouble(s.prefs.getLong("lat",0)),Double.longBitsToDouble(s.prefs.getLong("lon",0)),distance);
    return distance[0]<=250;
  }
  static synchronized boolean accept(Store s, Location l) {
    long nanos=SystemClock.elapsedRealtimeNanos()-l.getElapsedRealtimeNanos();
    if(nanos<0 || nanos>=120_000_000_000L){Diagnostics.once("gps","fix_rejected_age",false);return false;}
    if(!l.hasAccuracy() || l.getAccuracy()>500 || l.getAccuracy()<0
        || !Double.isFinite(l.getLatitude()) || !Double.isFinite(l.getLongitude())
        || Math.abs(l.getLatitude())>90 || Math.abs(l.getLongitude())>180){Diagnostics.once("gps","fix_rejected_quality",false);return false;}
    long at=System.currentTimeMillis()-nanos/1_000_000L;
    if(at<s.prefs.getLong("gpsAt",0))return false;
    if(s.prefs.getLong("gpsAt",0)>0){
      float[] distance=new float[1];Location.distanceBetween(Double.longBitsToDouble(s.prefs.getLong("lat",0)),
          Double.longBitsToDouble(s.prefs.getLong("lon",0)),l.getLatitude(),l.getLongitude(),distance);
      long delta=at-s.prefs.getLong("gpsAt",0);float previous=s.prefs.getFloat("accuracy",500);
      // Prefer a recent precise fix over overlapping low-accuracy network jitter.
      if(delta<30000&&l.getAccuracy()>Math.max(35,previous*2)&&distance[0]<previous+l.getAccuracy())return false;
      // Ignore teleportation outside both uncertainty circles; reacquire after a long gap.
      if(delta<120000&&distance[0]>previous+l.getAccuracy()+Math.max(100,delta/1000f*85))return false;
      if(distance[0]>250)s.readings("me",Collections.emptyList());
    }
    s.prefs.edit().putLong("lat",Double.doubleToLongBits(l.getLatitude()))
        .putLong("lon",Double.doubleToLongBits(l.getLongitude())).putLong("gpsAt",at).putFloat("accuracy",l.getAccuracy()).apply();
    if(s.prefs.contains("readQueryLat")&&!readingCurrent(s)&&!s.readings("me").isEmpty())s.readings("me",Collections.emptyList());
    return true;
  }
}
