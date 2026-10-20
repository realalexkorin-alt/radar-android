package dev.raf.radar;

import android.view.accessibility.AccessibilityNodeInfo;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;

/** Runs alone on a clean emulator installation, before the ordinary granted-access suite. */
public class AccessEntryTest {
  private boolean launcherRecovered;
  private AccessibilityNodeInfo button(String suffix) {
    long end = android.os.SystemClock.uptimeMillis() + 10000;
    String observed="";
    do {
      StringBuilder nodes=new StringBuilder();
      AccessibilityNodeInfo launcherClose=null;
      android.app.UiAutomation automation=InstrumentationRegistry.getInstrumentation().getUiAutomation();
      java.util.ArrayDeque<AccessibilityNodeInfo> queue=new java.util.ArrayDeque<>();
      AccessibilityNodeInfo root=automation.getRootInActiveWindow();if(root!=null)queue.add(root);
      for(android.view.accessibility.AccessibilityWindowInfo window:automation.getWindows()){
        AccessibilityNodeInfo candidate=window.getRoot();if(candidate!=null)queue.add(candidate);
      }
      {
        while (!queue.isEmpty()) {
          AccessibilityNodeInfo n = queue.remove();
          String id = n.getViewIdResourceName();
          nodes.append("[").append(n.getPackageName()).append(" ").append(id).append(" ").append(n.getText()).append(" visible=").append(n.isVisibleToUser()).append("]\n");
          if ("android:id/aerr_close".equals(id) && n.isVisibleToUser()) launcherClose=n;
          if (id != null && id.endsWith(":id/" + suffix) && n.isVisibleToUser()) return n;
          for (int i = 0; i < n.getChildCount(); i++) { AccessibilityNodeInfo child = n.getChild(i); if (child != null) queue.add(child); }
        }
      }
      observed=nodes.toString();
      // A system launcher ANR can cover the permission prompt on fresh API 35.
      // Recover only the observed Quickstep dialog, never a Radar error dialog.
      if(!launcherRecovered && launcherClose!=null && observed.contains("Quickstep isn't responding")){
        launcherRecovered=true;launcherClose.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        System.out.println("Recovered system Quickstep ANR before permission assertion");
        end=android.os.SystemClock.uptimeMillis()+10000;
      }
      android.os.SystemClock.sleep(100);
    } while (android.os.SystemClock.uptimeMillis() < end);
    ScreenTest.capture("permission-button-missing");
    throw new AssertionError("Permission button missing: " + suffix + "\n" + observed);
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
  private void shell(String command) {
    try (android.os.ParcelFileDescriptor p = InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command);
         java.io.InputStream in = new android.os.ParcelFileDescriptor.AutoCloseInputStream(p)) {
      byte[] b = new byte[1024]; while (in.read(b) != -1) {}
    } catch (Exception e) { throw new AssertionError(e); }
  }
  private void waitForSettings() {
    long end = android.os.SystemClock.uptimeMillis() + 10000;
    do {
      AccessibilityNodeInfo root = InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow();
      if (root != null && "com.android.settings".contentEquals(root.getPackageName())) return;
      android.os.SystemClock.sleep(100);
    } while (android.os.SystemClock.uptimeMillis() < end);
    throw new AssertionError("System settings did not open");
  }
  @Test public void denialIsGatedAndNextEntryRequestsAgain() throws Exception {
    android.content.Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
    android.accessibilityservice.AccessibilityServiceInfo info = InstrumentationRegistry.getInstrumentation().getUiAutomation().getServiceInfo();
    info.flags |= android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS | android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
    InstrumentationRegistry.getInstrumentation().getUiAutomation().setServiceInfo(info);
    Assert.assertFalse(Access.location(c));
    try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
      button("permission_deny_button").performAction(AccessibilityNodeInfo.ACTION_CLICK);
      if (android.os.Build.VERSION.SDK_INT >= 33)
        button("permission_allow_button").performAction(AccessibilityNodeInfo.ACTION_CLICK);
      waitForAppDialog();
      onView(withText("Разрешения для Радара")).inRoot(isDialog()).check(matches(isDisplayed()));
      Assert.assertFalse(Access.monitorReady(c)); Assert.assertFalse(MonitorService.running);
      onView(withText("Позже")).inRoot(isDialog()).perform(click());
      scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED);
      // Android 15 can pause the activity for the runtime prompt before
      // ActivityScenario observes RESUMED. Answer that prompt while the
      // lifecycle transition is pending instead of deadlocking the test.
      java.util.concurrent.ExecutorService lifecycle = java.util.concurrent.Executors.newSingleThreadExecutor();
      try {
        java.util.concurrent.Future<?> returning = lifecycle.submit(
            () -> scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED));
        button("permission_allow_foreground_only_button").performAction(AccessibilityNodeInfo.ACTION_CLICK);
        returning.get(15, java.util.concurrent.TimeUnit.SECONDS);
      } finally { lifecycle.shutdownNow(); }
      waitForAppDialog();
      onView(withText("Разрешения для Радара")).inRoot(isDialog()).check(matches(isDisplayed()));
      Assert.assertTrue(Access.location(c));
      Assert.assertEquals("Поверх других приложений", Access.missing(c));
      onView(withText("Разрешить")).inRoot(isDialog()).perform(click());
      waitForSettings();
      shell("input keyevent 4");
      waitForAppDialog();
      Assert.assertEquals("Поверх других приложений", Access.missing(c));
      // Special permission changes may kill the app UID on Android 11. Grant them
      // outside instrumentation in CI; the next suite verifies a fresh ready entry.
    }
  }
}
