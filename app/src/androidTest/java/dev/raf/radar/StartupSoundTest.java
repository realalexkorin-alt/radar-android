package dev.raf.radar;

import android.content.Context;
import android.media.MediaPlayer;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import static org.junit.Assert.*;

public class StartupSoundTest {
  private Context context() {
    return InstrumentationRegistry.getInstrumentation().getTargetContext();
  }
  @Before public void setup() {
    context().stopService(new android.content.Intent(context(), MonitorService.class));
    new Store(context()).prefs.edit().clear().putBoolean("autoUpdate", false).commit();
    ScreenTest.grantAccess();
  }
  @Test public void bundledClipDecodesAndPlaysWithoutLooping() throws Exception {
    MediaPlayer player = MediaPlayer.create(context(), R.raw.startup);
    assertNotNull("Bundled launch clip must decode", player);
    try {
      assertTrue(player.getDuration() > 8000 && player.getDuration() < 8600);
      assertFalse(player.isLooping());
      player.start();
      android.os.SystemClock.sleep(250);
      assertTrue("Decoded clip must enter playback", player.isPlaying());
    } finally { player.release(); }
  }
  @Test public void launchCueDoesNotRepeatAfterRecreationOrResume() {
    try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
      scenario.onActivity(a -> assertTrue(a.startupSound.attempted));
      scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED);
      scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED);
      scenario.onActivity(a -> assertFalse(a.startupSoundPending));
      scenario.recreate();
      scenario.onActivity(a -> {
        assertFalse(a.startupSoundPending);
        assertFalse("Rotation/theme recreation must not replay launch cue", a.startupSound.attempted);
      });
    }
  }
  @Test public void disabledCueIsSavedAndNeverStarts() {
    new Store(context()).prefs.edit().putBoolean("startupSound", false).commit();
    StartupSound cue = new StartupSound();
    cue.play(context(), false); cue.stop(); cue.stop();
    assertTrue(cue.attempted);
    assertFalse(new Store(context()).prefs.getBoolean("startupSound", true));
  }
}
