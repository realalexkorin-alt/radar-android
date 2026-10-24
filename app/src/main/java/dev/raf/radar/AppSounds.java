package dev.raf.radar;

import android.content.Context;
import android.media.*;
import android.os.*;
import java.util.*;

/** One event cue at a time; never affects the success of the user action. */
final class AppSounds {
  enum Event { UPDATE_REPLY, DOWNLOAD, INSTALL, POINT_CREATE, POINT_SAVE, POINT_DELETE, MONITOR_READY }
  private static final Handler main=new Handler(Looper.getMainLooper());
  private static final Random random=new Random();
  private static final Map<Event,Integer> previous=new EnumMap<>(Event.class);
  private static MediaPlayer player;
  private static Event current;
  private static AudioManager audio;
  private static AudioFocusRequest focus;
  static int[] variants(Event event) {
    switch(event){
      case UPDATE_REPLY:return new int[]{R.raw.update_reply_a,R.raw.update_reply_b};
      case DOWNLOAD:return new int[]{R.raw.update_download};
      case INSTALL:return new int[]{R.raw.update_install};
      case POINT_CREATE:return new int[]{R.raw.point_create};
      case POINT_SAVE:return new int[]{R.raw.point_create,R.raw.point_save_b};
      case POINT_DELETE:return new int[]{R.raw.point_delete};
      default:return new int[]{R.raw.monitor_ready};
    }
  }
  static int choose(Event event) {
    int[] choices=variants(event);int index=random.nextInt(choices.length);
    if(choices.length>1&&Objects.equals(previous.get(event),choices[index]))index=(index+1)%choices.length;
    previous.put(event,choices[index]);return choices[index];
  }
  static void play(Context context,Event event) {
    Context app=context.getApplicationContext();
    if(Looper.myLooper()!=Looper.getMainLooper()){main.post(()->play(app,event));return;}
    if(!Appearance.sounds(app))return;
    AudioManager manager=app.getSystemService(AudioManager.class);
    if(manager==null||manager.getRingerMode()!=AudioManager.RINGER_MODE_NORMAL)return;
    stop();audio=manager;current=event;
    AudioAttributes attributes=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
    focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes).setOnAudioFocusChangeListener(change->{if(change<0)stop();}).build();
    try{
      if(audio.requestAudioFocus(focus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED){stop();return;}
      player=MediaPlayer.create(app,choose(event),attributes,0);
      if(player==null){stop();return;}
      player.setOnCompletionListener(p->stop());player.setOnErrorListener((p,w,e)->{stop();return true;});player.start();
    }catch(RuntimeException unavailable){stop();}
  }
  static void stopMonitor(){if(current==Event.MONITOR_READY)stop();}
  static void stop(){
    current=null;
    if(player!=null){player.release();player=null;}
    if(audio!=null&&focus!=null)audio.abandonAudioFocusRequest(focus);
    focus=null;
  }
}
