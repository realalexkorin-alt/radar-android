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
  static boolean accept(Store s, Location l) {
    long nanos=SystemClock.elapsedRealtimeNanos()-l.getElapsedRealtimeNanos();
    if(nanos<0 || nanos>=120_000_000_000L || !l.hasAccuracy() || l.getAccuracy()>500 || l.getAccuracy()<0
        || !Double.isFinite(l.getLatitude()) || !Double.isFinite(l.getLongitude()))return false;
    long at=System.currentTimeMillis()-nanos/1_000_000L;
    if(at<s.prefs.getLong("gpsAt",0))return false;
    if(s.prefs.getLong("gpsAt",0)>0){
      float[] distance=new float[1];Location.distanceBetween(Double.longBitsToDouble(s.prefs.getLong("lat",0)),
          Double.longBitsToDouble(s.prefs.getLong("lon",0)),l.getLatitude(),l.getLongitude(),distance);
      if(distance[0]>250)s.readings("me",Collections.emptyList());
    }
    s.prefs.edit().putLong("lat",Double.doubleToLongBits(l.getLatitude()))
        .putLong("lon",Double.doubleToLongBits(l.getLongitude())).putLong("gpsAt",at).putFloat("accuracy",l.getAccuracy()).apply();
    return true;
  }
}
