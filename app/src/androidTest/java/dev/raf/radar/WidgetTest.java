package dev.raf.radar;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import android.content.*;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class WidgetTest {
  Overlay overlay;
  CaptureContext capture;
  Store store;
  static class CaptureContext extends ContextWrapper {
    Intent last;
    CaptureContext(Context context) { super(context); }
    @Override public void startActivity(Intent intent) { last = intent; }
  }
  @Before public void setup() throws Exception {
    Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
    c.stopService(new Intent(c, MonitorService.class));
    store = new Store(c); store.prefs.edit().clear().commit();
    ScreenTest.grantAccess();
    ScreenTest.shell("appops set dev.raf.radar SYSTEM_ALERT_WINDOW allow");
  }
  @After public void cleanup() {
    InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
      if (overlay != null) overlay.close();
    });
  }
  private void attach(MainActivity a) {
    capture = new CaptureContext(a);
    Point me = store.points().get(0);
    store.readings(me.id, java.util.Arrays.asList(
        new Store.Reading("econom", 260, null, System.currentTimeMillis(), "test"),
        new Store.Reading("business", 50, null, System.currentTimeMillis(), "test"),
        new Store.Reading("comfortplus", 50, null, System.currentTimeMillis(), "test")));
    store.prefs.edit().putBoolean("overlay", true).putInt("ox", 20).putInt("oy", 200).commit();
    overlay = new Overlay(capture, store); overlay.refresh();
  }
  private View root() {
    try {
      java.lang.reflect.Field field = Overlay.class.getDeclaredField("root");
      field.setAccessible(true); return (View) field.get(overlay);
    } catch (Exception e) { throw new AssertionError(e); }
  }
  private String texts(View v) {
    String value = v instanceof TextView ? ((TextView) v).getText().toString() : "";
    if (v instanceof android.view.ViewGroup) {
      android.view.ViewGroup group = (android.view.ViewGroup) v;
      for (int i = 0; i < group.getChildCount(); i++) value += "|" + texts(group.getChildAt(i));
    }
    return value;
  }
  private View find(View view, String description) {
    if (description.equals(view.getContentDescription())) return view;
    if (view instanceof android.view.ViewGroup) {
      android.view.ViewGroup group = (android.view.ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        View found = find(group.getChildAt(i), description);
        if (found != null) return found;
      }
    }
    return null;
  }
  private void event(int action, long start, long elapsed, float x, float y) {
    MotionEvent e = MotionEvent.obtain(start, start + elapsed, action, x, y, 0);
    root().dispatchTouchEvent(e); e.recycle();
  }
  @Test public void minimalContentsAndCloseButton() {
    try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
      scenario.onActivity(this::attach);
      InstrumentationRegistry.getInstrumentation().waitForIdleSync();
      scenario.onActivity(a -> {
        Assert.assertNotNull(root());
        String text = texts(root());
        Assert.assertFalse(text.contains("Моя геопозиция"));
        Assert.assertTrue(text.contains("Эконом  +260"));
        Assert.assertTrue(text.contains("Комфорт  +50"));
        Assert.assertTrue(text.contains("Комфорт+  +50"));
        Assert.assertFalse(text.contains("расчёт"));
        Assert.assertFalse(text.contains("↻")); Assert.assertFalse(text.contains("↗"));
      });
      ScreenTest.capture("widget-minimal-013");
      scenario.onActivity(a -> {
        int width=root().getWidth(),height=root().getHeight();
        find(root(), "Раскрыть меню виджета").performClick();
        Assert.assertEquals(width,root().getWidth());Assert.assertEquals(height,root().getHeight());
        Assert.assertNotSame(root(),ReferenceDesignTest.menu(overlay));
        View close = find(ReferenceDesignTest.menu(overlay), "Закрыть виджет");
        Assert.assertEquals("Закрыть виджет", close.getContentDescription());
        close.performClick();
        Assert.assertFalse(store.prefs.getBoolean("overlay", true));
        Assert.assertNull(root()); Assert.assertNull(capture.last);
      });
    }
  }
  @Test public void tapHoldDragAndSavedPosition() {
    try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
      scenario.onActivity(this::attach);
      InstrumentationRegistry.getInstrumentation().waitForIdleSync();
      scenario.onActivity(a -> {
        View view = root(); Assert.assertTrue(view.getHeight() > 0);
        float x = overlay.dp(18), y = view.getHeight() - overlay.dp(15);
        long t = android.os.SystemClock.uptimeMillis();
        event(MotionEvent.ACTION_DOWN, t, 0, x, y);
        event(MotionEvent.ACTION_UP, t, 100, x, y);
        Assert.assertNotNull(capture.last);
        Assert.assertFalse(capture.last.getBooleanExtra("widgetSettings", true));
        Assert.assertEquals(MainActivity.class.getName(), capture.last.getComponent().getClassName());
        capture.last = null;
        long hold = ViewConfiguration.getLongPressTimeout() + 100;
        event(MotionEvent.ACTION_DOWN, t + 1000, 0, x, y);
        event(MotionEvent.ACTION_MOVE, t + 1000, hold, x, y);
        Assert.assertNull(capture.last);
        event(MotionEvent.ACTION_UP, t + 1000, hold + 100, x, y);
        Assert.assertTrue(capture.last.getBooleanExtra("widgetSettings", false));
        capture.last = null;
        event(MotionEvent.ACTION_DOWN, t + 3000, 0, x, y);
        event(MotionEvent.ACTION_MOVE, t + 3000, hold, x + 60, y + 100);
        event(MotionEvent.ACTION_UP, t + 3000, hold + 100, x + 60, y + 100);
        Assert.assertNull(capture.last);
        Assert.assertTrue(store.prefs.getInt("oy", 0) > 200);
        int savedY = store.prefs.getInt("oy", 0);
        overlay.close(); overlay.refresh();
        Assert.assertEquals(savedY, ((WindowManager.LayoutParams) root().getLayoutParams()).y);
      });
      ScreenTest.capture("widget-moved-013");
    }
  }
  @Test public void holdIntentOpensSettingsAndDoesNotRepeatOnRecreate() {
    Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
    try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(
        new Intent(c, MainActivity.class).putExtra("widgetSettings", true))) {
      onView(withContentDescription("Вкладка Настройки")).check(matches(isSelected()));
      scenario.onActivity(a -> Assert.assertFalse(a.getIntent().hasExtra("widgetSettings")));
    }
  }
  @Test public void foregroundDemandExpiresOnBackground() {
    try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
      scenario.onActivity(a -> Assert.assertTrue(
          store.prefs.getLong("viewingUntil", 0) > System.currentTimeMillis()));
      scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED);
      Assert.assertEquals(0L, store.prefs.getLong("viewingUntil", -1));
      scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED);
      scenario.onActivity(a -> Assert.assertTrue(
          store.prefs.getLong("viewingUntil", 0) > System.currentTimeMillis()));
    }
  }
}
