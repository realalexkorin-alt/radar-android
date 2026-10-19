package dev.raf.radar;
import static org.junit.Assert.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static org.hamcrest.Matchers.*;

public class AccessTest {
  @Before public void setup() {
    android.content.Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
    c.stopService(new android.content.Intent(c, MonitorService.class));
    new Store(c).prefs.edit().clear().commit(); ScreenTest.grantAccess();
  }
  @Test public void readyEntryNeedsNoPermissionDialogAndChannelIsFixed() {
    try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
      scenario.onActivity(a -> { assertTrue(Access.monitorReady(a)); assertNull(Access.missing(a));
        new Store(a).prefs.edit().putString("repo", "wrong/repository").commit(); });
      onView(withText("Разрешения для Радара")).check(doesNotExist());
      onView(withText("⚙")).perform(click());
      onView(withText("Проверка обновлений при каждом запуске. Канал GitHub подключён.")).perform(scrollTo()).check(matches(isDisplayed()));
      onView(withHint("GitHub: владелец/репозиторий")).check(doesNotExist());
      assertEquals("realalexkorin-alt/radar-android", Updates.DEFAULT_REPO);
    }
  }
  @Test public void downloadValidationRejectsCorruptCachedApk() {
    try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
      scenario.onActivity(a -> {
        try {
          java.io.File f = new java.io.File(a.getCacheDir(), "corrupt-test.apk");
          try (java.io.FileOutputStream out = new java.io.FileOutputStream(f)) { out.write(new byte[] {1,2,3}); }
          try { Updates.verify(a, f, "sha256:" + String.join("", java.util.Collections.nCopies(64, "0"))); fail(); }
          catch (java.io.IOException expected) { assertTrue(expected.getMessage().contains("Контрольная сумма")); }
          finally { f.delete(); }
        } catch (Exception e) { throw new AssertionError(e); }
      });
    }
  }
}
