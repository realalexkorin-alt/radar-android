package dev.raf.radar;

import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.*;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ScreenTest {
  @Before
  public void reset() {
    android.content.Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
    c.stopService(new android.content.Intent(c, MonitorService.class));
    c.getSharedPreferences("radar", 0).edit().clear().commit();
    grantAccess();
  }

  static void grantAccess() {
    try {
      shell("pm grant dev.raf.radar android.permission.ACCESS_COARSE_LOCATION");
      shell("pm grant dev.raf.radar android.permission.ACCESS_FINE_LOCATION");
      if (android.os.Build.VERSION.SDK_INT >= 33)
        shell("pm grant dev.raf.radar android.permission.POST_NOTIFICATIONS");
      shell("appops set dev.raf.radar SYSTEM_ALERT_WINDOW allow");
      // Android 11 kills the UID whenever this app-op is set, even to its current
      // value. CI grants installation access before starting instrumentation.
      if (!InstrumentationRegistry.getInstrumentation().getTargetContext()
          .getPackageManager().canRequestPackageInstalls())
        throw new AssertionError("Grant REQUEST_INSTALL_PACKAGES before instrumentation");
    } catch (Exception e) { throw new AssertionError(e); }
  }

  @Test
  public void homeAndSettings() {
    try (ActivityScenario<MainActivity> a = ActivityScenario.launch(MainActivity.class)) {
      onView(withText("Радар Pro")).check(matches(isDisplayed()));
      capture("home-dark");
      onView(withContentDescription("Вкладка Настройки")).perform(click());
      onView(allOf(withText("Настройки"), not(isAssignableFrom(android.widget.Button.class)))).check(matches(isDisplayed()));
      onView(withText("Тёмная тема")).check(matches(isDisplayed()));
      capture("settings-dark");
      onView(withText("Тёмная тема")).perform(click());
      onView(withText("Применить оформление")).perform(scrollTo(), click());
      onView(withText("Радар Pro")).check(matches(isDisplayed()));
      capture("home-light");
    }
  }

  @Test
  public void savePointAndPreserveAfterRecreation() {
    try (ActivityScenario<MainActivity> a = ActivityScenario.launch(MainActivity.class)) {
      onView(withText("＋ Точка")).perform(click());
      onView(withHint("Адрес или широта, долгота"))
          .perform(
              replaceText("56.8389, 60.5975"),
              androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
      onView(withText("Найти")).perform(click());
      waitForText("Сохранить");
      onView(withText("Сохранить")).perform(click());
      a.recreate();
      a.onActivity(x -> Assert.assertEquals(2, new Store(x).points().size()));
    }
  }

  static void capture(String name) {
    InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    android.os.SystemClock.sleep(1500);
    try {
      android.graphics.Bitmap bitmap =
          InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
      if (bitmap == null) return;
      android.content.Context context =
          InstrumentationRegistry.getInstrumentation().getTargetContext();
      android.content.ContentValues values = new android.content.ContentValues();
      values.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name + ".png");
      values.put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png");
      values.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/RadarSmoke");
      android.net.Uri uri =
          context
              .getContentResolver()
              .insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
      if (uri != null)
        try (java.io.OutputStream out = context.getContentResolver().openOutputStream(uri)) {
          bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
        }
      bitmap.recycle();
    } catch (Exception e) {
      android.util.Log.w("RadarScreens", "Cannot capture test screen", e);
    }
  }

  static void shell(String command) throws Exception {
    try (android.os.ParcelFileDescriptor fd =
            InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .executeShellCommand(command);
        java.io.InputStream in = new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd)) {
      byte[] bytes = new byte[1024];
      while (in.read(bytes) != -1) {}
    }
  }

  @Test
  public void overlaySingleTariffAndScale() throws Exception {
    shell("pm grant dev.raf.radar android.permission.ACCESS_COARSE_LOCATION");
    shell("pm grant dev.raf.radar android.permission.ACCESS_FINE_LOCATION");
    shell("appops set dev.raf.radar SYSTEM_ALERT_WINDOW allow");
    try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
      scenario.onActivity(
          a -> {
            Store s = new Store(a);
            Point me = s.points().get(0);
            me.tariffs = java.util.Collections.singletonList("econom");
            s.update(me);
            s.prefs.edit().putBoolean("overlay", true).apply();
            a.startForegroundService(new android.content.Intent(a, MonitorService.class));
          });
      InstrumentationRegistry.getInstrumentation().waitForIdleSync();
      android.os.SystemClock.sleep(1000);
      Assert.assertTrue(MonitorService.running);
      capture("overlay-single");
      scenario.onActivity(
          a -> {
            Store s = new Store(a);
            s.prefs
                .edit()
                .putInt("scale", 150)
                .putInt("opacity", 40)
                .putInt("textScale", 120)
                .apply();
            a.startService(new android.content.Intent(a, MonitorService.class));
          });
      InstrumentationRegistry.getInstrumentation().waitForIdleSync();
      android.os.SystemClock.sleep(500);
      capture("overlay-scaled");
      scenario.onActivity(a -> a.stopService(new android.content.Intent(a, MonitorService.class)));
      InstrumentationRegistry.getInstrumentation().waitForIdleSync();
      onView(withText("Радар Pro")).check(matches(isDisplayed()));
    }
  }

  static void waitForText(String target) {
    onView(isRoot())
        .perform(
            new androidx.test.espresso.ViewAction() {
              public org.hamcrest.Matcher<android.view.View> getConstraints() {
                return isRoot();
              }

              public String getDescription() {
                return "Wait for point editor";
              }

              public void perform(androidx.test.espresso.UiController ui, android.view.View root) {
                long end = android.os.SystemClock.uptimeMillis() + 10000;
                do {
                  java.util.ArrayDeque<android.view.View> q = new java.util.ArrayDeque<>();
                  q.add(root.getRootView());
                  while (!q.isEmpty()) {
                    android.view.View v = q.remove();
                    if (v instanceof android.widget.TextView
                        && target.contentEquals(((android.widget.TextView) v).getText())
                        && v.isShown()) return;
                    if (v instanceof android.view.ViewGroup) {
                      android.view.ViewGroup g = (android.view.ViewGroup) v;
                      for (int i = 0; i < g.getChildCount(); i++) q.add(g.getChildAt(i));
                    }
                  }
                  ui.loopMainThreadForAtLeast(100);
                } while (android.os.SystemClock.uptimeMillis() < end);
              }
            });
  }

  @Test
  public void dynamicPointSettings() {
    try (ActivityScenario<MainActivity> a = ActivityScenario.launch(MainActivity.class)) {
      onView(withContentDescription("Вкладка Точки")).perform(click());
      onView(withText(startsWith("◎ Моя геопозиция"))).perform(click());
      onView(allOf(withText("Моя геопозиция"), isAssignableFrom(android.widget.EditText.class)))
          .check(matches(isDisplayed()));
      onView(withText("Уведомлять")).perform(scrollTo(), click());
      onView(withText("Сохранить")).perform(click());
      a.onActivity(
          x -> {
            Point p = new Store(x).points().get(0);
            Assert.assertTrue(p.dynamic);
            Assert.assertTrue(p.notify);
          });
    }
  }
  @Test public void tabsRetainMapAndPointMonitoringAfterRecreation() {
    try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
      scenario.onActivity(a -> {
        Point p = new Point("saved", "Дом", 55.75, 37.61, false);
        p.address = "Тестовый адрес"; a.store.update(p); a.refreshCards();
      });
      onView(withContentDescription("Вкладка Точки")).perform(click());
      onView(withText("Тестовый адрес")).check(matches(isDisplayed()));
      onView(withContentDescription("Мониторинг: Дом")).perform(click());
      scenario.recreate();
      onView(withContentDescription("Мониторинг: Дом")).check(matches(isNotChecked()));
      scenario.onActivity(a -> {
        Assert.assertEquals(1, a.currentTab);
        Assert.assertFalse(a.store.points().get(1).enabled);
        Assert.assertFalse(PollPlanner.needed(a.store.points().get(1), true, "saved"));
      });
      onView(withContentDescription("Вкладка Настройки")).perform(click());
      scenario.onActivity(a -> Assert.assertEquals(0L, a.store.prefs.getLong("viewingUntil", -1)));
      onView(withContentDescription("Вкладка Карта")).perform(click());
      scenario.onActivity(a -> {
        Assert.assertNotNull(a.mapView);
        Assert.assertEquals(android.view.View.VISIBLE, a.mapPanel.getVisibility());
        Assert.assertTrue(a.store.prefs.getLong("viewingUntil", 0) > System.currentTimeMillis());
      });
      capture("map-tabs-015");
    }
  }

  @Test public void cancellingMapPointDoesNotSaveIt() {
    try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
      scenario.onActivity(a -> a.edit(new Point("cancel", "Новая точка", 55.75, 37.61, false)));
      onView(withText(startsWith("Кеф:"))).check(matches(isDisplayed()));
      onView(withText("Отменить")).perform(click());
      scenario.onActivity(a -> Assert.assertEquals(1, a.store.points().size()));
    }
  }

  @Test public void pointSettingsSurviveUpgradeModelMigration() throws Exception {
    android.content.Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
    Store store = new Store(c);
    // A pre-0.1.5 payload has neither address nor enabled fields.
    org.json.JSONObject old = new org.json.JSONObject("{\"id\":\"legacy\",\"name\":\"Старая точка\",\"lat\":55,\"lon\":37,\"notify\":true,\"threshold\":350,\"tariffs\":[\"econom\"]}");
    store.save(java.util.Arrays.asList(Point.from(old)));
    try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
      scenario.onActivity(a -> {
        Point p = a.store.points().stream().filter(x -> x.id.equals("legacy")).findFirst().get();
        Assert.assertTrue(p.enabled); Assert.assertTrue(p.notify); Assert.assertEquals(350, p.threshold);
        Assert.assertEquals("", p.address);
      });
    }
  }

}
