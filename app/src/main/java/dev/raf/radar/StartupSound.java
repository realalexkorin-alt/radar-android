package dev.raf.radar;

import android.content.Context;
import android.media.*;

/** One short launch cue; never loop or restart on resume/configuration changes. */
final class StartupSound {
  private MediaPlayer player;
  private AudioManager audio;
  private AudioFocusRequest focus;
  boolean attempted;

  void play(Context context, boolean enabled) {
    if (attempted) return;
    attempted = true;
    if (!enabled || !Appearance.sounds(context)) return;
    audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    if (audio == null || audio.getRingerMode() != AudioManager.RINGER_MODE_NORMAL) return;
    AudioAttributes attributes = new AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
    focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes).setOnAudioFocusChangeListener(change -> {
          if (change < 0) stop();
        }).build();
    try {
      if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
        stop(); return;
      }
      player = MediaPlayer.create(context, Appearance.lineage(context)?R.raw.l2_game_start:R.raw.startup, attributes, 0);
      if (player == null) { stop(); return; }
      if(Appearance.lineage(context))player.setVolume(0.45f,0.45f);
      player.setLooping(false);
      player.setOnCompletionListener(completed -> stop());
      player.setOnErrorListener((failed, what, extra) -> { stop(); return true; });
      player.start();
    } catch (RuntimeException unavailable) {
      stop(); // Playback must never prevent opening the app.
    }
  }

  void stop() {
    MediaPlayer current = player;
    player = null;
    if (current != null) current.release();
    AudioFocusRequest request = focus;
    focus = null;
    if (audio != null && request != null) audio.abandonAudioFocusRequest(request);
  }
}
