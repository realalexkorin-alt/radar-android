package dev.raf.radar;

import org.junit.Test;
import static org.junit.Assert.*;

public class LogoMotionTest {
  @Test public void stopAndRestartKeepAngleAndIntegrateTheRamp(){
    LogoMotion m=new LogoMotion();m.visible(true,1000);m.active(true,1000);
    m.advance(1500);assertEquals(1,m.level,.0001);assertEquals(-22.5,m.angle,.001);
    m.advance(2500);float angle=m.angle;m.active(false,2500);
    assertEquals(angle,m.angle,.001);assertEquals(1,m.level,.0001);
    m.advance(2750);assertEquals(.5,m.level,.0001);
    m.advance(3000);assertEquals(0,m.level,.0001);assertEquals(angle+22.5,m.angle,.001);assertFalse(m.moving());
    float stopped=m.angle;m.advance(9000);assertEquals(stopped,m.angle,.001);
    m.active(true,9000);assertEquals(stopped,m.angle,.001);m.advance(9500);assertEquals(stopped+22.5,m.angle,.001);
  }
  @Test public void quickReversalAndBackgroundNeverJump(){
    LogoMotion m=new LogoMotion();m.visible(true,1000);m.active(true,1000);m.advance(1250);
    float angle=m.angle,level=m.level;m.active(false,1250);assertEquals(angle,m.angle,.001);assertEquals(level,m.level,.001);
    m.advance(1350);m.active(true,1350);assertEquals(.3,m.level,.001);
    m.visible(false,1400);float paused=m.angle;m.advance(10000);assertEquals(paused,m.angle,.001);
    m.visible(true,20000);assertEquals(paused,m.angle,.001);m.advance(20010);assertTrue(m.angle>paused);
  }
}
