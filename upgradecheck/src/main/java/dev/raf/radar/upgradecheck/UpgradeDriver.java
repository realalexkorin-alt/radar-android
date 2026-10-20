package dev.raf.radar.upgradecheck;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.*;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayDeque;

/** Separate UID: it stays alive when installing the update terminates Radar's process. */
public final class UpgradeDriver extends Instrumentation {
  @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
  @Override public void onStart() {
    Bundle result = new Bundle();
    try {
      android.accessibilityservice.AccessibilityServiceInfo info = getUiAutomation().getServiceInfo();
      info.flags |= android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
      getUiAutomation().setServiceInfo(info);
      long before = getContext().getPackageManager().getPackageInfo("dev.raf.radar", 0).getLongVersionCode();
      long expected = before + 1;
      getContext().startActivity(new Intent().setClassName("dev.raf.radar", "dev.raf.radar.MainActivity")
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      clickInstallerConfirmation();
      long deadline = SystemClock.uptimeMillis() + 120000;
      while (getContext().getPackageManager().getPackageInfo("dev.raf.radar", 0).getLongVersionCode() != expected) {
        if (SystemClock.uptimeMillis() >= deadline) throw new AssertionError("System installer did not install version " + expected);
        SystemClock.sleep(250);
      }
      result.putString("stream", "\nOK (1 upgrade) — Radar verified the APK, opened the installer and updated " + before + " → " + expected + "\n");
      finish(android.app.Activity.RESULT_OK, result);
    } catch (Throwable e) {
      result.putString("stream", "\nUPGRADE FAILED: " + android.util.Log.getStackTraceString(e));
      finish(android.app.Activity.RESULT_CANCELED, result);
    }
  }
  private void clickInstallerConfirmation() {
    long deadline = SystemClock.uptimeMillis() + 60000;
    String last = "no active window";
    while (SystemClock.uptimeMillis() < deadline) {
      AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
      if (root != null) {
        last = String.valueOf(root.getPackageName());
        String pkg = String.valueOf(root.getPackageName());
        if (pkg.contains("packageinstaller") || pkg.equals("com.android.permissioncontroller")) {
          ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>(); queue.add(root);
          while (!queue.isEmpty()) {
            AccessibilityNodeInfo node = queue.remove();
            String text = node.getText() == null ? "" : node.getText().toString();
            if ((text.equalsIgnoreCase("Install") || text.equalsIgnoreCase("Update"))
                && node.isEnabled() && node.isVisibleToUser()) {
              if (!node.isClickable() && node.getParent() != null) node = node.getParent();
              if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return;
            }
            for (int i = 0; i < node.getChildCount(); i++) {
              AccessibilityNodeInfo child = node.getChild(i); if (child != null) queue.add(child);
            }
          }
        }
      }
      SystemClock.sleep(200);
    }
    throw new AssertionError("Installer confirmation missing, active window: " + last);
  }
}
