package dev.raf.radar;
/** Geodesic circle independent of screen zoom. Fixed 1000 m radius. */
final class MapRegion {
 static final double RADIUS=1000,EARTH=6371000;
 static double[] edge(double lat,double lon,double bearing){double a=RADIUS/EARTH,p=Math.toRadians(lat),l=Math.toRadians(lon),b=Math.toRadians(bearing);double p2=Math.asin(Math.sin(p)*Math.cos(a)+Math.cos(p)*Math.sin(a)*Math.cos(b));double l2=l+Math.atan2(Math.sin(b)*Math.sin(a)*Math.cos(p),Math.cos(a)-Math.sin(p)*Math.sin(p2));return new double[]{Math.toDegrees(p2),((Math.toDegrees(l2)+540)%360)-180};}
}
