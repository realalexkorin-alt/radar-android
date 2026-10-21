package dev.raf.radar;
import org.junit.Test;
import static org.junit.Assert.*;
public class SenseMotionTest {
 @Test public void audioLengthAndRealServiceIndicator(){
  SenseMotion m=new SenseMotion();m.bind(false,true,1000);m.bind(true,true,1000);
  m.advance(1500);assertEquals(500/1985.25f,m.progress,.0001);assertEquals(1,m.pro,.0001);assertEquals(0,m.speed(),.0001);
  m.advance(2986);assertEquals(1,m.progress,.0001);assertEquals(1,m.speed(),.0001);
  float angle=m.angle;m.bind(false,true,2986);assertEquals(angle,m.angle,.0001);m.advance(4486);assertEquals(0,m.progress,.0001);assertFalse(m.needsFrames());
 }
 @Test public void reversalPreservesCurrentGeometry(){
  SenseMotion m=new SenseMotion();m.bind(false,true,1000);m.bind(true,true,1000);m.advance(2000);float p=m.progress,a=m.angle;
  m.bind(false,true,2000);assertEquals(p,m.progress,0);assertEquals(a,m.angle,0);m.advance(2200);assertTrue(m.progress<p);
  p=m.progress;m.bind(true,true,2200);assertEquals(p,m.progress,0);m.advance(2400);assertTrue(m.progress>p);
 }
 @Test public void hiddenAndReturnDoNotReplay(){
  SenseMotion m=new SenseMotion();m.bind(false,true,1000);m.bind(true,true,1000);m.advance(1500);m.bind(true,false,1500);float a=m.angle;m.advance(10000);assertFalse(m.needsFrames());assertEquals(a,m.angle,0);
  m.bind(true,true,10000);assertEquals(1,m.progress,0);assertEquals(a,m.angle,0);
 }
 @Test public void failureCannotLeaveGreen(){
  SenseMotion m=new SenseMotion();m.bind(false,true,1000);m.bind(true,true,1000);m.advance(1500);m.bind(false,true,1500);m.advance(3000);assertEquals(0,m.progress,0);assertEquals(0,m.pro,0);assertFalse(m.needsFrames());
 }
}
