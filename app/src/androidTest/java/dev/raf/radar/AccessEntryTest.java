package dev.raf.radar;

import android.view.accessibility.AccessibilityNodeInfo;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;

/** Runs alone on a clean emulator installation, before the ordinary granted-access suite. */
public class AccessEntryTest {
  private AccessibilityNodeInfo button(String suffix) {
    long end = android.os.SystemClock.uptimeMillis() + 10000;
    do {
      AccessibilityNodeInfo root = InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow();
      if (root != null) {
        java.util.ArrayDeque<AccessibilityNodeInfo> queue = new java.util.ArrayDeque<>(); queue.add(root);
        while (!queue.isEmpty()) {
          AccessibilityNodeInfo n = queue.remove();
          String id = n.getViewIdResourceName();
          if (id != null && id.endsWith(":id/" + suffix) && n.isVisibleToUser()) return n;
          for (int i = 0; i < n.getChildCount(); i++) { AccessibilityNodeInfo child = n.getChild(i); if (child != null) queue.add(child); }
        }
      }
      android.os.SystemClock.sleep(100);
    } while (android.os.SystemClock.uptimeMillis() < end);
    throw new AssertionError("Permission button missing: " + suffix);
  }
  private void waitForAppDialog() {
    long end = android.os.SystemClock.uptimeMillis() + 10000;
    do {
      AccessibilityNodeInfo root = InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow();
      if (root != null && !root.findAccessibilityNodeInfosByText("Разрешения для Радара").isEmpty()) return;
      android.os.SystemClock.sleep(100);
    } while (android.os.SystemClock.uptimeMillis() < end);
    throw new AssertionError("Access explanation did not appear");
  }
  @Test public void denialIsGatedAndNextEntryRequestsAgain() {
    android.content.Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
    android.accessibilityservice.AccessibilityServiceInfo info = InstrumentationRegistry.getInstrumentation().getUiAutomation().getServiceInfo();
    info.flags |= android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
    InstrumentationRegistry.getInstrumentation().getUiAutomation().setServiceInfo(info);
    Assert.assertFalse(Access.location(c));
    try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
      button("permission_deny_button").performAction(AccessibilityNodeInfo.ACTION_CLICK);
      if (android.os.Build.VERSION.SDK_INT >= 33)
        button("permission_allow_button").performAction(AccessibilityNodeInfo.ACTION_CLICK);
      waitForAppDialog();
      onView(withText("Разрешения для Радара")).check(matches(isDisplayed()));
      Assert.assertFalse(Access.monitorReady(c)); Assert.assertFalse(MonitorService.running);
      onView(withText("Позже")).perform(click());
      scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED);
      scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED);
      button("permission_allow_foreground_only_button").performAction(AccessibilityNodeInfo.ACTION_CLICK);
      waitForAppDialog();
      onView(withText("Разрешения для Радара")).check(matches(isDisplayed()));
      Assert.assertTrue(Access.location(c));
      Assert.assertEquals("Поверх других приложений", Access.missing(c));
    }
  }
}
