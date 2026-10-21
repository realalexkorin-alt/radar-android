package dev.raf.radar;

/** One reversible mechanical timeline; the beam angle is integrated, never substituted. */
final class SenseMotion {
  float progress,pro,angle=-45;
  private boolean running,visible;
  private long last;
  void bind(boolean active,boolean shown,long now){
    advance(now);
    // Returning to the map shows the actual service state without replaying startup.
    if(!shown||!visible){progress=active?1:0;pro=progress;}
    running=active;visible=shown;last=now;
  }
  void advance(long now){
    float dt=Math.max(0,now-last);last=now;if(!visible)return;
    float oldSpeed=speed();
    progress=approach(progress,running?1:0,dt/(running?5000f:1500f));
    pro=approach(pro,running?1:0,dt/500f);
    angle=(angle+dt*.09f*(oldSpeed+speed())*.5f)%360f;
  }
  boolean needsFrames(){return visible&&(running||progress>0||pro>0);}
  float speed(){return smooth(progress,.8f,1);}
  static float smooth(float value,float start,float end){float t=Math.max(0,Math.min(1,(value-start)/(end-start)));return t*t*(3-2*t);}
  private static float approach(float value,float target,float amount){return target>value?Math.min(target,value+amount):Math.max(target,value-amount);}
}
