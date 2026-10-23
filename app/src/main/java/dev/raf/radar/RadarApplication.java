package dev.raf.radar;
public final class RadarApplication extends android.app.Application {
 @Override public void onCreate(){super.onCreate();Diagnostics.init(this);}
}
