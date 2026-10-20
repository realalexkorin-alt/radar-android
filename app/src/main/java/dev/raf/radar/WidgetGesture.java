package dev.raf.radar;

/** A hold resolves on release, so a drag never opens settings. */
public final class WidgetGesture {
  public static final int NONE = 0, TAP = 1, SETTINGS = 2, DRAG = 3;
  private final float slop;
  private final long hold;
  private float x, y;
  private long started;
  private boolean active, displaced, dragging;
  public WidgetGesture(float slop, long hold) { this.slop = slop; this.hold = hold; }
  public void down(float x, float y, long time) {
    this.x = x; this.y = y; started = time;
    active = true; displaced = false; dragging = false;
  }
  public boolean move(float x, float y, long time) {
    if (!active) return false;
    if (Math.hypot(x - this.x, y - this.y) > slop) displaced = true;
    if (displaced) dragging = true;
    return dragging;
  }
  public int up(float x, float y, long time) {
    if (!active) return NONE;
    move(x, y, time); active = false;
    if (dragging) return DRAG;
    if (displaced || time < started) return NONE;
    return time - started >= hold ? SETTINGS : TAP;
  }
  public void cancel() { active = false; }
}
