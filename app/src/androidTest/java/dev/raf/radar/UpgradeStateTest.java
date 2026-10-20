package dev.raf.radar;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import static org.junit.Assert.*;

/** CI installs 0.1.5 debug, seeds settings, then performs adb install -r of the candidate. */
public class UpgradeStateTest {
  private Context context() {
    return InstrumentationRegistry.getInstrumentation().getTargetContext();
  }
  @Test public void seedBaseline() throws Exception {
    Context c = context();
    assertEquals(6L, c.getPackageManager().getPackageInfo(c.getPackageName(), 0).getLongVersionCode());
    assertTrue(c.getSharedPreferences("radar", 0).edit().clear()
        .putString("points", "[{\"id\":\"upgrade-home\",\"name\":\"Дом\",\"lat\":55.75,\"lon\":37.61,\"dynamic\":false,\"notify\":true,\"threshold\":350,\"tariffs\":[\"econom\",\"business\"]}]")
        .putInt("opacity", 65).putInt("scale", 130).putInt("textScale", 120)
        .putInt("ox", 37).putInt("oy", 210).putBoolean("dark", false)
        .putInt("interval", 180).putString("overlayPoint", "upgrade-home").commit());
  }
  @Test public void verifyCandidatePreservedData() throws Exception {
    Context c = context();
    assertEquals(12L, c.getPackageManager().getPackageInfo(c.getPackageName(), 0).getLongVersionCode());
    Store store = new Store(c);
    Point home = store.points().stream().filter(p -> p.id.equals("upgrade-home")).findFirst().get();
    assertEquals("Дом", home.name); assertEquals(350, home.threshold);
    assertTrue(home.notify); assertTrue(home.enabled); assertEquals("", home.address);
    assertEquals(java.util.Arrays.asList("econom", "business"), home.tariffs);
    assertEquals(65, store.prefs.getInt("opacity", 0)); assertEquals(130, store.prefs.getInt("scale", 0));
    assertEquals(120, store.prefs.getInt("textScale", 0)); assertEquals(180, store.prefs.getInt("interval", 0));
    assertEquals(37, store.prefs.getInt("ox", 0)); assertEquals(210, store.prefs.getInt("oy", 0));
    assertFalse(store.prefs.getBoolean("dark", true));
    assertEquals("upgrade-home", store.prefs.getString("overlayPoint", ""));
    assertEquals(java.util.Arrays.asList("econom", "business"), store.widgetTariffs());
  }
}
