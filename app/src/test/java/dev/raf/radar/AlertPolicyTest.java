package dev.raf.radar;

import static org.junit.Assert.*;

import org.junit.Test;

public class AlertPolicyTest {
  final long t = 10_000_000;

  @Test
  public void firstThreshold() {
    assertTrue(AlertPolicy.shouldNotify(true, 200, 200, true, t, 0, 0));
  }

  @Test
  public void belowThreshold() {
    assertFalse(AlertPolicy.shouldNotify(true, 200, 199, true, t, 0, 0));
  }

  @Test
  public void noSpam() {
    assertFalse(AlertPolicy.shouldNotify(true, 200, 249, true, t + 1000, t, 200));
  }

  @Test
  public void fiftyRise() {
    assertTrue(AlertPolicy.shouldNotify(true, 200, 250, true, t + 1000, t, 200));
  }

  @Test
  public void cooldownBoundary() {
    assertFalse(AlertPolicy.shouldNotify(true, 200, 200, true, t + 1799999, t, 200));
    assertTrue(AlertPolicy.shouldNotify(true, 200, 200, true, t + 1800000, t, 200));
  }

  @Test
  public void fallAndRiseDuringPause() {
    assertFalse(AlertPolicy.shouldNotify(true, 200, 210, true, t + 1000, t, 250));
  }

  @Test
  public void staleUnknownDisabled() {
    assertFalse(AlertPolicy.shouldNotify(true, 200, null, true, t, 0, 0));
    assertFalse(AlertPolicy.shouldNotify(true, 200, 300, false, t, 0, 0));
    assertFalse(AlertPolicy.shouldNotify(false, 200, 300, true, t, 0, 0));
  }

  @Test
  public void clockMovedBack() {
    assertFalse(AlertPolicy.shouldNotify(true, 200, 300, true, t - 1, t, 200));
  }

  @Test
  public void nextRiseIsRelativeToLastSent() {
    assertFalse(AlertPolicy.shouldNotify(true, 200, 299, true, t + 1000, t, 250));
    assertTrue(AlertPolicy.shouldNotify(true, 200, 300, true, t + 1000, t, 250));
  }
}
