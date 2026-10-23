package dev.raf.radar;
import org.junit.Test;import static org.junit.Assert.*;
public class MapRegionTest {
 @Test public void circleKeepsOneKilometerAtDifferentLatitudesAndAcrossDateline(){for(double lat:new double[]{0,55,80})for(double lon:new double[]{61,179.999})for(int bearing=0;bearing<360;bearing+=15){double[] p=MapRegion.edge(lat,lon,bearing);double a=Math.pow(Math.sin(Math.toRadians(p[0]-lat)/2),2)+Math.cos(Math.toRadians(lat))*Math.cos(Math.toRadians(p[0]))*Math.pow(Math.sin(Math.toRadians(p[1]-lon)/2),2);assertEquals(1000,6371000*2*Math.atan2(Math.sqrt(a),Math.sqrt(1-a)),.001);assertTrue(p[1]>=-180&&p[1]<=180);}}
}
