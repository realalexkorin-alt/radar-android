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
  }

  @Test
  public void homeAndSettings() {
    try (ActivityScenario<MainActivity> a = ActivityScenario.launch(MainActivity.class)) {
      onView(withText("Радар")).check(matches(isDisplayed()));
      capture("home-dark");
      onView(withText("⚙")).perform(click());
      onView(withText("Настройки")).check(matches(isDisplayed()));
      onView(withText("Тёмная тема")).check(matches(isDisplayed()));
      capture("settings-dark");
      onView(withText("Тёмная тема")).perform(click());
      onView(withText("Готово")).perform(click());
      onView(withText("Радар")).check(matches(isDisplayed()));
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
    try {
      android.graphics.Bitmap b =
          InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
      if (b == null) return;
      java.io.File dir =
          new java.io.File(
              InstrumentationRegistry.getInstrumentation()
                  .getTargetContext()
                  .getExternalFilesDir(null),
              "screens");
      dir.mkdirs();
      try (java.io.FileOutputStream out =
          new java.io.FileOutputStream(new java.io.File(dir, name + ".png"))) {
        b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
      }
      b.recycle();
    } catch (Exception ignored) {
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
}
