package dev.raf.radar;

import org.junit.Test;
import static org.junit.Assert.*;

public class SenseMotionTest {
  @Test public void fiveSecondStartAndIndependentRealServiceIndicator(){
    SenseMotion m=new SenseMotion();m.bind(false,true,1000);m.bind(true,true,1000);
    assertEquals(0,m.progress,.0001);assertEquals(0,m.pro,.0001);
    m.advance(1500);assertEquals(.1,m.progress,.0001);assertEquals(1,m.pro,.0001);
    m.advance(2000);assertEquals(.2,m.progress,.0001);m.advance(3500);assertEquals(.5,m.progress,.0001);assertEquals(0,m.speed(),.0001);
    m.advance(5000);assertEquals(.8,m.progress,.0001);m.advance(6000);assertEquals(1,m.progress,.0001);assertEquals(1,m.speed(),.0001);
    float angle=m.angle;m.bind(false,true,6000);assertEquals(angle,m.angle,.0001);
    m.advance(6750);assertEquals(.5,m.progress,.0001);assertEquals(0,m.pro,.0001);
    m.advance(7500);assertEquals(0,m.progress,.0001);assertFalse(m.needsFrames());
    angle=m.angle;m.advance(20000);assertEquals(angle,m.angle,.0001);
  }
  @Test public void fastReversalKeepsGeometryAndBeamPosition(){
    SenseMotion m=new SenseMotion();m.bind(false,true,1000);m.bind(true,true,1000);m.advance(3000);
    float p=m.progress,angle=m.angle;m.bind(false,true,3000);assertEquals(p,m.progress,.0001);assertEquals(angle,m.angle,.0001);
    m.advance(3300);assertEquals(.2,m.progress,.0001);p=m.progress;m.bind(true,true,3300);assertEquals(p,m.progress,.0001);
    m.advance(4300);assertEquals(.4,m.progress,.0001);
  }
  @Test public void returnShowsCurrentStateWithoutReplayingOrAdvancingHiddenBeam(){
    SenseMotion m=new SenseMotion();m.bind(false,true,1000);m.bind(true,true,1000);m.advance(2000);
    m.bind(true,false,2000);float angle=m.angle;m.advance(20000);assertEquals(angle,m.angle,.0001);assertFalse(m.needsFrames());
    m.bind(true,true,20000);assertEquals(1,m.progress,.0001);assertEquals(1,m.pro,.0001);assertEquals(angle,m.angle,.0001);
    m.bind(false,false,21000);assertEquals(0,m.progress,.0001);assertEquals(0,m.pro,.0001);
    m.bind(false,true,22000);assertFalse(m.needsFrames());
  }
  @Test public void failedOrExternallyStoppedServiceCannotLeaveGreenIndicator(){
    SenseMotion m=new SenseMotion();m.bind(false,true,1000);m.advance(10000);assertEquals(0,m.progress,.0001);assertEquals(0,m.pro,.0001);
    m.bind(true,true,10000);m.advance(10500);m.bind(false,true,10500);m.advance(11000);assertEquals(0,m.pro,.0001);assertEquals(0,m.progress,.0001);
  }
}
