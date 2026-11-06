package dev.raf.radar;
final class MapVisibility {
 static boolean visible(Store store){long left=store.prefs.getLong("viewingUntil",0)-System.currentTimeMillis();return left>0&&left<=15000;}
}
