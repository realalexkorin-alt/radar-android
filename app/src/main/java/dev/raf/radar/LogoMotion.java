package dev.raf.radar;

/** Time-integrated sweep: switching or pausing never replaces the current angle. */
final class LogoMotion {
  float angle=-45,level;
  private boolean active,visible;
  private long last;
  void active(boolean value,long now){advance(now);active=value;}
  void visible(boolean value,long now){advance(now);visible=value;last=now;}
  boolean moving(){return active||level>0;}
  void advance(long now){
    long elapsed=Math.max(0,now-last);last=now;if(!visible)return;
    float target=active?1:0;
    float ramp=Math.min(elapsed,Math.abs(target-level)*500f);
    float next=level+Math.signum(target-level)*ramp/500f;
    float travel=(level+next)*.5f*ramp+target*(elapsed-ramp);
    angle=(angle+travel*.09f)%360f;level=Math.max(0,Math.min(1,next));
  }
}
